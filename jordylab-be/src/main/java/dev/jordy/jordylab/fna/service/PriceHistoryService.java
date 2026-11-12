package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.PriceHistoryPeriod;
import dev.jordy.jordylab.fna.domain.PositionPriceHistory;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.fna.domain.repository.PositionPriceHistoryRepository;
import dev.jordy.jordylab.fna.rest.client.PriceSourceRateLimitedException;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient.YahooBar;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient.YahooDailyHistory;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryDto;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryPointDto;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryStatus;
import dev.jordy.jordylab.fna.util.PriceHistorySummaryCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Records and reads the euro price history of tracked positions, held or wishlisted (spec 023, research sections 2 to 5).
 * History is keyed by the
 * Yahoo price symbol, so positions that share a symbol share rows. Reads never call the price source.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    /** The exchange calendar the daily rows are dated in (research section 4). */
    private static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Brussels");
    private static final String EURO = "EUR";
    private static final String PENCE = "GBp";
    private static final String POUNDS_TO_EURO = "GBPEUR=X";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    /** The scale of price_in_euro in finance.position_price_history. */
    private static final int STORED_SCALE = 4;

    private final PositionPriceHistoryRepository historyRepository;
    private final PortfolioPositionRepository positionRepository;
    private final YahooFinanceChartClient chartClient;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /** Writes the refreshed price of a tracked position as today's value, held or wishlisted (spec 023). */
    @Transactional
    public void recordTodaysPrice(PortfolioPosition position) {
        if (position.getPriceSymbol() == null || !isStorable(position.getLastPrice())) {
            return;
        }
        historyRepository.upsertDailyPrice(UUID.randomUUID(), position.getPriceSymbol(), today(),
                toStoredPrice(position.getLastPrice()), clock.instant());
    }

    /**
     * Pulls the ten-year daily history of a tracked position whose symbol has no day before today. The refresh records
     * today's price before this runs, so checking for any row would skip the backfill for every new holding or wishlist item.
     */
    public void backfillIfMissing(PortfolioPosition position) {
        if (position.getPriceSymbol() == null) {
            return;
        }
        if (historyRepository.existsByPriceSymbolAndPriceDateBefore(position.getPriceSymbol(), today())) {
            return;
        }
        storeDailyHistory(position.getPriceSymbol());
    }

    /**
     * Re-pulls the history of tracked positions whose newest row is older than the previous weekday (gap repair). One
     * symbol failing is logged and the others still run; a rate limit stops the pass.
     */
    public void repairStaleSymbols() {
        LocalDate expectedNewestDay = previousWeekday(today());
        for (PortfolioPosition position : positionRepository.findAllByOrderByTickerAsc()) {
            if (position.getPriceSymbol() != null && isStale(position.getPriceSymbol(), expectedNewestDay)) {
                try {
                    storeDailyHistory(position.getPriceSymbol());
                } catch (PriceSourceRateLimitedException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    log.warn("Could not repair the history of {}: {}", position.getPriceSymbol(), exception.getMessage());
                }
            }
        }
    }

    /** The recorded history of a tracked position for a period. Empty rows give {@code NO_HISTORY_YET}. */
    @Transactional(readOnly = true)
    public PriceHistoryDto readWindow(UUID positionId, PriceHistoryPeriod period) {
        PortfolioPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new PositionNotFoundException(positionId));
        LocalDate windowStartDate = period.windowStartDate(today());
        List<PriceHistoryPointDto> points = recordedRows(position.getPriceSymbol(), windowStartDate).stream()
                .map(row -> new PriceHistoryPointDto(row.getPriceDate(), row.getPriceInEuro()))
                .toList();
        if (points.isEmpty()) {
            return new PriceHistoryDto(position.getId(), position.getTicker(), period, PriceHistoryStatus.NO_HISTORY_YET,
                    windowStartDate, null, List.of(), null, null);
        }

        return new PriceHistoryDto(position.getId(), position.getTicker(), period, PriceHistoryStatus.AVAILABLE,
                windowStartDate, points.getFirst().date(), points, PriceHistorySummaryCalculator.summarize(points),
                lastRecordedAt(position.getPriceSymbol()));
    }

    /**
     * Fetches and converts a symbol's daily closes outside any transaction, then writes them in one short transaction.
     * A currency without a rate series stores nothing.
     */
    private void storeDailyHistory(String priceSymbol) {
        Optional<YahooDailyHistory> history = chartClient.fetchDailyHistory(priceSymbol);
        if (history.isEmpty()) {
            return;
        }
        Optional<List<EuroDailyPrice>> euroPrices = toEuroDailyPrices(history.get());
        if (euroPrices.isEmpty()) {
            log.warn("No euro history stored for {}: currency {} has no rate history", priceSymbol, history.get().currency());

            return;
        }
        List<EuroDailyPrice> prices = euroPrices.get();
        Instant recordedAt = clock.instant();
        transactionTemplate.executeWithoutResult(status -> writeDailyPrices(priceSymbol, prices, recordedAt));
    }

    private void writeDailyPrices(String priceSymbol, List<EuroDailyPrice> prices, Instant recordedAt) {
        for (EuroDailyPrice euroPrice : prices) {
            historyRepository.upsertDailyPrice(UUID.randomUUID(), priceSymbol, euroPrice.date(),
                    euroPrice.priceInEuro(), recordedAt);
        }
    }

    private Optional<List<EuroDailyPrice>> toEuroDailyPrices(YahooDailyHistory history) {
        if (EURO.equals(history.currency())) {
            return Optional.of(history.bars().stream()
                    .filter(bar -> isStorable(bar.close()))
                    .map(bar -> new EuroDailyPrice(bar.date(), toStoredPrice(bar.close())))
                    .toList());
        }
        boolean pence = PENCE.equals(history.currency());
        String rateSymbol = pence ? POUNDS_TO_EURO : history.currency() + "EUR=X";

        return chartClient.fetchDailyHistory(rateSymbol)
                .map(rates -> convertWithRates(history.bars(), closesByDate(rates), pence));
    }

    /** Each day's close with that day's rate; a day with no rate of its own uses the most recent earlier rate. */
    private static List<EuroDailyPrice> convertWithRates(List<YahooBar> bars, NavigableMap<LocalDate, BigDecimal> dailyRates,
                                                         boolean pence) {
        List<EuroDailyPrice> euroPrices = new ArrayList<>();
        for (YahooBar bar : bars) {
            Map.Entry<LocalDate, BigDecimal> rate = dailyRates.floorEntry(bar.date());
            if (bar.close() == null || rate == null) {
                continue;
            }
            BigDecimal priceInEuro = toStoredPrice(euroValue(bar.close(), rate.getValue(), pence));
            if (isStorable(priceInEuro)) {
                euroPrices.add(new EuroDailyPrice(bar.date(), priceInEuro));
            }
        }

        return euroPrices;
    }

    /** Rates stay unrounded: rounding them would lose precision before the product is stored. */
    private static NavigableMap<LocalDate, BigDecimal> closesByDate(YahooDailyHistory rates) {
        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (YahooBar bar : rates.bars()) {
            if (isPositive(bar.close())) {
                closes.put(bar.date(), bar.close());
            }
        }

        return closes;
    }

    private static BigDecimal euroValue(BigDecimal close, BigDecimal rate, boolean pence) {
        BigDecimal priceInCurrency = pence ? close.divide(ONE_HUNDRED) : close;

        return priceInCurrency.multiply(rate, MathContext.DECIMAL64);
    }

    private List<PositionPriceHistory> recordedRows(String priceSymbol, LocalDate windowStartDate) {
        if (priceSymbol == null) {
            return List.of();
        }
        if (windowStartDate == null) {
            return historyRepository.findAllByPriceSymbolOrderByPriceDateAsc(priceSymbol);
        }

        return historyRepository.findAllByPriceSymbolAndPriceDateGreaterThanEqualOrderByPriceDateAsc(priceSymbol, windowStartDate);
    }

    private Instant lastRecordedAt(String priceSymbol) {
        return historyRepository.findFirstByPriceSymbolOrderByPriceDateDesc(priceSymbol)
                .map(PositionPriceHistory::getLastModifiedDate)
                .orElse(null);
    }

    private boolean isStale(String priceSymbol, LocalDate expectedNewestDay) {
        return historyRepository.findFirstByPriceSymbolOrderByPriceDateDesc(priceSymbol)
                .map(newestRow -> newestRow.getPriceDate().isBefore(expectedNewestDay))
                .orElse(false);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(MARKET_ZONE));
    }

    /** The last weekday before the given day: a Monday expects Friday's row, because weekends have no trading. */
    private static LocalDate previousWeekday(LocalDate day) {
        LocalDate candidate = day.minusDays(1);
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.minusDays(1);
        }

        return candidate;
    }

    /** The value as the database will hold it: four decimals, rounded half up. */
    private static BigDecimal toStoredPrice(BigDecimal price) {
        return price.setScale(STORED_SCALE, RoundingMode.HALF_UP);
    }

    /** A price the database accepts: positive once rounded to its stored scale. */
    private static boolean isStorable(BigDecimal price) {
        return price != null && toStoredPrice(price).signum() > 0;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private record EuroDailyPrice(LocalDate date, BigDecimal priceInEuro) {
    }
}
