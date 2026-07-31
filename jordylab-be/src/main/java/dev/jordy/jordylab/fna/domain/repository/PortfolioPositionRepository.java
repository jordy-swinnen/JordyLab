package dev.jordy.jordylab.fna.domain.repository;

import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, UUID> {

    List<PortfolioPosition> findAllByOrderByTickerAsc();

    Optional<PortfolioPosition> findByTicker(String ticker);
}
