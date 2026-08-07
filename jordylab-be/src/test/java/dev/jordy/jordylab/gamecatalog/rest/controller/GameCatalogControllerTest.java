package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatGameRef;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.service.ArtworkContent;
import dev.jordy.jordylab.gamecatalog.service.ArtworkService;
import dev.jordy.jordylab.gamecatalog.service.ChatService;
import dev.jordy.jordylab.gamecatalog.service.ChatUnavailableException;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameCatalogController.class)
class GameCatalogControllerTest {

    private static final UUID GAME_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameQueryService gameQueryService;

    @MockitoBean
    private ArtworkService artworkService;

    @MockitoBean
    private ChatService chatService;

    @Test
    void gamesReturnsPaginatedSummaries() throws Exception {
        when(gameQueryService.getGames(isNull(), isNull(), eq(0), eq(60)))
                .thenReturn(new GamesPageResponse(List.of(
                        new GameSummaryResponse(GAME_ID, "Super Mario World", "SNES", ArtworkStatus.EXTERNAL_URL,
                                "https://example.com/smw.png", null)),
                        0, 60, 312, 6));

        mockMvc.perform(get("/api/gamecatalog/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Super Mario World"))
                .andExpect(jsonPath("$.content[0].platform").value("SNES"))
                .andExpect(jsonPath("$.content[0].artworkStatus").value("EXTERNAL_URL"))
                .andExpect(jsonPath("$.content[0].artworkUrl").value("https://example.com/smw.png"))
                .andExpect(jsonPath("$.content[0].artworkEndpoint").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(60))
                .andExpect(jsonPath("$.totalElements").value(312))
                .andExpect(jsonPath("$.totalPages").value(6));
    }

    @Test
    void gamesPassesSearchPlatformAndPagination() throws Exception {
        when(gameQueryService.getGames(eq("mario"), eq("SNES"), eq(2), eq(30)))
                .thenReturn(new GamesPageResponse(List.of(), 2, 30, 0, 0));

        mockMvc.perform(get("/api/gamecatalog/games")
                        .param("search", "mario")
                        .param("platform", "SNES")
                        .param("page", "2")
                        .param("size", "30"))
                .andExpect(status().isOk());

        verify(gameQueryService).getGames("mario", "SNES", 2, 30);
    }

    @Test
    void gamesCapsPageSizeAtTwoHundred() throws Exception {
        when(gameQueryService.getGames(isNull(), isNull(), eq(0), eq(200)))
                .thenReturn(new GamesPageResponse(List.of(), 0, 200, 0, 0));

        mockMvc.perform(get("/api/gamecatalog/games").param("size", "5000"))
                .andExpect(status().isOk());

        verify(gameQueryService).getGames(null, null, 0, 200);
    }

    @Test
    void platformsReturnsDistinctVisiblePlatforms() throws Exception {
        when(gameQueryService.getPlatforms())
                .thenReturn(new PlatformsResponse(List.of("SNES", "PlayStation 2", "Steam")));

        mockMvc.perform(get("/api/gamecatalog/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms.length()").value(3))
                .andExpect(jsonPath("$.platforms[1]").value("PlayStation 2"));
    }

    @Test
    void gameDetailReturnsEnrichedGame() throws Exception {
        when(gameQueryService.getGameDetail(GAME_ID))
                .thenReturn(Optional.of(new GameDetailResponse(GAME_ID, "Super Mario World", "SNES", "snes",
                        ArtworkStatus.EXTERNAL_URL, "https://example.com/smw.png", null,
                        dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus.ENRICHED,
                        "Platformer", 2, false, true, "A classic.",
                        java.time.Instant.parse("2026-08-02T10:15:00Z"))));

        mockMvc.perform(get("/api/gamecatalog/games/{id}", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.title").value("Super Mario World"))
                .andExpect(jsonPath("$.sourceKey").value("snes"))
                .andExpect(jsonPath("$.enrichmentStatus").value("ENRICHED"))
                .andExpect(jsonPath("$.genre").value("Platformer"))
                .andExpect(jsonPath("$.maxLocalPlayers").value(2))
                .andExpect(jsonPath("$.singlePlayer").value(true))
                .andExpect(jsonPath("$.description").value("A classic."))
                .andExpect(jsonPath("$.firstSeenAt").value("2026-08-02T10:15:00Z"));
    }

    @Test
    void gameDetailIsNotFoundWhenNotVisible() throws Exception {
        when(gameQueryService.getGameDetail(GAME_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/gamecatalog/games/{id}", GAME_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void chatReturnsAnswerWithCitations() throws Exception {
        when(chatService.ask("which games support 4-player co-op?"))
                .thenReturn(new ChatResponse("One game supports 4-player local co-op.",
                        List.of(new ChatGameRef(GAME_ID, "Super Mario World", "SNES")), false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/gamecatalog/chat")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(validChatBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("One game supports 4-player local co-op."))
                .andExpect(jsonPath("$.games[0].id").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.games[0].title").value("Super Mario World"))
                .andExpect(jsonPath("$.noMatch").value(false));
    }

    @Test
    void chatBlankQuestionIsBadRequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/gamecatalog/chat")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"question\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("QUESTION_INVALID"));
    }

    @Test
    void chatUnavailableIsServiceUnavailable() throws Exception {
        when(chatService.ask("which games support 4-player co-op?"))
                .thenThrow(new ChatUnavailableException("chat translation failed: TIMEOUT"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/gamecatalog/chat")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(validChatBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("CHAT_UNAVAILABLE"));
    }

    @Language("JSON")
    private String validChatBody() {
        return """
                { "question": "which games support 4-player co-op?" }
                """;
    }

    @Test
    void artworkServesBytesWithNosniffAndCacheHeaders() throws Exception {
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3};
        when(artworkService.loadVisibleArtwork(GAME_ID))
                .thenReturn(Optional.of(new ArtworkContent(pngBytes, "image/png")));

        mockMvc.perform(get("/api/gamecatalog/games/{id}/artwork", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(pngBytes))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Cache-Control"));
    }

    @Test
    void artworkIsNotFoundWhenServiceHasNothing() throws Exception {
        when(artworkService.loadVisibleArtwork(GAME_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/gamecatalog/games/{id}/artwork", GAME_ID))
                .andExpect(status().isNotFound());
    }
}
