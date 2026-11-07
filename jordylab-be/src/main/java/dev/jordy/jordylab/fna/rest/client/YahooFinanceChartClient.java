package dev.jordy.jordylab.fna.rest.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the Yahoo Finance chart endpoint for quotes and daily history (spec 023, contracts/yahoo-chart-history.md).
 * Quotes stay best-effort; history reports rate limiting so the refresh cycle can stop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceChartClient {

    /** Yahoo answers 429 to clients that call themselves curl or Java; a browser-compatible agent gets the data. */
    private static final String CHART_USER_AGENT = "Mozilla/5.0 (compatible; JordyLab/1.0)";
    private static final String QUOTE_RANGE = "1d";
    private static final String HISTORY_RANGE = "10y";
    private static final JdkClientHttpRequestFactory QUOTE_REQUEST_FACTORY = requestFactory(Duration.ofSeconds(5));
    private static final JdkClientHttpRequestFactory HISTORY_REQUEST_FACTORY = requestFactory(Duration.ofSeconds(10));

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${fna.yahoo-finance.base-url:https://query1.finance.yahoo.com}")
    @Setter
    String yahooFinanceBaseUrl;

    /** The latest price of a symbol and the currency it is quoted in. */
    public record YahooQuote(BigDecimal price, String currency) {
    }

    /** One trading day: its date and its close, or {@code null} when the close is missing. */
    public record YahooBar(LocalDate date, BigDecimal close) {
    }

    public record YahooDailyHistory(String currency, List<YahooBar> bars) {
    }

    /** Best-effort quote: a 404, a 429, an outage or a malformed answer all give no quote. */
    public Optional<YahooQuote> fetchQuote(String symbol) {
        try {
            JsonNode meta = readFirstResult(symbol, QUOTE_RANGE, QUOTE_REQUEST_FACTORY).path("meta");
            JsonNode priceNode = meta.path("regularMarketPrice");
            JsonNode currencyNode = meta.path("currency");
            if (priceNode.isMissingNode() || !currencyNode.isTextual()) {
                // An answer without a price or without a currency cannot be valued in euro: treat it as unknown.
                return Optional.empty();
            }

            return Optional.of(new YahooQuote(priceNode.decimalValue(), currencyNode.asText()));
        } catch (HttpClientErrorException.NotFound exception) {
            // Normal while probing candidate listings.
            log.debug("No quote for {}: not found", symbol);

            return Optional.empty();
        } catch (Exception exception) {
            // Anything else (429, 5xx, timeout, bad payload) is a real problem with the price source: make it visible.
            log.warn("Could not fetch a quote for {}: {}", symbol, exception.getMessage());

            return Optional.empty();
        }
    }

    /**
     * Daily closes of the last ten years. A 429 is rethrown so the refresh cycle stops; a 404 or any other failure gives
     * no history for the symbol.
     */
    public Optional<YahooDailyHistory> fetchDailyHistory(String symbol) {
        try {
            return Optional.of(parseDailyHistory(readFirstResult(symbol, HISTORY_RANGE, HISTORY_REQUEST_FACTORY)));
        } catch (PriceSourceRateLimitedException exception) {
            throw exception;
        } catch (HttpClientErrorException.NotFound exception) {
            log.debug("No daily history for {}: not found", symbol);

            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Could not fetch daily history for {}: {}", symbol, exception.getMessage());

            return Optional.empty();
        }
    }

    private JsonNode readFirstResult(String symbol, String range, JdkClientHttpRequestFactory requestFactory)
            throws JsonProcessingException {
        String responseBody = requestChart(symbol, range, requestFactory);
        JsonNode result = objectMapper.readTree(responseBody).path("chart").path("result").path(0);
        if (result.isMissingNode()) {
            throw new IllegalStateException("Yahoo answered without a chart result for " + symbol);
        }

        return result;
    }

    private String requestChart(String symbol, String range, JdkClientHttpRequestFactory requestFactory) {
        try {
            return restClient.mutate().requestFactory(requestFactory).build().get()
                    .uri(URI.create(yahooFinanceBaseUrl + "/v8/finance/chart/"
                            + UriUtils.encodePathSegment(symbol, StandardCharsets.UTF_8)
                            + "?interval=1d&range=" + range))
                    .header(HttpHeaders.USER_AGENT, CHART_USER_AGENT)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.TooManyRequests exception) {
            throw new PriceSourceRateLimitedException(symbol, exception);
        }
    }

    private YahooDailyHistory parseDailyHistory(JsonNode result) {
        JsonNode currencyNode = result.path("meta").path("currency");
        if (!currencyNode.isTextual()) {
            throw new IllegalStateException("Yahoo answered a history without a currency");
        }
        JsonNode timestamps = result.path("timestamp");
        JsonNode closes = result.path("indicators").path("quote").path(0).path("close");
        List<YahooBar> bars = new ArrayList<>();
        for (int index = 0; index < timestamps.size(); index++) {
            LocalDate date = Instant.ofEpochSecond(timestamps.get(index).asLong()).atZone(ZoneOffset.UTC).toLocalDate();
            JsonNode closeNode = closes.path(index);
            BigDecimal close = closeNode.isNumber() ? closeNode.decimalValue() : null;
            bars.add(new YahooBar(date, close));
        }

        return new YahooDailyHistory(currencyNode.asText(), bars);
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(readTimeout);

        return factory;
    }
}
