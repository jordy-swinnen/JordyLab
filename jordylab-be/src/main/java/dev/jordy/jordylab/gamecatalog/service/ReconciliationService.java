package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final GameInstallationRepository gameInstallationRepository;
    private final GameIdentityService gameIdentityService;
    private final PlaceRemovalService placeRemovalService;
    private final GameCatalogProperties properties;
    private final Clock clock;

    public ReconciliationCounts applySnapshot(ScanSource source, List<GamePayload> validEntries, Instant snapshotTime) {
        Map<String, GamePayload> entriesByRef = deduplicateByExternalRef(validEntries);
        Map<String, GameInstallation> existingByRef = gameInstallationRepository
                .findAllBySourceId(source.getId()).stream()
                .collect(Collectors.toMap(GameInstallation::getExternalRef, Function.identity()));

        int added = 0;
        int updated = 0;
        for (GamePayload entry : entriesByRef.values()) {
            GameInstallation existing = existingByRef.get(entry.externalRef());
            if (existing == null) {
                addInstallationFor(source, entry, snapshotTime);
                added++;
                continue;
            }
            if (refreshExisting(existing, entry)) {
                updated++;
            }
            existing.seenAgain(snapshotTime);
        }

        int removed = hideMissingInstallations(existingByRef, entriesByRef, snapshotTime);

        return new ReconciliationCounts(added, updated, removed);
    }

    /**
     * Deletes installations past the grace period and any game left with no place at all: no installation, no console
     * entry, and no library entry that is active or within grace. Invoked inline by the scan and library flows (FR-014).
     */
    @Transactional
    public void purgeUninstalledGames() {
        Instant cutoff = clock.instant().minus(properties.gracePeriodDays(), ChronoUnit.DAYS);
        List<GameInstallation> expired = gameInstallationRepository
                .findByPresenceAndUninstalledAtBefore(Presence.UNINSTALLED, cutoff);
        if (expired.isEmpty()) {
            return;
        }

        Set<UUID> affectedGameIds = expired.stream()
                .map(installation -> installation.getGame().getId())
                .collect(Collectors.toSet());
        gameInstallationRepository.deleteAll(expired);
        gameInstallationRepository.flush();

        int purgedGames = 0;
        for (UUID gameId : affectedGameIds) {
            if (placeRemovalService.releaseGameIfOrphaned(gameId)) {
                purgedGames++;
            }
        }

        log.info("Purged {} installation(s) and {} orphaned game(s) past the {}-day grace period",
                expired.size(), purgedGames, properties.gracePeriodDays());
    }

    private Map<String, GamePayload> deduplicateByExternalRef(List<GamePayload> validEntries) {
        return validEntries.stream()
                .collect(Collectors.toMap(GamePayload::externalRef, Function.identity(), (first, duplicate) -> first,
                        LinkedHashMap::new));
    }

    /** True when the title or platform of an already known installation changed. */
    private boolean refreshExisting(GameInstallation existing, GamePayload entry) {
        boolean changed = false;
        Game game = existing.getGame();
        if (!game.getTitle().equals(entry.title())) {
            game.updateCatalogInfo(entry.title(), titleSourceFor(entry));
            changed = true;
        }
        String platform = PlatformCatalog.canonical(entry.platform());
        if (!platform.equals(existing.getPlatform())) {
            existing.updatePlatform(platform);
            changed = true;
        }

        return changed;
    }

    /**
     * Links the submitted entry to an existing host-independent game when it is recognised (Steam: app id; anything else:
     * normalised title), otherwise creates a new one. Adoption only adds this source's installation: the game's enrichment,
     * metadata and artwork are never touched (FR-003, FR-005, FR-025).
     */
    private void addInstallationFor(ScanSource source, GamePayload entry, Instant snapshotTime) {
        Game game = resolveOrCreate(entry);
        gameInstallationRepository.save(GameInstallation.builder()
                .game(game)
                .source(source)
                .externalRef(entry.externalRef())
                .platform(PlatformCatalog.canonical(entry.platform()))
                .firstSeenAt(snapshotTime)
                .lastSeenAt(snapshotTime)
                .build());
    }

    private Game resolveOrCreate(GamePayload entry) {
        if (PlatformCatalog.STEAM.equals(PlatformCatalog.canonical(entry.platform()))) {
            return gameIdentityService.resolveOrCreateSteamGame(entry.externalRef(), entry.title(), TitleSource.MANIFEST);
        }

        return gameIdentityService.resolveOrCreateByTitle(entry.title(), TitleSource.ROM);
    }

    private TitleSource titleSourceFor(GamePayload entry) {
        return PlatformCatalog.STEAM.equals(PlatformCatalog.canonical(entry.platform())) ? TitleSource.MANIFEST
                : TitleSource.ROM;
    }

    private int hideMissingInstallations(Map<String, GameInstallation> existingByRef,
            Map<String, GamePayload> entriesByRef, Instant snapshotTime) {
        int removed = 0;
        for (GameInstallation existing : existingByRef.values()) {
            if (existing.isInstalled() && !entriesByRef.containsKey(existing.getExternalRef())) {
                existing.markUninstalled(snapshotTime);
                removed++;
            }
        }

        return removed;
    }
}
