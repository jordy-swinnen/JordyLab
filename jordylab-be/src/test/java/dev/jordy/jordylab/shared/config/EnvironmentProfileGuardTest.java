package dev.jordy.jordylab.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guard runs as an {@code EnvironmentPostProcessor}, i.e. before any bean exists, so a missing
 * profile fails with its own message instead of a datasource error from an unresolved
 * {@code ${POSTGRES_URL}} (008 FR-002, spec 011 BUG-024).
 */
class EnvironmentProfileGuardTest {

    private final EnvironmentProfileGuard guard = new EnvironmentProfileGuard();
    private final SpringApplication application = new SpringApplication();

    @Test
    void refusesToStartWithNoActiveProfile() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE");
    }

    @Test
    void refusesToStartWithAnUnrecognisedProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("staging");
    }

    @Test
    void startsCleanlyWithTheLocalProfileActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatCode(() -> guard.postProcessEnvironment(environment, application)).doesNotThrowAnyException();
    }

    @Test
    void startsCleanlyWithTheProdProfileActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> guard.postProcessEnvironment(environment, application)).doesNotThrowAnyException();
    }
}
