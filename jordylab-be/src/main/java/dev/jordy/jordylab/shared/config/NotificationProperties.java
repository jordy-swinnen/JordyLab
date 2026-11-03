package dev.jordy.jordylab.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin push-notification settings (spec 018 US3, contracts/notifications-contract.md). Moved here from the retired
 * {@code mobile} module: the ntfy server, topic and token are unchanged ({@code NTFY_*} environment variables), and
 * {@code app-base-url} is the public site origin the notification's tap-through link is built from.
 *
 * <p>The notifier stays a no-op while base-url or topic is blank — the same optional framing the settings module
 * introduced and spec 011 BUG-013 kept visible in the startup log.
 */
@ConfigurationProperties(prefix = "jordylab.notifications")
public record NotificationProperties(Ntfy ntfy, String appBaseUrl) {

    public NotificationProperties {
        ntfy = ntfy == null ? new Ntfy(null, null, null) : ntfy;
        appBaseUrl = appBaseUrl == null ? "" : appBaseUrl;
    }

    public record Ntfy(String baseUrl, String topic, String token) {
    }
}
