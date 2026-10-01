package dev.jordy.jordylab.mobile.rest.client;

import dev.jordy.jordylab.mobile.MobileProperties;
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
 * Publishes admin push notifications to the existing Ntfy server (spec FR-016, research D9/D10)
 * — the first real Ntfy sender in this codebase; 006 only ever scaffolded the config keys. A
 * push failure is logged and swallowed by the caller ({@link dev.jordy.jordylab.mobile.service.MobileNotificationListener})
 * — it must never fail the sign-up or briefing flow that triggered it. Stays a no-op while
 * {@code base-url} or {@code topic} is blank, matching 006's original "optional" framing — but never silently: the
 * startup log says which key is missing, and every sent push is logged (spec 011 BUG-013: prod never had a topic,
 * so no sign-up push was ever sent and nothing said so).
 *
 * <p><strong>Implementation note:</strong> the exact header Ntfy expects for a tap-through URL
 * is assumed to be {@code X-Click} (a plain HTTP header, Ntfy also accepts the shorthand
 * {@code Click}) per Ntfy's publish-by-HTTP-header convention — verify against the specific
 * Ntfy server version in use (research §4 item 4).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NtfyClient {

    private final MobileProperties properties;
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

    public void publish(String title, String body, String clickUrl) {
        MobileProperties.Ntfy ntfy = properties.notifications().ntfy();
        if (!StringUtils.hasText(ntfy.baseUrl()) || !StringUtils.hasText(ntfy.topic())) {
            log.debug("Ntfy not configured — skipping notification '{}'", title);

            return;
        }
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(ntfy.baseUrl() + "/" + ntfy.topic())
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("X-Title", title)
                    .header("X-Click", clickUrl);
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

    private List<String> missingKeys() {
        MobileProperties.Ntfy ntfy = properties.notifications().ntfy();
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(ntfy.baseUrl())) {
            missing.add("NTFY_BASE_URL");
        }
        if (!StringUtils.hasText(ntfy.topic())) {
            missing.add("NTFY_TOPIC");
        }

        return missing;
    }
}
