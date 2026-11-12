package dev.jordy.jordylab.feedback;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the feedback module (spec 017): the per-guest daily submission budget, the
 * backlog that accepted tickets go to (spec 022: Kaneo, or Linear until the cutover), and the
 * settings of each backlog client.
 */
@ConfigurationProperties(prefix = "jordylab.feedback")
public record FeedbackProperties(
        int dailyTicketLimit,
        String dailyLimitZone,
        Backlog backlog,
        Linear linear,
        Kaneo kaneo) {

    public FeedbackProperties {
        if (dailyTicketLimit <= 0) {
            dailyTicketLimit = 5;
        }
        if (dailyLimitZone == null || dailyLimitZone.isBlank()) {
            dailyLimitZone = "Europe/Brussels";
        }
        backlog = backlog == null ? new Backlog(null) : backlog;
        linear = linear == null ? new Linear(null, null, null, 0, 0) : linear;
        kaneo = kaneo == null ? new Kaneo(null, null, null, null, null, null, 0, 0) : kaneo;
    }

    /**
     * Which backlog an accepted ticket goes to: {@code kaneo} (the default) or {@code linear}. The
     * client selection reads {@code jordylab.feedback.backlog.provider} directly
     * ({@code @ConditionalOnProperty}); this record carries the same value for the rest of the module.
     */
    public record Backlog(String provider) {
        public Backlog {
            if (provider == null || provider.isBlank()) {
                provider = "kaneo";
            }
        }
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

    /**
     * The Kaneo settings (spec 022): the instance's base URL, the backend's own API key (a production
     * environment secret, never sent to the browser), the workspace id (looked up by name when blank), the
     * project key, the column new tickets enter, the admin link template, and the short timeouts.
     * {@code {baseUrl}}, {@code {workspaceId}}, {@code {projectId}}, {@code {taskId}} and {@code {key}} are
     * filled in by the client.
     */
    public record Kaneo(
            String baseUrl,
            String apiKey,
            String workspaceId,
            String projectKey,
            String triageColumn,
            String ticketUrlTemplate,
            int connectTimeoutMs,
            int readTimeoutMs) {
        public Kaneo {
            if (projectKey == null || projectKey.isBlank()) {
                projectKey = "JOR";
            }
            if (triageColumn == null || triageColumn.isBlank()) {
                triageColumn = "triage";
            }
            if (ticketUrlTemplate == null || ticketUrlTemplate.isBlank()) {
                ticketUrlTemplate = "{baseUrl}/dashboard/workspace/{workspaceId}/project/{projectId}/board";
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
