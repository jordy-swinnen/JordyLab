package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.DescriptionSource;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.GameSource;
import dev.jordy.jordylab.gamecatalog.domain.InstallStatus;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.DescriptionResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.FactSourcesResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameDetailResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamesPageResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlacesResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.RomSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.VoteTotalsResponse;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameFilter;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformsResponse;
import dev.jordy.jordylab.gamecatalog.service.ArtworkContent;
import dev.jordy.jordylab.gamecatalog.service.ArtworkService;
import dev.jordy.jordylab.gamecatalog.service.CatalogRefreshService;
import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.MetadataNotSupportedException;
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
    private CatalogRefreshService catalogRefreshService;

    @Test
    void gamesReturnsPaginatedSummaries() throws Exception {
        when(gameQueryService.getGames(eq(GameFilter.builder().build()), eq(0), eq(60)))
                .thenReturn(new GamesPageResponse(List.of(
                        new GameSummaryResponse(GAME_ID, "Super Mario World", List.of(PlatformChip.of("SNES")),
                                List.of(GameSource.EMULATED), ArtworkStatus.EXTERNAL_URL,
                                "https://example.com/smw.png", null, InstallStatus.INSTALLED, null,
                                new VoteTotalsResponse(3, 1, 0), MarkType.WANT_TO_PLAY,
                                new RomSummaryResponse(RomSummaryResponse.State.MIXED, 1, 0, 1, 2))),
                        0, 60, 312, 6, null));

        mockMvc.perform(get("/api/gamecatalog/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Super Mario World"))
                .andExpect(jsonPath("$.content[0].platforms[0].name").value("SNES"))
                .andExpect(jsonPath("$.content[0].platforms[0].family").value("NINTENDO"))
                .andExpect(jsonPath("$.content[0].platforms[0].background").value("#E60012"))
                .andExpect(jsonPath("$.content[0].sources[0]").value("EMULATED"))
                .andExpect(jsonPath("$.content[0].coverStatus").value("EXTERNAL_URL"))
                .andExpect(jsonPath("$.content[0].coverUrl").value("https://example.com/smw.png"))
                .andExpect(jsonPath("$.content[0].coverEndpoint").doesNotExist())
                .andExpect(jsonPath("$.content[0].votes.wantToPlay").value(3))
                .andExpect(jsonPath("$.content[0].myMark").value("WANT_TO_PLAY"))
                .andExpect(jsonPath("$.content[0].romSummary.state").value("MIXED"))
                .andExpect(jsonPath("$.unknownPlayerCount").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(60))
                .andExpect(jsonPath("$.totalElements").value(312))
                .andExpect(jsonPath("$.totalPages").value(6));
    }

    @Test
    void gamesPassesEveryFilterAndPaginationToTheService() throws Exception {
        UUID livingRoom = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        GameFilter expected = GameFilter.builder().search("mario").platforms(List.of("SNES", "Steam"))
                .whereIds(List.of(livingRoom)).installStatus("ALL").sources(List.of(GameSource.EMULATED))
                .minLocalPlayers(6).romStatuses(List.of(RomStatus.BROKEN)).marks(List.of(MarkType.WANT_TO_PLAY))
                .markScope(GameFilter.MarkScope.MINE).sort(GameFilter.Sort.MOST_WANTED).build();
        when(gameQueryService.getGames(eq(expected), eq(2), eq(30)))
                .thenReturn(new GamesPageResponse(List.of(), 2, 30, 0, 0, 31L));

        mockMvc.perform(get("/api/gamecatalog/games")
                        .param("search", " mario ")
                        .param("platform", "SNES", "Steam")
                        .param("where", livingRoom.toString())
                        .param("installStatus", "all")
                        .param("source", "EMULATED")
                        .param("minLocalPlayers", "6")
                        .param("romStatus", "BROKEN")
                        .param("mark", "WANT_TO_PLAY")
                        .param("markScope", "MINE")
                        .param("sort", "MOST_WANTED")
                        .param("page", "2")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unknownPlayerCount").value(31));
    }

    @Test
    void gamesFallBackToInstalledForAnUnknownInstallStatus() throws Exception {
        when(gameQueryService.getGames(eq(GameFilter.builder().build()), eq(0), eq(60)))
                .thenReturn(new GamesPageResponse(List.of(), 0, 60, 0, 0, null));

        mockMvc.perform(get("/api/gamecatalog/games").param("installStatus", "bogus"))
                .andExpect(status().isOk());
    }

    @Test
    void gamesRejectsAPlayerCountOutsideOneToEight() throws Exception {
        mockMvc.perform(get("/api/gamecatalog/games").param("minLocalPlayers", "9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gamesCapsPageSizeAtTwoHundred() throws Exception {
        when(gameQueryService.getGames(eq(GameFilter.builder().build()), eq(0), eq(200)))
                .thenReturn(new GamesPageResponse(List.of(), 0, 200, 0, 0, null));

        mockMvc.perform(get("/api/gamecatalog/games").param("size", "5000"))
                .andExpect(status().isOk());

        verify(gameQueryService).getGames(GameFilter.builder().build(), 0, 200);
    }

    @Test
    void platformsReturnsDistinctVisiblePlatforms() throws Exception {
        when(gameQueryService.getPlatforms())
                .thenReturn(new PlatformsResponse(List.of(PlatformChip.of("SNES"), PlatformChip.of("PlayStation 2"),
                        PlatformChip.of("Steam"))));

        mockMvc.perform(get("/api/gamecatalog/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms.length()").value(3))
                .andExpect(jsonPath("$.platforms[1].name").value("PlayStation 2"))
                .andExpect(jsonPath("$.platforms[1].background").value("#0070D1"))
                .andExpect(jsonPath("$.platforms[2].foreground").value("#66C0F4"));
    }

    @Test
    void placesReturnsHostsAndConsolesByLabel() throws Exception {
        UUID hostId = UUID.fromString("11111111-2222-4333-8444-555555555555");
        UUID consoleId = UUID.fromString("66666666-7777-4888-9999-000000000000");
        when(gameQueryService.getPlaces()).thenReturn(new PlacesResponse(List.of(
                new PlacesResponse.PlaceOption(hostId, PlacesResponse.Kind.HOST, "Living room PC"),
                new PlacesResponse.PlaceOption(consoleId, PlacesResponse.Kind.CONSOLE, "Nintendo Switch"))));

        mockMvc.perform(get("/api/gamecatalog/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].kind").value("HOST"))
                .andExpect(jsonPath("$.places[0].label").value("Living room PC"))
                .andExpect(jsonPath("$.places[1].kind").value("CONSOLE"));
    }

    @Test
    void gameDetailReturnsEnrichedGameWithHostsMetadataAndBanner() throws Exception {
        when(gameQueryService.getGameDetail(GAME_ID, null))
                .thenReturn(Optional.of(new GameDetailResponse(GAME_ID, "Portal 2", List.of(PlatformChip.of("Steam")),
                        List.of(GameSource.STEAM_OWNED), List.of(aSteamPlace()),
                        ArtworkStatus.EXTERNAL_URL, "https://example.com/cover.png", null,
                        ArtworkStatus.EXTERNAL_URL, "https://example.com/banner.png", null,
                        EnrichmentStatus.ENRICHED, "Puzzle", "Puzzle, Adventure", "Valve", "Valve", 2011, "STEAM",
                        2, false, true, new DescriptionResponse("A classic.", DescriptionSource.AI, "claude-haiku-4.5", "jev-router",
                        Instant.parse("2026-10-07T10:12:00Z")), FIRST_SEEN_AT, InstallStatus.INSTALLED, null, null, null,
                MultiplayerSource.UNKNOWN, new FactSourcesResponse("STEAM", null), VoteTotalsResponse.none(), null)));

        mockMvc.perform(get("/api/gamecatalog/games/{id}", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GAME_ID.toString()))
                .andExpect(jsonPath("$.title").value("Portal 2"))
                .andExpect(jsonPath("$.platforms[0].name").value("Steam"))
                .andExpect(jsonPath("$.sources[0]").value("STEAM_OWNED"))
                .andExpect(jsonPath("$.places[0].kind").value("STEAM_LIBRARY"))
                .andExpect(jsonPath("$.places[0].librarySource").value("OWNED"))
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
                .andExpect(jsonPath("$.description.text").value("A classic."))
                .andExpect(jsonPath("$.description.source").value("AI"))
                .andExpect(jsonPath("$.description.model").value("claude-haiku-4.5"))
                .andExpect(jsonPath("$.description.requestedModel").value("jev-router"))
                .andExpect(jsonPath("$.description.writtenAt").value("2026-10-07T10:12:00Z"))
                .andExpect(jsonPath("$.factSources.facts").value("STEAM"))
                .andExpect(jsonPath("$.factSources.multiplayer").doesNotExist())
                .andExpect(jsonPath("$.firstSeenAt").value("2026-08-02T10:15:00Z"));
    }

    @Test
    void gameDetailIsNotFoundWhenNotVisible() throws Exception {
        when(gameQueryService.getGameDetail(GAME_ID, null)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/gamecatalog/games/{id}", GAME_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshMetadataReturnsTheUpdatedDetail() throws Exception {
        when(catalogRefreshService.refreshMetadata(GAME_ID, null)).thenReturn(Optional.of(aDetail()));

        mockMvc.perform(post("/api/gamecatalog/games/{id}/metadata/refresh", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.developer").value("Valve"))
                .andExpect(jsonPath("$.metadataSource").value("STEAM"));
    }

    @Test
    void refreshMetadataForANonSteamGameIsBadRequest() throws Exception {
        when(catalogRefreshService.refreshMetadata(GAME_ID, null))
                .thenThrow(new MetadataNotSupportedException("deterministic metadata is Steam-only"));

        mockMvc.perform(post("/api/gamecatalog/games/{id}/metadata/refresh", GAME_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("METADATA_NOT_SUPPORTED"));
    }

    @Test
    void refreshMetadataForAnInvisibleGameIsNotFound() throws Exception {
        when(catalogRefreshService.refreshMetadata(GAME_ID, null)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/gamecatalog/games/{id}/metadata/refresh", GAME_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshEnrichmentReturnsTheUpdatedDetail() throws Exception {
        when(catalogRefreshService.refreshEnrichment(GAME_ID, null)).thenReturn(Optional.of(aDetail()));

        mockMvc.perform(post("/api/gamecatalog/games/{id}/enrichment/refresh", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description.text").value("A classic."))
                .andExpect(jsonPath("$.description.source").value("AI"))
                .andExpect(jsonPath("$.description.model").value("claude-haiku-4.5"))
                .andExpect(jsonPath("$.description.requestedModel").value("jev-router"))
                .andExpect(jsonPath("$.description.writtenAt").value("2026-10-07T10:12:00Z"))
                .andExpect(jsonPath("$.factSources.facts").value("STEAM"))
                .andExpect(jsonPath("$.factSources.multiplayer").doesNotExist());
    }

    @Test
    void refreshEnrichmentForAnInvisibleGameIsNotFound() throws Exception {
        when(catalogRefreshService.refreshEnrichment(GAME_ID, null)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/gamecatalog/games/{id}/enrichment/refresh", GAME_ID))
                .andExpect(status().isNotFound());
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
        return new GameDetailResponse(GAME_ID, "Portal 2", List.of(PlatformChip.of("Steam")),
                List.of(GameSource.STEAM_OWNED), List.of(aSteamPlace()),
                ArtworkStatus.EXTERNAL_URL, "https://example.com/cover.png", null,
                ArtworkStatus.EXTERNAL_URL, "https://example.com/banner.png", null,
                EnrichmentStatus.ENRICHED, "Puzzle", "Puzzle, Adventure", "Valve", "Valve", 2011, "STEAM",
                2, false, true, new DescriptionResponse("A classic.", DescriptionSource.AI, "claude-haiku-4.5", "jev-router",
                        Instant.parse("2026-10-07T10:12:00Z")), FIRST_SEEN_AT, InstallStatus.INSTALLED, null, null, null,
                MultiplayerSource.UNKNOWN, new FactSourcesResponse("STEAM", null), VoteTotalsResponse.none(), null);
    }

    private PlaceResponse aSteamPlace() {
        return new PlaceResponse(PlaceResponse.PlaceKind.STEAM_LIBRARY, null, null, null, "Steam", "Steam", null, null,
                dev.jordy.jordylab.gamecatalog.domain.LibrarySource.OWNED, null);
    }
}
