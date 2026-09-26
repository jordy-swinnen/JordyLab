package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.EntryRejection;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.EntryRejectionReason;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SyncCounts;
import dev.jordy.jordylab.gamecatalog.service.ScanService;
import dev.jordy.jordylab.gamecatalog.service.ScriptService;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
class IngestControllerTest {

    @Language("JSON")
    private static final String VALID_SCAN_REQUEST = """
            {
              "hostname": "jordybox",
              "libraryType": "EMUDECK",
              "capturedAt": "2026-08-02T10:20:00Z",
              "paths": [
                {"relpath": "snes/Super Mario World.sfc", "size": 524288, "mtime": "2026-08-02T10:15:00Z"}
              ],
              "manifestContents": {}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private ScriptService scriptService;

    @Test
    void scanReturnsTheOutcomeCountsAndRejections() throws Exception {
        when(scanService.submitScan(any())).thenReturn(new ScanResponse(
                SyncOutcome.APPLIED,
                true,
                new SyncCounts(1, 1, 0, 0, 1),
                List.of(new EntryRejection("snes/broken.sfc", EntryRejectionReason.TITLE_BLANK))));

        mockMvc.perform(post("/api/gamecatalog/ingest/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCAN_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.sourceEnabled").value(true))
                .andExpect(jsonPath("$.counts.submitted").value(1))
                .andExpect(jsonPath("$.counts.added").value(1))
                .andExpect(jsonPath("$.counts.rejected").value(1))
                .andExpect(jsonPath("$.rejections[0].externalRef").value("snes/broken.sfc"))
                .andExpect(jsonPath("$.rejections[0].reason").value("TITLE_BLANK"));
    }

    @Test
    void scanWithNoChangeReportsAnEmptyRejectionsList() throws Exception {
        when(scanService.submitScan(any())).thenReturn(new ScanResponse(
                SyncOutcome.NO_CHANGE, true, new SyncCounts(1, 0, 0, 0, 0), List.of()));

        mockMvc.perform(post("/api/gamecatalog/ingest/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCAN_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NO_CHANGE"))
                .andExpect(jsonPath("$.rejections").isEmpty());
    }

    @Test
    void scanWithBlankHostnameIsRejected() throws Exception {
        @Language("JSON")
        String requestWithBlankHostname = """
                {
                  "hostname": "",
                  "libraryType": "EMUDECK",
                  "capturedAt": "2026-08-02T10:20:00Z",
                  "paths": [],
                  "manifestContents": {}
                }
                """;

        mockMvc.perform(post("/api/gamecatalog/ingest/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithBlankHostname))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scriptDownloadReturnsTheRenderedShellScriptAsAnAttachment() throws Exception {
        when(scriptService.generateScript(eq("steam"), any())).thenReturn("#!/bin/bash\necho steam-scan\n");

        mockMvc.perform(get("/api/gamecatalog/ingest/script").param("libraryType", "steam"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"jordylab-scan-steam.sh\""))
                .andExpect(content().string("#!/bin/bash\necho steam-scan\n"));
    }
}
