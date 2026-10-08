package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.AutoFillProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.service.GameEmbeddingService;
import dev.jordy.jordylab.gamecatalog.service.GamePlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The background worker that makes the catalog fill itself in (spec 013 US4, research B7): covers, facts, descriptions and
 * the search index, with nobody pressing a button. One run at a time. Games come in small batches, installed ones first;
 * each game goes through the steps in order, every lookup and model call outside a transaction, every result written in its
 * own short one. One failing game never stops the batch, and a game is handled at most once per run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogAutoFillService {

    /** Safety limit so a run always ends, however large the backlog. */
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final GameRepository gameRepository;
    private final GameEmbeddingRepository embeddingRepository;
    private final GamePlatformService platformService;
    private final FactsStep factsStep;
    private final ArtworkStep artworkStep;
    private final DescriptionStep descriptionStep;
    private final GameEmbeddingService embeddingService;
    private final AutoFillProperties properties;
    private final TransactionOperations transactions;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean();

    /** What one run did. */
    public record Report(boolean skipped, int processed, int failed) {

        static Report alreadyRunning() {
            return new Report(true, 0, 0);
        }
    }

    public Report run() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Auto-fill is already running; skipping");

            return Report.alreadyRunning();
        }
        try {
            return drain();
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private Report drain() {
        Instant retryBefore = clock.instant().minus(Duration.ofHours(properties.freeLookupRetryHours()));
        Set<UUID> handled = new HashSet<>();
        int failed = 0;
        boolean steamPaused = false;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<UUID> ids = gameRepository.findAutoFillBacklog(retryBefore,
                    handled.isEmpty() ? Set.of(new UUID(0, 0)) : handled, PageRequest.of(0, properties.batchSize()));
            if (ids.isEmpty()) {
                break;
            }
            for (UUID id : ids) {
                handled.add(id);
                try {
                    steamPaused = fill(id, retryBefore, steamPaused) || steamPaused;
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn("Auto-fill failed for game {}: {}", id, exception.getMessage());
                }
            }
        }
        sweepSearchIndex();
        if (!handled.isEmpty()) {
            log.info("Auto-fill handled {} game(s), {} failed", handled.size(), failed);
        }

        return new Report(false, handled.size(), failed);
    }

    /** Runs the steps for one game; returns true when Steam asked us to slow down. */
    private boolean fill(UUID id, Instant retryBefore, boolean steamPaused) {
        Optional<Game> snapshot = transactions.execute(status -> gameRepository.findById(id));
        if (snapshot == null || snapshot.isEmpty()) {
            return false;
        }
        Game game = snapshot.get();
        List<String> platforms = platformService.platformsOf(id);
        FactsStep.Fetched facts = factsStep.needed(game, retryBefore)
                ? factsStep.fetch(game, platforms, steamPaused) : FactsStep.Fetched.none();
        ArtworkStep.Fetched artwork = artworkStep.needed(game, retryBefore)
                ? artworkStep.fetch(game, platforms, facts) : ArtworkStep.Fetched.none();
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> gameRepository.findById(id).ifPresent(fresh -> {
            factsStep.apply(fresh, facts, now);
            artworkStep.apply(fresh, artwork, now);
            gameRepository.save(fresh);
        }));
        Optional<Game> afterFacts = transactions.execute(status -> gameRepository.findById(id));
        if (afterFacts != null && afterFacts.isPresent()
                && descriptionStep.needed(afterFacts.get(), gameRepository.isAvailableNow(id))) {
            descriptionStep.run(afterFacts.get());
        }
        embeddingService.embed(List.of(id));

        return facts.steamRateLimited();
    }

    /** Embeds games whose search-index row is missing or was made with another model, in a bounded sweep per run. */
    private void sweepSearchIndex() {
        try {
            List<UUID> missing = embeddingRepository.findGameIdsMissingOrEmbeddedWithAnotherModel(
                    embeddingService.embeddingModel());
            if (!missing.isEmpty()) {
                embeddingService.embed(missing.stream().limit((long) properties.batchSize() * 10).toList());
            }
        } catch (RuntimeException exception) {
            log.warn("Search index sweep failed: {}", exception.getMessage());
        }
    }
}
