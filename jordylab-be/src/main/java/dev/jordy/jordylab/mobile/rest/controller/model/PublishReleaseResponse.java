package dev.jordy.jordylab.mobile.rest.controller.model;

import dev.jordy.jordylab.mobile.domain.MobileRelease;

import java.time.Instant;
import java.util.UUID;

public record PublishReleaseResponse(UUID id, String versionName, int versionCode, String sha256, long sizeBytes,
        Instant publishedAt) {

    public static PublishReleaseResponse from(MobileRelease release) {
        return new PublishReleaseResponse(release.getId(), release.getVersionName(), release.getVersionCode(),
                release.getSha256(), release.getSizeBytes(), release.getPublishedAt());
    }
}
