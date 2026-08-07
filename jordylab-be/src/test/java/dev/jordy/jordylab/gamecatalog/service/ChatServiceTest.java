package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatResponse;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Instant SEEN_AT = Instant.parse("2026-08-02T10:15:00Z");
    private static final List<String> VISIBLE_PLATFORMS = List.of("SNES", "Steam");
    private static final String QUESTION = "which games support 4+ player local co-op?";

    @Mock
    private GameRepository gameRepository;

    @Mock
    private ResilientAiService aiService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(gameRepository, aiService, new ObjectMapper(), properties());
    }

    @Test
    void validTranslationRunsGroundedQueryAndComposesAnswerWithCitations() {
        Game mario = aGame("Super Mario World", "SNES");
        Game kart = aGame("Super Mario Kart", "SNES");
        stubVisiblePlatforms();
        when(aiService.call(eq("gamecatalog"), eq(ChatService.TRANSLATION_SYSTEM_PROMPT),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude",
                        """
                        {"titleSearch": null, "genre": null, "minLocalPlayers": 4, "onlineMultiplayer": null,
                         "singlePlayer": null, "platforms": null}
                        """));
        when(gameRepository.findForChatFilter(isNull(), isNull(), eq(4), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(mario, kart));
        when(aiService.call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude",
                        "Two games support 4+ player local co-op."));

        ChatResponse response = chatService.ask(QUESTION);

        assertSoftly(softly -> {
            softly.assertThat(response.answer()).isEqualTo("Two games support 4+ player local co-op.");
            softly.assertThat(response.noMatch()).isFalse();
            softly.assertThat(response.games()).hasSize(2);
            softly.assertThat(response.games().get(0).id()).isEqualTo(mario.getId());
            softly.assertThat(response.games().get(0).title()).isEqualTo("Super Mario World");
            softly.assertThat(response.games().get(0).platform()).isEqualTo("SNES");
            softly.assertThat(response.games().get(1).title()).isEqualTo("Super Mario Kart");
        });
    }

    @Test
    void compositionPromptContainsTheActualDbRows() {
        Game mario = aGame("Super Mario World", "SNES");
        stubVisiblePlatforms();
        stubTranslation("""
                {"titleSearch": null, "genre": null, "minLocalPlayers": 4, "onlineMultiplayer": null,
                 "singlePlayer": null, "platforms": null}
                """);
        when(gameRepository.findForChatFilter(isNull(), isNull(), eq(4), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(mario));
        when(aiService.call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude", "One game."));

        chatService.ask(QUESTION);

        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(aiService).call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                promptCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(promptCaptor.getValue()).contains("Super Mario World");
            softly.assertThat(promptCaptor.getValue()).contains("Platformer");
        });
    }

    @Test
    void translationWithUnknownFieldIsChatUnavailable() {
        stubVisiblePlatforms();
        stubTranslation("""
                {"titleSearch": "mario", "hackAttempts": 5}
                """);

        assertThatThrownBy(() -> chatService.ask(QUESTION))
                .isInstanceOf(ChatUnavailableException.class);
        verify(gameRepository, never()).findForChatFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void translationWithUnknownPlatformIsChatUnavailable() {
        stubVisiblePlatforms();
        stubTranslation("""
                {"titleSearch": null, "genre": null, "minLocalPlayers": null, "onlineMultiplayer": null,
                 "singlePlayer": null, "platforms": ["Dreamcast"]}
                """);

        assertThatThrownBy(() -> chatService.ask(QUESTION))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void translationWithOutOfBoundsValueIsChatUnavailable() {
        stubVisiblePlatforms();
        stubTranslation("""
                {"titleSearch": null, "genre": null, "minLocalPlayers": 99, "onlineMultiplayer": null,
                 "singlePlayer": null, "platforms": null}
                """);

        assertThatThrownBy(() -> chatService.ask(QUESTION))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void malformedTranslationIsChatUnavailable() {
        stubVisiblePlatforms();
        stubTranslation("here is what I think you meant...");

        assertThatThrownBy(() -> chatService.ask(QUESTION))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void translationAiFailureIsChatUnavailable() {
        stubVisiblePlatforms();
        when(aiService.call(eq("gamecatalog"), eq(ChatService.TRANSLATION_SYSTEM_PROMPT),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.failure("gamecatalog", "anthropic", "claude",
                        ProviderFailureReason.TIMEOUT));

        assertThatThrownBy(() -> chatService.ask(QUESTION))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void compositionAiFailureIsChatUnavailable() {
        Game mario = aGame("Super Mario World", "SNES");
        stubVisiblePlatforms();
        stubTranslation("""
                {"titleSearch": "mario", "genre": null, "minLocalPlayers": null, "onlineMultiplayer": null,
                 "singlePlayer": null, "platforms": null}
                """);
        when(gameRepository.findForChatFilter(eq("mario"), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(mario));
        when(aiService.call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.failure("gamecatalog", "anthropic", "claude",
                        ProviderFailureReason.RATE_LIMITED));

        assertThatThrownBy(() -> chatService.ask(QUESTION))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void zeroRowsIsExplicitNoMatchAndSkipsComposition() {
        stubVisiblePlatforms();
        stubTranslation("""
                {"titleSearch": "zelda", "genre": null, "minLocalPlayers": null, "onlineMultiplayer": null,
                 "singlePlayer": null, "platforms": null}
                """);
        when(gameRepository.findForChatFilter(eq("zelda"), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 50))))
                .thenReturn(List.of());

        ChatResponse response = chatService.ask(QUESTION);

        assertSoftly(softly -> {
            softly.assertThat(response.noMatch()).isTrue();
            softly.assertThat(response.games()).isEmpty();
            softly.assertThat(response.answer()).isNotBlank();
        });
        verify(aiService, never()).call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void translationPromptIncludesQuestionAndVisiblePlatforms() {
        stubVisiblePlatforms();
        stubTranslation("""
                {"titleSearch": null, "genre": null, "minLocalPlayers": null, "onlineMultiplayer": null,
                 "singlePlayer": null, "platforms": null}
                """);
        when(gameRepository.findForChatFilter(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 50))))
                .thenReturn(List.of());

        chatService.ask(QUESTION);

        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(aiService).call(eq("gamecatalog"), eq(ChatService.TRANSLATION_SYSTEM_PROMPT),
                promptCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(promptCaptor.getValue()).contains(QUESTION);
            softly.assertThat(promptCaptor.getValue()).contains("SNES");
            softly.assertThat(promptCaptor.getValue()).contains("Steam");
        });
    }

    private void stubVisiblePlatforms() {
        when(gameRepository.findVisiblePlatforms()).thenReturn(VISIBLE_PLATFORMS);
    }

    private void stubTranslation(String json) {
        when(aiService.call(eq("gamecatalog"), eq(ChatService.TRANSLATION_SYSTEM_PROMPT),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude", json));
    }

    private Game aGame(String title, String platform) {
        Game game = Game.builder()
                .source(ScanSource.builder()
                        .sourceKey("snes")
                        .hostname("jordybox")
                        .sourceType(SourceType.EMUDECK)
                        .platform(platform)
                        .enabled(true)
                        .build())
                .platform(platform)
                .externalRef(title + ".smc")
                .title(title)
                .firstSeenAt(SEEN_AT)
                .lastSeenAt(SEEN_AT)
                .build();
        game.applyEnrichment("Platformer", 4, false, true, "A classic.");
        return game;
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
