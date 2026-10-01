package dev.jordy.jordylab.settings.rest.controller.model;

import java.time.Instant;
import java.util.List;

/** {@code GET /api/settings/ai-models}: one row per AI feature (006 contracts/settings-ai-models-api.md). */
public record AiModelsResponse(List<Feature> features) {

    public record Feature(String key, String displayName, String moduleName, String description, String currentModel,
            String defaultModel, String fallbackModel, boolean modelAvailable, LastRun lastRun) {
    }

    public record LastRun(String provider, String model, boolean fallbackUsed, String outcome, String failureReason,
            Instant ranAt) {
    }
}
