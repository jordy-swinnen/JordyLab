package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.domain.DescriptionSource;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRun;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunKind;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.RefreshRunRepository;
import dev.jordy.jordylab.gamecatalog.service.EnrichmentService;
import dev.jordy.jordylab.gamecatalog.service.GameEmbeddingService;
import dev.jordy.jordylab.gamecatalog.service.GamePlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The admin's two bulk refreshes (spec 013 US11): {@code DATA} looks facts and artwork up again for every visible game,
 * {@code AI} regenerates the descriptions that are not Steam's own. A run is a database row, so its progress survives leaving
 * the page, and the work commits per game: one failing game never stops it, but five failures in a row with the same cause
 * (no credits, a rejected key) end it early and say why. Hand-corrected data is never overwritten, because the steps only fill
 * what is empty and never touch a title or a link an admin set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshRunService {

    static final int SAME_CAUSE_LIMIT = 5;

    private final RefreshRunRepository runs;
    private final GameRepository gameRepository;
    private final GamePlatformService platformService;
    private final FactsStep factsStep;
    private final ArtworkStep artworkStep;
    private final EnrichmentService enrichmentService;
    private final GameEmbeddingService embeddingService;
    private final TransactionOperations transactions;
    private final RefreshRunLauncher launcher;
    private final Clock clock;

    /** What happened to one game: {@code failureCause} is empty when it went well. */
    private record Outcome(Optional<String> failureCause) {

        static Outcome succeeded() {
            return new Outcome(Optional.empty());
        }

        static Outcome failed(String cause) {
            return new Outcome(Optional.of(cause));
        }
    }

    /**
     * Starts a run of {@code kind} over the games it applies to. The AI kind needs {@code confirmedCost}, because every game is a
     * paid model call; without it nothing starts and the exception carries how many calls it would be.
     */
    public RefreshRun start(RefreshRunKind kind, String startedBy, boolean confirmedCost) {
        if (runs.existsByKindAndStatus(kind, RefreshRunStatus.RUNNING)) {
            throw new RefreshAlreadyActiveException();
        }
        List<UUID> gameIds = kind == RefreshRunKind.AI ? gameRepository.findVisibleIdsWithoutStoreDescription()
                : gameRepository.findAllVisibleIds();
        if (kind == RefreshRunKind.AI && !confirmedCost) {
            throw new CostConfirmationRequiredException(gameIds.size());
        }
        RefreshRun run;
        try {
            run = transactions.execute(status -> runs.saveAndFlush(RefreshRun.builder().kind(kind).total(gameIds.size())
                    .startedBy(startedBy).startedAt(clock.instant()).build()));
        } catch (DataIntegrityViolationException secondStart) {
            throw new RefreshAlreadyActiveException();
        }
        UUID runId = run.getId();
        launcher.launch(() -> execute(runId, kind, gameIds));
        log.info("Refresh run {} ({}) started for {} game(s)", runId, kind, gameIds.size());

        return run;
    }

    /** The latest run of {@code kind}, running or finished. */
    public Optional<RefreshRun> current(RefreshRunKind kind) {
        return runs.findFirstByKindOrderByStartedAtDesc(kind);
    }

    /** Asks a running run to end after the game it is on. */
    public RefreshRun stop(UUID runId) {
        return transactions.execute(status -> {
            RefreshRun run = runs.findById(runId).orElseThrow(RefreshRunNotFoundException::new);
            if (!run.isRunning()) {
                throw new RefreshRunNotRunningException();
            }
            run.requestStop();

            return runs.save(run);
        });
    }

    /** After a restart nothing is really running: leftovers become {@code INTERRUPTED} so the page stops showing progress. */
    @EventListener(ApplicationReadyEvent.class)
    void interruptLeftoverRuns() {
        Integer interrupted = transactions.execute(status -> runs.markRunningAsInterrupted(clock.instant()));
        if (interrupted != null && interrupted > 0) {
            log.warn("{} refresh run(s) were interrupted by a restart", interrupted);
        }
    }

    void execute(UUID runId, RefreshRunKind kind, List<UUID> gameIds) {
        String lastCause = null;
        String latestFailure = null;
        int sameCause = 0;
        for (UUID gameId : gameIds) {
            if (stopRequested(runId)) {
                finish(runId, RefreshRunStatus.STOPPED, null);

                return;
            }
            Outcome outcome = refreshOne(kind, gameId);
            recordProgress(runId, outcome.failureCause().isEmpty());
            if (outcome.failureCause().isEmpty()) {
                lastCause = null;
                sameCause = 0;
                continue;
            }
            String cause = outcome.failureCause().get();
            sameCause = cause.equals(lastCause) ? sameCause + 1 : 1;
            lastCause = cause;
            latestFailure = cause;
            if (sameCause >= SAME_CAUSE_LIMIT) {
                finish(runId, RefreshRunStatus.FAILED, "Stopped after " + SAME_CAUSE_LIMIT + " failures in a row: " + cause);

                return;
            }
        }
        finishNormally(runId, latestFailure);
    }

    private boolean stopRequested(UUID runId) {
        Boolean requested = transactions.execute(status -> runs.findById(runId).map(RefreshRun::isStopRequested).orElse(true));

        return Boolean.TRUE.equals(requested);
    }

    private void recordProgress(UUID runId, boolean succeeded) {
        transactions.executeWithoutResult(status -> runs.findById(runId).ifPresent(run -> {
            run.advance(succeeded);
            runs.save(run);
        }));
    }

    private void finishNormally(UUID runId, String lastCause) {
        transactions.executeWithoutResult(status -> runs.findById(runId).ifPresent(run -> {
            boolean nothingWorked = run.getTotal() > 0 && run.getFailed() == run.getTotal();
            String summary = run.getFailed() == 0 ? null
                    : run.getFailed() + " of " + run.getTotal() + " games could not be refreshed"
                            + (lastCause == null ? "" : " (" + lastCause + ")");
            run.finish(nothingWorked ? RefreshRunStatus.FAILED : RefreshRunStatus.SUCCEEDED, summary, clock.instant());
            runs.save(run);
        }));
    }

    private void finish(UUID runId, RefreshRunStatus status, String summary) {
        transactions.executeWithoutResult(transaction -> runs.findById(runId).ifPresent(run -> {
            run.finish(status, summary, clock.instant());
            runs.save(run);
        }));
    }

    private Outcome refreshOne(RefreshRunKind kind, UUID gameId) {
        try {
            return kind == RefreshRunKind.AI ? regenerateDescription(gameId) : refreshData(gameId);
        } catch (RuntimeException exception) {
            log.warn("Refresh of game {} failed: {}", gameId, exception.getMessage());

            return Outcome.failed(exception.getClass().getSimpleName());
        }
    }

    private Outcome refreshData(UUID gameId) {
        Optional<Game> snapshot = transactions.execute(status -> gameRepository.findById(gameId));
        if (snapshot == null || snapshot.isEmpty()) {
            return Outcome.succeeded();
        }
        Game game = snapshot.get();
        List<String> platforms = platformService.platformsOf(gameId);
        FactsStep.Fetched facts = factsStep.fetch(game, platforms, false);
        if (facts.steamRateLimited()) {
            return Outcome.failed("STEAM_RATE_LIMITED");
        }
        ArtworkStep.Fetched artwork = artworkStep.fetch(game, platforms, facts, true);
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> gameRepository.findById(gameId).ifPresent(fresh -> {
            if (StringUtils.hasText(fresh.getSteamAppId())) {
                fresh.resetMetadataForRetry();
            }
            factsStep.apply(fresh, facts, now);
            artworkStep.apply(fresh, artwork, now, true);
            gameRepository.save(fresh);
        }));
        embeddingService.embed(List.of(gameId));

        return Outcome.succeeded();
    }

    private Outcome regenerateDescription(UUID gameId) {
        Optional<Game> snapshot = transactions.execute(status -> gameRepository.findById(gameId));
        if (snapshot == null || snapshot.isEmpty() || snapshot.get().getDescriptionSource() == DescriptionSource.STEAM) {
            return Outcome.succeeded();
        }
        Optional<String> failure = enrichmentService.refreshReporting(snapshot.get());
        if (failure.isPresent()) {
            return Outcome.failed(failure.get());
        }
        embeddingService.embed(List.of(gameId));

        return Outcome.succeeded();
    }
}
