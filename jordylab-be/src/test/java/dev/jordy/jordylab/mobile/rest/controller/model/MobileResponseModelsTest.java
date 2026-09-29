package dev.jordy.jordylab.mobile.rest.controller.model;

import dev.jordy.jordylab.mobile.domain.MobileRelease;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MobileResponseModelsTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-28T10:00:00Z");

    @Test
    void publishReleaseResponseFromRelease() {
        MobileRelease release = aMobileRelease();

        PublishReleaseResponse response = PublishReleaseResponse.from(release);

        assertThat(response.id()).isEqualTo(release.getId());
        assertThat(response.versionName()).isEqualTo("1.0.0");
        assertThat(response.versionCode()).isEqualTo(7);
        assertThat(response.sha256()).isEqualTo("a".repeat(64));
        assertThat(response.sizeBytes()).isEqualTo(1024);
        assertThat(response.publishedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void latestReleaseResponseFromReleaseWithoutInstalledVersion() {
        MobileRelease release = aMobileRelease();

        LatestReleaseResponse response = LatestReleaseResponse.from(release, null);

        assertThat(response.id()).isEqualTo(release.getId());
        assertThat(response.versionName()).isEqualTo("1.0.0");
        assertThat(response.updateAvailable()).isFalse();
        assertThat(response.updateRequired()).isFalse();
    }

    @Test
    void latestReleaseResponseFlagsUpdateAvailable() {
        MobileRelease release = MobileRelease.builder()
                .id(UUID.randomUUID())
                .versionName("1.0.0")
                .versionCode(7)
                .releaseNotes("notes")
                .sha256("a".repeat(64))
                .sizeBytes(1024)
                .minSupportedVersionCode(5)
                .storageKey("jordylab-1.0.0.apk")
                .publishedAt(PUBLISHED_AT)
                .build();

        LatestReleaseResponse response = LatestReleaseResponse.from(release, 6);

        assertThat(response.updateAvailable()).isTrue();
        assertThat(response.updateRequired()).isFalse();
    }

    @Test
    void latestReleaseResponseFlagsUpdateRequired() {
        MobileRelease release = MobileRelease.builder()
                .id(UUID.randomUUID())
                .versionName("1.0.0")
                .versionCode(10)
                .releaseNotes("notes")
                .sha256("a".repeat(64))
                .sizeBytes(1024)
                .minSupportedVersionCode(8)
                .storageKey("jordylab-1.0.0.apk")
                .publishedAt(PUBLISHED_AT)
                .build();

        LatestReleaseResponse response = LatestReleaseResponse.from(release, 5);

        assertThat(response.updateAvailable()).isTrue();
        assertThat(response.updateRequired()).isTrue();
    }

    private MobileRelease aMobileRelease() {
        return MobileRelease.builder()
                .id(UUID.randomUUID())
                .versionName("1.0.0")
                .versionCode(7)
                .releaseNotes("notes")
                .sha256("a".repeat(64))
                .sizeBytes(1024)
                .minSupportedVersionCode(7)
                .storageKey("jordylab-1.0.0.apk")
                .publishedAt(PUBLISHED_AT)
                .build();
    }
}
