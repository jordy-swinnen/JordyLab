package dev.jordy.jordylab.shared.ai;

import java.time.Instant;

/**
 * Published after every AI call (006 research D4) — the AI Models page shows each feature's last run from it.
 * {@code failureReason} is null on success; {@code answeredModel} is the model the provider reports having used, and
 * the token counts are null when it reported none.
 */
public record AiCallCompleted(AiFeature feature, String provider, String model, boolean success, boolean fallbackUsed,
        ProviderFailureReason failureReason, Instant completedAt, String answeredModel, Integer inputTokens,
        Integer outputTokens) {
}
