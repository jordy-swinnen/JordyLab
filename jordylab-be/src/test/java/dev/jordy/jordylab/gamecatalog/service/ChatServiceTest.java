package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatResponse;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final List<String> VISIBLE_PLATFORMS = List.of("SNES", "Steam");
    private static final List<String> VISIBLE_HOSTS = List.of("jordybox");
    private static final String QUESTION = "which games support 4+ player local co-op?";
    private static final String TRANSLATION_USER_PROMPT = "Question: " + QUESTION
            + "\n\nVisible platforms in the catalog: SNES, Steam"
            + "\n\nVisible hosts in the catalog: jordybox";
    private static final String ALL_NULL_FILTER = """
            {"titleSearch": null, "genre": null, "genresSearch": null, "developerSearch": null,
             "releaseYearMin": null, "releaseYearMax": null, "minLocalPlayers": null,
             "onlineMultiplayer": null, "singlePlayer": null, "platforms": null, "hosts": null}
            """;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInstallationRepository gameInstallationRepository;

    @Mock
    private ResilientAiService aiService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(gameRepository, gameInstallationRepository, aiService, new ObjectMapper(),
                properties());
    }

    @Test
    void validTranslationRunsGroundedQueryAndComposesAnswerWithCitations() {
        Game mario = aGame("Super Mario World", "SNES");
        Game kart = aGame("Super Mario Kart", "SNES");
        stubVisibleFilters();
        stubTranslation("""
                {"titleSearch": null, "genre": null, "genresSearch": null, "developerSearch": null,
                 "releaseYearMin": null, "releaseYearMax": null, "minLocalPlayers": 4,
                 "onlineMultiplayer": null, "singlePlayer": null, "platforms": null, "hosts": null}
                """);
        when(gameRepository.findForChatFilter(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(4),
                isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(mario, kart));
        when(aiService.call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                eq(compositionPromptFor(mario, kart))))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude",
                        "Two games support 4+ player local co-op."));

        ChatResponse response = chatService.ask(QUESTION, List.of());

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
        stubVisibleFilters();
        stubTranslation(ALL_NULL_FILTER);
        when(gameRepository.findForChatFilter(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(mario));
        when(aiService.call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                eq(compositionPromptFor(mario))))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude", "One game."));

        chatService.ask(QUESTION, List.of());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                promptCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(promptCaptor.getValue()).contains("Super Mario World");
            softly.assertThat(promptCaptor.getValue()).contains("Platformer");
        });
    }

    @Test
    void attachedGameIsComposedIntoTheAnswerWhenTheFilterMatchesNothing() {
        Game portal = aGame("Portal 2", "Steam");
        stubVisibleFilters();
        stubTranslation(ALL_NULL_FILTER);
        when(gameRepository.findVisibleById(portal.getId())).thenReturn(Optional.of(portal));
        when(gameRepository.findForChatFilter(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of());
        when(aiService.call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                eq(attachedCompositionPromptFor(portal))))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude",
                        "Portal 2 supports 4-player local co-op."));

        ChatResponse response = chatService.ask(QUESTION, List.of(portal.getId()));

        assertSoftly(softly -> {
            softly.assertThat(response.noMatch()).isFalse();
            softly.assertThat(response.games()).hasSize(1);
            softly.assertThat(response.games().getFirst().id()).isEqualTo(portal.getId());
            softly.assertThat(response.answer()).isEqualTo("Portal 2 supports 4-player local co-op.");
        });
    }

    @Test
    void attachedGameIsMergedWithFilterRowsAndCitedOnce() {
        Game portal = aGame("Portal 2", "Steam");
        Game mario = aGame("Super Mario World", "SNES");
        stubVisibleFilters();
        stubTranslation(ALL_NULL_FILTER);
        when(gameRepository.findVisibleById(portal.getId())).thenReturn(Optional.of(portal));
        when(gameRepository.findForChatFilter(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(portal, mario));
        String expectedPrompt = "Question: " + QUESTION + "\n\nCatalog rows:\n"
                + rowLine(portal, true) + "\n" + rowLine(mario, false) + "\n";
        when(aiService.call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT), eq(expectedPrompt)))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude", "Both."));

        ChatResponse response = chatService.ask(QUESTION, List.of(portal.getId()));

        assertThat(response.games()).extracting("id").containsExactly(portal.getId(), mario.getId());
    }

    @Test
    void attachedGameThatIsNotVisibleIsRejected() {
        UUID hiddenId = UUID.fromString("cccccccc-dddd-4eee-8fff-000000000000");
        when(gameRepository.findVisibleById(hiddenId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.ask(QUESTION, List.of(hiddenId)))
                .isInstanceOf(ChatAttachmentException.class);
    }

    @Test
    void moreThanFiveAttachedGamesIsRejected() {
        List<UUID> tooMany = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> chatService.ask(QUESTION, tooMany))
                .isInstanceOf(ChatAttachmentException.class);
        verifyNoMoreInteractions(aiService);
    }

    @Test
    void translationWithUnknownFieldIsChatUnavailable() {
        stubVisibleFilters();
        stubTranslation("""
                {"titleSearch": "mario", "hackAttempts": 5}
                """);

        assertThatThrownBy(() -> chatService.ask(QUESTION, List.of()))
                .isInstanceOf(ChatUnavailableException.class);
        verify(gameRepository).findVisiblePlatforms();
        verifyNoMoreInteractions(gameRepository);
    }

    @Test
    void translationWithUnknownPlatformIsChatUnavailable() {
        stubVisibleFilters();
        stubTranslation("""
                {"titleSearch": null, "genre": null, "genresSearch": null, "developerSearch": null,
                 "releaseYearMin": null, "releaseYearMax": null, "minLocalPlayers": null,
                 "onlineMultiplayer": null, "singlePlayer": null, "platforms": ["Dreamcast"], "hosts": null}
                """);

        assertThatThrownBy(() -> chatService.ask(QUESTION, List.of()))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void translationWithUnknownHostIsChatUnavailable() {
        stubVisibleFilters();
        stubTranslation("""
                {"titleSearch": null, "genre": null, "genresSearch": null, "developerSearch": null,
                 "releaseYearMin": null, "releaseYearMax": null, "minLocalPlayers": null,
                 "onlineMultiplayer": null, "singlePlayer": null, "platforms": null, "hosts": ["ghost-host"]}
                """);

        assertThatThrownBy(() -> chatService.ask(QUESTION, List.of()))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void translationWithOutOfBoundsValueIsChatUnavailable() {
        stubVisibleFilters();
        stubTranslation("""
                {"titleSearch": null, "genre": null, "minLocalPlayers": 99, "onlineMultiplayer": null,
                 "singlePlayer": null, "platforms": null}
                """);

        assertThatThrownBy(() -> chatService.ask(QUESTION, List.of()))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void malformedTranslationIsChatUnavailable() {
        stubVisibleFilters();
        stubTranslation("here is what I think you meant...");

        assertThatThrownBy(() -> chatService.ask(QUESTION, List.of()))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void translationAiFailureIsChatUnavailable() {
        stubVisibleFilters();
        when(aiService.call(eq("gamecatalog"), eq(ChatService.TRANSLATION_SYSTEM_PROMPT),
                eq(TRANSLATION_USER_PROMPT)))
                .thenReturn(AiCallResult.failure("gamecatalog", "anthropic", "claude",
                        ProviderFailureReason.TIMEOUT));

        assertThatThrownBy(() -> chatService.ask(QUESTION, List.of()))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void compositionAiFailureIsChatUnavailable() {
        Game mario = aGame("Super Mario World", "SNES");
        stubVisibleFilters();
        stubTranslation("""
                {"titleSearch": "mario", "genre": null, "genresSearch": null, "developerSearch": null,
                 "releaseYearMin": null, "releaseYearMax": null, "minLocalPlayers": null,
                 "onlineMultiplayer": null, "singlePlayer": null, "platforms": null, "hosts": null}
                """);
        when(gameRepository.findForChatFilter(eq("mario"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(mario));
        when(aiService.call(eq("gamecatalog"), eq(ChatService.COMPOSITION_SYSTEM_PROMPT),
                eq(compositionPromptFor(mario))))
                .thenReturn(AiCallResult.failure("gamecatalog", "anthropic", "claude",
                        ProviderFailureReason.RATE_LIMITED));

        assertThatThrownBy(() -> chatService.ask(QUESTION, List.of()))
                .isInstanceOf(ChatUnavailableException.class);
    }

    @Test
    void zeroRowsIsExplicitNoMatchAndSkipsComposition() {
        stubVisibleFilters();
        stubTranslation("""
                {"titleSearch": "zelda", "genre": null, "genresSearch": null, "developerSearch": null,
                 "releaseYearMin": null, "releaseYearMax": null, "minLocalPlayers": null,
                 "onlineMultiplayer": null, "singlePlayer": null, "platforms": null, "hosts": null}
                """);
        when(gameRepository.findForChatFilter(eq("zelda"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of());

        ChatResponse response = chatService.ask(QUESTION, List.of());

        assertSoftly(softly -> {
            softly.assertThat(response.noMatch()).isTrue();
            softly.assertThat(response.games()).isEmpty();
            softly.assertThat(response.answer()).isNotBlank();
        });
        verify(aiService).call(eq("gamecatalog"), eq(ChatService.TRANSLATION_SYSTEM_PROMPT),
                eq(TRANSLATION_USER_PROMPT));
        verifyNoMoreInteractions(aiService);
    }

    @Test
    void translationPromptIncludesQuestionVisiblePlatformsAndHosts() {
        stubVisibleFilters();
        stubTranslation(ALL_NULL_FILTER);
        when(gameRepository.findForChatFilter(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of());

        chatService.ask(QUESTION, List.of());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).call(eq("gamecatalog"), eq(ChatService.TRANSLATION_SYSTEM_PROMPT),
                promptCaptor.capture());
        assertSoftly(softly -> {
            softly.assertThat(promptCaptor.getValue()).contains(QUESTION);
            softly.assertThat(promptCaptor.getValue()).contains("SNES");
            softly.assertThat(promptCaptor.getValue()).contains("Steam");
            softly.assertThat(promptCaptor.getValue()).contains("jordybox");
        });
    }

    private String compositionPromptFor(Game... games) {
        StringBuilder prompt = new StringBuilder("Question: " + QUESTION + "\n\nCatalog rows:\n");
        for (Game game : games) {
            prompt.append(rowLine(game, false)).append('\n');
        }

        return prompt.toString();
    }

    private String attachedCompositionPromptFor(Game... games) {
        StringBuilder prompt = new StringBuilder("Question: " + QUESTION + "\n\nCatalog rows:\n");
        for (Game game : games) {
            prompt.append(rowLine(game, true)).append('\n');
        }

        return prompt.toString();
    }

    private String rowLine(Game game, boolean attached) {
        StringBuilder line = new StringBuilder("- " + game.getTitle() + " (" + game.getPlatform() + ")");
        if (attached) {
            line.append(" [attached — the user is asking about this game]");
        }
        line.append(" | genre: ").append(game.getGenre())
                .append(" | genres: ").append(game.getGenres())
                .append(" | developer: ").append(game.getDeveloper())
                .append(" | publisher: ").append(game.getPublisher())
                .append(" | releaseYear: ").append(game.getReleaseYear())
                .append(" | hosts: ")
                .append(" | maxLocalPlayers: ").append(game.getMaxLocalPlayers())
                .append(" | onlineMultiplayer: ").append(game.getOnlineMultiplayer())
                .append(" | singlePlayer: ").append(game.getSinglePlayer());
        if (attached) {
            line.append(" | description: ").append(game.getDescription());
        }

        return line.toString();
    }

    private void stubVisibleFilters() {
        when(gameRepository.findVisiblePlatforms()).thenReturn(VISIBLE_PLATFORMS);
        when(gameRepository.findVisibleHosts()).thenReturn(VISIBLE_HOSTS);
    }

    private void stubTranslation(String json) {
        when(aiService.call(eq("gamecatalog"), eq(ChatService.TRANSLATION_SYSTEM_PROMPT),
                eq(TRANSLATION_USER_PROMPT)))
                .thenReturn(AiCallResult.success("gamecatalog", "anthropic", "claude", json));
    }

    private Game aGame(String title, String platform) {
        Game game = Game.builder()
                .platform(platform)
                .title(title)
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
                new GameCatalogProperties.Metadata(25, 3),
                new GameCatalogProperties.Scan(10000, 1_048_576, 262_144, 0.5));
    }
}
