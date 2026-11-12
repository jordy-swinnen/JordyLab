package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.domain.PriceHistoryPeriod;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryDto;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryPointDto;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistoryStatus;
import dev.jordy.jordylab.fna.rest.controller.model.PriceHistorySummaryDto;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@UtilityClass
public class PriceHistoryDtoTestBuilder {

    public static final UUID DEFAULT_POSITION_ID = UUID.fromString("77777777-0000-0000-0000-000000000007");
    public static final String DEFAULT_TICKER = "ASML";
    public static final LocalDate DEFAULT_START_DATE = LocalDate.of(2025, 11, 7);
    public static final LocalDate DEFAULT_END_DATE = LocalDate.of(2026, 11, 6);
    public static final Instant DEFAULT_LAST_RECORDED_AT = Instant.parse("2026-11-06T16:00:00Z");

    public static PriceHistoryDto aDefaultPriceHistoryDto() {
        List<PriceHistoryPointDto> points = List.of(
                new PriceHistoryPointDto(DEFAULT_START_DATE, new BigDecimal("1000.00")),
                new PriceHistoryPointDto(DEFAULT_END_DATE, new BigDecimal("1250.00")));
        PriceHistorySummaryDto summary = new PriceHistorySummaryDto(DEFAULT_START_DATE, new BigDecimal("1000.00"),
                DEFAULT_END_DATE, new BigDecimal("1250.00"), new BigDecimal("250.00"), new BigDecimal("25.00"),
                new BigDecimal("1250.00"), new BigDecimal("1000.00"));

        return new PriceHistoryDto(DEFAULT_POSITION_ID, DEFAULT_TICKER, PriceHistoryPeriod.ONE_YEAR,
                PriceHistoryStatus.AVAILABLE, DEFAULT_START_DATE, DEFAULT_START_DATE, points, summary,
                DEFAULT_LAST_RECORDED_AT);
    }

    public static PriceHistoryDto aNoHistoryPriceHistoryDto() {
        return new PriceHistoryDto(DEFAULT_POSITION_ID, DEFAULT_TICKER, PriceHistoryPeriod.MAX,
                PriceHistoryStatus.NO_HISTORY_YET, null, null, List.of(), null, null);
    }
}
