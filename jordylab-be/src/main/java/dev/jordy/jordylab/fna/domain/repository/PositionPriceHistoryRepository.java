package dev.jordy.jordylab.fna.domain.repository;

import dev.jordy.jordylab.fna.domain.PositionPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionPriceHistoryRepository extends JpaRepository<PositionPriceHistory, UUID> {

    /** Writes a day's euro value; a day already recorded for the symbol takes the new value. Needs a transaction. */
    @Modifying
    @Query(value = """
            INSERT INTO finance.position_price_history (id, price_symbol, price_date, price_in_euro, created_at, updated_at)
            VALUES (:id, :priceSymbol, :priceDate, :priceInEuro, :recordedAt, :recordedAt)
            ON CONFLICT (price_symbol, price_date)
            DO UPDATE SET price_in_euro = EXCLUDED.price_in_euro, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertDailyPrice(@Param("id") UUID id,
                         @Param("priceSymbol") String priceSymbol,
                         @Param("priceDate") LocalDate priceDate,
                         @Param("priceInEuro") BigDecimal priceInEuro,
                         @Param("recordedAt") Instant recordedAt);

    /** True when the symbol has a day before {@code priceDate}: today's row, written by the refresh, does not count. */
    boolean existsByPriceSymbolAndPriceDateBefore(String priceSymbol, LocalDate priceDate);

    List<PositionPriceHistory> findAllByPriceSymbolOrderByPriceDateAsc(String priceSymbol);

    List<PositionPriceHistory> findAllByPriceSymbolAndPriceDateGreaterThanEqualOrderByPriceDateAsc(String priceSymbol,
                                                                                                   LocalDate windowStartDate);

    Optional<PositionPriceHistory> findFirstByPriceSymbolOrderByPriceDateDesc(String priceSymbol);
}
