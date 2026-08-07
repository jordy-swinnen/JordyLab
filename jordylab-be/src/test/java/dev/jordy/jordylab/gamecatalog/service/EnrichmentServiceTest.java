package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrichmentServiceTest {

    private static final Instant SEEN_AT = Instant.parse("2026-08-02T10:15:00Z");
    private static final String VALID_JSON = """
            {"genre": "Platformer", "maxLocalPlayers": 2, "onlineMultiplayer": false, "singlePlayer": true,
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
        when(aiService.call(eq("gamecatalog"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude", VALID_JSON));

        enrichmentService.enrichPendingGames();

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
            softly.assertThat(game.getGenre()).isEqualTo("Platformer");
            softly.assertThat(game.getMaxLocalPlayers()).isEqualTo(2);
            softly.assertThat(game.getOnlineMultiplayer()).isFalse();
            softly.assertThat(game.getSinglePlayer()).isTrue();
            softly.assertThat(game.getDescription()).isEqualTo("A classic SNES platformer.");
        });
    }

    @Test
    void promptContainsGameTitleAndPlatform() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq("gamecatalog"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude", VALID_JSON));

        enrichmentService.enrichPendingGames();

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).call(eq("gamecatalog"), org.mockito.ArgumentMatchers.anyString(),
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
        when(aiService.call(eq("gamecatalog"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude",
                        "I think this is a great game!"));

        enrichmentService.enrichPendingGames();

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(1);
            softly.assertThat(game.getGenre()).isNull();
            softly.assertThat(game.getDescription()).isNull();
        });
    }

    @Test
    void outOfBoundsValuesRecordAttemptWithoutFabricating() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq("gamecatalog"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude",
                        """
                        {"genre": "Platformer", "maxLocalPlayers": 99, "onlineMultiplayer": false,
                         "singlePlayer": true, "description": "A classic."}
                        """));

        enrichmentService.enrichPendingGames();

        assertSoftly(softly -> {
            softly.assertThat(game.getEnrichmentAttempts()).isEqualTo(1);
            softly.assertThat(game.getDescription()).isNull();
        });
    }

    @Test
    void aiFailureNeverFabricatesAndCountsAsAttempt() {
        Game game = aGame("Super Mario World");
        stubPendingBatch(List.of(game));
        when(aiService.call(eq("gamecatalog"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.failure("gamecatalog", "anthropic", "claude",
                        ProviderFailureReason.TIMEOUT));

        enrichmentService.enrichPendingGames();

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
        when(aiService.call(eq("gamecatalog"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.failure("gamecatalog", "anthropic", "claude",
                        ProviderFailureReason.UNREACHABLE));

        enrichmentService.enrichPendingGames();

        assertThat(game.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.FAILED);
    }

    @Test
    void batchIsLimitedToConfiguredBatchSize() {
        stubPendingBatch(List.of());

        enrichmentService.enrichPendingGames();

        verify(gameRepository).findByEnrichmentStatusOrderByFirstSeenAtAsc(EnrichmentStatus.PENDING,
                PageRequest.of(0, 50));
    }

    @Test
    void dailyJobResetsFailedGamesForRetry() {
        Game failed = aGame("Super Mario World");
        failed.recordEnrichmentFailure(3);
        failed.recordEnrichmentFailure(3);
        failed.recordEnrichmentFailure(3);
        Game enriched = aGame("Chrono Trigger");
        enriched.applyEnrichment("RPG", null, false, true, "A masterpiece.");
        when(gameRepository.findByEnrichmentStatus(EnrichmentStatus.FAILED)).thenReturn(List.of(failed));

        enrichmentService.resetFailedEnrichments();

        assertSoftly(softly -> {
            softly.assertThat(failed.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
            softly.assertThat(failed.getEnrichmentAttempts()).isZero();
            softly.assertThat(enriched.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.ENRICHED);
        });
    }

    @Test
    void emptyPendingBatchSkipsAiCalls() {
        stubPendingBatch(List.of());

        enrichmentService.enrichPendingGames();

        verify(aiService, never()).call(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private void stubPendingBatch(List<Game> games) {
        when(gameRepository.findByEnrichmentStatusOrderByFirstSeenAtAsc(EnrichmentStatus.PENDING,
                PageRequest.of(0, 50)))
                .thenReturn(games);
    }

    private Game aGame(String title) {
        return Game.builder()
                .source(ScanSource.builder()
                        .sourceKey("snes")
                        .hostname("jordybox")
                        .sourceType(SourceType.EMUDECK)
                        .platform("SNES")
                        .enabled(true)
                        .build())
                .platform("SNES")
                .externalRef(title + ".smc")
                .title(title)
                .firstSeenAt(SEEN_AT)
                .lastSeenAt(SEEN_AT)
                .build();
    }

    private GameCatalogProperties properties() {
        return new GameCatalogProperties(
                                new GameCatalogProperties.Artwork("/tmp/artwork", 2097152L, true, 2000L),
                30,
                new GameCatalogProperties.Enrichment(50, 3),
                new GameCatalogProperties.Chat(50),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144));
    }
}
