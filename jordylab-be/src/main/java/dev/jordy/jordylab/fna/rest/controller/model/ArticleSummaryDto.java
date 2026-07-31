package dev.jordy.jordylab.fna.rest.controller.model;

import java.time.Instant;
import java.util.UUID;

public record ArticleSummaryDto(UUID id, String title, String url, Instant publishedAt, String feedName) {
}
