package dev.jordy.jordylab.shared.ai;

import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
class AiModuleConfigTestBuilder {

    public static final int DEFAULT_HEALTH_CHECK_TTL_SECONDS = 30;
    public static final int DEFAULT_CALL_TIMEOUT_SECONDS = 120;

    public static AiModuleConfig aDefaultAiModuleConfig() {
        return anAiModuleConfigWithTtl(DEFAULT_HEALTH_CHECK_TTL_SECONDS);
    }

    public static AiModuleConfig anAiModuleConfigWithTtl(int healthCheckTtlSeconds) {
        return new AiModuleConfig(
                healthCheckTtlSeconds,
                DEFAULT_CALL_TIMEOUT_SECONDS,
                Map.of()
        );
    }
}
