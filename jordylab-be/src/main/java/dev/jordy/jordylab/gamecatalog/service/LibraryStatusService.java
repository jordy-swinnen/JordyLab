package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncRun;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.LibrarySyncRunRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamOwnedGamesClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibrarySourceStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Read-only library status for the settings page: last run times and outcomes, active entry
 * counts, the cost of the last run, and a staleness hint for the family library. Never exposes
 * the family token value (FR-011).
 */
@Service
@RequiredArgsConstructor
public class LibraryStatusService {

    private final LibrarySyncRunRepository librarySyncRunRepository;
    private final GameLibraryEntryRepository libraryEntryRepository;
    private final SteamOwnedGamesClient ownedGamesClient;
    private final GameCatalogProperties properties;

    @Transactional(readOnly = true)
    public LibraryStatusResponse status() {
        return new LibraryStatusResponse(statusFor(LibrarySource.OWNED), statusFor(LibrarySource.FAMILY),
                ownedGamesClient.isConfigured());
    }

    private LibrarySourceStatus statusFor(LibrarySource source) {
        Optional<LibrarySyncRun> lastRun = librarySyncRunRepository
                .findFirstByLibrarySourceOrderByFinishedAtDesc(source);
        Instant lastSuccessAt = librarySyncRunRepository
                .findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(source, LibrarySyncOutcome.APPLIED)
                .map(LibrarySyncRun::getFinishedAt)
                .orElse(null);
        long entriesActive = libraryEntryRepository.countActiveByLibrarySource(source);

        return new LibrarySourceStatus(
                lastSuccessAt,
                lastRun.map(LibrarySyncRun::getOutcome).orElse(null),
                entriesActive,
                lastRun.map(LibrarySyncRun::getMetadataCalls).orElse(0),
                lastRun.map(LibrarySyncRun::getAiCalls).orElse(0),
                false,
                isStale(source, lastSuccessAt));
    }

    private boolean isStale(LibrarySource source, Instant lastSuccessAt) {
        if (source != LibrarySource.FAMILY) {
            return false;
        }
        if (lastSuccessAt == null) {
            return true;
        }

        return lastSuccessAt.isBefore(Instant.now().minus(properties.library().staleAfterDays(), ChronoUnit.DAYS));
    }
}
