package dev.jordy.jordylab.fna.rest.controller.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One wishlist entry with its last known price and when that price was fetched. */
public record WishlistItem(String ticker, BigDecimal lastPrice, Instant lastPriceFetchedAt) {
}
