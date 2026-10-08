package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Removes a game only when its last place is gone (spec 013 FR-026): no installed or grace-period copy, no console
 * entry, and no Steam library entry that is active or still within the grace period. One implementation for the
 * grace-period purge, console removal and console game removal, so a game's marks, search-index row, library entries
 * and local artwork file always leave with it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceRemovalService {

    private final GameRepository gameRepository;
    private final GameInstallationRepository gameInstallationRepository;
    private final GameLibraryEntryRepository gameLibraryEntryRepository;
    private final ConsoleGameEntryRepository consoleGameEntryRepository;
    private final GameMarkRepository gameMarkRepository;
    private final GameEmbeddingRepository gameEmbeddingRepository;
    private final GameCatalogProperties properties;
    private final Clock clock;

    /** Deletes the game when no place holds it. Returns true when the game was deleted. */
    @Transactional
    public boolean releaseGameIfOrphaned(UUID gameId) {
        if (gameInstallationRepository.countByGameId(gameId) > 0 || consoleGameEntryRepository.existsByGameId(gameId)
                || heldInLibrary(gameId)) {
            return false;
        }
        Optional<Game> orphan = gameRepository.findById(gameId);
        if (orphan.isEmpty()) {
            return false;
        }
        // Any library entries left are past their grace period: the FK has no cascade, so they go first.
        gameLibraryEntryRepository.deleteAll(gameLibraryEntryRepository.findAllByGameId(gameId));
        gameMarkRepository.deleteAllByGameId(gameId);
        gameEmbeddingRepository.deleteById(gameId);
        deleteLocalArtworkFile(orphan.get());
        gameRepository.delete(orphan.get());
        log.info("Removed game '{}' ({}): its last place is gone", orphan.get().getTitle(), gameId);

        return true;
    }

    /** Whether the game would still have a place if the given console let go of it (for the removal dialog's counts). */
    @Transactional(readOnly = true)
    public boolean isHeldOutsideConsole(UUID gameId, UUID consoleId) {
        long otherConsoleEntries = consoleGameEntryRepository.countByGameId(gameId)
                - (consoleGameEntryRepository.existsByGameIdAndConsoleId(gameId, consoleId) ? 1 : 0);

        return gameInstallationRepository.countByGameId(gameId) > 0 || otherConsoleEntries > 0 || heldInLibrary(gameId);
    }

    private boolean heldInLibrary(UUID gameId) {
        Instant cutoff = clock.instant().minus(properties.gracePeriodDays(), ChronoUnit.DAYS);

        return gameLibraryEntryRepository.existsByGameIdAndRemovedAtIsNull(gameId)
                || gameLibraryEntryRepository.existsByGameIdAndRemovedAtAfter(gameId, cutoff);
    }

    private void deleteLocalArtworkFile(Game game) {
        if (game.getCoverStatus() != ArtworkStatus.LOCAL_UPLOAD || game.getCoverRef() == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(properties.artwork().dir()).resolve(game.getCoverRef()).normalize());
        } catch (IOException exception) {
            log.warn("Could not delete artwork file for removed game {}: {}", game.getId(), exception.getMessage());
        }
    }
}
