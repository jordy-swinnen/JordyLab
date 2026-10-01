package dev.jordy.jordylab.shared.ai;

/**
 * Outcome of one {@link ResilientAiService#call} — the provider and model that actually answered (or last failed),
 * and whether the fallback was needed.
 */
public record AiCallResult(boolean success, AiFeature feature, String provider, String model, String content,
        ProviderFailureReason failureReason, boolean fallbackUsed) {

    public static AiCallResult success(AiFeature feature, String provider, String model, String content,
            boolean fallbackUsed) {
        return new AiCallResult(true, feature, provider, model, content, null, fallbackUsed);
    }

    public static AiCallResult failure(AiFeature feature, String provider, String model, ProviderFailureReason reason,
            boolean fallbackUsed) {
        return new AiCallResult(false, feature, provider, model, null, reason, fallbackUsed);
    }
}
