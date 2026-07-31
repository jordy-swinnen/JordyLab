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

    private Instant lastPriceFetchedAt;

    public void updateShareCount(BigDecimal newShareCount) {
        Preconditions.checkArgument(newShareCount != null, "shareCount must not be null");
        this.shareCount = newShareCount;
    }

    public void updateLastPrice(BigDecimal newLastPrice, Instant fetchedAt) {
        this.lastPrice = newLastPrice;
        this.lastPriceFetchedAt = fetchedAt;
    }

    public static class PortfolioPositionBuilder {
        public PortfolioPosition build() {
            Preconditions.checkArgument(StringUtils.hasText(ticker), "ticker is required");
            Preconditions.checkArgument(shareCount != null, "shareCount is required");
            if (id == null) id = UUID.randomUUID();

            return new PortfolioPosition(id, ticker, shareCount, lastPrice, lastPriceFetchedAt);
        }
    }
}
