package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrichmentServiceTest {

    private static final int SCAN_CAP = 50;
    private static final String VALID_JSON = """
            {"genre": "Platformer", "onlineMultiplayer": false, "singlePlayer": true,
             "description": "A classic SNES platformer."}
            """;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private ResilientAiService aiService;

    private EnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new EnrichmentService(gameRepository, aiService, new ObjectMapper(), properties());
    }

    @Test
    void enrichesPendingGameFromValidStrictJson() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5", VALID_JSON, false));

        int processed = enrichmentService.enrichPending(SCAN_CAP);

        assertThat(processed).isEqualTo(1);
        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
            softly.assertThat(game.getGenre()).isEqualTo("Platformer");            softly.assertThat(game.getOnlineMultiplayer()).isFalse();
            softly.assertThat(game.getSinglePlayer()).isTrue();
            softly.assertThat(game.getDescription()).isEqualTo("A classic SNES platformer.");
        });
    }

    @Test
    void promptContainsGameTitleAndPlatform() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5", VALID_JSON, false));

        enrichmentService.enrichPending(SCAN_CAP);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                userPromptCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(userPromptCaptor.getValue()).contains("Super Mario World");
            softly.assertThat(userPromptCaptor.getValue()).contains("SNES");
        });
    }

    @Test
    void malformedAiOutputRecordsAttemptWithoutFabricating() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5",
                        "I think this is a great game!", false));

        enrichmentService.enrichPending(SCAN_CAP);

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(1);
            softly.assertThat(game.getGenre()).isNull();
            softly.assertThat(game.getDescription()).isNull();
        });
    }

    @Test
    void userPromptIncludesKnownMultiplayerFacts() {
        Game game = aGame("Super Mario World");
        game.applyDeterministicMultiplayer(true, true, 4, MultiplayerSource.IGDB);
        stubPendingBatch(List.of(game));
        String expectedPrompt = "Game: Super Mario World\nPlatform: SNES"
                + "\nKnown multiplayer facts (use verbatim, do not contradict):"
                + "\n- Local multiplayer: yes\n- Split-screen: yes\n- Max local players: 4";
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT), eq(expectedPrompt)))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5", VALID_JSON, false));

        int processed = enrichmentService.enrichPending(SCAN_CAP);

        assertThat(processed).isEqualTo(1);
        verify(aiService).call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT), eq(expectedPrompt));
    }

    @Test
    void outOfBoundsValuesRecordAttemptWithoutFabricating() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5",
                        """
                        {"genre": "Platformer", "releaseYear": 3000, "onlineMultiplayer": false,
                         "singlePlayer": true, "description": "A classic."}
                        """, false));

        enrichmentService.enrichPending(SCAN_CAP);

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(1);
            softly.assertThat(game.getDescription()).isNull();
        });
    }

    @Test
    void aiFailureNeverFabricatesAndCountsAsAttempt() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.failure(AiFeature.GAMECATALOG_ENRICHMENT, "anthropic", "claude-sonnet-5", ProviderFailureReason.TIMEOUT, true));

        enrichmentService.enrichPending(SCAN_CAP);

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(1);
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(game.getGenre()).isNull();
            softly.assertThat(game.getDescription()).isNull();
        });
    }

    @Test
    void thirdFailureMarksGameFailed() {
        Game game = aGame("Super Mario World");
        game.recordEnrichmentFailure(3);
        game.recordEnrichmentFailure(3);
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.failure(AiFeature.GAMECATALOG_ENRICHMENT, "anthropic", "claude-sonnet-5", ProviderFailureReason.UNREACHABLE, true));

        enrichmentService.enrichPending(SCAN_CAP);

        assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.FAILED);
    }

    @Test
    void enrichPendingRespectsThePerScanCap() {
        when(gameRepository.findEnrichmentBacklog(EnrichmentStatus.PENDING,
                PageRequest.of(0, 7))).thenReturn(List.of());

        int processed = enrichmentService.enrichPending(7);

        assertThat(processed).isZero();
        verify(gameRepository).findEnrichmentBacklog(EnrichmentStatus.PENDING,
                PageRequest.of(0, 7));
    }

    @Test
    void refreshClearsTheFailureCounterAndReEnriches() {
        Game game = aGame("Super Mario World");
        game.recordEnrichmentFailure(3);
        game.recordEnrichmentFailure(3);
        game.recordEnrichmentFailure(3);
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5", VALID_JSON, false));

        enrichmentService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
            softly.assertThat(game.getEnrichmentAttempts()).isZero();
            softly.assertThat(game.getGenre()).isEqualTo("Platformer");
        });
    }

    @Test
    void refreshClearsTheFailureCounterEvenWhenTheAiCallFails() {
        Game game = aGame("Super Mario World");
        game.recordEnrichmentFailure(3);
        game.recordEnrichmentFailure(3);
        game.recordEnrichmentFailure(3);
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(EnrichmentService.SYSTEM_PROMPT),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.failure(AiFeature.GAMECATALOG_ENRICHMENT, "anthropic", "claude-sonnet-5", ProviderFailureReason.TIMEOUT, true));

        enrichmentService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(1);
        });
    }

    @Test
    void emptyPendingBatchSkipsAiCalls() {
        stubPendingBatch(List.of());

        int processed = enrichmentService.enrichPending(SCAN_CAP);

        assertThat(processed).isZero();
        verifyNoInteractions(aiService);
    }

    private void stubPendingBatch(List<Game> games) {
        when(gameRepository.findEnrichmentBacklog(EnrichmentStatus.PENDING,
                PageRequest.of(0, SCAN_CAP)))
                .thenReturn(games);
    }

    private String userPromptFor(Game game) {
        return "Game: " + game.getTitle() + "\nPlatform: " + game.getPlatform();
    }

    private Game aGame(String title) {
        return Game.builder()
                .platform("SNES")
                .title(title)
                .build();
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(SCAN_CAP, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5), null);
    }
}
