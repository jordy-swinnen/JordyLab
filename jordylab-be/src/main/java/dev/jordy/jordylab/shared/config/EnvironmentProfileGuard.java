package dev.jordy.jordylab.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails application startup immediately when neither the {@code local} nor {@code prod} Spring
 * profile is active. Shared configuration ({@code application.yaml}) intentionally has no safe
 * default for hosts, origins or credentials, so an unset (or misspelled) profile must never
 * silently fall back to an empty or placeholder value (FR-002).
 */
@Slf4j
@Component
@Profile("!local & !prod")
public class EnvironmentProfileGuard {

    @PostConstruct
    void rejectMissingProfile() {
        String message = "No valid Spring profile is active. Set SPRING_PROFILES_ACTIVE to exactly "
                + "\"local\" or \"prod\" - JordyLab refuses to start with any other value (FR-002).";
        log.error(message);

        throw new IllegalStateException(message);
    }
}
