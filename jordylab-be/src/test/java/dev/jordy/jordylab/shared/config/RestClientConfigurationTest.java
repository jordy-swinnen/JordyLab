package dev.jordy.jordylab.shared.config;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the {@code spring.http.clients.read-timeout} backstop actually bounds the
 * {@link RestClient} bean at the socket level — thread interruption alone doesn't
 * guarantee a Reactor-Netty-backed blocking call aborts (see
 * specs/001-fna-mvp1-completion/contracts/resilient-ai-service.md, Bounds section).
 */
@SpringJUnitConfig(classes = RestClientConfigurationTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.http.clients.connect-timeout=200ms",
        "spring.http.clients.read-timeout=500ms"
})
@WireMockTest(httpPort = 9998)
class RestClientConfigurationTest {

    private static final long WIREMOCK_DELAY_MILLIS = 3000;
    private static final long BOUND_ASSERTION_MILLIS = 2000;

    @Autowired
    private RestClient restClient;

    @Test
    void restClientAbortsWhenResponseExceedsConfiguredReadTimeout() {
        stubFor(get(urlPathEqualTo("/slow"))
                .willReturn(aResponse().withFixedDelay((int) WIREMOCK_DELAY_MILLIS).withStatus(200)));

        long start = System.nanoTime();

        assertThatThrownBy(() -> restClient.get()
                .uri("http://localhost:9998/slow")
                .retrieve()
                .body(String.class))
                .isInstanceOf(RestClientException.class);

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMillis).isLessThan(BOUND_ASSERTION_MILLIS);
    }

    @Test
    void restClientSucceedsForAFastResponse() {
        stubFor(get(urlPathEqualTo("/fast"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String response = restClient.get()
                .uri("http://localhost:9998/fast")
                .retrieve()
                .body(String.class);

        assertThat(response).isEqualTo("ok");
    }

    @Configuration
    @Import(RestClientConfiguration.class)
    @ImportAutoConfiguration({
            HttpClientAutoConfiguration.class,
            ImperativeHttpClientAutoConfiguration.class,
            RestClientAutoConfiguration.class
    })
    static class TestConfig {
    }
}
