package dev.jordy.jordylab.fna.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final RestClient restClient;
    private final PortfolioPositionRepository positionRepository;
    private final ObjectMapper objectMapper;

    @Value("${fna.yahoo-finance.base-url:https://query1.finance.yahoo.com}")
    String yahooFinanceBaseUrl;

    public Optional<BigDecimal> fetchPrice(String ticker) {
        try {
            String responseBody = restClient.get()
                    .uri(URI.create(yahooFinanceBaseUrl + "/v8/finance/chart/" + ticker + "?interval=1d&range=1d"))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode priceNode = root.path("chart").path("result").get(0)
                    .path("meta").path("regularMarketPrice");

            if (!priceNode.isMissingNode()) {
                return Optional.of(priceNode.decimalValue());
            }

            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Could not fetch price for {}: {}", ticker, exception.getMessage());

            return Optional.empty();
        }
    }

    @Scheduled(fixedDelayString = "PT30M")
    public void refreshAllPrices() {
        for (PortfolioPosition position : positionRepository.findAllByOrderByTickerAsc()) {
            fetchPrice(position.getTicker()).ifPresentOrElse(
                    price -> {
                        position.updateLastPrice(price, Instant.now());
                        positionRepository.save(position);
                    },
                    () -> log.debug("Retaining cached price for {}", position.getTicker())
            );
        }
    }
}
