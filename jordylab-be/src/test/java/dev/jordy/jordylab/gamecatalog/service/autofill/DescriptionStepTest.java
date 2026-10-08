package dev.jordy.jordylab.gamecatalog.service.autofill;

import dev.jordy.jordylab.gamecatalog.AutoFillProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.service.EnrichmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DescriptionStepTest {

    @Mock
    private EnrichmentService enrichmentService;

    private DescriptionStep step() {
        return new DescriptionStep(enrichmentService, new AutoFillProperties(10, 3, 24, null, null));
    }

    @Test
    void aGameWithoutADescriptionGetsOneWhileItIsAvailableAndAttemptsRemain() {
        Game game = Game.builder().title("Super Mario World").build();

        assertSoftly(softly -> {
            softly.assertThat(step().needed(game, true)).isTrue();
            softly.assertThat(step().needed(game, false)).as("library-only games are never sent to the model").isFalse();
        });
    }

    @Test
    void aSteamStoreDescriptionIsKept() {
        Game game = Game.builder().title("Portal 2").steamAppId("620").build();
        game.applyDeterministicDescription("Valve's puzzle game.");

        assertSoftly(softly -> softly.assertThat(step().needed(game, true)).isFalse());
    }

    @Test
    void theModelIsAskedNoMoreOnceTheAttemptsAreSpent() {
        Game game = Game.builder().title("Super Mario World").build();
        game.recordEnrichmentFailure(10);
        game.recordEnrichmentFailure(10);
        game.recordEnrichmentFailure(10);

        assertSoftly(softly -> softly.assertThat(step().needed(game, true)).isFalse());
    }

    @Test
    void runningTheStepAsksTheEnrichmentService() {
        Game game = Game.builder().title("Super Mario World").build();

        step().run(game);

        verify(enrichmentService).enrichOne(game);
    }
}
