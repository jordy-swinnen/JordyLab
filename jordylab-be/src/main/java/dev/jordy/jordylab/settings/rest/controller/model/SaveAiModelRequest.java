package dev.jordy.jordylab.settings.rest.controller.model;

/** {@code PUT /api/settings/ai-models/{featureKey}}. Blank is rejected by the service as {@code BLANK_MODEL}. */
public record SaveAiModelRequest(String modelId) {
}
