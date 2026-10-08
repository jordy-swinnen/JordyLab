package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
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
                new LibraryHealthResponse(230, 12, 30, 5)));

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
        when(libraryHealthService.exceptions(LibraryHealthService.Kind.COVER)).thenReturn(
                new HealthExceptionsResponse("COVER", 12, List.of(
                        new HealthExceptionsResponse.HealthException(SOURCE_ID, "Obscure Game"))));

        mockMvc.perform(get("/api/gamecatalog/sources/health/exceptions").param("kind", "COVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("COVER"))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.games[0].title").value("Obscure Game"));
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
