package dev.jordy.jordylab.mobile.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class MobileReleaseTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f");
    public static final String DEFAULT_VERSION_NAME = "1.0.0";
    public static final int DEFAULT_VERSION_CODE = 1;
    public static final String DEFAULT_RELEASE_NOTES = "Initial release.";
    public static final String DEFAULT_SHA256 = "a".repeat(64);
    public static final long DEFAULT_SIZE_BYTES = 18_345_213L;
    public static final int DEFAULT_MIN_SUPPORTED_VERSION_CODE = 1;
    public static final String DEFAULT_STORAGE_KEY = "jordylab-1.0.0.apk";
    public static final Instant DEFAULT_PUBLISHED_AT = Instant.parse("2026-09-28T10:00:00Z");

    public static MobileRelease aDefaultMobileRelease() {
        return aMobileRelease().build();
    }

    public static MobileRelease.MobileReleaseBuilder aMobileRelease() {
        return MobileRelease.builder()
                .id(DEFAULT_ID)
                .versionName(DEFAULT_VERSION_NAME)
                .versionCode(DEFAULT_VERSION_CODE)
                .releaseNotes(DEFAULT_RELEASE_NOTES)
                .sha256(DEFAULT_SHA256)
                .sizeBytes(DEFAULT_SIZE_BYTES)
                .minSupportedVersionCode(DEFAULT_MIN_SUPPORTED_VERSION_CODE)
                .storageKey(DEFAULT_STORAGE_KEY)
                .publishedAt(DEFAULT_PUBLISHED_AT);
    }
}
