package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.AutoFillProperties;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.service.EnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Step 3 of the auto-fill (spec 013 FR-021, FR-059): a description for games that have none. Steam games keep Steam's own
 * description (the facts step stores it); the model is asked only for a game with no store description, only while its
 * attempts last, and only for a game that is installed or on a console. The model call runs outside any transaction.
 */
@Component
@RequiredArgsConstructor
public class DescriptionStep {

    private final EnrichmentService enrichmentService;
    private final AutoFillProperties properties;

    public boolean needed(Game game, boolean availableNow) {
        return availableNow && !StringUtils.hasText(game.getDescription())
                && game.getEnrichmentStatus() == EnrichmentStatus.PENDING
                && game.getEnrichmentAttempts() < properties.aiMaxAttempts();
    }

    /** Asks the model and saves the outcome; a failed attempt is counted and retried by a later run. */
    public void run(Game game) {
        enrichmentService.enrichOne(game);
    }
}
