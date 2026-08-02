package dev.jordy.jordylab.shared.ai;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AiCallResultTestBuilder {

    public static final String DEFAULT_MODULE = "fna";
    public static final String DEFAULT_PROVIDER = "anthropic";
    public static final String DEFAULT_MODEL = "claude-sonnet-5";
    public static final String DEFAULT_CONTENT = "AI briefing content";

    public static AiCallResult aDefaultSuccessResult() {
        return AiCallResult.success(DEFAULT_MODULE, DEFAULT_PROVIDER, DEFAULT_MODEL, DEFAULT_CONTENT);
    }

    public static AiCallResult aSuccessResult(String content) {
        return AiCallResult.success(DEFAULT_MODULE, DEFAULT_PROVIDER, DEFAULT_MODEL, content);
    }

    public static AiCallResult aFailureResult(ProviderFailureReason reason) {
        return AiCallResult.failure(DEFAULT_MODULE, DEFAULT_PROVIDER, DEFAULT_MODEL, reason);
    }
}
