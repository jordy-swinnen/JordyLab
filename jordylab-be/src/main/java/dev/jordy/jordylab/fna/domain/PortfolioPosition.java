package dev.jordy.jordylab.fna.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A tracked ticker. A position with a share count is held; a position without one is on the wishlist (spec 015) — the
 * same row carries both, so promoting a wishlist item keeps the resolved price symbol and the cached price.
 */
@Entity
@Table(schema = "finance", name = "portfolio_position")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioPosition extends BaseEntity<PortfolioPosition> {

    @Id
    private UUID id;

    private String ticker;

    private BigDecimal shareCount;

    private BigDecimal lastPrice;

    /** The Yahoo symbol this position is priced with; may differ from the ticker the user typed. */
    private String priceSymbol;

    private Instant lastPriceFetchedAt;

    public boolean isHeld() {
        return shareCount != null;
    }

    public boolean isWishlisted() {
        return shareCount == null;
    }

    /** Also promotes a wishlist item to a held position: the row keeps its resolved symbol and cached price. */
    public void updateShareCount(BigDecimal newShareCount) {
        Preconditions.checkArgument(isPositive(newShareCount), "shareCount must be positive");

        this.shareCount = newShareCount;
    }

    public void updatePriceSymbol(String newPriceSymbol) {
        this.priceSymbol = newPriceSymbol;
    }

    public void updateLastPrice(BigDecimal newLastPrice, Instant fetchedAt) {
        this.lastPrice = newLastPrice;
        this.lastPriceFetchedAt = fetchedAt;
    }

    private static boolean isPositive(BigDecimal shareCount) {
        return shareCount != null && shareCount.compareTo(BigDecimal.ZERO) > 0;
    }

    public static class PortfolioPositionBuilder {
        public PortfolioPosition build() {
            Preconditions.checkArgument(StringUtils.hasText(ticker), "ticker is required");
            Preconditions.checkArgument(shareCount == null || isPositive(shareCount),
                    "shareCount must be positive when present");
            if (id == null) id = UUID.randomUUID();

            return new PortfolioPosition(id, ticker, shareCount, lastPrice, priceSymbol, lastPriceFetchedAt);
        }
    }
}
