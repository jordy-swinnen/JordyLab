package dev.jordy.jordylab.fna.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One euro value of one price symbol on one trading day (spec 023). The backfill and each refresh cycle write these rows;
 * {@code (price_symbol, price_date)} is unique, so writing a day again replaces its value.
 */
@Entity
@Table(schema = "finance", name = "position_price_history")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionPriceHistory extends BaseEntity<PositionPriceHistory> {

    @Id
    private UUID id;

    private String priceSymbol;

    private LocalDate priceDate;

    private BigDecimal priceInEuro;

    public static class PositionPriceHistoryBuilder {
        public PositionPriceHistory build() {
            Preconditions.checkArgument(StringUtils.hasText(priceSymbol), "priceSymbol is required");
            Preconditions.checkArgument(priceDate != null, "priceDate is required");
            Preconditions.checkArgument(priceInEuro != null && priceInEuro.signum() > 0, "priceInEuro must be positive");
            if (id == null) id = UUID.randomUUID();

            return new PositionPriceHistory(id, priceSymbol, priceDate, priceInEuro);
        }
    }
}
