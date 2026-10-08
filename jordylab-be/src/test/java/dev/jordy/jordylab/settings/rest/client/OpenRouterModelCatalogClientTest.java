package dev.jordy.jordylab.settings.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.settings.SettingsProperties;
import dev.jordy.jordylab.shared.ai.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@WireMockTest(httpPort = 9996)
class OpenRouterModelCatalogClientTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final String MODELS = """
            {"data":[
              {"id":"anthropic/claude-haiku-4.5","name":"Anthropic: Claude Haiku 4.5","context_length":200000,
               "architecture":{"output_modalities":["text"]},
               "pricing":{"prompt":"0.000001","completion":"0.000005"},"expiration_date":null},
              {"id":"openrouter/auto","name":"Auto Router","context_length":2000000,
               "architecture":{"output_modalities":["text"]},
               "pricing":{"prompt":"-1","completion":"-1"},"expiration_date":"2026-12-31"},
              {"id":"~anthropic/claude-latest","name":"Alias","architecture":{"output_modalities":["text"]},
               "pricing":{"prompt":"0","completion":"0"}},
              {"id":"vendor/image-only","name":"Images","architecture":{"output_modalities":["image"]},
               "pricing":{"prompt":"0","completion":"0"}}
            ]}
            """;

    private final MutableClock clock = new MutableClock(NOW);

    private OpenRouterModelCatalogClient client() {
        AiProperties aiProperties = new AiProperties(30, 120,
                new AiProperties.Gateway("http://localhost:9996/api/v1", null),
                new AiProperties.Fallback("anthropic", "claude-sonnet-5"), Map.of(),
                new AiProperties.Embedding("openai/text-embedding-3-small"));
        SettingsProperties settingsProperties = new SettingsProperties(null, new SettingsProperties.ModelCatalog(60));

        return new OpenRouterModelCatalogClient(aiProperties, settingsProperties, RestClient.create(),
                new ObjectMapper(), clock);
    }

    @Test
    void keepsTextChatModelsWithPerMillionPricesAndNoAliases() {
        stubFor(get(urlPathEqualTo("/api/v1/models")).willReturn(okJson(MODELS)));

        OpenRouterModelCatalogClient.Catalog catalog = client().catalog();

        assertSoftly(softly -> {
            softly.assertThat(catalog.fresh()).isTrue();
            softly.assertThat(catalog.fetchedAt()).isEqualTo(NOW);
            softly.assertThat(catalog.models()).extracting(OpenRouterModelCatalogClient.CatalogModel::id)
                    .containsExactly("anthropic/claude-haiku-4.5", "openrouter/auto");
            softly.assertThat(catalog.models().getFirst()).isEqualTo(new OpenRouterModelCatalogClient.CatalogModel(
                    "anthropic/claude-haiku-4.5", "Anthropic: Claude Haiku 4.5", "anthropic", new BigDecimal("1"),
                    new BigDecimal("5"), 200000, false));
            softly.assertThat(catalog.models().get(1).inputPerMillion()).isNull();
            softly.assertThat(catalog.models().get(1).expiring()).isTrue();
        });
        // Keyless: the catalog is public, so no gateway key ever leaves for it.
        verify(getRequestedFor(urlPathEqualTo("/api/v1/models")).withHeader("Authorization", absent()));
    }

    @Test
    void servesTheCacheWithinTheTtl() {
        stubFor(get(urlPathEqualTo("/api/v1/models")).willReturn(okJson(MODELS)));
        OpenRouterModelCatalogClient catalogClient = client();
        catalogClient.catalog();
        clock.advance(Duration.ofMinutes(59));

        catalogClient.catalog();

        verify(1, getRequestedFor(urlPathEqualTo("/api/v1/models")));
    }

    @Test
    void servesAStaleCacheWhenTheGatewayIsDown() {
        stubFor(get(urlPathEqualTo("/api/v1/models")).willReturn(okJson(MODELS)));
        OpenRouterModelCatalogClient catalogClient = client();
        catalogClient.catalog();
        stubFor(get(urlPathEqualTo("/api/v1/models")).willReturn(aResponse().withStatus(503)));
        clock.advance(Duration.ofMinutes(61));

        OpenRouterModelCatalogClient.Catalog catalog = catalogClient.catalog();

        assertSoftly(softly -> {
            softly.assertThat(catalog.fresh()).isFalse();
            softly.assertThat(catalog.fetchedAt()).isEqualTo(NOW);
            softly.assertThat(catalog.contains("anthropic/claude-haiku-4.5")).isTrue();
        });
    }

    @Test
    void failsLoudlyWithoutAnyCache() {
        stubFor(get(urlPathEqualTo("/api/v1/models")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client().catalog()).isInstanceOf(ModelCatalogUnavailableException.class);
    }

    @Test
    void anEmptyListIsNotACatalog() {
        stubFor(get(urlPathEqualTo("/api/v1/models")).willReturn(okJson("{\"data\":[]}")));

        assertThatThrownBy(() -> client().catalog()).isInstanceOf(ModelCatalogUnavailableException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
