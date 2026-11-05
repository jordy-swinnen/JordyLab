package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.ArticleItem;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingView;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioPositionDto;
import dev.jordy.jordylab.fna.rest.controller.model.PortfolioSummary;
import dev.jordy.jordylab.fna.rest.controller.model.WishlistItem;
import dev.jordy.jordylab.fna.rest.controller.model.WishlistItemDto;
import dev.jordy.jordylab.fna.rest.controller.model.ArticleSummaryDto;
import dev.jordy.jordylab.fna.service.FnaService;
import dev.jordy.jordylab.mcp.McpToolPage;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.jordy.jordylab.fna.rest.controller.ArticleSummaryDtoTestBuilder.aDefaultArticleSummaryDto;
import static dev.jordy.jordylab.fna.rest.controller.BriefingDtoTestBuilder.aDefaultBriefingDto;
import static dev.jordy.jordylab.fna.rest.controller.PortfolioPositionDtoTestBuilder.aDefaultPortfolioPositionDto;
import static dev.jordy.jordylab.fna.rest.controller.WishlistItemDtoTestBuilder.aDefaultWishlistItemDto;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FnaMcpToolsTest {

    private static final PageRequest FIRST_FIFTY = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "publishedAt"));

    private final FnaService fnaService = mock(FnaService.class);
    private final FnaMcpTools tools = new FnaMcpTools(fnaService);

    @Test
    void searchArticlesReturnsTheTitleMatchesWithTheirTotal() {
        Page<ArticleSummaryDto> page = new PageImpl<>(List.of(aDefaultArticleSummaryDto()), FIRST_FIFTY, 1);
        when(fnaService.searchArticles("BEL20", FIRST_FIFTY)).thenReturn(page);

        McpToolPage<ArticleItem> result = tools.searchArticles("BEL20", null, null);

        ArticleItem article = result.items().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(article.title()).isEqualTo("BEL20 hits record high");
            softly.assertThat(article.feedName()).isEqualTo("HLN Economie");
            softly.assertThat(result.totalMatching()).isEqualTo(1L);
            softly.assertThat(result.hasMore()).isFalse();
        });
    }

    @Test
    void searchArticlesClampsTheLimitToFifty() {
        Page<ArticleSummaryDto> page = new PageImpl<>(List.of(aDefaultArticleSummaryDto()), FIRST_FIFTY, 1);
        when(fnaService.searchArticles(null, FIRST_FIFTY)).thenReturn(page);

        McpToolPage<ArticleItem> result = tools.searchArticles(null, 0, 500);

        assertSoftly(softly -> softly.assertThat(result.returned()).isEqualTo(1));
    }

    @Test
    void getPortfolioTotalsOnlyThePricedPositionsAndSaysWhenOneIsUnpriced() {
        PortfolioPositionDto priced = aDefaultPortfolioPositionDto();
        PortfolioPositionDto unpriced = new PortfolioPositionDto(UUID.fromString("cccccccc-0000-0000-0000-000000000012"),
                "UNPRICED.BR", new BigDecimal("5"), null, null);
        when(fnaService.getPortfolioPositions()).thenReturn(List.of(priced, unpriced));

        PortfolioSummary summary = tools.getPortfolio();

        assertSoftly(softly -> {
            softly.assertThat(summary.totalValue()).isEqualByComparingTo("725.00");
            softly.assertThat(summary.allPriced()).isFalse();
            softly.assertThat(summary.items().items().getFirst().value()).isEqualByComparingTo("725.00");
            softly.assertThat(summary.items().items().get(1).value()).isNull();
        });
    }

    @Test
    void getWishlistReturnsEachStockWithItsLastPriceAndFetchTime() {
        WishlistItemDto item = aDefaultWishlistItemDto();
        when(fnaService.getWishlistItems()).thenReturn(List.of(item));

        McpToolPage<WishlistItem> result = tools.getWishlist();

        WishlistItem wishlist = result.items().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(wishlist.ticker()).isEqualTo("ASML.AS");
            softly.assertThat(wishlist.lastPrice()).isEqualByComparingTo("642.80");
            softly.assertThat(wishlist.lastPriceFetchedAt()).isEqualTo(item.lastPriceFetchedAt());
        });
    }

    @Test
    void getLatestBriefingReturnsTheBriefingWhenOneExists() {
        BriefingDto briefing = aDefaultBriefingDto();
        when(fnaService.getLatestBriefing()).thenReturn(Optional.of(briefing));

        BriefingView view = tools.getLatestBriefing();

        assertSoftly(softly -> {
            softly.assertThat(view.content()).isEqualTo(briefing.content());
            softly.assertThat(view.modelUsed()).isEqualTo(briefing.modelUsed());
            softly.assertThat(view.generatedAt()).isEqualTo(briefing.generatedAt());
            softly.assertThat(view.reason()).isNull();
        });
    }

    @Test
    void getLatestBriefingReportsNoBriefingYetAndNeverGeneratesOne() {
        when(fnaService.getLatestBriefing()).thenReturn(Optional.empty());

        BriefingView view = tools.getLatestBriefing();

        assertSoftly(softly -> {
            softly.assertThat(view.reason()).isEqualTo("NO_BRIEFING_YET");
            softly.assertThat(view.content()).isNull();
        });
        verify(fnaService, never()).triggerBriefing();
    }
}
