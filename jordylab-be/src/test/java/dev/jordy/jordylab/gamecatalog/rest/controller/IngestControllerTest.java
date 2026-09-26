package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.EntryRejection;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.EntryRejectionReason;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanCheckRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanCheckResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SyncCounts;
import dev.jordy.jordylab.gamecatalog.service.ClientService;
import dev.jordy.jordylab.gamecatalog.service.ScanService;
import jakarta.servlet.http.HttpServletRequest;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static final ScanRequest EXPECTED_SCAN_REQUEST = new ScanRequest(
            null,
            "jordybox",
            SourceType.EMUDECK,
            Instant.parse("2026-08-02T10:20:00Z"),
            null,
            null,
            List.of(new ScanEntry("snes/Super Mario World.sfc", 524288L, Instant.parse("2026-08-02T10:15:00Z"))),
            Map.of(),
            null);

    @Language("JSON")
    private static final String VALID_CHECK_REQUEST = """
            {
              "hostname": "jordybox",
              "libraryType": "EMUDECK",
              "clientDigest": "sha256:abc"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private ClientService clientService;

    @Test
    void scanReturnsTheOutcomeCountsAndRejections() throws Exception {
        when(scanService.submitScan(EXPECTED_SCAN_REQUEST)).thenReturn(new ScanResponse(
                SyncOutcome.APPLIED,
                true,
                new SyncCounts(1, 1, 0, 0, 1),
                List.of(new EntryRejection("snes/broken.sfc", EntryRejectionReason.TITLE_BLANK)),
                null));

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
        when(scanService.submitScan(EXPECTED_SCAN_REQUEST)).thenReturn(new ScanResponse(
                SyncOutcome.NO_CHANGE, true, new SyncCounts(1, 0, 0, 0, 0), List.of(), null));

        mockMvc.perform(post("/api/gamecatalog/ingest/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCAN_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NO_CHANGE"))
                .andExpect(jsonPath("$.rejections").isEmpty());
    }

    @Test
    void scanRejectedByTheServiceReportsWhyInTheResponse() throws Exception {
        when(scanService.submitScan(EXPECTED_SCAN_REQUEST)).thenReturn(new ScanResponse(
                SyncOutcome.REJECTED, false, new SyncCounts(0, 0, 0, 0, 0), List.of(), "TOO_MANY_GAMES"));

        mockMvc.perform(post("/api/gamecatalog/ingest/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCAN_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("TOO_MANY_GAMES"));
    }

    @Test
    void scanThatIsNotRejectedOmitsTheReasonField() throws Exception {
        when(scanService.submitScan(EXPECTED_SCAN_REQUEST)).thenReturn(new ScanResponse(
                SyncOutcome.NO_CHANGE, true, new SyncCounts(1, 0, 0, 0, 0), List.of(), null));

        mockMvc.perform(post("/api/gamecatalog/ingest/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCAN_REQUEST))
                .andExpect(jsonPath("$.reason").doesNotExist());
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
    void clientDownloadReturnsTheRenderedPythonClientAsAnAttachment() throws Exception {
        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        when(clientService.generateClient(eq("steam"), requestCaptor.capture())).thenReturn("print('steam-scan')\n");

        mockMvc.perform(get("/api/gamecatalog/ingest/client").param("libraryType", "steam"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"jordylab-scan-steam.py\""))
                .andExpect(content().contentTypeCompatibleWith("text/x-python"))
                .andExpect(content().string("print('steam-scan')\n"));

        assertThat(requestCaptor.getValue().getRequestURI()).isEqualTo("/api/gamecatalog/ingest/client");
    }

    @Test
    void clientDownloadForAnUnknownLibraryTypeIsABadRequest() throws Exception {
        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        when(clientService.generateClient(eq("bogus"), requestCaptor.capture()))
                .thenThrow(new IllegalArgumentException("libraryType must be 'steam' or 'emudeck'"));

        mockMvc.perform(get("/api/gamecatalog/ingest/client").param("libraryType", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("libraryType must be 'steam' or 'emudeck'"));

        assertThat(requestCaptor.getValue().getRequestURI()).isEqualTo("/api/gamecatalog/ingest/client");
    }

    @Test
    void checkReportsWhetherAScanIsNeeded() throws Exception {
        ScanCheckRequest expected = new ScanCheckRequest(null, "jordybox", SourceType.EMUDECK, "sha256:abc");
        when(scanService.submitCheck(expected)).thenReturn(new ScanCheckResponse(true, true));

        mockMvc.perform(post("/api/gamecatalog/ingest/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CHECK_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanNeeded").value(true))
                .andExpect(jsonPath("$.sourceEnabled").value(true));
    }

    @Test
    void checkWithBlankHostnameIsRejected() throws Exception {
        @Language("JSON")
        String requestWithBlankHostname = """
                {
                  "hostname": "",
                  "libraryType": "EMUDECK",
                  "clientDigest": "sha256:abc"
                }
                """;

        mockMvc.perform(post("/api/gamecatalog/ingest/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithBlankHostname))
                .andExpect(status().isBadRequest());
    }
}
