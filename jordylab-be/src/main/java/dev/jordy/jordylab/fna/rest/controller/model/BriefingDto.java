package dev.jordy.jordylab.fna.rest.controller.model;

import java.time.Instant;
import java.util.UUID;

public record BriefingDto(UUID id, Instant generatedAt, String content, String modelUsed) {
}
