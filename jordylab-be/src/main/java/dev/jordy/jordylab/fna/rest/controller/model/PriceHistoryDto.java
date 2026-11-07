package dev.jordy.jordylab.fna.rest.controller.model;

import dev.jordy.jordylab.fna.domain.PriceHistoryPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The recorded euro history of one held position for one period (spec 023, contracts/price-history-api.md). It carries
 * no field that expresses a recommendation (FR-009).
 */
public record PriceHistoryDto(
        UUID positionId,
        String ticker,
        PriceHistoryPeriod period,
        PriceHistoryStatus status,
        LocalDate windowStartDate,
        LocalDate dataStartDate,
        List<PriceHistoryPointDto> points,
        PriceHistorySummaryDto summary,
        Instant lastRecordedAt) {
}
