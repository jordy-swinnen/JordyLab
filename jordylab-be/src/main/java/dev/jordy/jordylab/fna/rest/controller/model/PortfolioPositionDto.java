package dev.jordy.jordylab.fna.rest.controller.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioPositionDto(UUID id, String ticker, BigDecimal shareCount, BigDecimal lastPrice,
                                   Instant lastPriceFetchedAt) {
}
