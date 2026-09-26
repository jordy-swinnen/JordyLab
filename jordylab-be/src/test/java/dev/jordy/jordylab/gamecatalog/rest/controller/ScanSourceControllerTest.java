package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanSourceResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourceEnabledResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SourcesResponse;
import dev.jordy.jordylab.gamecatalog.service.ScanSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

@WebMvcTest(ScanSourceController.class)
class ScanSourceControllerTest {

    private static final UUID SOURCE_ID = UUID.fromString("2c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanSourceService scanSourceService;

    @Test
    void sourcesReturnsListWithCountsAndSyncState() throws Exception {
        when(scanSourceService.listSources()).thenReturn(new SourcesResponse(List.of(
                new ScanSourceResponse(SOURCE_ID, "snes", "jordybox", SourceType.EMUDECK, "SNES",
                        true, Instant.parse("2026-08-02T10:20:00Z"), Instant.parse("2026-08-02T10:20:00Z"),
                        SyncOutcome.APPLIED, 412L))));

        mockMvc.perform(get("/api/gamecatalog/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].id").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.sources[0].sourceKey").value("snes"))
                .andExpect(jsonPath("$.sources[0].hostname").value("jordybox"))
                .andExpect(jsonPath("$.sources[0].sourceType").value("EMUDECK"))
                .andExpect(jsonPath("$.sources[0].platform").value("SNES"))
                .andExpect(jsonPath("$.sources[0].enabled").value(true))
                .andExpect(jsonPath("$.sources[0].lastOutcome").value("APPLIED"))
                .andExpect(jsonPath("$.sources[0].installedGameCount").value(412));
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
