package dev.jordy.jordylab.fna.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void buildWithoutShareCount() {
        assertThatThrownBy(() -> PortfolioPosition.builder().ticker("KBC.BR").build())
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
