package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AiConfiguration.class, initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = {
        "jordylab.ai.health-check-ttl-seconds=45",
        "jordylab.ai.health-check-timeout-seconds=3",
        "jordylab.ai.modules.fna.provider=anthropic",
        "jordylab.ai.modules.fna.model=claude-sonnet-4-20250514"
})
class AiModuleConfigTest {

    @Autowired
    private AiModuleConfig aiModuleConfig;

    @Test
    void bindsPerModuleProviderAndModel() {
        AiModuleConfig.ModuleProvider config = aiModuleConfig.getModuleConfig("fna");

        assertThat(config).isNotNull();
        assertThat(config.provider()).isEqualTo("anthropic");
        assertThat(config.model()).isEqualTo("claude-sonnet-4-20250514");
    }

    @Test
    void bindsHealthCheckTimeouts() {
        assertThat(aiModuleConfig.healthCheckTtlSeconds()).isEqualTo(45);
        assertThat(aiModuleConfig.healthCheckTimeoutSeconds()).isEqualTo(3);
    }

    @Test
    void returnsNullForUnknownModule() {
        assertThat(aiModuleConfig.getModuleConfig("unknown")).isNull();
    }
}
