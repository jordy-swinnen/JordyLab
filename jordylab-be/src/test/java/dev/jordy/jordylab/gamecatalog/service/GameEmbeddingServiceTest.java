package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameEmbedding;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.shared.ai.AiEmbeddingResult;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameEmbeddingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final String MODEL = "openai/text-embedding-3-small";

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameEmbeddingRepository gameEmbeddingRepository;

    @Mock
    private GamePlatformService gamePlatformService;

    @Mock
    private ResilientAiService aiService;

    private Game game;
    private GameEmbeddingService service;

    @BeforeEach
    void setUp() {
        game = Game.builder().title("Overcooked! All You Can Eat").build();
        game.applyDeterministicMetadata("Party, Cooking", "Ghost Town Games", "Team17", 2020);
        game.applyDeterministicMultiplayer(true, true, 4, dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource.IGDB);
        lenient().when(aiService.embeddingModelName()).thenReturn(MODEL);
        service = new GameEmbeddingService(gameRepository, gameEmbeddingRepository, gamePlatformService, aiService,
                TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AiEmbeddingResult success(float[]... vectors) {
        return new AiEmbeddingResult(true, List.of(vectors), "openrouter", MODEL, MODEL, 10, null);
    }

    private void givenTheGameIsKnown() {
        when(gameRepository.findAllById(List.of(game.getId()))).thenReturn(List.of(game));
        when(gamePlatformService.platformsOf(List.of(game.getId())))
                .thenReturn(Map.of(game.getId(), List.of("PlayStation 5", "Steam")));
    }

    @Test
    void theDocumentStatesTheFactsInWords() {
        String document = service.documentFor(game, List.of("PlayStation 5", "Steam"));

        assertThat(document).startsWith("Overcooked! All You Can Eat. Platforms: PlayStation 5, Steam")
                .contains("Genres: Party, Cooking")
                .contains("Developer: Ghost Town Games")
                .contains("Released: 2020")
                .contains("supports up to 4 players locally on one screen")
                .contains("has split-screen");
    }

    @Test
    void aNewGameIsEmbeddedAndStoredWithItsHash() {
        givenTheGameIsKnown();
        String document = service.documentFor(game, List.of("PlayStation 5", "Steam"));
        when(gameEmbeddingRepository.findById(game.getId())).thenReturn(Optional.empty());
        when(aiService.embed(List.of(document))).thenReturn(success(new float[] {0.5f, -0.25f}));

        GameEmbeddingService.Outcome outcome = service.embed(List.of(game.getId()));

        assertThat(outcome).isEqualTo(new GameEmbeddingService.Outcome(1, 0, 0));
        verify(gameEmbeddingRepository).upsert(game.getId(), MODEL, GameEmbeddingService.contentHash(document),
                "[0.5,-0.25]", NOW);
    }

    @Test
    void anUnchangedGameIsSkippedWithoutACall() {
        givenTheGameIsKnown();
        String document = service.documentFor(game, List.of("PlayStation 5", "Steam"));
        when(gameEmbeddingRepository.findById(game.getId())).thenReturn(Optional.of(GameEmbedding.builder()
                .id(game.getId()).model(MODEL).contentHash(GameEmbeddingService.contentHash(document))
                .embeddedAt(NOW).build()));

        GameEmbeddingService.Outcome outcome = service.embed(List.of(game.getId()));

        assertThat(outcome).isEqualTo(new GameEmbeddingService.Outcome(0, 1, 0));
        verify(aiService, never()).embed(List.of(document));
    }

    @Test
    void aChangedEmbeddingModelReEmbedsTheGame() {
        givenTheGameIsKnown();
        String document = service.documentFor(game, List.of("PlayStation 5", "Steam"));
        when(gameEmbeddingRepository.findById(game.getId())).thenReturn(Optional.of(GameEmbedding.builder()
                .id(game.getId()).model("old/model").contentHash(GameEmbeddingService.contentHash(document))
                .embeddedAt(NOW).build()));
        when(aiService.embed(List.of(document))).thenReturn(success(new float[] {1f}));

        assertThat(service.embed(List.of(game.getId())).embedded()).isEqualTo(1);
    }

    @Test
    void aChangedDescriptionReEmbedsTheGame() {
        givenTheGameIsKnown();
        String before = service.documentFor(game, List.of("PlayStation 5", "Steam"));
        game.applyDeterministicDescription("A chaotic co-op kitchen.");
        String after = service.documentFor(game, List.of("PlayStation 5", "Steam"));
        when(gameEmbeddingRepository.findById(game.getId())).thenReturn(Optional.of(GameEmbedding.builder()
                .id(game.getId()).model(MODEL).contentHash(GameEmbeddingService.contentHash(before))
                .embeddedAt(NOW).build()));
        when(aiService.embed(List.of(after))).thenReturn(success(new float[] {1f}));

        assertThat(service.embed(List.of(game.getId())).embedded()).isEqualTo(1);
    }

    @Test
    void aFailedCallLeavesTheRowUntouchedAndCountsAsFailed() {
        givenTheGameIsKnown();
        String document = service.documentFor(game, List.of("PlayStation 5", "Steam"));
        when(gameEmbeddingRepository.findById(game.getId())).thenReturn(Optional.empty());
        when(aiService.embed(List.of(document)))
                .thenReturn(AiEmbeddingResult.failure("openrouter", MODEL, ProviderFailureReason.RATE_LIMITED));

        GameEmbeddingService.Outcome outcome = service.embed(List.of(game.getId()));

        assertSoftly(softly -> softly.assertThat(outcome).isEqualTo(new GameEmbeddingService.Outcome(0, 0, 1)));
        verify(gameEmbeddingRepository, never()).upsert(game.getId(), MODEL, GameEmbeddingService.contentHash(document),
                "[1.0]", NOW);
    }

    @Test
    void aQueryIsEmbeddedAndAFailureYieldsNothing() {
        when(aiService.embed(List.of("cosy cooking chaos"))).thenReturn(success(new float[] {0.1f, 0.2f}));
        when(aiService.embed(List.of("broken"))).thenReturn(
                AiEmbeddingResult.failure("openrouter", MODEL, ProviderFailureReason.NOT_CONFIGURED));

        assertSoftly(softly -> {
            softly.assertThat(service.embedQuery("cosy cooking chaos")).hasValueSatisfying(
                    vector -> softly.assertThat(vector).containsExactly(0.1f, 0.2f));
            softly.assertThat(service.embedQuery("broken")).isEmpty();
            softly.assertThat(service.embedQuery("  ")).isEmpty();
        });
    }

    @Test
    void theVectorLiteralIsPgvectorText() {
        assertThat(GameEmbeddingService.vectorLiteral(new float[] {1f, 0.5f, -2f})).isEqualTo("[1.0,0.5,-2.0]");
    }
}
