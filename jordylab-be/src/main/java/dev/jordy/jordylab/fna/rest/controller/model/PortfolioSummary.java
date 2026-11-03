package dev.jordy.jordylab.fna.rest.controller.model;

import dev.jordy.jordylab.mcp.McpToolPage;

import java.math.BigDecimal;

/** The portfolio with the total of the priced positions and whether every position has a price (FR-010). */
public record PortfolioSummary(McpToolPage<PortfolioItem> items, BigDecimal totalValue, boolean allPriced) {
}
