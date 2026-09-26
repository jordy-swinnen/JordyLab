package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final GameRepository gameRepository;
    private final GameCatalogProperties properties;

    public ReconciliationCounts applySnapshot(ScanSource source, List<GamePayload> validEntries, Instant snapshotTime) {
        Map<String, GamePayload> entriesByRef = deduplicateByExternalRef(validEntries);
        Map<String, Game> existingByRef = gameRepository.findAllBySourceId(source.getId()).stream()
                .collect(Collectors.toMap(Game::getExternalRef, Function.identity()));

        int added = 0;
        int updated = 0;
        for (GamePayload entry : entriesByRef.values()) {
            Game existing = existingByRef.get(entry.externalRef());
            if (existing == null) {
                gameRepository.save(newGameFrom(source, entry, snapshotTime));
                added++;
                continue;
            }
            if (!existing.getTitle().equals(entry.title()) || !existing.getPlatform().equals(entry.platform())) {
                existing.updateCatalogInfo(entry.title(), entry.platform());
                updated++;
            }
            existing.seenAgain(snapshotTime);
        }

        int removed = hideMissingGames(existingByRef, entriesByRef, snapshotTime);

        return new ReconciliationCounts(added, updated, removed);
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void purgeUninstalledGames() {
        Instant cutoff = Instant.now().minus(properties.gracePeriodDays(), ChronoUnit.DAYS);
        List<Game> expired = gameRepository.findByPresenceAndUninstalledAtBefore(Presence.UNINSTALLED, cutoff);
        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(this::deleteLocalArtworkFile);
        gameRepository.deleteAll(expired);
        log.info("Purged {} uninstalled game(s) past the {}-day grace period", expired.size(),
                properties.gracePeriodDays());
    }

    private Map<String, GamePayload> deduplicateByExternalRef(List<GamePayload> validEntries) {
        return validEntries.stream()
                .collect(Collectors.toMap(GamePayload::externalRef, Function.identity(), (first, duplicate) -> first,
                        LinkedHashMap::new));
    }

    private Game newGameFrom(ScanSource source, GamePayload entry, Instant snapshotTime) {
        return Game.builder()
                .source(source)
                .platform(entry.platform())
                .externalRef(entry.externalRef())
                .title(entry.title())
                .firstSeenAt(snapshotTime)
                .lastSeenAt(snapshotTime)
                .build();
    }

    private int hideMissingGames(Map<String, Game> existingByRef, Map<String, GamePayload> entriesByRef,
            Instant snapshotTime) {
        int removed = 0;
        for (Game existing : existingByRef.values()) {
            if (existing.getPresence() == Presence.INSTALLED && !entriesByRef.containsKey(existing.getExternalRef())) {
                existing.markUninstalled(snapshotTime);
                removed++;
            }
        }

        return removed;
    }

    private void deleteLocalArtworkFile(Game game) {
        if (game.getArtworkStatus() != ArtworkStatus.LOCAL_UPLOAD || game.getArtworkRef() == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(properties.artwork().dir()).resolve(game.getArtworkRef()).normalize());
        } catch (IOException exception) {
            log.warn("Could not delete artwork file for purged game {}: {}", game.getId(), exception.getMessage());
        }
    }
}
