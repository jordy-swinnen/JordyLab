package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrichmentServiceTest {

    private static final int SCAN_CAP = 50;
    private static final String BLURB = "Super Mario World is a side-scrolling platformer in which Mario and Luigi run, jump "
            + "and spin through Dinosaur Land to rescue Princess Toadstool from Bowser. Each level hides secret exits "
            + "that open new routes across the map, and a friendly dinosaur called Yoshi can be ridden to eat enemies "
            + "and reach higher ground. Power-ups such as the cape feather let the player glide over obstacles.";
    private static final String VALID_JSON = """
            {"genre": "Platformer", "onlineMultiplayer": false, "singlePlayer": true,
             "description": "%s"}
            """.formatted(BLURB);

    @Mock
    private GameRepository gameRepository;

    @Mock
    private ResilientAiService aiService;

    @Mock
    private GamePlatformService gamePlatformService;

    @Mock
    private IgdbClient igdbClient;

    private EnrichmentService enrichmentService;

    private String systemPrompt;

    @BeforeEach
    void setUp() throws IOException {
        enrichmentService = new EnrichmentService(gameRepository, aiService, new ObjectMapper(), properties(), gamePlatformService,
                igdbClient, new DescriptionQualityValidator(), java.time.Clock.fixed(java.time.Instant.parse("2026-10-07T10:00:00Z"), java.time.ZoneOffset.UTC));
        enrichmentService.systemPromptResource = new ClassPathResource("prompts/gamecatalog/enrichment.st");
        enrichmentService.init();
        systemPrompt = new ClassPathResource("prompts/gamecatalog/enrichment.st").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void theSystemPromptAsksForAStoreBlurbAndForbidsInventingFacts() {
        assertSoftly(softly -> {
            softly.assertThat(systemPrompt).contains("2 to 4 sentences, 40 to 110 words");
            softly.assertThat(systemPrompt).contains("Third person");
            softly.assertThat(systemPrompt).contains("Never invent");
            softly.assertThat(systemPrompt).contains("never contradict them");
        });
    }

    @Test
    void enrichesPendingGameFromValidStrictJson() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5", VALID_JSON, false));

        int processed = enrichmentService.enrichPending(SCAN_CAP);

        assertThat(processed).isEqualTo(1);
        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
            softly.assertThat(game.getGenre()).isEqualTo("Platformer");            softly.assertThat(game.getOnlineMultiplayer()).isFalse();
            softly.assertThat(game.getSinglePlayer()).isTrue();
            softly.assertThat(game.getDescription()).isEqualTo(BLURB);
        });
    }

    @Test
    void promptContainsGameTitleAndPlatform() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5", VALID_JSON, false));

        enrichmentService.enrichPending(SCAN_CAP);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
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
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
                org.mockito.ArgumentMatchers.startsWith("Game:")))
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
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), eq(expectedPrompt)))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5", VALID_JSON, false));

        int processed = enrichmentService.enrichPending(SCAN_CAP);

        assertThat(processed).isEqualTo(1);
        verify(aiService).call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), eq(expectedPrompt));
    }

    @Test
    void outOfBoundsValuesRecordAttemptWithoutFabricating() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
                org.mockito.ArgumentMatchers.startsWith("Game:")))
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
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
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
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
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
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
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
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
                eq(userPromptFor(game))))
                .thenReturn(AiCallResult.failure(AiFeature.GAMECATALOG_ENRICHMENT, "anthropic", "claude-sonnet-5", ProviderFailureReason.TIMEOUT, true));

        enrichmentService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(1);
        });
    }

    @Test
    void aTextThatIsNotAStoreBlurbIsNeverStoredAndTheModelIsAskedOnceMoreWithTheReason() {
        Game game = aGame("Super Mario World");
        String oneLiner = "{\"genre\": \"Platformer\", \"description\": \"A classic SNES platformer.\"}";
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "m", oneLiner, false));
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), org.mockito.ArgumentMatchers.contains("rejected because")))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "m", VALID_JSON, false));

        enrichmentService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getDescription()).isEqualTo(BLURB);
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
        });
        verify(aiService, org.mockito.Mockito.times(2)).call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void whenBothTriesAreRejectedNothingIsStoredAndTheAttemptCounts() {
        Game game = aGame("Super Mario World");
        String oneLiner = "{\"genre\": \"Platformer\", \"description\": \"A classic SNES platformer.\"}";
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), org.mockito.ArgumentMatchers.startsWith("Game:")))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "m", oneLiner, false));

        java.util.Optional<String> failure = enrichmentService.refreshReporting(game);

        assertSoftly(softly -> {
            softly.assertThat(failure).contains("INVALID_OUTPUT");
            softly.assertThat(game.getDescription()).isNull();
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(1);
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
        });
    }

    @Test
    void theIgdbSummaryAndTheKnownReleaseYearGroundThePrompt() {
        Game game = aGame("Super Mario World");
        game.applyDeterministicMetadata(null, null, null, 1990);
        when(igdbClient.isConfigured()).thenReturn(true);
        when(igdbClient.findGame("Super Mario World", dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog.entryFor("SNES").igdbPlatformId())).thenReturn(java.util.Optional.of(
                new IgdbClient.IgdbGame(76L, "Super Mario World")));
        when(igdbClient.fetchFacts(76L)).thenReturn(java.util.Optional.of(new IgdbClient.IgdbFacts(76L,
                "Super Mario World", 1990, List.of(), null, null, "Mario runs through Dinosaur Land.", null, null, null)));
        String expected = userPromptFor(game) + "\nKnown release year: 1990"
                + "\nSummary for grounding (say it in your own words):\nMario runs through Dinosaur Land.";
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), eq(expected)))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "m", VALID_JSON, false));

        enrichmentService.refresh(game);

        assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
    }

    @Test
    void aRouterIsRecordedWithTheModelItPickedAndTheOneThatWasRequested() {
        Game game = aGame("Super Mario World");
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "jev-router",
                        "claude-haiku-4.5", VALID_JSON, 100, 80));

        enrichmentService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getDescriptionModel()).isEqualTo("claude-haiku-4.5");
            softly.assertThat(game.getDescriptionRequestedModel()).isEqualTo("jev-router");
            softly.assertThat(game.getDescriptionWrittenAt()).isEqualTo(java.time.Instant.parse("2026-10-07T10:00:00Z"));
        });
    }

    @Test
    void aProviderThatAnswersWithTheModelItWasAskedForRecordsItOnce() {
        Game game = aGame("Super Mario World");
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), eq(userPromptFor(game))))
                .thenReturn(AiCallResult.success(AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "anthropic/claude-haiku-4.5",
                        "anthropic/claude-haiku-4.5", VALID_JSON, 100, 80));

        enrichmentService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getDescriptionModel()).isEqualTo("anthropic/claude-haiku-4.5");
            softly.assertThat(game.getDescriptionRequestedModel()).isNull();
        });
    }

    @Test
    void aProviderThatReportsNoModelIsRecordedWithTheSelectedIdOnly() {
        Game game = aGame("Super Mario World");
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), eq(userPromptFor(game))))
                .thenReturn(new AiCallResult(true, AiFeature.GAMECATALOG_ENRICHMENT, "openrouter", "jev-router", VALID_JSON,
                        null, false, null, null, null));

        enrichmentService.refresh(game);

        assertSoftly(softly -> {
            softly.assertThat(game.getDescriptionModel()).isNull();
            softly.assertThat(game.getDescriptionRequestedModel()).isEqualTo("jev-router");
        });
    }

    @Test
    void theFallbackProviderIsRecordedAsTheModelThatAnswered() {
        Game game = aGame("Super Mario World");
        when(aiService.call(eq(AiFeature.GAMECATALOG_ENRICHMENT), eq(systemPrompt), eq(userPromptFor(game))))
                .thenReturn(new AiCallResult(true, AiFeature.GAMECATALOG_ENRICHMENT, "anthropic", "claude-sonnet-5", VALID_JSON,
                        null, true, "claude-sonnet-5", null, null));

        enrichmentService.refresh(game);

        assertThat(game.getDescriptionModel()).isEqualTo("claude-sonnet-5");
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
        return "Game: " + game.getTitle() + "\nPlatform: SNES";
    }

    private Game aGame(String title) {
        Game game = Game.builder()
                .title(title)
                .build();
        lenient().when(gamePlatformService.platformsOf(game.getId())).thenReturn(List.of("SNES"));

        return game;
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
