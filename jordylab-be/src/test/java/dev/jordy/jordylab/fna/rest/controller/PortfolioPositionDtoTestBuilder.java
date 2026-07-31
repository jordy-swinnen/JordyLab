package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.PortfolioPositionDto;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@UtilityClass
class PortfolioPositionDtoTestBuilder {

    static final UUID DEFAULT_ID = UUID.fromString("66666666-0000-0000-0000-000000000006");
    static final String DEFAULT_TICKER = "KBC.BR";
    static final BigDecimal DEFAULT_SHARE_COUNT = new BigDecimal("10");
    static final BigDecimal DEFAULT_LAST_PRICE = new BigDecimal("72.50");
    static final Instant DEFAULT_LAST_PRICE_FETCHED_AT = Instant.parse("2026-03-15T09:00:00Z");

    static PortfolioPositionDto aDefaultPortfolioPositionDto() {
        return new PortfolioPositionDto(DEFAULT_ID, DEFAULT_TICKER, DEFAULT_SHARE_COUNT, DEFAULT_LAST_PRICE, DEFAULT_LAST_PRICE_FETCHED_AT);
    }
}
