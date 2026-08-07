package dev.jordy.jordylab.fna.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                                {"chart":{"result":[{"meta":{"regularMarketPrice":65.50}}]}}
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
}
