package dev.jordy.jordylab.fna.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class PositionPriceHistoryTest {

    @Test
    void buildPositionPriceHistoryKeepsEveryField() {
        PositionPriceHistory history = PositionPriceHistoryTestBuilder.aDefaultPositionPriceHistory();

        assertSoftly(softly -> {
            softly.assertThat(history.getId()).isEqualTo(PositionPriceHistoryTestBuilder.DEFAULT_ID);
            softly.assertThat(history.getPriceSymbol()).isEqualTo(PositionPriceHistoryTestBuilder.DEFAULT_PRICE_SYMBOL);
            softly.assertThat(history.getPriceDate()).isEqualTo(PositionPriceHistoryTestBuilder.DEFAULT_PRICE_DATE);
            softly.assertThat(history.getPriceInEuro()).isEqualByComparingTo(PositionPriceHistoryTestBuilder.DEFAULT_PRICE_IN_EURO);
        });
    }

    @Test
    void buildAssignsAnIdWhenNoneIsGiven() {
        PositionPriceHistory history = PositionPriceHistoryTestBuilder.aPositionPriceHistory().id(null).build();

        assertThat(history.getId()).isNotNull();
    }

    @Test
    void buildWithoutSymbolIsRejected() {
        assertThatThrownBy(() -> PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceSymbol(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankSymbolIsRejected() {
        assertThatThrownBy(() -> PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceSymbol(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutDateIsRejected() {
        assertThatThrownBy(() -> PositionPriceHistoryTestBuilder.aPositionPriceHistory().priceDate(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithZeroPriceIsRejected() {
        assertThatThrownBy(() -> PositionPriceHistoryTestBuilder.aPositionPriceHistory()
                .priceInEuro(BigDecimal.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithNegativePriceIsRejected() {
        assertThatThrownBy(() -> PositionPriceHistoryTestBuilder.aPositionPriceHistory()
                .priceInEuro(new BigDecimal("-1")).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsAndHashCodeFollowTheIdentity() {
        EqualsVerifier.forClass(PositionPriceHistory.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
