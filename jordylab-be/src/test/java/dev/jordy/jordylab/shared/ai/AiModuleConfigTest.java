package dev.jordy.jordylab.shared.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AiConfiguration.class, initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = {
        "jordylab.ai.health-check-ttl-seconds=45",
        "jordylab.ai.health-check-timeout-seconds=3",
        "jordylab.ai.call-timeout-seconds=90",
        "jordylab.ai.modules.fna.provider=anthropic",
        "jordylab.ai.modules.fna.model=claude-sonnet-5"
})
class AiModuleConfigTest {

    @Autowired
    private AiModuleConfig aiModuleConfig;

    @Test
    void bindsPerModuleProviderAndModel() {
        AiModuleConfig.ModuleProvider config = aiModuleConfig.getModuleConfig("fna");

        assertThat(config).isNotNull();
        assertSoftly(softly -> {
            softly.assertThat(config.provider()).isEqualTo("anthropic");
            softly.assertThat(config.model()).isEqualTo("claude-sonnet-5");
        });
    }

    @Test
    void bindsHealthCheckAndCallTimeouts() {
        assertSoftly(softly -> {
            softly.assertThat(aiModuleConfig.healthCheckTtlSeconds()).isEqualTo(45);
            softly.assertThat(aiModuleConfig.healthCheckTimeoutSeconds()).isEqualTo(3);
            softly.assertThat(aiModuleConfig.callTimeoutSeconds()).isEqualTo(90);
        });
    }

    @Test
    void returnsNullForUnknownModule() {
        assertThat(aiModuleConfig.getModuleConfig("unknown")).isNull();
    }
}
