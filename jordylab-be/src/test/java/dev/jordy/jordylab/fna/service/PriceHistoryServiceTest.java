package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.PortfolioPositionTestBuilder;
import dev.jordy.jordylab.fna.domain.PositionPriceHistoryTestBuilder;
import dev.jordy.jordylab.fna.domain.PriceHistoryPeriod;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.fna.domain.repository.PositionPriceHistoryRepository;
import dev.jordy.jordylab.fna.rest.client.PriceSourceRateLimitedException;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient.YahooBar;
import dev.jordy.jordylab.fna.rest.client.YahooFinanceChartClient.YahooDailyHistory;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryDto;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    /** At this instant Brussels reads Saturday 2026-11-07, so the previous weekday is Friday 2026-11-06. */
    private static final Instant NOW = Instant.parse("2026-11-06T23:30:00Z");
    private static final String SYMBOL = "ASML.AS";
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 4);
    private static final LocalDate THURSDAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    @Mock
    private PositionPriceHistoryRepository historyRepository;

    @Mock
    private PortfolioPositionRepository positionRepository;

    @Mock
    private YahooFinanceChartClient chartClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PriceHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PriceHistoryService(historyRepository, positionRepository, chartClient,
                Clock.fixed(NOW, ZoneOffset.UTC), new TransactionTemplate(transactionManager));
    }

    @Test
    void recordTodaysPriceWritesTheBrusselsDateOfTheRefresh() {
        PortfolioPosition held = PortfolioPositionTestBuilder.aPortfolioPosition()
                .ticker("ASML")
                .priceSymbol(SYMBOL)
                .lastPrice(new BigDecimal("1250.00"))
                .build();
        ArgumentCaptor<UUID> generatedIds = ArgumentCaptor.forClass(UUID.class);

        service.recordTodaysPrice(held);

        verify(historyRepository).upsertDailyPrice(generatedIds.capture(), eq(SYMBOL),
                eq(LocalDate.of(2026, 11, 7)), eq(new BigDecimal("1250.0000")), eq(NOW));
        assertThat(generatedIds.getAllValues()).doesNotContainNull();
    }

    @Test
    void recordTodaysPriceLeavesWishlistItemsWithoutHistory() {
        PortfolioPosition wishlistItem = PortfolioPositionTestBuilder.aWishlistPosition().priceSymbol(SYMBOL).build();

        service.recordTodaysPrice(wishlistItem);

        verifyNoInteractions(historyRepository);
    }

    @Test
    void backfillStoresEveryRecordedCloseOfAEuroListing() {
        when(historyRepository.existsByPriceSymbolAndPriceDateBefore(SYMBOL, TODAY)).thenReturn(false);
        when(chartClient.fetchDailyHistory(SYMBOL)).thenReturn(Optional.of(new YahooDailyHistory("EUR", List.of(
                new YahooBar(WEDNESDAY, new BigDecimal("1000.00")),
                new YahooBar(THURSDAY, null),
                new YahooBar(FRIDAY, new BigDecimal("1010.00"))))));
        ArgumentCaptor<UUID> generatedIds = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<BigDecimal> prices = ArgumentCaptor.forClass(BigDecimal.class);

        service.backfillIfMissing(heldPosition());

        verify(historyRepository, times(2)).upsertDailyPrice(generatedIds.capture(), eq(SYMBOL),
                dates.capture(), prices.capture(), eq(NOW));
        assertSoftly(softly -> {
            softly.assertThat(generatedIds.getAllValues()).doesNotContainNull();
            softly.assertThat(dates.getAllValues()).containsExactly(WEDNESDAY, FRIDAY);
            softly.assertThat(prices.getAllValues()).usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("1000.00"), new BigDecimal("1010.00"));
        });
    }

    @Test
    void backfillConvertsForeignClosesWithTheRateOfTheSameDayCarriedForwardOverGaps() {
        when(historyRepository.existsByPriceSymbolAndPriceDateBefore("AAPL", TODAY)).thenReturn(false);
        when(chartClient.fetchDailyHistory("AAPL")).thenReturn(Optional.of(new YahooDailyHistory("USD", List.of(
                new YahooBar(WEDNESDAY, new BigDecimal("200")),
                new YahooBar(FRIDAY, new BigDecimal("210"))))));
        when(chartClient.fetchDailyHistory("USDEUR=X")).thenReturn(Optional.of(new YahooDailyHistory("EUR", List.of(
                new YahooBar(WEDNESDAY, new BigDecimal("0.9"))))));
        ArgumentCaptor<UUID> generatedIds = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<BigDecimal> prices = ArgumentCaptor.forClass(BigDecimal.class);

        service.backfillIfMissing(PortfolioPositionTestBuilder.aPortfolioPosition().ticker("AAPL").priceSymbol("AAPL").build());

        verify(historyRepository, times(2)).upsertDailyPrice(generatedIds.capture(), eq("AAPL"),
                dates.capture(), prices.capture(), eq(NOW));
        assertSoftly(softly -> {
            softly.assertThat(generatedIds.getAllValues()).doesNotContainNull();
            softly.assertThat(dates.getAllValues()).containsExactly(WEDNESDAY, FRIDAY);
            softly.assertThat(prices.getAllValues()).usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("180.0"), new BigDecimal("189.0"));
        });
    }

    @Test
    void backfillStoresNothingWhenTheCurrencyHasNoRateHistory() {
        when(historyRepository.existsByPriceSymbolAndPriceDateBefore("AAPL", TODAY)).thenReturn(false);
        when(chartClient.fetchDailyHistory("AAPL")).thenReturn(Optional.of(new YahooDailyHistory("USD", List.of(
                new YahooBar(WEDNESDAY, new BigDecimal("200"))))));
        when(chartClient.fetchDailyHistory("USDEUR=X")).thenReturn(Optional.empty());

        service.backfillIfMissing(PortfolioPositionTestBuilder.aPortfolioPosition().ticker("AAPL").priceSymbol("AAPL").build());

        verify(historyRepository).existsByPriceSymbolAndPriceDateBefore("AAPL", TODAY);
        verifyNoMoreInteractions(historyRepository);
    }

    @Test
    void backfillSkipsASymbolThatAlreadyHasRows() {
        when(historyRepository.existsByPriceSymbolAndPriceDateBefore(SYMBOL, TODAY)).thenReturn(true);

        service.backfillIfMissing(heldPosition());

        verifyNoInteractions(chartClient);
    }

    @Test
    void backfillLeavesWishlistItemsAlone() {
        PortfolioPosition wishlistItem = PortfolioPositionTestBuilder.aWishlistPosition().priceSymbol(SYMBOL).build();

        service.backfillIfMissing(wishlistItem);

        verifyNoInteractions(historyRepository, chartClient);
    }

    @Test
    void backfillPassesARateLimitOnSoTheRefreshCycleCanStop() {
        when(historyRepository.existsByPriceSymbolAndPriceDateBefore(SYMBOL, TODAY)).thenReturn(false);
        when(chartClient.fetchDailyHistory(SYMBOL)).thenThrow(new PriceSourceRateLimitedException(SYMBOL, null));
        PortfolioPosition held = heldPosition();

        assertThatThrownBy(() -> service.backfillIfMissing(held))
                .isInstanceOf(PriceSourceRateLimitedException.class);
    }

    @Test
    void repairRefetchesASymbolWhoseNewestRowIsOlderThanThePreviousWeekday() {
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc()).thenReturn(List.of(heldPosition()));
        when(historyRepository.findFirstByPriceSymbolOrderByPriceDateDesc(SYMBOL)).thenReturn(Optional.of(
                PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceDate(WEDNESDAY).build()));
        when(chartClient.fetchDailyHistory(SYMBOL)).thenReturn(Optional.of(new YahooDailyHistory("EUR", List.of(
                new YahooBar(FRIDAY, new BigDecimal("1010.00"))))));
        ArgumentCaptor<UUID> generatedIds = ArgumentCaptor.forClass(UUID.class);

        service.repairStaleSymbols();

        verify(historyRepository).upsertDailyPrice(generatedIds.capture(), eq(SYMBOL), eq(FRIDAY),
                eq(new BigDecimal("1010.0000")), eq(NOW));
        assertThat(generatedIds.getAllValues()).doesNotContainNull();
    }

    @Test
    void repairLeavesASymbolAloneWhenItsNewestRowIsCurrent() {
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc()).thenReturn(List.of(heldPosition()));
        when(historyRepository.findFirstByPriceSymbolOrderByPriceDateDesc(SYMBOL)).thenReturn(Optional.of(
                PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceDate(FRIDAY).build()));

        service.repairStaleSymbols();

        verifyNoInteractions(chartClient);
    }

    @Test
    void readWindowReportsNoHistoryYetWhenNothingIsRecorded() {
        PortfolioPosition held = heldPosition();
        when(positionRepository.findById(held.getId())).thenReturn(Optional.of(held));
        when(historyRepository.findAllByPriceSymbolAndPriceDateGreaterThanEqualOrderByPriceDateAsc(SYMBOL,
                LocalDate.of(2025, 11, 7))).thenReturn(List.of());

        PriceHistoryDto history = service.readWindow(held.getId(), PriceHistoryPeriod.ONE_YEAR);

        assertSoftly(softly -> {
            softly.assertThat(history.status()).isEqualTo(PriceHistoryStatus.NO_HISTORY_YET);
            softly.assertThat(history.points()).isEmpty();
            softly.assertThat(history.summary()).isNull();
            softly.assertThat(history.windowStartDate()).isEqualTo(LocalDate.of(2025, 11, 7));
        });
    }

    @Test
    void readWindowReturnsTheRecordedPointsAndTheirSummary() {
        PortfolioPosition held = heldPosition();
        when(positionRepository.findById(held.getId())).thenReturn(Optional.of(held));
        when(historyRepository.findAllByPriceSymbolAndPriceDateGreaterThanEqualOrderByPriceDateAsc(SYMBOL,
                LocalDate.of(2025, 11, 7))).thenReturn(List.of(
                PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceDate(WEDNESDAY)
                        .priceInEuro(new BigDecimal("1000.00")).build(),
                PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceDate(FRIDAY)
                        .priceInEuro(new BigDecimal("1250.00")).build()));

        PriceHistoryDto history = service.readWindow(held.getId(), PriceHistoryPeriod.ONE_YEAR);

        assertSoftly(softly -> {
            softly.assertThat(history.status()).isEqualTo(PriceHistoryStatus.AVAILABLE);
            softly.assertThat(history.dataStartDate()).isEqualTo(WEDNESDAY);
            softly.assertThat(history.points()).hasSize(2);
            softly.assertThat(history.summary().changeInEuro()).isEqualByComparingTo("250.00");
            softly.assertThat(history.summary().changePercent()).isEqualByComparingTo("25.00");
            softly.assertThat(history.summary().highestPriceInEuro()).isEqualByComparingTo("1250.00");
            softly.assertThat(history.summary().lowestPriceInEuro()).isEqualByComparingTo("1000.00");
        });
    }

    @Test
    void readWindowForTheMaximumPeriodReadsEveryRecordedRow() {
        PortfolioPosition held = heldPosition();
        when(positionRepository.findById(held.getId())).thenReturn(Optional.of(held));
        when(historyRepository.findAllByPriceSymbolOrderByPriceDateAsc(SYMBOL)).thenReturn(List.of(
                PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceDate(WEDNESDAY)
                        .priceInEuro(new BigDecimal("1000.00")).build()));
        when(historyRepository.findFirstByPriceSymbolOrderByPriceDateDesc(SYMBOL)).thenReturn(Optional.of(
                PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceDate(WEDNESDAY)
                        .priceInEuro(new BigDecimal("1000.00")).build()));

        PriceHistoryDto history = service.readWindow(held.getId(), PriceHistoryPeriod.MAX);

        assertSoftly(softly -> {
            softly.assertThat(history.windowStartDate()).isNull();
            softly.assertThat(history.points()).hasSize(1);
            softly.assertThat(history.summary().changeInEuro()).isNull();
        });
    }

    @Test
    void readWindowRejectsAWishlistItem() {
        PortfolioPosition wishlistItem = PortfolioPositionTestBuilder.aWishlistPosition().build();
        when(positionRepository.findById(wishlistItem.getId())).thenReturn(Optional.of(wishlistItem));

        assertThatThrownBy(() -> service.readWindow(wishlistItem.getId(), PriceHistoryPeriod.ONE_YEAR))
                .isInstanceOf(PositionNotHeldException.class);
    }

    @Test
    void theYahooFetchHappensBeforeAnyTransactionOpens() {
        when(historyRepository.existsByPriceSymbolAndPriceDateBefore(SYMBOL, TODAY)).thenReturn(false);
        when(chartClient.fetchDailyHistory(SYMBOL)).thenReturn(Optional.of(new YahooDailyHistory("EUR", List.of(
                new YahooBar(WEDNESDAY, new BigDecimal("1000.00"))))));

        service.backfillIfMissing(heldPosition());

        InOrder order = inOrder(chartClient, transactionManager);
        order.verify(chartClient).fetchDailyHistory(SYMBOL);
        order.verify(transactionManager).getTransaction(isNotNull());
    }

    @Test
    void roundsStoredPricesToFourDecimalsAndSkipsAValueThatRoundsToZero() {
        when(historyRepository.existsByPriceSymbolAndPriceDateBefore(SYMBOL, TODAY)).thenReturn(false);
        when(chartClient.fetchDailyHistory(SYMBOL)).thenReturn(Optional.of(new YahooDailyHistory("EUR", List.of(
                new YahooBar(WEDNESDAY, new BigDecimal("0.00001")),
                new YahooBar(FRIDAY, new BigDecimal("1010.123456"))))));
        ArgumentCaptor<UUID> generatedIds = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<BigDecimal> prices = ArgumentCaptor.forClass(BigDecimal.class);

        service.backfillIfMissing(heldPosition());

        verify(historyRepository, times(1)).upsertDailyPrice(generatedIds.capture(), eq(SYMBOL),
                dates.capture(), prices.capture(), eq(NOW));
        assertSoftly(softly -> {
            softly.assertThat(generatedIds.getAllValues()).doesNotContainNull();
            softly.assertThat(dates.getAllValues()).containsExactly(FRIDAY);
            softly.assertThat(prices.getAllValues()).usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("1010.1235"));
            softly.assertThat(prices.getAllValues().getFirst().scale()).isEqualTo(4);
        });
    }

    private static PortfolioPosition heldPosition() {
        return PortfolioPositionTestBuilder.aPortfolioPosition().ticker("ASML").priceSymbol(SYMBOL).build();
    }
}
