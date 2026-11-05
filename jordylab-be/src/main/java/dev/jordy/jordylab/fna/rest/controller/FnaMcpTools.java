package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.ArticleItem;
import dev.jordy.jordylab.fna.rest.controller.model.ArticleSummaryDto;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingView;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioItem;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioPositionDto;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioSummary;
import dev.jordy.jordylab.fna.rest.controller.model.WishlistItem;
import dev.jordy.jordylab.fna.rest.controller.model.WishlistItemDto;
import dev.jordy.jordylab.fna.service.FnaService;
import dev.jordy.jordylab.mcp.McpToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The finance tools of the JordyLab connector (spec 021, contracts/tools.md). Each one reads through the same
 * {@link FnaService} call as the web app, and returns its own record shape. None of them triggers a briefing (FR-012).
 */
@Component
@RequiredArgsConstructor
public class FnaMcpTools {

    private final FnaService fnaService;

    @McpTool(name = "search_articles",
            description = "Lists the newest news articles. With text, only articles whose title contains it are listed, "
                    + "for example a ticker or a company name. Returns up to 50 per call with the total match count. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public McpToolPage<ArticleItem> searchArticles(
            @McpToolParam(description = "Text to find in article titles. Leave empty for the newest articles", required = false)
            String text,
            @McpToolParam(description = "Zero-based page index", required = false) Integer page,
            @McpToolParam(description = "Items per page, from 1 to 50", required = false) Integer limit) {
        int pageSize = McpToolPage.clampLimit(limit == null ? McpToolPage.MAX_ITEMS_PER_CALL : limit);
        int pageIndex = Math.max(0, page == null ? 0 : page);
        PageRequest request = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<ArticleSummaryDto> result = fnaService.searchArticles(text, request);
        List<ArticleItem> items = result.getContent().stream().map(FnaMcpTools::toArticleItem).toList();
        return new McpToolPage<>(items, items.size(), result.getTotalElements(), result.hasNext());
    }

    @McpTool(name = "get_portfolio",
            description = "Lists the held positions with their last known prices and fetch times, the total value of the "
                    + "priced positions, and whether every position has a price. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public PortfolioSummary getPortfolio() {
        List<PortfolioItem> positions = fnaService.getPortfolioPositions().stream()
                .map(FnaMcpTools::toPortfolioItem)
                .toList();
        BigDecimal totalValue = positions.stream()
                .map(PortfolioItem::value)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean allPriced = positions.stream().allMatch(position -> position.lastPrice() != null);
        return new PortfolioSummary(McpToolPage.firstPage(positions), totalValue, allPriced);
    }

    @McpTool(name = "get_wishlist",
            description = "Lists the wishlist with each stock's last known price and when it was fetched. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public McpToolPage<WishlistItem> getWishlist() {
        List<WishlistItem> items = fnaService.getWishlistItems().stream()
                .map(FnaMcpTools::toWishlistItem)
                .toList();
        return McpToolPage.firstPage(items);
    }

    @McpTool(name = "get_latest_briefing",
            description = "Returns the most recent investment briefing with its generation time and model. When no briefing "
                    + "exists yet, returns reason NO_BRIEFING_YET and no content. Reading never generates a new briefing. Read-only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public BriefingView getLatestBriefing() {
        return fnaService.getLatestBriefing()
                .map(FnaMcpTools::toBriefingView)
                .orElseGet(() -> new BriefingView(null, null, null, "NO_BRIEFING_YET"));
    }

    private static ArticleItem toArticleItem(ArticleSummaryDto article) {
        return new ArticleItem(article.title(), article.url(), article.publishedAt(), article.feedName());
    }

    private static PortfolioItem toPortfolioItem(PortfolioPositionDto position) {
        BigDecimal value = position.shareCount() != null && position.lastPrice() != null
                ? position.shareCount().multiply(position.lastPrice())
                : null;
        return new PortfolioItem(position.ticker(), position.shareCount(), position.lastPrice(),
                position.lastPriceFetchedAt(), value);
    }

    private static WishlistItem toWishlistItem(WishlistItemDto item) {
        return new WishlistItem(item.ticker(), item.lastPrice(), item.lastPriceFetchedAt());
    }

    private static BriefingView toBriefingView(BriefingDto briefing) {
        return new BriefingView(briefing.generatedAt(), briefing.modelUsed(), briefing.content(), null);
    }
}
