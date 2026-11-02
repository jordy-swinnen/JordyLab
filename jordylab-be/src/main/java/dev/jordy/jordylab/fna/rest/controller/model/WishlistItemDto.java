package dev.jordy.jordylab.fna.rest.controller.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A stock the investor is considering but does not hold: no share count, only a price (spec 016). */
public record WishlistItemDto(UUID id, String ticker, BigDecimal lastPrice, Instant lastPriceFetchedAt) {
}
