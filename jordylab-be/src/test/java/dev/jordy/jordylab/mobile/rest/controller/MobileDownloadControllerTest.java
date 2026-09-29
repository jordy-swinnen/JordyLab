package dev.jordy.jordylab.mobile.rest.controller;

import dev.jordy.jordylab.mobile.domain.MobileRelease;
import dev.jordy.jordylab.mobile.service.DownloadLinkInvalidException;
import dev.jordy.jordylab.mobile.service.DownloadLinkService;
import dev.jordy.jordylab.mobile.service.MobileReleaseService;
import dev.jordy.jordylab.mobile.service.ReleaseNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(MobileDownloadController.class)
class MobileDownloadControllerTest {

    private static final UUID RELEASE_ID = UUID.fromString("7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DownloadLinkService downloadLinkService;

    @MockitoBean
    private MobileReleaseService releaseService;

    private Path apkFile;

    @AfterEach
    void cleanUp() throws IOException {
        if (apkFile != null) {
            Files.deleteIfExists(apkFile);
        }
    }

    @Test
    void streamsTheApkForAValidToken() throws Exception {
        apkFile = Files.createTempFile("jordylab-test", ".apk");
        Files.writeString(apkFile, "fake-apk-bytes");
        MobileRelease release = MobileRelease.builder()
                .id(RELEASE_ID)
                .versionName("1.3.0")
                .versionCode(14)
                .releaseNotes("notes")
                .sha256("a".repeat(64))
                .sizeBytes(14)
                .minSupportedVersionCode(1)
                .storageKey("jordylab-1.3.0.apk")
                .publishedAt(java.time.Instant.parse("2026-09-28T10:00:00Z"))
                .build();
        when(downloadLinkService.verify("valid-token")).thenReturn(RELEASE_ID);
        when(releaseService.findById(RELEASE_ID)).thenReturn(release);
        when(releaseService.resolveStoragePath(release)).thenReturn(apkFile);

        mockMvc.perform(get("/api/mobile/download/{token}", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.android.package-archive"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"jordylab-1.3.0.apk\""));
    }

    @Test
    void refusesAnInvalidToken() throws Exception {
        when(downloadLinkService.verify("bad-token"))
                .thenThrow(new DownloadLinkInvalidException("Download token has expired"));

        mockMvc.perform(get("/api/mobile/download/{token}", "bad-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("DOWNLOAD_LINK_INVALID"));
    }

    @Test
    void refusesATokenForADeletedRelease() throws Exception {
        when(downloadLinkService.verify("orphaned-token")).thenReturn(RELEASE_ID);
        when(releaseService.findById(RELEASE_ID)).thenThrow(new ReleaseNotFoundException(RELEASE_ID));

        mockMvc.perform(get("/api/mobile/download/{token}", "orphaned-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("DOWNLOAD_LINK_INVALID"));
    }
}
