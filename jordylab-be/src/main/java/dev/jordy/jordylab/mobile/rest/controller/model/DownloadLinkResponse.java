package dev.jordy.jordylab.mobile.rest.controller.model;

import java.time.Instant;

public record DownloadLinkResponse(String downloadUrl, Instant expiresAt) {
}
