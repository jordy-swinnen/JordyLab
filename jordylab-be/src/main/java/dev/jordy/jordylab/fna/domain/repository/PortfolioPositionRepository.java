package dev.jordy.jordylab.fna.domain.repository;

import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, UUID> {

    /** Every tracked ticker, held or wishlisted; the price refresh walks all of them. */
    List<PortfolioPosition> findAllByOrderByTickerAsc();

    /** Held positions only (spec 016: a null share count marks a wishlist item). */
    List<PortfolioPosition> findAllByShareCountIsNotNullOrderByTickerAsc();

    /** Wishlist items only. */
    List<PortfolioPosition> findAllByShareCountIsNullOrderByTickerAsc();

    Optional<PortfolioPosition> findByTicker(String ticker);
}
