package dev.jordy.jordylab.shared.rest.client;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.shared.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 9994)
@ExtendWith(OutputCaptureExtension.class)
class NtfyClientTest {

    private static final String BASE = "http://localhost:9994";
    private static final String APP_BASE_URL = "https://jordylab.example";

    @Test
    void publishesTitleBodyAndClickThroughToTheConfiguredTopic() {
        stubFor(post(urlPathEqualTo("/alerts")).willReturn(aResponse().withStatus(200)));
        NtfyClient client = new NtfyClient(properties(BASE, "alerts", null, APP_BASE_URL), RestClient.create());

        client.publish("New JordyLab sign-up", "Ada Palmer is waiting for approval.", "/settings/users");

        verify(postRequestedFor(urlPathEqualTo("/alerts"))
                .withHeader("X-Title", equalTo("New JordyLab sign-up"))
                .withHeader("X-Click", equalTo("https://jordylab.example/settings/users"))
                .withRequestBody(equalTo("Ada Palmer is waiting for approval.")));
    }

    @Test
    void buildsTheClickThroughAsAPlainWebRoute() {
        stubFor(post(urlPathEqualTo("/alerts")).willReturn(aResponse().withStatus(200)));
        NtfyClient client = new NtfyClient(properties(BASE, "alerts", null, APP_BASE_URL + "/"), RestClient.create());

        client.publish("Your monthly briefing is ready", "Ready to read.", "/fna/briefing");

        verify(postRequestedFor(urlPathEqualTo("/alerts"))
                .withHeader("X-Click", equalTo("https://jordylab.example/fna/briefing")));
    }

    @Test
    void addsABearerAuthorizationHeaderWhenATokenIsConfigured() {
        stubFor(post(urlPathEqualTo("/alerts")).willReturn(aResponse().withStatus(200)));
        NtfyClient client = new NtfyClient(properties(BASE, "alerts", "secret-token", APP_BASE_URL), RestClient.create());

        client.publish("Title", "Body", "/fna/briefing");

        verify(postRequestedFor(urlPathEqualTo("/alerts"))
                .withHeader("Authorization", equalTo("Bearer secret-token")));
    }

    @Test
    void isANoOpWhenNtfyIsNotConfigured() {
        NtfyClient client = new NtfyClient(properties(null, null, null, APP_BASE_URL), RestClient.create());

        client.publish("Title", "Body", "/fna/briefing");

        // No stub registered and no exception — a call would fail against an unstubbed WireMock.
    }

    @Test
    void isANoOpWhenTheAppBaseUrlIsMissing() {
        NtfyClient client = new NtfyClient(properties(BASE, "alerts", null, null), RestClient.create());

        client.publish("Title", "Body", "/fna/briefing");

        // A push without a resolvable click-through is pointless; no request is attempted.
    }

    @Test
    void logsWhichKeyIsMissingAtStartupWithoutPrintingAnyValue(CapturedOutput output) {
        NtfyClient client = new NtfyClient(properties(BASE, null, "secret-token", APP_BASE_URL), RestClient.create());

        client.logConfiguration();

        assertThat(output).contains("Ntfy notifications disabled: NTFY_TOPIC not set")
                .doesNotContain("secret-token")
                .doesNotContain(BASE);
    }

    @Test
    void logsThatNotificationsAreEnabledAndEachSentPush(CapturedOutput output) {
        stubFor(post(urlPathEqualTo("/alerts")).willReturn(aResponse().withStatus(200)));
        NtfyClient client = new NtfyClient(properties(BASE, "alerts", null, APP_BASE_URL), RestClient.create());

        client.logConfiguration();
        client.publish("New JordyLab sign-up", "Ada Palmer is waiting for approval.", "/settings/users");

        assertThat(output).contains("Ntfy notifications enabled")
                .contains("Ntfy notification sent: 'New JordyLab sign-up'")
                .doesNotContain("Ada Palmer");
    }

    private NotificationProperties properties(String baseUrl, String topic, String token, String appBaseUrl) {
        return new NotificationProperties(new NotificationProperties.Ntfy(baseUrl, topic, token), appBaseUrl);
    }
}
