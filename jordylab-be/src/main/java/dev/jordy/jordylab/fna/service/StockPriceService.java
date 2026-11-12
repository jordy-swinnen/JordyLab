package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.fna.rest.client.PriceSourceRateLimitedException;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient.YahooQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Prices portfolio positions in euro. The user types a plain name (KBC, MEUD, BTC); the Yahoo Finance symbol that
 * actually quotes it in euro is looked up once ({@link #resolve}) and remembered on the position, because the same
 * letters mean different things on different markets (plain BTC is a US-dollar ETF, Bitcoin in euro is BTC-EUR).
 * After each refresh the price history of every tracked position, held or wishlisted, is recorded and backfilled
 * ({@link PriceHistoryService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    /** European listings tried, in order, for a plain name; crypto pairs (-EUR) come first. */
    private static final List<String> EURO_SUFFIXES = List.of("-EUR", ".PA", ".AS", ".BR", ".DE", ".MI");

    private static final String EURO = "EUR";
    private static final String PENCE = "GBp";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final YahooFinanceChartClient chartClient;
    private final PortfolioPositionRepository positionRepository;
    private final PriceHistoryService priceHistoryService;

    /** What a position resolved to: the Yahoo symbol it is priced with and its price in euro. */
    public record ResolvedQuote(String symbol, BigDecimal priceInEuro) {
    }

    /** Price in euro for an exact Yahoo symbol (converted when it is quoted in another currency). */
    public Optional<BigDecimal> fetchPrice(String symbol) {
        return chartClient.fetchQuote(symbol).flatMap(this::toEuro);
    }

    /**
     * Finds the symbol that quotes {@code typedName} in euro. An exact symbol (with a dot, dash, caret or equals sign)
     * is used as typed; a plain name tries crypto and European listings first and falls back to the plain symbol,
     * converting its currency. Empty when nothing answers.
     */
    public Optional<ResolvedQuote> resolve(String typedName) {
        List<String> candidates = candidateSymbols(typedName);
        Optional<YahooQuote> typedQuote = Optional.empty();
        for (String candidate : candidates) {
            Optional<YahooQuote> quote = chartClient.fetchQuote(candidate);
            if (quote.isPresent() && EURO.equals(quote.get().currency())) {
                return Optional.of(new ResolvedQuote(candidate, quote.get().price()));
            }
            if (candidate.equals(typedName)) {
                typedQuote = quote;
            }
        }
        if (!candidates.contains(typedName)) {
            typedQuote = chartClient.fetchQuote(typedName);
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
                        recordHistory(position);
                    },
                    () -> log.warn("No euro price found for {}", position.getTicker()));

            return;
        }
        fetchPrice(position.getPriceSymbol()).ifPresentOrElse(
                price -> {
                    position.updateLastPrice(price, Instant.now());
                    positionRepository.save(position);
                    recordHistory(position);
                },
                () -> log.debug("Retaining cached price for {}", position.getTicker()));
    }

    @Scheduled(fixedDelayString = "PT30M")
    public void refreshAllPrices() {
        List<PortfolioPosition> positions = positionRepository.findAllByOrderByTickerAsc();
        for (PortfolioPosition position : positions) {
            refresh(position);
        }
        keepPriceHistoryCurrent(positions);
    }

    /**
     * Records today's price for a tracked position. A history write must not stop the price refresh of the other positions,
     * so a failure here is logged and the cycle continues.
     */
    private void recordHistory(PortfolioPosition position) {
        try {
            priceHistoryService.recordTodaysPrice(position);
        } catch (RuntimeException exception) {
            log.warn("Could not record today's price for {}: {}", position.getTicker(), exception.getMessage());
        }
    }

    /**
     * Backfills and repairs the history after prices. Each symbol is isolated: one failing symbol is logged and the rest
     * still run. A rate-limited price source stops the history work for this cycle only; the next cycle retries it.
     */
    private void keepPriceHistoryCurrent(List<PortfolioPosition> positions) {
        for (PortfolioPosition position : positions) {
            try {
                priceHistoryService.backfillIfMissing(position);
            } catch (PriceSourceRateLimitedException exception) {
                log.warn("Price history paused until the next refresh cycle: {}", exception.getMessage());

                return;
            } catch (RuntimeException exception) {
                log.warn("Could not backfill the history of {}: {}", position.getTicker(), exception.getMessage());
            }
        }
        try {
            priceHistoryService.repairStaleSymbols();
        } catch (PriceSourceRateLimitedException exception) {
            log.warn("Price history paused until the next refresh cycle: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Could not repair the price history: {}", exception.getMessage());
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

    private Optional<BigDecimal> toEuro(YahooQuote quote) {
        if (EURO.equals(quote.currency())) {
            return Optional.of(quote.price());
        }
        if (PENCE.equals(quote.currency())) {
            return chartClient.fetchQuote("GBPEUR=X")
                    .map(rate -> quote.price().divide(ONE_HUNDRED).multiply(rate.price(), MathContext.DECIMAL64));
        }

        return chartClient.fetchQuote(quote.currency() + "EUR=X")
                .map(rate -> quote.price().multiply(rate.price(), MathContext.DECIMAL64));
    }
}
