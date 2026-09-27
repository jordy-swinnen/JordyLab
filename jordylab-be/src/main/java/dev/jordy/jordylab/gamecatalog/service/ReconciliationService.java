package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final GameRepository gameRepository;
    private final GameInstallationRepository gameInstallationRepository;
    private final GameLibraryEntryRepository gameLibraryEntryRepository;
    private final GameCatalogProperties properties;

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
            Game game = existing.getGame();
            if (!game.getTitle().equals(entry.title()) || !game.getPlatform().equals(entry.platform())) {
                game.updateCatalogInfo(entry.title(), entry.platform(), titleSourceFor(entry));
                updated++;
            }
            existing.seenAgain(snapshotTime);
        }

        int removed = hideMissingInstallations(existingByRef, entriesByRef, snapshotTime);

        return new ReconciliationCounts(added, updated, removed);
    }

    /**
     * Resolves the host-independent game for a Steam app ID, creating it only when absent. The
     * insert is race-safe through the unique index; a concurrent creator is adopted instead
     * (FR-001, FR-002, SC-004). The title follows the reporting source's authority (FR-012).
     */
    @Transactional
    public Game resolveOrCreateSteamGame(String steamAppId, String title, TitleSource titleSource) {        Optional<Game> existing = gameRepository.findBySteamAppId(steamAppId);
        if (existing.isPresent()) {
            Game game = existing.get();
            game.updateCatalogInfo(title, SourceType.STEAM.platform(), titleSource);

            return game;
        }

        gameRepository.insertSteamGameIfAbsent(UUID.randomUUID(), SourceType.STEAM.platform(), steamAppId, title,
                titleSource.name());

        return gameRepository.findBySteamAppId(steamAppId)
                .orElseThrow(() -> new IllegalStateException("Could not create or adopt Steam game " + steamAppId));
    }

    /**
     * Deletes installations past the grace period and any game left with no installations, no
     * active library entry, and no library entry within grace. Invoked inline by the scan and
     * library flows — there is no scheduler (FR-014).
     */
    public void purgeUninstalledGames() {
        Instant cutoff = Instant.now().minus(properties.gracePeriodDays(), ChronoUnit.DAYS);
        List<GameInstallation> expired = gameInstallationRepository
                .findByPresenceAndUninstalledAtBefore(Presence.UNINSTALLED, cutoff);
        if (expired.isEmpty()) {
            return;
        }

        Set<UUID> affectedGameIds = expired.stream()
                .map(installation -> installation.getGame().getId())
                .collect(Collectors.toSet());
        gameInstallationRepository.deleteAll(expired);

        int purgedGames = 0;
        for (UUID gameId : affectedGameIds) {
            if (gameInstallationRepository.countByGameId(gameId) > 0 || heldInLibrary(gameId, cutoff)) {
                continue;
            }
            Optional<Game> orphanedGame = gameRepository.findById(gameId);
            if (orphanedGame.isPresent()) {
                deleteLocalArtworkFile(orphanedGame.get());
                gameRepository.delete(orphanedGame.get());
                purgedGames++;
            }
        }

        log.info("Purged {} installation(s) and {} orphaned game(s) past the {}-day grace period",
                expired.size(), purgedGames, properties.gracePeriodDays());
    }

    private boolean heldInLibrary(UUID gameId, Instant cutoff) {
        return gameLibraryEntryRepository.existsByGameIdAndRemovedAtIsNull(gameId)
                || gameLibraryEntryRepository.existsByGameIdAndRemovedAtAfter(gameId, cutoff);
    }

    private Map<String, GamePayload> deduplicateByExternalRef(List<GamePayload> validEntries) {
        return validEntries.stream()
                .collect(Collectors.toMap(GamePayload::externalRef, Function.identity(), (first, duplicate) -> first,
                        LinkedHashMap::new));
    }

    /**
     * Links the submitted entry to an existing host-independent game when it is recognized
     * (Steam: app ID alone; ROM: platform + normalized title), otherwise creates a new one.
     * Adoption only adds this source's installation — the game's enrichment, metadata, and
     * artwork are never touched (FR-003/FR-005).
     */
    private void addInstallationFor(ScanSource source, GamePayload entry, Instant snapshotTime) {
        Game game = resolveOrCreate(entry);
        gameInstallationRepository.save(GameInstallation.builder()
                .game(game)
                .source(source)
                .externalRef(entry.externalRef())
                .firstSeenAt(snapshotTime)
                .lastSeenAt(snapshotTime)
                .build());
    }

    private Game resolveOrCreate(GamePayload entry) {
        if (SourceType.STEAM.platform().equals(entry.platform())) {
            return resolveOrCreateSteamGame(entry.externalRef(), entry.title(), TitleSource.MANIFEST);
        }

        return gameRepository.findByPlatformAndLowercaseTitle(entry.platform(), entry.title(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseGet(() -> gameRepository.save(Game.builder()
                        .platform(entry.platform())
                        .title(entry.title())
                        .titleSource(TitleSource.ROM)
                        .build()));
    }

    private TitleSource titleSourceFor(GamePayload entry) {
        return SourceType.STEAM.platform().equals(entry.platform()) ? TitleSource.MANIFEST : TitleSource.ROM;
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

    private void deleteLocalArtworkFile(Game game) {
        if (game.getCoverStatus() != ArtworkStatus.LOCAL_UPLOAD || game.getCoverRef() == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(properties.artwork().dir()).resolve(game.getCoverRef()).normalize());
        } catch (IOException exception) {
            log.warn("Could not delete artwork file for purged game {}: {}", game.getId(), exception.getMessage());
        }
    }
}
