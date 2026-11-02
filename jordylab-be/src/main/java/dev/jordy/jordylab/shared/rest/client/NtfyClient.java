package dev.jordy.jordylab.shared.rest.client;

import dev.jordy.jordylab.shared.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * Publishes admin push notifications to the self-hosted ntfy server (spec 017 US3, contracts/notifications-contract.md,
 * moved from the retired {@code mobile} module). Callers pass the in-app click-through <em>path</em> ({@code
 * /settings/users}, {@code /fna/briefing}); the public site origin comes from {@code jordylab.notifications.app-base-url},
 * so the tap opens the web app (installed or in the browser) directly instead of a native deep link.
 *
 * <p>A push failure is logged and swallowed — it must never fail the sign-up or briefing flow that triggered it. Stays a
 * no-op while {@code base-url}, {@code topic} or {@code app-base-url} is blank, but never silently: the startup log says
 * which key is missing (spec 011 BUG-013), and every sent push is logged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NtfyClient {

    private final NotificationProperties properties;
    private final RestClient restClient;

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguration() {
        List<String> missing = missingKeys();
        if (missing.isEmpty()) {
            log.info("Ntfy notifications enabled");
        } else {
            log.warn("Ntfy notifications disabled: {} not set — sign-up and briefing pushes are skipped",
                    String.join(" and ", missing));
        }
    }

    public void publish(String title, String body, String clickPath) {
        NotificationProperties.Ntfy ntfy = properties.ntfy();
        if (!StringUtils.hasText(ntfy.baseUrl()) || !StringUtils.hasText(ntfy.topic())
                || !StringUtils.hasText(properties.appBaseUrl())) {
            log.debug("Ntfy not configured — skipping notification '{}'", title);

            return;
        }
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(ntfy.baseUrl() + "/" + ntfy.topic())
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("X-Title", title)
                    .header("X-Click", stripTrailingSlash(properties.appBaseUrl()) + clickPath);
            if (StringUtils.hasText(ntfy.token())) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ntfy.token());
            }
            request.body(body).retrieve().toBodilessEntity();
            // Titles are fixed strings ("New JordyLab sign-up"); never put names or emails in a title — it is logged.
            log.info("Ntfy notification sent: '{}'", title);
        } catch (RestClientException exception) {
            log.warn("Ntfy publish failed for '{}': {}", title, exception.getMessage());
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private List<String> missingKeys() {
        NotificationProperties.Ntfy ntfy = properties.ntfy();
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(ntfy.baseUrl())) {
            missing.add("NTFY_BASE_URL");
        }
        if (!StringUtils.hasText(ntfy.topic())) {
            missing.add("NTFY_TOPIC");
        }
        if (!StringUtils.hasText(properties.appBaseUrl())) {
            missing.add("JORDYLAB_APP_BASE_URL");
        }

        return missing;
    }
}
