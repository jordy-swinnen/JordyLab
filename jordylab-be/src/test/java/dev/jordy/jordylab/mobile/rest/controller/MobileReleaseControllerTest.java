package dev.jordy.jordylab.mobile.rest.controller;

import dev.jordy.jordylab.mobile.domain.MobileRelease;
import dev.jordy.jordylab.mobile.service.DownloadLinkService;
import dev.jordy.jordylab.mobile.service.MobileReleaseService;
import dev.jordy.jordylab.mobile.service.NoReleasesPublishedException;
import dev.jordy.jordylab.mobile.service.ReleaseNotFoundException;
import dev.jordy.jordylab.mobile.service.SigningCertMismatchException;
import dev.jordy.jordylab.mobile.service.VersionCodeNotMonotonicException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(MobileReleaseController.class)
class MobileReleaseControllerTest {

    private static final UUID RELEASE_ID = UUID.fromString("7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-28T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MobileReleaseService releaseService;

    @MockitoBean
    private DownloadLinkService downloadLinkService;

    private MobileRelease aRelease() {
        return MobileRelease.builder()
                .id(RELEASE_ID)
                .versionName("1.3.0")
                .versionCode(14)
                .releaseNotes("Fixed artwork loading on slow connections.")
                .sha256("a".repeat(64))
                .sizeBytes(18_345_213L)
                .minSupportedVersionCode(10)
                .storageKey("jordylab-1.3.0.apk")
                .publishedAt(PUBLISHED_AT)
                .build();
    }

    @Test
    void latestReportsUpdateAvailableWhenInstalledVersionIsOlder() throws Exception {
        when(releaseService.latest()).thenReturn(Optional.of(aRelease()));

        mockMvc.perform(get("/api/mobile/releases/latest").param("installedVersionCode", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RELEASE_ID.toString()))
                .andExpect(jsonPath("$.versionName").value("1.3.0"))
                .andExpect(jsonPath("$.updateAvailable").value(true))
                .andExpect(jsonPath("$.updateRequired").value(false));
    }

    @Test
    void latestReportsUpdateRequiredWhenInstalledVersionIsBelowMinimum() throws Exception {
        when(releaseService.latest()).thenReturn(Optional.of(aRelease()));

        mockMvc.perform(get("/api/mobile/releases/latest").param("installedVersionCode", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateRequired").value(true));
    }

    @Test
    void latestWithoutInstalledVersionReportsNeitherFlag() throws Exception {
        when(releaseService.latest()).thenReturn(Optional.of(aRelease()));

        mockMvc.perform(get("/api/mobile/releases/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(false))
                .andExpect(jsonPath("$.updateRequired").value(false));
    }

    @Test
    void latestReturns404WhenNoReleasesArePublished() throws Exception {
        when(releaseService.latest()).thenThrow(new NoReleasesPublishedException());

        mockMvc.perform(get("/api/mobile/releases/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("NO_RELEASES_PUBLISHED"));
    }

    @Test
    void downloadLinkReturnsARelativeUrlAndExpiry() throws Exception {
        when(releaseService.findById(RELEASE_ID)).thenReturn(aRelease());
        Instant expiresAt = PUBLISHED_AT.plusSeconds(300);
        when(downloadLinkService.issue(eq(RELEASE_ID), eq("guest-subject")))
                .thenReturn(new DownloadLinkService.IssuedDownloadLink("signed-token", expiresAt));

        mockMvc.perform(post("/api/mobile/releases/{id}/download-link", RELEASE_ID)
                        .with(jwt().jwt(builder -> builder.subject("guest-subject"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("/api/mobile/download/signed-token"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()));
    }

    @Test
    void downloadLinkReturns404ForAnUnknownRelease() throws Exception {
        when(releaseService.findById(RELEASE_ID)).thenThrow(new ReleaseNotFoundException(RELEASE_ID));

        mockMvc.perform(post("/api/mobile/releases/{id}/download-link", RELEASE_ID)
                        .with(jwt().jwt(builder -> builder.subject("guest-subject"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("RELEASE_NOT_FOUND"));
    }

    @Test
    void publishRejectsASigningCertMismatch() throws Exception {
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        when(releaseService.publish(eq("1.3.0"), eq(14), eq("notes"), fileCaptor.capture(), eq(null)))
                .thenThrow(new SigningCertMismatchException());

        mockMvc.perform(publishRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("SIGNING_CERT_MISMATCH"));

        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("app.apk");
    }

    @Test
    void publishRejectsANonMonotonicVersionCode() throws Exception {
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        when(releaseService.publish(eq("1.3.0"), eq(14), eq("notes"), fileCaptor.capture(), eq(null)))
                .thenThrow(new VersionCodeNotMonotonicException(14, 20));

        mockMvc.perform(publishRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VERSION_CODE_NOT_MONOTONIC"));

        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("app.apk");
    }

    private MockMultipartHttpServletRequestBuilder publishRequest() {
        return multipart("/api/mobile/releases")
                .file(new MockMultipartFile("file", "app.apk", "application/octet-stream", "x".getBytes()))
                .part(new MockPart("versionName", "1.3.0".getBytes()))
                .part(new MockPart("versionCode", "14".getBytes()))
                .part(new MockPart("releaseNotes", "notes".getBytes()));
    }
}
