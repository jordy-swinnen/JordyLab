package dev.jordy.jordylab.feedback;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the feedback module (spec 017): the per-guest daily submission budget and the
 * settings of the Kaneo backlog client (spec 022; Kaneo only since the cutover).
 */
@ConfigurationProperties(prefix = "jordylab.feedback")
public record FeedbackProperties(
        int dailyTicketLimit,
        String dailyLimitZone,
        Kaneo kaneo) {

    public FeedbackProperties {
        if (dailyTicketLimit <= 0) {
            dailyTicketLimit = 5;
        }
        if (dailyLimitZone == null || dailyLimitZone.isBlank()) {
            dailyLimitZone = "Europe/Brussels";
        }
        kaneo = kaneo == null ? new Kaneo(null, null, null, null, null, null, 0, 0) : kaneo;
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
            String ticketColumn,
            String ticketUrlTemplate,
            int connectTimeoutMs,
            int readTimeoutMs) {
        public Kaneo {
            if (projectKey == null || projectKey.isBlank()) {
                projectKey = "JOR";
            }
            if (ticketColumn == null || ticketColumn.isBlank()) {
                ticketColumn = "ideas";
            }
            if (ticketUrlTemplate == null || ticketUrlTemplate.isBlank()) {
                ticketUrlTemplate = "{baseUrl}/dashboard/workspace/{workspaceId}/project/{projectId}/task/{taskId}";
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
