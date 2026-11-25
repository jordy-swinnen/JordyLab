package dev.jordy.jordylab.feedback;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The feedback module's Spring wiring. The module declares no beans of its own beyond the
 * configuration binding; the services, the Kaneo client and the listener are plain components.
 */
@Configuration
@EnableConfigurationProperties(FeedbackProperties.class)
class FeedbackConfiguration {
}
