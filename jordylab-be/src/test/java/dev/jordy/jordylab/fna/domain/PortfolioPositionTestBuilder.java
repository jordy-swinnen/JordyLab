package dev.jordy.jordylab.fna.domain;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class PortfolioPositionTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("33333333-0000-0000-0000-000000000003");
    public static final String DEFAULT_TICKER = "KBC.BR";
    public static final BigDecimal DEFAULT_SHARE_COUNT = new BigDecimal("10");
    public static final BigDecimal DEFAULT_LAST_PRICE = new BigDecimal("72.50");
    public static final Instant DEFAULT_LAST_PRICE_FETCHED_AT = Instant.parse("2026-03-15T09:00:00Z");

    public static PortfolioPosition aDefaultPortfolioPosition() {
        return aPortfolioPosition().build();
    }

    public static PortfolioPosition.PortfolioPositionBuilder aPortfolioPosition() {
        return PortfolioPosition.builder()
                .id(DEFAULT_ID)
                .ticker(DEFAULT_TICKER)
                .shareCount(DEFAULT_SHARE_COUNT)
                .lastPrice(DEFAULT_LAST_PRICE)
                .lastPriceFetchedAt(DEFAULT_LAST_PRICE_FETCHED_AT);
    }
}
