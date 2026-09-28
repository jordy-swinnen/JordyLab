package dev.jordy.jordylab.mobile.service;

import dev.jordy.jordylab.mobile.MobileProperties;
import dev.jordy.jordylab.mobile.domain.MobileRelease;
import dev.jordy.jordylab.mobile.domain.repository.MobileReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers the checks {@link MobileReleaseService#publish} performs before ever touching the
 * uploaded file — the signing-certificate and successful-publish paths need a real signed APK
 * and are proven by the device checklist (quickstart.md scenario 1), not here.
 */
class MobileReleaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    private final MobileReleaseRepository repository = Mockito.mock(MobileReleaseRepository.class);
    private final MobileProperties properties = new MobileProperties(
            new MobileProperties.Release(null, "a".repeat(64)),
            new MobileProperties.DownloadLink("secret", 5),
            new MobileProperties.App(null, null),
            null);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MobileReleaseService service = new MobileReleaseService(repository, properties, clock);

    private MockMultipartFile apkFile;

    @BeforeEach
    void setUp() {
        apkFile = new MockMultipartFile("file", "jordylab.apk", "application/vnd.android.package-archive",
                "not-a-real-apk".getBytes());
    }

    @Test
    void rejectsAVersionCodeEqualToTheCurrentLatest() {
        MobileRelease latest = aMobileReleaseWithVersionCode(7);
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.publish("1.0.0", 7, "notes", apkFile, null))
                .isInstanceOf(VersionCodeNotMonotonicException.class);
    }

    @Test
    void rejectsAVersionCodeBelowTheCurrentLatest() {
        MobileRelease latest = aMobileReleaseWithVersionCode(10);
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.publish("1.0.0", 5, "notes", apkFile, null))
                .isInstanceOf(VersionCodeNotMonotonicException.class);
    }

    private MobileRelease aMobileReleaseWithVersionCode(int versionCode) {
        return MobileRelease.builder()
                .versionName("1.0.0")
                .versionCode(versionCode)
                .releaseNotes("notes")
                .sha256("a".repeat(64))
                .sizeBytes(1024)
                .minSupportedVersionCode(versionCode)
                .storageKey("jordylab-1.0.0.apk")
                .publishedAt(NOW)
                .build();
    }

    @Test
    void latestReturnsEmptyWhenNoReleasesArePublished() {
        when(repository.findTopByOrderByVersionCodeDesc()).thenReturn(Optional.empty());

        assertThat(service.latest()).isEmpty();
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknownId))
                .isInstanceOf(ReleaseNotFoundException.class);
    }
}
