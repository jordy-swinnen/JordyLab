package dev.jordy.jordylab.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import dev.jordy.jordylab.settings.rest.controller.GuestChatLimitFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({SettingsProperties.class, KeycloakAdminProperties.class})
class SettingsConfiguration {

    /**
     * A {@code @Bean} method here (rather than {@code @Component} on the filter itself) keeps
     * {@code GuestChatLimitFilter} out of {@code @WebMvcTest} slices: that annotation's default
     * scanning picks up any {@code Filter}-typed {@code @Component} regardless of the test's
     * {@code controllers} argument, which would fail to wire {@link GuestChatUsageRepository} —
     * unavailable outside a full JPA-backed context. A plain {@code @Configuration} class (this
     * one) is not part of that scan, so the bean only exists where the real repository does.
     * Spring Boot still auto-registers it as a servlet filter with the default ordering (after
     * the security filter chain), unchanged from a {@code @Component}-declared filter.
     */
    @Bean
    GuestChatLimitFilter guestChatLimitFilter(GuestChatUsageRepository guestChatUsageRepository,
            SettingsProperties settingsProperties, ObjectMapper objectMapper, Clock clock) {
        return new GuestChatLimitFilter(guestChatUsageRepository, settingsProperties, objectMapper, clock);
    }
}
