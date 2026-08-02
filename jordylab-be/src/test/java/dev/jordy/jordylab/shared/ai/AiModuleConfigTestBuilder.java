package dev.jordy.jordylab.shared.ai;

import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
class AiModuleConfigTestBuilder {

    static final int DEFAULT_HEALTH_CHECK_TTL_SECONDS = 30;
    static final int DEFAULT_HEALTH_CHECK_TIMEOUT_SECONDS = 2;
    static final int DEFAULT_CALL_TIMEOUT_SECONDS = 120;

    static AiModuleConfig aDefaultAiModuleConfig() {
        return anAiModuleConfigWithTtl(DEFAULT_HEALTH_CHECK_TTL_SECONDS);
    }

    static AiModuleConfig anAiModuleConfigWithTtl(int healthCheckTtlSeconds) {
        return new AiModuleConfig(
                healthCheckTtlSeconds,
                DEFAULT_HEALTH_CHECK_TIMEOUT_SECONDS,
                DEFAULT_CALL_TIMEOUT_SECONDS,
                Map.of()
        );
    }
}
