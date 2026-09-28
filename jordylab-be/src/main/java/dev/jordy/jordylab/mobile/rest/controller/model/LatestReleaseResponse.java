package dev.jordy.jordylab.mobile.rest.controller.model;

import dev.jordy.jordylab.mobile.domain.MobileRelease;

import java.time.Instant;
import java.util.UUID;

public record LatestReleaseResponse(UUID id, String versionName, int versionCode, String releaseNotes,
        String sha256, long sizeBytes, int minSupportedVersionCode, Instant publishedAt, boolean updateAvailable,
        boolean updateRequired) {

    /**
     * {@code installedVersionCode} is {@code null} when the caller isn't reporting an installed
     * app (e.g. the admin viewing the release in Settings) — both flags are then {@code false}
     * (contracts/mobile-releases-api.md). {@code id} is required by the client to then call
     * {@code POST /releases/{id}/download-link} — omitted from the original contract draft, added
     * here once the frontend wiring exposed the gap.
     */
    public static LatestReleaseResponse from(MobileRelease release, Integer installedVersionCode) {
        boolean updateAvailable = installedVersionCode != null && installedVersionCode < release.getVersionCode();
        boolean updateRequired = installedVersionCode != null
                && installedVersionCode < release.getMinSupportedVersionCode();

        return new LatestReleaseResponse(release.getId(), release.getVersionName(), release.getVersionCode(),
                release.getReleaseNotes(), release.getSha256(), release.getSizeBytes(),
                release.getMinSupportedVersionCode(), release.getPublishedAt(), updateAvailable, updateRequired);
    }
}
