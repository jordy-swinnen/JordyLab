package dev.jordy.jordylab.mobile.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class MobileReleaseTest {

    @Test
    void buildMobileRelease() {
        MobileRelease release = MobileReleaseTestBuilder.aDefaultMobileRelease();

        assertSoftly(softly -> {
            softly.assertThat(release.getId()).isNotNull();
            softly.assertThat(release.getVersionName()).isEqualTo(MobileReleaseTestBuilder.DEFAULT_VERSION_NAME);
            softly.assertThat(release.getVersionCode()).isEqualTo(MobileReleaseTestBuilder.DEFAULT_VERSION_CODE);
            softly.assertThat(release.getReleaseNotes()).isEqualTo(MobileReleaseTestBuilder.DEFAULT_RELEASE_NOTES);
            softly.assertThat(release.getSha256()).isEqualTo(MobileReleaseTestBuilder.DEFAULT_SHA256);
            softly.assertThat(release.getSizeBytes()).isEqualTo(MobileReleaseTestBuilder.DEFAULT_SIZE_BYTES);
            softly.assertThat(release.getMinSupportedVersionCode())
                    .isEqualTo(MobileReleaseTestBuilder.DEFAULT_MIN_SUPPORTED_VERSION_CODE);
            softly.assertThat(release.getStorageKey()).isEqualTo(MobileReleaseTestBuilder.DEFAULT_STORAGE_KEY);
            softly.assertThat(release.getPublishedAt()).isEqualTo(MobileReleaseTestBuilder.DEFAULT_PUBLISHED_AT);
        });
    }

    @Test
    void buildWithoutVersionName() {
        assertThatThrownBy(() -> MobileReleaseTestBuilder.aMobileRelease().versionName(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithNonPositiveVersionCode() {
        assertThatThrownBy(() -> MobileReleaseTestBuilder.aMobileRelease().versionCode(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutReleaseNotes() {
        assertThatThrownBy(() -> MobileReleaseTestBuilder.aMobileRelease().releaseNotes(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithInvalidSha256Length() {
        assertThatThrownBy(() -> MobileReleaseTestBuilder.aMobileRelease().sha256("not-a-hash").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithNonPositiveSizeBytes() {
        assertThatThrownBy(() -> MobileReleaseTestBuilder.aMobileRelease().sizeBytes(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithMinSupportedVersionCodeAboveOwnVersionCode() {
        assertThatThrownBy(() -> MobileReleaseTestBuilder.aMobileRelease()
                .versionCode(5)
                .minSupportedVersionCode(6)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutStorageKey() {
        assertThatThrownBy(() -> MobileReleaseTestBuilder.aMobileRelease().storageKey(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutPublishedAt() {
        assertThatThrownBy(() -> MobileReleaseTestBuilder.aMobileRelease().publishedAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(MobileRelease.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
