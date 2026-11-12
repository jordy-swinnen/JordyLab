package dev.jordy.jordylab.fna.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient.YahooDailyHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@WireMockTest(httpPort = 9998)
class YahooFinanceChartClientTest {

    private YahooFinanceChartClient client;

    @BeforeEach
    void setUp() {
        client = new YahooFinanceChartClient(RestClient.builder().build(), new ObjectMapper());
        client.setYahooFinanceBaseUrl("http://localhost:9998");
    }

    @Test
    void readsTheCurrencyAndTheDateAndCloseOfEachDailyBar() {
        stubChart("ASML.AS", """
                {"chart":{"result":[{
                  "meta":{"currency":"EUR"},
                  "timestamp":[1793750400,1793836800,1793923200],
                  "indicators":{"quote":[{"close":[1000.5,null,1010.25]}]}}]}}
                """);

        Optional<YahooDailyHistory> history = client.fetchDailyHistory("ASML.AS");

        assertThat(history).isPresent();
        YahooDailyHistory daily = history.get();
        assertSoftly(softly -> {
            softly.assertThat(daily.currency()).isEqualTo("EUR");
            softly.assertThat(daily.bars()).hasSize(3);
            softly.assertThat(daily.bars().get(0).date()).isEqualTo(LocalDate.of(2026, 11, 4));
            softly.assertThat(daily.bars().get(0).close()).isEqualByComparingTo("1000.5");
            softly.assertThat(daily.bars().get(1).close()).isNull();
            softly.assertThat(daily.bars().get(2).date()).isEqualTo(LocalDate.of(2026, 11, 6));
        });
    }

    @Test
    void aNotFoundAnswerGivesNoHistory() {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/NONE.XX")).willReturn(aResponse().withStatus(404)));

        assertThat(client.fetchDailyHistory("NONE.XX")).isEmpty();
    }

    @Test
    void aRateLimitedAnswerIsRethrownSoTheRefreshCycleStops() {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/RATE.XX")).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.fetchDailyHistory("RATE.XX"))
                .isInstanceOf(PriceSourceRateLimitedException.class);
    }

    @Test
    void aMalformedAnswerGivesNoHistoryWithoutThrowing() {
        stubChart("BROKEN.XX", "{}");

        assertThat(client.fetchDailyHistory("BROKEN.XX")).isEmpty();
    }

    @Test
    void aRateLimitedQuoteGivesNoQuoteInsteadOfThrowing() {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/QUOTE.XX")).willReturn(aResponse().withStatus(429)));

        assertThat(client.fetchQuote("QUOTE.XX")).isEmpty();
    }

    private static void stubChart(String symbol, String body) {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/" + symbol))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
