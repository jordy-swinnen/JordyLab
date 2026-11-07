package dev.jordy.jordylab.fna.domain;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@UtilityClass
public class PositionPriceHistoryTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("55555555-0000-0000-0000-000000000005");
    public static final String DEFAULT_PRICE_SYMBOL = "ASML.AS";
    public static final LocalDate DEFAULT_PRICE_DATE = LocalDate.of(2026, 11, 6);
    public static final BigDecimal DEFAULT_PRICE_IN_EURO = new BigDecimal("1250.0000");

    public static PositionPriceHistory aDefaultPositionPriceHistory() {
        return aPositionPriceHistory().build();
    }

    public static PositionPriceHistory.PositionPriceHistoryBuilder aPositionPriceHistory() {
        return PositionPriceHistory.builder()
                .id(DEFAULT_ID)
                .priceSymbol(DEFAULT_PRICE_SYMBOL)
                .priceDate(DEFAULT_PRICE_DATE)
                .priceInEuro(DEFAULT_PRICE_IN_EURO);
    }
}
