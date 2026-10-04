package dev.jordy.jordylab.fna.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.PortfolioPositionTestBuilder;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@WireMockTest(httpPort = 9999)
class StockPriceServiceTest {

    @Mock
    private PortfolioPositionRepository positionRepository;

    private StockPriceService stockPriceService;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.builder().build();
        stockPriceService = new StockPriceService(restClient, positionRepository, new ObjectMapper());
        stockPriceService.yahooFinanceBaseUrl = "http://localhost:9999";
    }

    @Test
    void parsesPriceFromYahooFinanceResponse() {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/KBC.BR"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"chart":{"result":[{"meta":{"regularMarketPrice":65.50,"currency":"EUR"}}]}}
                                """)));

        assertThat(stockPriceService.fetchPrice("KBC.BR"))
                .isPresent()
                .hasValue(new BigDecimal("65.5"));
    }

    @Test
    void retainsCachedPriceWhenYahooFinanceReturns500() {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/INGA.AS"))
                .willReturn(aResponse()
                        .withStatus(500)));

        PortfolioPosition position = PortfolioPositionTestBuilder.aPortfolioPosition()
                .ticker("INGA.AS")
                .build();

        when(positionRepository.findAllByOrderByTickerAsc()).thenReturn(List.of(position));

        stockPriceService.refreshAllPrices();

        verify(positionRepository).findAllByOrderByTickerAsc();
        verifyNoMoreInteractions(positionRepository);
    }

    private static void stubChart(String symbol, String price, String currency) {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/" + symbol))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":" + price
                                + ",\"currency\":\"" + currency + "\"}}]}}")));
    }

    private static void stubNotFound(String symbol) {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/" + symbol)).willReturn(aResponse().withStatus(404)));
    }

    @Test
    void resolvesPlainBtcToTheEuroBitcoinPairInsteadOfTheDollarEtf() {
        stubChart("BTC-EUR", "76318.08", "EUR");
        stubChart("BTC", "38.25", "USD");

        assertThat(stockPriceService.resolve("BTC"))
                .contains(new StockPriceService.ResolvedQuote("BTC-EUR", new BigDecimal("76318.08")));
    }

    @Test
    void resolvesPlainMeudToItsParisListing() {
        stubNotFound("MEUD-EUR");
        stubChart("MEUD.PA", "309.7", "EUR");

        assertThat(stockPriceService.resolve("MEUD"))
                .contains(new StockPriceService.ResolvedQuote("MEUD.PA", new BigDecimal("309.7")));
    }

    @Test
    void usesAnExactSymbolAsTypedWithoutProbingOtherListings() {
        stubChart("KBC.BR", "65.50", "EUR");

        assertThat(stockPriceService.resolve("KBC.BR"))
                .contains(new StockPriceService.ResolvedQuote("KBC.BR", new BigDecimal("65.5")));
        WireMock.verify(1, getRequestedFor(urlPathEqualTo("/v8/finance/chart/KBC.BR")));
        WireMock.verify(0, getRequestedFor(urlPathEqualTo("/v8/finance/chart/KBC.BR-EUR")));
    }

    @Test
    void convertsAForeignCurrencyPriceToEuroWhenNoEuroListingExists() {
        for (String suffix : List.of("-EUR", ".PA", ".AS", ".BR", ".DE", ".MI")) {
            stubNotFound("AAPL" + suffix);
        }
        stubChart("AAPL", "200", "USD");
        stubChart("USDEUR=X", "0.9", "EUR");

        assertThat(stockPriceService.resolve("AAPL"))
                .map(StockPriceService.ResolvedQuote::priceInEuro)
                .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo("180"));
    }

    @Test
    void convertsPenceToEuroThroughPounds() {
        stubChart("SHEL.L", "2500", "GBp");
        stubChart("GBPEUR=X", "1.2", "EUR");

        assertThat(stockPriceService.fetchPrice("SHEL.L"))
                .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo("30"));
    }

    @Test
    void answersNothingWhenNoSymbolQuotesTheName() {
        for (String suffix : List.of("-EUR", ".PA", ".AS", ".BR", ".DE", ".MI", "")) {
            stubNotFound("ZZZZ" + suffix);
        }

        assertThat(stockPriceService.resolve("ZZZZ")).isEmpty();
    }

    @Test
    void refreshResolvesAndStoresTheSymbolOnceThenReusesIt() {
        stubChart("BTC-EUR", "76000", "EUR");
        PortfolioPosition position = PortfolioPositionTestBuilder.aPortfolioPosition().ticker("BTC").build();

        stockPriceService.refresh(position);
        stubChart("BTC-EUR", "77000", "EUR");
        stockPriceService.refresh(position);

        assertThat(position.getPriceSymbol()).isEqualTo("BTC-EUR");
        assertThat(position.getLastPrice()).isEqualByComparingTo("77000");
        WireMock.verify(0, getRequestedFor(urlPathEqualTo("/v8/finance/chart/BTC")));
        verify(positionRepository, times(2)).save(position);
    }

    @Test
    void anAnswerWithoutACurrencyIsUnknownNotEuro() {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/NOCUR.PA"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":12.5}}]}}")));

        assertThat(stockPriceService.fetchPrice("NOCUR.PA")).isEmpty();
    }

    @Test
    void anExactNonEuroSymbolIsFetchedOnlyOnce() {
        stubChart("TSLA.XX", "100", "USD");
        stubChart("USDEUR=X", "0.9", "EUR");

        assertThat(stockPriceService.resolve("TSLA.XX")).isPresent();
        WireMock.verify(1, getRequestedFor(urlPathEqualTo("/v8/finance/chart/TSLA.XX")));
    }

    @Test
    void sendsABrowserCompatibleUserAgentBecauseYahooRejectsCurlAndJava() {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/UA.PA"))
                .withHeader("User-Agent", containing("Mozilla/5.0"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":5,\"currency\":\"EUR\"}}]}}")));
        stubFor(get(urlPathEqualTo("/v8/finance/chart/UA.PA"))
                .withHeader("User-Agent", notMatching(".*Mozilla/5.0.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(429)));

        assertThat(stockPriceService.fetchPrice("UA.PA")).hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo("5"));
    }
}
