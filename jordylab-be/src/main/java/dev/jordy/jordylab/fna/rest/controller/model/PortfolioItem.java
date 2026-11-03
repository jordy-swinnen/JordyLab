package dev.jordy.jordylab.fna.rest.controller.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One held position. {@code value} is shares times the last price, and is null while either is unknown. */
public record PortfolioItem(String ticker, BigDecimal shareCount, BigDecimal lastPrice, Instant lastPriceFetchedAt,
                            BigDecimal value) {
}
