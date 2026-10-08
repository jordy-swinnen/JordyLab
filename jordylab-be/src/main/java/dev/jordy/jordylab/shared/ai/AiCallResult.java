package dev.jordy.jordylab.shared.ai;

/**
 * Outcome of one {@link ResilientAiService#call} — the provider and model that actually answered (or last failed),
 * and whether the fallback was needed. {@code model} is the model that was asked for; {@code answeredModel} is the one
 * the provider says it used, which differs when a router model such as {@code jev-router} picks one (spec 013 FR-059).
 * Token counts are null when the provider reported none.
 */
public record AiCallResult(boolean success, AiFeature feature, String provider, String model, String content,
        ProviderFailureReason failureReason, boolean fallbackUsed, String answeredModel, Integer inputTokens,
        Integer outputTokens) {

    public static AiCallResult success(AiFeature feature, String provider, String model, String content,
            boolean fallbackUsed) {
        return new AiCallResult(true, feature, provider, model, content, null, fallbackUsed, model, null, null);
    }

    public static AiCallResult success(AiFeature feature, String provider, String model, String answeredModel,
            String content, Integer inputTokens, Integer outputTokens) {
        return new AiCallResult(true, feature, provider, model, content, null, false, answeredModel, inputTokens,
                outputTokens);
    }

    public static AiCallResult failure(AiFeature feature, String provider, String model, ProviderFailureReason reason,
            boolean fallbackUsed) {
        return new AiCallResult(false, feature, provider, model, null, reason, fallbackUsed, null, null, null);
    }

    public AiCallResult withFallbackUsed() {
        return new AiCallResult(success, feature, provider, model, content, failureReason, true, answeredModel,
                inputTokens, outputTokens);
    }

    /** The same call, counted as a failure: the answer arrived but was not usable. */
    public AiCallResult asInvalidOutput() {
        return new AiCallResult(false, feature, provider, model, null, ProviderFailureReason.INVALID_OUTPUT,
                fallbackUsed, answeredModel, inputTokens, outputTokens);
    }

    /** Adds the tokens spent by an earlier attempt of the same logical call (a repair after an invalid answer). */
    public AiCallResult plusUsageOf(AiCallResult earlier) {
        return new AiCallResult(success, feature, provider, model, content, failureReason, fallbackUsed, answeredModel,
                sum(inputTokens, earlier.inputTokens), sum(outputTokens, earlier.outputTokens));
    }

    private static Integer sum(Integer first, Integer second) {
        if (first == null && second == null) {
            return null;
        }

        return (first == null ? 0 : first) + (second == null ? 0 : second);
    }
}
