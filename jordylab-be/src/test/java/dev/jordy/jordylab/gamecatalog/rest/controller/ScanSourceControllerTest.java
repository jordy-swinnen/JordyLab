package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.PlayerCountSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlayerCountReason;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanSourceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourceEnabledResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HealthExceptionsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HideImpactResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryHealthResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourcesResponse;
import dev.jordy.jordylab.gamecatalog.service.LibraryHealthService;
import dev.jordy.jordylab.gamecatalog.service.ScanSourceService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(ScanSourceController.class)
class ScanSourceControllerTest {

    private static final UUID SOURCE_ID = UUID.fromString("2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final UUID HOST_ID = UUID.fromString("3d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f60");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanSourceService scanSourceService;

    @MockitoBean
    private LibraryHealthService libraryHealthService;

    @Test
    void sourcesReturnsListWithCountsAndSyncState() throws Exception {
        when(scanSourceService.listSources()).thenReturn(new SourcesResponse(List.of(
                new ScanSourceResponse(SOURCE_ID, "snes", HOST_ID, "jordybox", "Living room PC", "Living room PC", SourceType.EMUDECK, "SNES", PlatformChip.of("SNES"),
                        true, Instant.parse("2026-08-02T10:20:00Z"), Instant.parse("2026-08-02T10:20:00Z"),
                        Instant.parse("2026-08-02T10:30:00Z"), SyncOutcome.APPLIED, 412L)),
                new LibraryHealthResponse(230, 12, 30, 5, null)));

        mockMvc.perform(get("/api/gamecatalog/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].id").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.sources[0].sourceKey").value("snes"))
                .andExpect(jsonPath("$.sources[0].hostname").value("jordybox"))
                .andExpect(jsonPath("$.sources[0].displayName").value("Living room PC"))
                .andExpect(jsonPath("$.sources[0].label").value("Living room PC"))
                .andExpect(jsonPath("$.sources[0].hostId").value(HOST_ID.toString()))
                .andExpect(jsonPath("$.sources[0].sourceType").value("EMUDECK"))
                .andExpect(jsonPath("$.sources[0].platform").value("SNES"))
                .andExpect(jsonPath("$.sources[0].enabled").value(true))
                .andExpect(jsonPath("$.sources[0].lastOutcome").value("APPLIED"))
                .andExpect(jsonPath("$.sources[0].installedGameCount").value(412))
                .andExpect(jsonPath("$.health.totalGames").value(230))
                .andExpect(jsonPath("$.health.gamesWithoutCover").value(12))
                .andExpect(jsonPath("$.health.gamesWithoutDescription").value(30))
                .andExpect(jsonPath("$.health.gamesPendingIndex").value(5));
    }

    @Test
    void healthExceptionsListTheGamesBehindOneCount() throws Exception {
        when(libraryHealthService.exceptions(LibraryHealthService.Kind.COVER, null)).thenReturn(
                new HealthExceptionsResponse("COVER", 12, List.of(
                        new HealthExceptionsResponse.HealthException(SOURCE_ID, "Obscure Game", null))));

        mockMvc.perform(get("/api/gamecatalog/sources/health/exceptions").param("kind", "COVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("COVER"))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.games[0].title").value("Obscure Game"))
                .andExpect(jsonPath("$.games[0].reason").doesNotExist());
    }

    @Test
    void thePlayerCountListCanBeNarrowedToOneReasonAndEachGameCarriesIt() throws Exception {
        when(libraryHealthService.exceptions(LibraryHealthService.Kind.PLAYER_COUNT, PlayerCountReason.ENTRIES_DISAGREE))
                .thenReturn(new HealthExceptionsResponse("PLAYER_COUNT", 2, List.of(
                        new HealthExceptionsResponse.HealthException(SOURCE_ID, "Mario Party 7",
                                PlayerCountReason.ENTRIES_DISAGREE))));

        mockMvc.perform(get("/api/gamecatalog/sources/health/exceptions").param("kind", "PLAYER_COUNT")
                        .param("reason", "ENTRIES_DISAGREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PLAYER_COUNT"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.games[0].title").value("Mario Party 7"))
                .andExpect(jsonPath("$.games[0].reason").value("ENTRIES_DISAGREE"));
    }

    @Test
    void anUnknownPlayerCountReasonIsRejected() throws Exception {
        mockMvc.perform(get("/api/gamecatalog/sources/health/exceptions").param("kind", "PLAYER_COUNT")
                        .param("reason", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theSourcesCarryThePlayerCountBlockWithTheStateOfTheSecondSource() throws Exception {
        LibraryHealthResponse.PlayerCounts playerCounts = new LibraryHealthResponse.PlayerCounts(105,
                Map.of(PlayerCountSource.IGDB, 61L, PlayerCountSource.THEGAMESDB, 13L), 31, 40,
                Map.of(PlayerCountReason.STEAM_NO_SOURCE, 29L, PlayerCountReason.ENTRIES_DISAGREE, 2L),
                new LibraryHealthResponse.SourceState(true, Instant.parse("2026-11-02T08:00:00Z"), "ALLOWANCE_LOW", 18));
        when(scanSourceService.listSources()).thenReturn(new SourcesResponse(List.of(),
                new LibraryHealthResponse(509, 0, 3, 0, playerCounts)));

        mockMvc.perform(get("/api/gamecatalog/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.health.playerCounts.localMultiplayerGames").value(105))
                .andExpect(jsonPath("$.health.playerCounts.filled.THEGAMESDB").value(13))
                .andExpect(jsonPath("$.health.playerCounts.unknown").value(31))
                .andExpect(jsonPath("$.health.playerCounts.flagUnknown").value(40))
                .andExpect(jsonPath("$.health.playerCounts.unknownByReason.ENTRIES_DISAGREE").value(2))
                .andExpect(jsonPath("$.health.playerCounts.theGamesDb.configured").value(true))
                .andExpect(jsonPath("$.health.playerCounts.theGamesDb.pauseReason").value("ALLOWANCE_LOW"))
                .andExpect(jsonPath("$.health.playerCounts.theGamesDb.remainingAllowance").value(18));
    }

    @Test
    void anUnknownHealthKindIsRejected() throws Exception {
        mockMvc.perform(get("/api/gamecatalog/sources/health/exceptions").param("kind", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hideImpactSaysHowManyGamesWouldDisappear() throws Exception {
        when(scanSourceService.hideImpact(SOURCE_ID)).thenReturn(Optional.of(new HideImpactResponse(87, 5)));

        mockMvc.perform(get("/api/gamecatalog/sources/{id}/hide-impact", SOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenGames").value(87))
                .andExpect(jsonPath("$.stillVisibleElsewhere").value(5));
    }

    @Test
    void hideImpactOfAnUnknownSourceIsNotFound() throws Exception {
        when(scanSourceService.hideImpact(SOURCE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/gamecatalog/sources/{id}/hide-impact", SOURCE_ID)).andExpect(status().isNotFound());
    }

    @Test
    void toggleReturnsUpdatedEnabledState() throws Exception {
        when(scanSourceService.setEnabled(SOURCE_ID, false))
                .thenReturn(Optional.of(new SourceEnabledResponse(SOURCE_ID, false)));

        mockMvc.perform(put("/api/gamecatalog/sources/{id}/enabled", SOURCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void toggleUnknownSourceIsNotFound() throws Exception {
        when(scanSourceService.setEnabled(SOURCE_ID, false)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/gamecatalog/sources/{id}/enabled", SOURCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isNotFound());
    }
}
