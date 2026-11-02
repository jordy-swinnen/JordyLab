package dev.jordy.jordylab.feedback;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the feedback module (spec 017): the per-guest daily submission budget and the
 * Linear client used when a ticket is accepted into Jordy's backlog.
 */
@ConfigurationProperties(prefix = "jordylab.feedback")
public record FeedbackProperties(
        int dailyTicketLimit,
        String dailyLimitZone,
        Linear linear) {

    public FeedbackProperties {
        if (dailyTicketLimit <= 0) {
            dailyTicketLimit = 5;
        }
        if (dailyLimitZone == null || dailyLimitZone.isBlank()) {
            dailyLimitZone = "Europe/Brussels";
        }
        linear = linear == null ? new Linear(null, null, null, 0, 0) : linear;
    }

    /**
     * The Linear workspace settings: a personal API key (SOPS secret in prod, never sent to the
     * browser), the team key whose backlog receives accepted tickets, and the short timeouts that
     * keep a hanging Linear from holding the accept flow for long.
     */
    public record Linear(String baseUrl, String apiKey, String teamKey, int connectTimeoutMs, int readTimeoutMs) {
        public Linear {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.linear.app/graphql";
            }
            if (teamKey == null || teamKey.isBlank()) {
                teamKey = "JOR";
            }
            if (connectTimeoutMs <= 0) {
                connectTimeoutMs = 5000;
            }
            if (readTimeoutMs <= 0) {
                readTimeoutMs = 15000;
            }
        }
    }
}
