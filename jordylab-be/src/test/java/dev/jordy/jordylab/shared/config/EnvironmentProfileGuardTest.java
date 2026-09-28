package dev.jordy.jordylab.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentProfileGuardTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EnvironmentProfileGuard.class);

    @Test
    void refusesToStartWithNoActiveProfile() {
        // The Gradle `test` task sets spring.profiles.active=local as a JVM system property (so
        // every context-loading test satisfies this guard by default) — ApplicationContextRunner
        // inherits system properties, so this override is needed to actually simulate "no
        // profile active" here.
        contextRunner.withSystemProperties("spring.profiles.active=")
                .run(context -> assertThat(context).hasFailed());
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
