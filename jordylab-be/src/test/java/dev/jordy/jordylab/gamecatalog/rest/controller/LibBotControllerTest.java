package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.libbot.Candidate;
import dev.jordy.jordylab.gamecatalog.service.libbot.ConversationStore;
import dev.jordy.jordylab.gamecatalog.service.libbot.Language;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotAttachmentException;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotOutcome;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotResult;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotService;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotStage;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotUnavailableException;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestSecurityConfig.class, LibBotControllerTest.StubConfiguration.class})
@WebMvcTest(LibBotController.class)
class LibBotControllerTest {

    private static final UUID GAME_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    /** Replaces the real pipeline with a scripted one so the stream shape is what is under test. */
    static class ScriptedLibBotService extends LibBotService {

        LibBotResult result;
        RuntimeException failure;

        ScriptedLibBotService() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public LibBotResult ask(Ask ask, Consumer<LibBotStage> onStage, BooleanSupplier stillConnected) {
            onStage.accept(LibBotStage.UNDERSTANDING);
            if (failure != null) {
                throw failure;
            }
            onStage.accept(LibBotStage.SEARCHING);
            onStage.accept(LibBotStage.WRITING);

            return result;
        }

        @Override
        public void requireAttachable(List<UUID> attachedGameIds, String userSubject) {
            if (attachedGameIds.contains(UUID.fromString("00000000-0000-0000-0000-00000000dead"))) {
                throw new LibBotAttachmentException("not visible");
            }
        }
    }

    @TestConfiguration
    static class StubConfiguration {

        @Bean
        ScriptedLibBotService libBotService() {
            return new ScriptedLibBotService();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScriptedLibBotService libBotService;

    @MockitoBean
    private ConversationStore conversationStore;

    @MockitoBean
    private GameQueryService gameQueryService;

    @BeforeEach
    void setUp() {
        libBotService.failure = null;
        libBotService.result = new LibBotResult(LibBotOutcome.ANSWERED, Language.EN,
                "Jackbox Party Pack 9 fits six people.", List.of("6+ local players", "local multiplayer"),
                new LibBotResult.Unknown(3, "For 3 more games the player count isn't known yet."),
                List.of(Candidate.builder().gameId(GAME_ID).title("Jackbox Party Pack 9").platforms(List.of("Steam"))
                        .confirmed(true).build()));
        when(gameQueryService.getSummaries(List.of(GAME_ID))).thenReturn(Map.of(GAME_ID, new GameSummaryResponse(
                GAME_ID, "Jackbox Party Pack 9", List.of(PlatformChip.of("Steam")), List.of(GameSource.STEAM_OWNED),
                ArtworkStatus.EXTERNAL_URL, "https://cdn.example/jackbox.jpg", null, InstallStatus.INSTALLED, true, VoteTotalsResponse.none(), null,
                null)));
    }

    private String stream(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/gamecatalog/libbot/ask")
                        .with(jwt().jwt(token -> token.subject("guest-subject")))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);

        return result.getResponse().getContentAsString();
    }

    private static String body(String message) {
        return "{\"conversationId\":\"7b1f6c2e-0000-4000-8000-000000000001\",\"message\":\"" + message + "\"}";
    }

    @Test
    void streamsTheStagesInOrderThenOneAnswerWithTheReferences() throws Exception {
        String events = stream(body("six people tonight"));

        assertSoftly(softly -> {
            softly.assertThat(events.indexOf("\"stage\":\"UNDERSTANDING\"")).isGreaterThanOrEqualTo(0);
            softly.assertThat(events.indexOf("\"stage\":\"UNDERSTANDING\""))
                    .isLessThan(events.indexOf("\"stage\":\"SEARCHING\""));
            softly.assertThat(events.indexOf("\"stage\":\"SEARCHING\""))
                    .isLessThan(events.indexOf("\"stage\":\"WRITING\""));
            softly.assertThat(events).contains("event:answer").contains("\"outcome\":\"ANSWERED\"")
                    .contains("\"language\":\"en\"").contains("6+ local players")
                    .contains("\"unknown\":{\"count\":3").contains("\"title\":\"Jackbox Party Pack 9\"")
                    .contains("\"externalUrl\":\"https://cdn.example/jackbox.jpg\"")
                    .doesNotContain("event:error");
            softly.assertThat(events.split("event:answer", -1)).hasSize(2);
        });
    }

    @Test
    void anUnavailableLibBotEndsWithOneRetryableErrorAndNoAnswer() throws Exception {
        libBotService.failure = new LibBotUnavailableException("providers down");

        String events = stream(body("anything"));

        assertSoftly(softly -> {
            softly.assertThat(events).contains("event:error").contains("\"code\":\"UNAVAILABLE\"")
                    .contains("\"retryable\":true").doesNotContain("event:answer");
        });
    }

    @Test
    void anUnexpectedFailureEndsWithAnInternalError() throws Exception {
        libBotService.failure = new IllegalStateException("boom");

        assertThat(stream(body("anything"))).contains("\"code\":\"INTERNAL\"").doesNotContain("boom");
    }

    @Test
    void anEmptyMessageIsRejectedBeforeAnyStream() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/libbot/ask").with(jwt().jwt(token -> token.subject("s")))
                .contentType(MediaType.APPLICATION_JSON).content(body("   "))).andExpect(status().isBadRequest());
    }

    @Test
    void aMessageOverAThousandCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/libbot/ask").with(jwt().jwt(token -> token.subject("s")))
                .contentType(MediaType.APPLICATION_JSON).content(body("x".repeat(1001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanFiveAttachedGamesAreRejected() throws Exception {
        String ids = String.join(",", java.util.Collections.nCopies(6, "\"" + GAME_ID + "\""));

        mockMvc.perform(post("/api/gamecatalog/libbot/ask").with(jwt().jwt(token -> token.subject("s")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"c1\",\"message\":\"hi\",\"attachedGameIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAttachedGameThatCannotBeUsedIsA400BeforeTheStreamOpens() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/libbot/ask").with(jwt().jwt(token -> token.subject("s")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"c1\",\"message\":\"hi\","
                        + "\"attachedGameIds\":[\"00000000-0000-0000-0000-00000000dead\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aConversationIdWithUnsafeCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/libbot/ask").with(jwt().jwt(token -> token.subject("s")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"../../etc\",\"message\":\"hi\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void forgettingAConversationIsIdempotentAndUsesTheCallersOwnKey() throws Exception {
        mockMvc.perform(delete("/api/gamecatalog/libbot/conversations/c1")
                .with(jwt().jwt(token -> token.subject("guest-subject")))).andExpect(status().isNoContent());

        verify(conversationStore).clear("guest-subject", "c1");
    }
}
