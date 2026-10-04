package dev.jordy.jordylab.fna.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Prices portfolio positions in euro. The user types a plain name (KBC, MEUD, BTC); the Yahoo Finance symbol that
 * actually quotes it in euro is looked up once ({@link #resolve}) and remembered on the position, because the same
 * letters mean different things on different markets (plain BTC is a US-dollar ETF, Bitcoin in euro is BTC-EUR).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    /** European listings tried, in order, for a plain name; crypto pairs (-EUR) come first. */
    private static final List<String> EURO_SUFFIXES = List.of("-EUR", ".PA", ".AS", ".BR", ".DE", ".MI");

    /** Yahoo answers 429 to clients that call themselves curl or Java; a browser-compatible agent gets the data. */
    private static final String QUOTE_USER_AGENT = "Mozilla/5.0 (compatible; JordyLab/1.0)";

    private static final String EURO = "EUR";
    private static final String PENCE = "GBp";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /** Quote lookups are cheap and best-effort: give up quickly so a slow Yahoo never stalls adding a position. */
    private static final JdkClientHttpRequestFactory QUOTE_REQUEST_FACTORY = quoteRequestFactory();

    private final RestClient restClient;
    private final PortfolioPositionRepository positionRepository;
    private final ObjectMapper objectMapper;

    @Value("${fna.yahoo-finance.base-url:https://query1.finance.yahoo.com}")
    String yahooFinanceBaseUrl;

    /** What a position resolved to: the Yahoo symbol it is priced with and its price in euro. */
    public record ResolvedQuote(String symbol, BigDecimal priceInEuro) {
    }

    private record Quote(BigDecimal price, String currency) {
    }

    /** Price in euro for an exact Yahoo symbol (converted when it is quoted in another currency). */
    public Optional<BigDecimal> fetchPrice(String symbol) {
        return fetchQuote(symbol).flatMap(this::toEuro);
    }

    /**
     * Finds the symbol that quotes {@code typedName} in euro. An exact symbol (with a dot, dash, caret or equals sign)
     * is used as typed; a plain name tries crypto and European listings first and falls back to the plain symbol,
     * converting its currency. Empty when nothing answers.
     */
    public Optional<ResolvedQuote> resolve(String typedName) {
        List<String> candidates = candidateSymbols(typedName);
        Optional<Quote> typedQuote = Optional.empty();
        for (String candidate : candidates) {
            Optional<Quote> quote = fetchQuote(candidate);
            if (quote.isPresent() && EURO.equals(quote.get().currency())) {
                return Optional.of(new ResolvedQuote(candidate, quote.get().price()));
            }
            if (candidate.equals(typedName)) {
                typedQuote = quote;
            }
        }
        if (!candidates.contains(typedName)) {
            typedQuote = fetchQuote(typedName);
        }

        return typedQuote.flatMap(this::toEuro).map(price -> new ResolvedQuote(typedName, price));
    }

    /** Resolves (when needed) and prices one position; keeps the cached price when nothing answers. */
    public void refresh(PortfolioPosition position) {
        if (position.getPriceSymbol() == null) {
            resolve(position.getTicker()).ifPresentOrElse(
                    resolved -> {
                        position.updatePriceSymbol(resolved.symbol());
                        position.updateLastPrice(resolved.priceInEuro(), Instant.now());
                        positionRepository.save(position);
                    },
                    () -> log.warn("No euro price found for {}", position.getTicker()));

            return;
        }
        fetchPrice(position.getPriceSymbol()).ifPresentOrElse(
                price -> {
                    position.updateLastPrice(price, Instant.now());
                    positionRepository.save(position);
                },
                () -> log.debug("Retaining cached price for {}", position.getTicker()));
    }

    @Scheduled(fixedDelayString = "PT30M")
    public void refreshAllPrices() {
        for (PortfolioPosition position : positionRepository.findAllByOrderByTickerAsc()) {
            refresh(position);
        }
    }

    private List<String> candidateSymbols(String typedName) {
        if (typedName.matches(".*[.\\-^=].*")) {
            return List.of(typedName);
        }
        List<String> candidates = new ArrayList<>();
        for (String suffix : EURO_SUFFIXES) {
            candidates.add(typedName + suffix);
        }

        return candidates;
    }

    private Optional<BigDecimal> toEuro(Quote quote) {
        if (EURO.equals(quote.currency())) {
            return Optional.of(quote.price());
        }
        if (PENCE.equals(quote.currency())) {
            return fetchQuote("GBPEUR=X").map(rate -> quote.price().divide(ONE_HUNDRED).multiply(rate.price(), MathContext.DECIMAL64));
        }

        return fetchQuote(quote.currency() + "EUR=X")
                .map(rate -> quote.price().multiply(rate.price(), MathContext.DECIMAL64));
    }

    private Optional<Quote> fetchQuote(String symbol) {
        try {
            String responseBody = restClient.mutate().requestFactory(QUOTE_REQUEST_FACTORY).build().get()
                    .uri(URI.create(yahooFinanceBaseUrl + "/v8/finance/chart/"
                            + UriUtils.encodePathSegment(symbol, StandardCharsets.UTF_8) + "?interval=1d&range=1d"))
                    .header(HttpHeaders.USER_AGENT, QUOTE_USER_AGENT)
                    .retrieve()
                    .body(String.class);
            JsonNode meta = objectMapper.readTree(responseBody).path("chart").path("result").get(0).path("meta");
            JsonNode priceNode = meta.path("regularMarketPrice");
            JsonNode currencyNode = meta.path("currency");
            if (priceNode.isMissingNode() || !currencyNode.isTextual()) {
                // An answer without a price or without a currency cannot be valued in euro: treat it as unknown.
                return Optional.empty();
            }

            return Optional.of(new Quote(priceNode.decimalValue(), currencyNode.asText()));
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

    private static JdkClientHttpRequestFactory quoteRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));

        return factory;
    }
}
