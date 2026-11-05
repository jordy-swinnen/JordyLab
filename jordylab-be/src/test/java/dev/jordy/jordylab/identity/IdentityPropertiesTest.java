package dev.jordy.jordylab.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class IdentityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsEverySection() {
        runner.withPropertyValues(
                        "jordylab.identity.password.min-length=12",
                        "jordylab.identity.lockout.max-failures=5",
                        "jordylab.identity.lockout.lock-minutes=15",
                        "jordylab.identity.bootstrap.username=jordy",
                        "jordylab.identity.bootstrap.password=dev-password",
                        "jordylab.identity.agent-test.username=agent",
                        "jordylab.identity.agent-test.password=agent-password",
                        "jordylab.identity.webauthn.rp-id=localhost",
                        "jordylab.identity.webauthn.rp-name=JordyLab",
                        "jordylab.identity.webauthn.allowed-origins=http://localhost:4200,http://localhost:4300")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    IdentityProperties properties = context.getBean(IdentityProperties.class);

                    assertSoftly(softly -> {
                        softly.assertThat(properties.password().minLength()).isEqualTo(12);
                        softly.assertThat(properties.lockout().maxFailures()).isEqualTo(5);
                        softly.assertThat(properties.lockout().lockMinutes()).isEqualTo(15);
                        softly.assertThat(properties.bootstrap().username()).isEqualTo("jordy");
                        softly.assertThat(properties.bootstrap().password()).isEqualTo("dev-password");
                        softly.assertThat(properties.agentTest().username()).isEqualTo("agent");
                        softly.assertThat(properties.agentTest().password()).isEqualTo("agent-password");
                        softly.assertThat(properties.webauthn().rpId()).isEqualTo("localhost");
                        softly.assertThat(properties.webauthn().rpName()).isEqualTo("JordyLab");
                        softly.assertThat(properties.webauthn().allowedOrigins())
                                .containsExactly("http://localhost:4200", "http://localhost:4300");
                    });
                });
    }

    @Configuration
    @EnableConfigurationProperties(IdentityProperties.class)
    static class TestConfiguration {
    }
}
