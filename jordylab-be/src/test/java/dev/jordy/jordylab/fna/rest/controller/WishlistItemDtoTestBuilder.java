package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.WishlistItemDto;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@UtilityClass
class WishlistItemDtoTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("88888888-0000-0000-0000-000000000008");
    public static final String DEFAULT_TICKER = "ASML.AS";
    public static final BigDecimal DEFAULT_LAST_PRICE = new BigDecimal("642.80");
    public static final Instant DEFAULT_LAST_PRICE_FETCHED_AT = Instant.parse("2026-11-02T08:00:00Z");

    public static WishlistItemDto aDefaultWishlistItemDto() {
        return new WishlistItemDto(DEFAULT_ID, DEFAULT_TICKER, DEFAULT_LAST_PRICE, DEFAULT_LAST_PRICE_FETCHED_AT);
    }
}
