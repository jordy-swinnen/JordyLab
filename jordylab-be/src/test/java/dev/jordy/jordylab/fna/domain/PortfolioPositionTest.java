package dev.jordy.jordylab.fna.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class PortfolioPositionTest {

    @Test
    void buildPortfolioPosition() {
        PortfolioPosition position = PortfolioPosition.builder()
                .ticker("KBC.BR")
                .shareCount(BigDecimal.valueOf(10))
                .lastPrice(BigDecimal.valueOf(72.50))
                .build();

        assertThat(position.getId()).isNotNull();
        assertThat(position.getTicker()).isEqualTo("KBC.BR");
        assertThat(position.getShareCount()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(position.getLastPrice()).isEqualByComparingTo(BigDecimal.valueOf(72.50));
    }

    @Test
    void buildWithoutTicker() {
        assertThatThrownBy(() -> PortfolioPosition.builder().shareCount(BigDecimal.TEN).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankTicker() {
        assertThatThrownBy(() -> PortfolioPosition.builder().ticker(" ").shareCount(BigDecimal.TEN).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWishlistItemWithoutShareCount() {
        PortfolioPosition position = PortfolioPosition.builder()
                .ticker("ASML.AS")
                .build();

        assertSoftly(softly -> {
            softly.assertThat(position.getShareCount()).isNull();
            softly.assertThat(position.isWishlisted()).isTrue();
            softly.assertThat(position.isHeld()).isFalse();
        });
    }

    @Test
    void buildHeldPositionIsHeldAndNotWishlisted() {
        PortfolioPosition position = PortfolioPosition.builder()
                .ticker("KBC.BR")
                .shareCount(BigDecimal.TEN)
                .build();

        assertSoftly(softly -> {
            softly.assertThat(position.isHeld()).isTrue();
            softly.assertThat(position.isWishlisted()).isFalse();
        });
    }

    @Test
    void buildWithZeroShareCount() {
        assertThatThrownBy(() -> PortfolioPosition.builder().ticker("KBC.BR").shareCount(BigDecimal.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithNegativeShareCount() {
        assertThatThrownBy(() -> PortfolioPosition.builder().ticker("KBC.BR").shareCount(BigDecimal.valueOf(-1)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateShareCountChangesField() {
        PortfolioPosition position = PortfolioPosition.builder()
                .ticker("KBC.BR")
                .shareCount(BigDecimal.valueOf(10))
                .build();

        position.updateShareCount(BigDecimal.valueOf(25));

        assertThat(position.getShareCount()).isEqualByComparingTo(BigDecimal.valueOf(25));
    }

    @Test
    void updateShareCountRejectsZero() {
        PortfolioPosition position = PortfolioPosition.builder()
                .ticker("KBC.BR")
                .shareCount(BigDecimal.TEN)
                .build();

        assertThatThrownBy(() -> position.updateShareCount(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateShareCountRejectsNull() {
        PortfolioPosition position = PortfolioPosition.builder()
                .ticker("KBC.BR")
                .shareCount(BigDecimal.TEN)
                .build();

        assertThatThrownBy(() -> position.updateShareCount(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateShareCountPromotesAWishlistItemKeepingItsPrice() {
        PortfolioPosition position = PortfolioPosition.builder()
                .ticker("ASML.AS")
                .priceSymbol("ASML.AS")
                .lastPrice(BigDecimal.valueOf(642.80))
                .lastPriceFetchedAt(Instant.parse("2026-11-02T08:00:00Z"))
                .build();

        position.updateShareCount(BigDecimal.valueOf(2));

        assertSoftly(softly -> {
            softly.assertThat(position.isHeld()).isTrue();
            softly.assertThat(position.getShareCount()).isEqualByComparingTo(BigDecimal.valueOf(2));
            softly.assertThat(position.getPriceSymbol()).isEqualTo("ASML.AS");
            softly.assertThat(position.getLastPrice()).isEqualByComparingTo("642.80");
        });
    }

    @Test
    void updateLastPriceChangesBothFields() {
        PortfolioPosition position = PortfolioPosition.builder()
                .ticker("KBC.BR")
                .shareCount(BigDecimal.valueOf(10))
                .build();

        Instant fetchedAt = Instant.parse("2026-03-17T09:00:00Z");
        position.updateLastPrice(BigDecimal.valueOf(75.00), fetchedAt);

        assertThat(position.getLastPrice()).isEqualByComparingTo(BigDecimal.valueOf(75.00));
        assertThat(position.getLastPriceFetchedAt()).isEqualTo(fetchedAt);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(PortfolioPosition.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
