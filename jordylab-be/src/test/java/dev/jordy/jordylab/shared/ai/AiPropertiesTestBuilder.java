package dev.jordy.jordylab.shared.ai;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
class AiPropertiesTestBuilder {

    public static final int DEFAULT_HEALTH_CHECK_TTL_SECONDS = 30;
    public static final int DEFAULT_CALL_TIMEOUT_SECONDS = 120;
    public static final String GATEWAY_URL = "https://openrouter.test/api/v1";
    public static final String FALLBACK_MODEL = "claude-sonnet-5";

    public static AiProperties aDefaultAiProperties() {
        return anAiProperties(DEFAULT_HEALTH_CHECK_TTL_SECONDS, DEFAULT_CALL_TIMEOUT_SECONDS, "gateway-key");
    }

    public static AiProperties anAiPropertiesWithTtl(int healthCheckTtlSeconds) {
        return anAiProperties(healthCheckTtlSeconds, DEFAULT_CALL_TIMEOUT_SECONDS, "gateway-key");
    }

    public static AiProperties anAiProperties(int healthCheckTtlSeconds, int callTimeoutSeconds, String gatewayKey) {
        Map<String, AiProperties.Feature> features = Arrays.stream(AiFeature.values())
                .collect(Collectors.toMap(AiFeature::key,
                        feature -> new AiProperties.Feature("anthropic/model-for-" + feature.key())));

        return new AiProperties(healthCheckTtlSeconds, callTimeoutSeconds,
                new AiProperties.Gateway(GATEWAY_URL, gatewayKey),
                new AiProperties.Fallback("anthropic", FALLBACK_MODEL), features);
    }
}
