package dev.jordy.jordylab.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentProfileGuardTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withSystemProperties("spring.profiles.active=")
                    .withUserConfiguration(EnvironmentProfileGuard.class);

    @Test
    void refusesToStartWithNoActiveProfile() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStartWithAnUnrecognisedProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=staging")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsCleanlyWithTheLocalProfileActive() {
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startsCleanlyWithTheProdProfileActive() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
