package dev.jordy.jordylab.shared.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.List;

/**
 * Fails application startup immediately when neither the {@code local} nor {@code prod} Spring
 * profile is active. Shared configuration ({@code application.yaml}) intentionally has no safe
 * default for hosts, origins or credentials, so an unset (or misspelled) profile must never
 * silently fall back to an empty or placeholder value (FR-002).
 *
 * <p>Runs as an {@link EnvironmentPostProcessor} (registered in {@code META-INF/spring.factories}),
 * after config data has resolved the active profiles but before any bean is created — so the
 * operator sees this message, not a datasource error from an unresolved {@code ${POSTGRES_URL}}
 * (spec 011 BUG-024).
 */
public class EnvironmentProfileGuard implements EnvironmentPostProcessor, Ordered {

    private static final List<String> VALID_PROFILES = List.of("local", "prod");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (activeProfiles.stream().anyMatch(VALID_PROFILES::contains)) {
            return;
        }

        throw new IllegalStateException("No valid Spring profile is active (active: " + activeProfiles + "). Set "
                + "SPRING_PROFILES_ACTIVE to exactly \"local\" or \"prod\" - JordyLab refuses to start with any "
                + "other value (FR-002).");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
