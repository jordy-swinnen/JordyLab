package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatGameRef;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostRef;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RefreshAllResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RefreshCountResponse;
import dev.jordy.jordylab.gamecatalog.service.ArtworkContent;
import dev.jordy.jordylab.gamecatalog.service.ArtworkService;
import dev.jordy.jordylab.gamecatalog.service.CatalogRefreshService;
import dev.jordy.jordylab.gamecatalog.service.ChatAttachmentException;
import dev.jordy.jordylab.gamecatalog.service.ChatService;
import dev.jordy.jordylab.gamecatalog.service.ChatUnavailableException;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.MetadataNotSupportedException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(GameCatalogController.class)
class GameCatalogControllerTest {

    private static final UUID GAME_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final Instant FIRST_SEEN_AT = Instant.parse("2026-08-02T10:15:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameQueryService gameQueryService;

    @MockitoBean
    private ArtworkService artworkService;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private CatalogRefreshService catalogRefreshService;

    @Test
    void gamesReturnsPaginatedSummaries() throws Exception {
        when(gameQueryService.getGames(isNull(), isNull(), isNull(), eq("INSTALLED"), isNull(), isNull(), eq(0), eq(60)))
                .thenReturn(new GamesPageResponse(List.of(
                        new GameSummaryResponse(GAME_ID, "Super Mario World", "SNES", ArtworkStatus.EXTERNAL_URL,
                                "https://example.com/smw.png", null, InstallStatus.INSTALLED, LibrarySource.LOCAL, null)),
                        0, 60, 312, 6));

        mockMvc.perform(get("/api/gamecatalog/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Super Mario World"))
                .andExpect(jsonPath("$.content[0].platform").value("SNES"))
                .andExpect(jsonPath("$.content[0].coverStatus").value("EXTERNAL_URL"))
                .andExpect(jsonPath("$.content[0].coverUrl").value("https://example.com/smw.png"))
                .andExpect(jsonPath("$.content[0].coverEndpoint").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(60))
                .andExpect(jsonPath("$.totalElements").value(312))
                .andExpect(jsonPath("$.totalPages").value(6));
    }

    @Test
    void gamesPassesSearchPlatformHostAndPagination() throws Exception {
        when(gameQueryService.getGames(eq("mario"), eq("SNES"), eq("jordybox"), eq("INSTALLED"), isNull(), isNull(), eq(2), eq(30)))
                .thenReturn(new GamesPageResponse(List.of(), 2, 30, 0, 0));

        mockMvc.perform(get("/api/gamecatalog/games")
                        .param("search", "mario")
                        .param("platform", "SNES")
                        .param("host", "jordybox")
                        .param("page", "2")
                        .param("size", "30"))
                .andExpect(status().isOk());

        verify(gameQueryService).getGames("mario", "SNES", "jordybox", "INSTALLED", null, null, 2, 30);
    }

    @Test
    void gamesCapsPageSizeAtTwoHundred() throws Exception {
        when(gameQueryService.getGames(isNull(), isNull(), isNull(), eq("INSTALLED"), isNull(), isNull(), eq(0), eq(200)))
                .thenReturn(new GamesPageResponse(List.of(), 0, 200, 0, 0));

        mockMvc.perform(get("/api/gamecatalog/games").param("size", "5000"))
                .andExpect(status().isOk());

        verify(gameQueryService).getGames(null, null, null, "INSTALLED", null, null, 0, 200);
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
    void hostsReturnsDistinctVisibleHosts() throws Exception {
        when(gameQueryService.getHosts()).thenReturn(new HostsResponse(List.of("jordybox", "ryzen-desktop")));

        mockMvc.perform(get("/api/gamecatalog/hosts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosts.length()").value(2))
                .andExpect(jsonPath("$.hosts[0]").value("jordybox"));
    }

    @Test
    void gameDetailReturnsEnrichedGameWithHostsMetadataAndBanner() throws Exception {
        when(gameQueryService.getGameDetail(GAME_ID))
                .thenReturn(Optional.of(new GameDetailResponse(GAME_ID, "Portal 2", "Steam",
                        List.of(new HostRef("jordybox", SourceType.STEAM)), Map.of(),
                        ArtworkStatus.EXTERNAL_URL, "https://example.com/cover.png", null,
                        ArtworkStatus.EXTERNAL_URL, "https://example.com/banner.png", null,
                        EnrichmentStatus.ENRICHED, "Puzzle", "Puzzle, Adventure", "Valve", "Valve", 2011, "STEAM",
                        2, false, true, "A classic.", FIRST_SEEN_AT, InstallStatus.INSTALLED, LibrarySource.OWNED, List.of("jordy"), null, null, null, MultiplayerSource.UNKNOWN)));

        mockMvc.perform(get("/api/gamecatalog/games/{id}", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.title").value("Portal 2"))
                .andExpect(jsonPath("$.hosts[0].hostname").value("jordybox"))
                .andExpect(jsonPath("$.hosts[0].sourceType").value("STEAM"))
                .andExpect(jsonPath("$.coverUrl").value("https://example.com/cover.png"))
                .andExpect(jsonPath("$.bannerUrl").value("https://example.com/banner.png"))
                .andExpect(jsonPath("$.enrichmentStatus").value("ENRICHED"))
                .andExpect(jsonPath("$.genre").value("Puzzle"))
                .andExpect(jsonPath("$.genres").value("Puzzle, Adventure"))
                .andExpect(jsonPath("$.developer").value("Valve"))
                .andExpect(jsonPath("$.publisher").value("Valve"))
                .andExpect(jsonPath("$.releaseYear").value(2011))
                .andExpect(jsonPath("$.metadataSource").value("STEAM"))
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
    void refreshMetadataReturnsTheUpdatedDetail() throws Exception {
        when(catalogRefreshService.refreshMetadata(GAME_ID)).thenReturn(Optional.of(aDetail()));

        mockMvc.perform(post("/api/gamecatalog/games/{id}/metadata/refresh", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.developer").value("Valve"))
                .andExpect(jsonPath("$.metadataSource").value("STEAM"));
    }

    @Test
    void refreshMetadataForANonSteamGameIsBadRequest() throws Exception {
        when(catalogRefreshService.refreshMetadata(GAME_ID))
                .thenThrow(new MetadataNotSupportedException("deterministic metadata is Steam-only"));

        mockMvc.perform(post("/api/gamecatalog/games/{id}/metadata/refresh", GAME_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("METADATA_NOT_SUPPORTED"));
    }

    @Test
    void refreshMetadataForAnInvisibleGameIsNotFound() throws Exception {
        when(catalogRefreshService.refreshMetadata(GAME_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/gamecatalog/games/{id}/metadata/refresh", GAME_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshEnrichmentReturnsTheUpdatedDetail() throws Exception {
        when(catalogRefreshService.refreshEnrichment(GAME_ID)).thenReturn(Optional.of(aDetail()));

        mockMvc.perform(post("/api/gamecatalog/games/{id}/enrichment/refresh", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("A classic."));
    }

    @Test
    void refreshEnrichmentForAnInvisibleGameIsNotFound() throws Exception {
        when(catalogRefreshService.refreshEnrichment(GAME_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/gamecatalog/games/{id}/enrichment/refresh", GAME_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshPendingReturnsProcessedAndRemainingCounts() throws Exception {
        when(catalogRefreshService.refreshPending())
                .thenReturn(new RefreshAllResponse(new RefreshCountResponse(3, 0), new RefreshCountResponse(2, 5), new RefreshCountResponse(1, 4)));

        mockMvc.perform(post("/api/gamecatalog/games/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.processed").value(3))
                .andExpect(jsonPath("$.metadata.remaining").value(0))
                .andExpect(jsonPath("$.enrichment.processed").value(2))
                .andExpect(jsonPath("$.enrichment.remaining").value(5));
    }

    @Test
    void chatReturnsAnswerWithCitations() throws Exception {
        when(chatService.ask("which games support 4-player co-op?", List.of()))
                .thenReturn(new ChatResponse("One game supports 4-player local co-op.",
                        List.of(new ChatGameRef(GAME_ID, "Super Mario World", "SNES", null, null)), false));

        mockMvc.perform(post("/api/gamecatalog/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("One game supports 4-player local co-op."))
                .andExpect(jsonPath("$.games[0].id").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.games[0].title").value("Super Mario World"))
                .andExpect(jsonPath("$.noMatch").value(false));
    }

    @Test
    void chatPassesAttachedGameIdsToTheService() throws Exception {
        when(chatService.ask("is this good for 4 players?", List.of(GAME_ID)))
                .thenReturn(new ChatResponse("Yes.",
                        List.of(new ChatGameRef(GAME_ID, "Portal 2", "Steam", null, null)), false));

        mockMvc.perform(post("/api/gamecatalog/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"is this good for 4 players?\", \"gameIds\": [\""
                                + GAME_ID + "\"]}"))
                .andExpect(status().isOk());

        verify(chatService).ask("is this good for 4 players?", List.of(GAME_ID));
    }

    @Test
    void chatBlankQuestionIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("QUESTION_INVALID"));
    }

    @Test
    void chatInvalidAttachmentIsBadRequest() throws Exception {
        when(chatService.ask("is this good for 4 players?", List.of(GAME_ID)))
                .thenThrow(new ChatAttachmentException("attached game is not visible"));

        mockMvc.perform(post("/api/gamecatalog/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"is this good for 4 players?\", \"gameIds\": [\""
                                + GAME_ID + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("GAME_IDS_INVALID"));
    }

    @Test
    void chatWithMalformedGameIdIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"which games?\", \"gameIds\": [\"not-a-uuid\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("GAME_IDS_INVALID"));
    }

    @Test
    void chatWithNonTextQuestionIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": {\"nested\": true}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("QUESTION_INVALID"));
    }

    @Test
    void chatUnavailableIsServiceUnavailable() throws Exception {
        when(chatService.ask("which games support 4-player co-op?", List.of()))
                .thenThrow(new ChatUnavailableException("chat translation failed: TIMEOUT"));

        mockMvc.perform(post("/api/gamecatalog/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChatBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("CHAT_UNAVAILABLE"));
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

    private GameDetailResponse aDetail() {
        return new GameDetailResponse(GAME_ID, "Portal 2", "Steam",
                List.of(new HostRef("jordybox", SourceType.STEAM)), Map.of(),
                ArtworkStatus.EXTERNAL_URL, "https://example.com/cover.png", null,
                ArtworkStatus.EXTERNAL_URL, "https://example.com/banner.png", null,
                EnrichmentStatus.ENRICHED, "Puzzle", "Puzzle, Adventure", "Valve", "Valve", 2011, "STEAM",
                2, false, true, "A classic.", FIRST_SEEN_AT, InstallStatus.INSTALLED, LibrarySource.OWNED, List.of("jordy"), null, null, null, MultiplayerSource.UNKNOWN);
    }

    @Language("JSON")
    private String validChatBody() {
        return """
                { "question": "which games support 4-player co-op?" }
                """;
    }
}
