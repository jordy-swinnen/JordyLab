package dev.jordy.jordylab.fna.rest.controller.model;

/** Whether any euro price is recorded for the period (spec 023, contracts/price-history-api.md). */
public enum PriceHistoryStatus {
    AVAILABLE,
    NO_HISTORY_YET
}
