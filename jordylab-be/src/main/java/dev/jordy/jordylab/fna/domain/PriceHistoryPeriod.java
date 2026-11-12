package dev.jordy.jordylab.fna.domain;

import java.time.LocalDate;

/** The period the price history shows, counted back from today (spec 023, FR-003). */
public enum PriceHistoryPeriod {
    ONE_MONTH,
    THREE_MONTHS,
    ONE_YEAR,
    MAX;

    /** First day of the window ending on {@code today}; {@code null} for MAX, which takes every recorded day. */
    public LocalDate windowStartDate(LocalDate today) {
        return switch (this) {
            case ONE_MONTH -> today.minusMonths(1);
            case THREE_MONTHS -> today.minusMonths(3);
            case ONE_YEAR -> today.minusYears(1);
            case MAX -> null;
        };
    }
}
