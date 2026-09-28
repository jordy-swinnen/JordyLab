package dev.jordy.jordylab.mobile.rest.controller.model;

import dev.jordy.jordylab.mobile.domain.MobileRelease;

import java.time.Instant;

public record LatestReleaseResponse(String versionName, int versionCode, String releaseNotes, String sha256,
        long sizeBytes, int minSupportedVersionCode, Instant publishedAt, boolean updateAvailable,
        boolean updateRequired) {

    /**
     * {@code installedVersionCode} is {@code null} when the caller isn't reporting an installed
     * app (e.g. the admin viewing the release in Settings) — both flags are then {@code false}
     * (contracts/mobile-releases-api.md).
     */
    public static LatestReleaseResponse from(MobileRelease release, Integer installedVersionCode) {
        boolean updateAvailable = installedVersionCode != null && installedVersionCode < release.getVersionCode();
        boolean updateRequired = installedVersionCode != null
                && installedVersionCode < release.getMinSupportedVersionCode();

        return new LatestReleaseResponse(release.getVersionName(), release.getVersionCode(),
                release.getReleaseNotes(), release.getSha256(), release.getSizeBytes(),
                release.getMinSupportedVersionCode(), release.getPublishedAt(), updateAvailable, updateRequired);
    }
}
