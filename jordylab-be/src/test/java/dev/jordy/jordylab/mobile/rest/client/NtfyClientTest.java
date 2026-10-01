package dev.jordy.jordylab.mobile.rest.client;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.mobile.MobileProperties;
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

    @Test
    void publishesTitleBodyAndClickUrlToTheConfiguredTopic() {
        stubFor(post(urlPathEqualTo("/mobile-alerts")).willReturn(aResponse().withStatus(200)));
        MobileProperties properties = properties(BASE, "mobile-alerts", null);
        NtfyClient client = new NtfyClient(properties, RestClient.create());

        client.publish("New JordyLab sign-up", "Ada Palmer is waiting for approval.",
                "https://jordylab.example/mobile/open?screen=settings-users");

        verify(postRequestedFor(urlPathEqualTo("/mobile-alerts"))
                .withHeader("X-Title", equalTo("New JordyLab sign-up"))
                .withHeader("X-Click", equalTo("https://jordylab.example/mobile/open?screen=settings-users"))
                .withRequestBody(equalTo("Ada Palmer is waiting for approval.")));
    }

    @Test
    void addsABearerAuthorizationHeaderWhenATokenIsConfigured() {
        stubFor(post(urlPathEqualTo("/mobile-alerts")).willReturn(aResponse().withStatus(200)));
        MobileProperties properties = properties(BASE, "mobile-alerts", "secret-token");
        NtfyClient client = new NtfyClient(properties, RestClient.create());

        client.publish("Title", "Body", "https://jordylab.example/mobile/open?screen=fna-briefing");

        verify(postRequestedFor(urlPathEqualTo("/mobile-alerts"))
                .withHeader("Authorization", equalTo("Bearer secret-token")));
    }

    @Test
    void isANoOpWhenNtfyIsNotConfigured() {
        MobileProperties properties = properties(null, null, null);
        NtfyClient client = new NtfyClient(properties, RestClient.create());

        client.publish("Title", "Body", "https://jordylab.example/mobile/open?screen=fna-briefing");

        // No stub registered and no exception — a call would fail against an unstubbed WireMock.
    }

    @Test
    void logsWhichKeyIsMissingAtStartupWithoutPrintingAnyValue(CapturedOutput output) {
        NtfyClient client = new NtfyClient(properties(BASE, null, "secret-token"), RestClient.create());

        client.logConfiguration();

        assertThat(output).contains("Ntfy notifications disabled: NTFY_TOPIC not set")
                .doesNotContain("secret-token")
                .doesNotContain(BASE);
    }

    @Test
    void logsThatNotificationsAreEnabledAndEachSentPush(CapturedOutput output) {
        stubFor(post(urlPathEqualTo("/mobile-alerts")).willReturn(aResponse().withStatus(200)));
        NtfyClient client = new NtfyClient(properties(BASE, "mobile-alerts", null), RestClient.create());

        client.logConfiguration();
        client.publish("New JordyLab sign-up", "Ada Palmer is waiting for approval.",
                "https://jordylab.example/mobile/open?screen=settings-users");

        assertThat(output).contains("Ntfy notifications enabled")
                .contains("Ntfy notification sent: 'New JordyLab sign-up'")
                .doesNotContain("Ada Palmer");
    }

    private MobileProperties properties(String baseUrl, String topic, String token) {
        return new MobileProperties(
                new MobileProperties.Release(null, null),
                new MobileProperties.DownloadLink(null, 5),
                new MobileProperties.App(null, null),
                new MobileProperties.Notifications(new MobileProperties.Ntfy(baseUrl, topic, token)));
    }
}
