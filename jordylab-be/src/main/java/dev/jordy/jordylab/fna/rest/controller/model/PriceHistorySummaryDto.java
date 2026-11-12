package dev.jordy.jordylab.fna.rest.controller.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Figures for the period. The change fields are {@code null} with a single point: a change of zero would mislead
 * (spec 023, edge case "Single recorded price").
 */
public record PriceHistorySummaryDto(
        LocalDate startDate,
        BigDecimal startPriceInEuro,
        LocalDate endDate,
        BigDecimal endPriceInEuro,
        BigDecimal changeInEuro,
        BigDecimal changePercent,
        BigDecimal highestPriceInEuro,
        BigDecimal lowestPriceInEuro) {
}
