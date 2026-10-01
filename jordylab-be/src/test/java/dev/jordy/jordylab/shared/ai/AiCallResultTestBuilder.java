package dev.jordy.jordylab.shared.ai;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AiCallResultTestBuilder {

    public static final AiFeature DEFAULT_FEATURE = AiFeature.FNA_BRIEFING;
    public static final String DEFAULT_PROVIDER = "openrouter";
    public static final String DEFAULT_MODEL = "anthropic/claude-sonnet-5";
    public static final String DEFAULT_CONTENT = "AI briefing content";

    public static AiCallResult aDefaultSuccessResult() {
        return AiCallResult.success(DEFAULT_FEATURE, DEFAULT_PROVIDER, DEFAULT_MODEL, DEFAULT_CONTENT, false);
    }

    public static AiCallResult aSuccessResult(String content) {
        return AiCallResult.success(DEFAULT_FEATURE, DEFAULT_PROVIDER, DEFAULT_MODEL, content, false);
    }

    public static AiCallResult aFailureResult(ProviderFailureReason reason) {
        return AiCallResult.failure(DEFAULT_FEATURE, "anthropic", "claude-sonnet-5", reason, true);
    }
}
