package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.domain.PriceHistoryPeriod;
import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.service.BriefingGenerationException;
import dev.jordy.jordylab.fna.service.FnaService;
import dev.jordy.jordylab.fna.service.PositionNotHeldException;
import dev.jordy.jordylab.fna.service.PriceHistoryService;
import dev.jordy.jordylab.fna.service.TickerAlreadyHeldException;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(FnaController.class)
class FnaControllerTest {

    private static final UUID POSITION_ID = UUID.fromString("77777777-0000-0000-0000-000000000007");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FnaService fnaService;

    @MockitoBean
    private dev.jordy.jordylab.fna.service.ManualArticleSubmissionService manualArticleSubmissionService;

    @MockitoBean
    private PriceHistoryService priceHistoryService;

    @Test
    void queuesAManualArticleAndReturnsTheCreatedArticle() throws Exception {
        when(manualArticleSubmissionService.queueArticle(dev.jordy.jordylab.fna.domain.ArticleTestBuilder.DEFAULT_URL))
                .thenReturn(dev.jordy.jordylab.fna.domain.ArticleTestBuilder.aDefaultArticle());

        mockMvc.perform(post("/api/fna/articles/manual")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + dev.jordy.jordylab.fna.domain.ArticleTestBuilder.DEFAULT_URL + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(
                        dev.jordy.jordylab.fna.domain.ArticleTestBuilder.DEFAULT_ID.toString()))
                .andExpect(jsonPath("$.title").value(
                        dev.jordy.jordylab.fna.domain.ArticleTestBuilder.DEFAULT_TITLE))
                .andExpect(jsonPath("$.url").value(
                        dev.jordy.jordylab.fna.domain.ArticleTestBuilder.DEFAULT_URL));
    }

    @Test
    void getArticlesReturnsArticleList() throws Exception {
        when(fnaService.getRecentArticles()).thenReturn(List.of(
                ArticleSummaryDtoTestBuilder.aDefaultArticleSummaryDto()
        ));

        mockMvc.perform(get("/api/fna/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getPortfolioReturnsPositionList() throws Exception {
        when(fnaService.getPortfolioPositions()).thenReturn(List.of(
                PortfolioPositionDtoTestBuilder.aDefaultPortfolioPositionDto()
        ));

        mockMvc.perform(get("/api/fna/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void upsertPositionReturnsPosition() throws Exception {
        when(fnaService.upsertPosition("KBC.BR", new BigDecimal("10")))
                .thenReturn(PortfolioPositionDtoTestBuilder.aDefaultPortfolioPositionDto());

        mockMvc.perform(put("/api/fna/portfolio/KBC.BR").param("shares", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value(PortfolioPositionDtoTestBuilder.DEFAULT_TICKER));
    }

    @Test
    void removePositionReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/fna/portfolio/{id}", POSITION_ID))
                .andExpect(status().isNoContent());

        verify(fnaService).removePosition(POSITION_ID);
    }

    @Test
    void getPriceHistoryReturnsTheRecordedWindowWithItsSummary() throws Exception {
        when(priceHistoryService.readWindow(POSITION_ID, PriceHistoryPeriod.ONE_YEAR))
                .thenReturn(PriceHistoryDtoTestBuilder.aDefaultPriceHistoryDto());

        mockMvc.perform(get("/api/fna/portfolio/{id}/price-history", POSITION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.summary.changePercent").value(25.0))
                .andExpect(jsonPath("$.recommendation").doesNotExist());
    }

    @Test
    void getPriceHistoryPassesTheRequestedPeriodAndReportsNoHistoryYet() throws Exception {
        when(priceHistoryService.readWindow(POSITION_ID, PriceHistoryPeriod.MAX))
                .thenReturn(PriceHistoryDtoTestBuilder.aNoHistoryPriceHistoryDto());

        mockMvc.perform(get("/api/fna/portfolio/{id}/price-history", POSITION_ID).param("period", "MAX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_HISTORY_YET"));
    }

    @Test
    void getPriceHistoryReturns404WhenThePositionIsAWishlistItem() throws Exception {
        when(priceHistoryService.readWindow(POSITION_ID, PriceHistoryPeriod.ONE_YEAR))
                .thenThrow(new PositionNotHeldException(POSITION_ID));

        mockMvc.perform(get("/api/fna/portfolio/{id}/price-history", POSITION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("POSITION_NOT_HELD"));
    }

    @Test
    void getPriceHistoryReturns400ForAnUnknownPeriod() throws Exception {
        mockMvc.perform(get("/api/fna/portfolio/{id}/price-history", POSITION_ID).param("period", "FOREVER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getWishlistReturnsWishlistItemList() throws Exception {
        when(fnaService.getWishlistItems()).thenReturn(List.of(WishlistItemDtoTestBuilder.aDefaultWishlistItemDto()));

        mockMvc.perform(get("/api/fna/wishlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value(WishlistItemDtoTestBuilder.DEFAULT_TICKER))
                .andExpect(jsonPath("$[0].shareCount").doesNotExist());
    }

    @Test
    void addWishlistItemReturnsTheItem() throws Exception {
        when(fnaService.addWishlistItem("ASML.AS")).thenReturn(WishlistItemDtoTestBuilder.aDefaultWishlistItemDto());

        mockMvc.perform(put("/api/fna/wishlist/ASML.AS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value(WishlistItemDtoTestBuilder.DEFAULT_TICKER))
                .andExpect(jsonPath("$.lastPrice").value(WishlistItemDtoTestBuilder.DEFAULT_LAST_PRICE.doubleValue()));
    }

    @Test
    void addWishlistItemReturns409WhenTheTickerIsAlreadyHeld() throws Exception {
        when(fnaService.addWishlistItem("KBC.BR")).thenThrow(new TickerAlreadyHeldException("KBC.BR"));

        mockMvc.perform(put("/api/fna/wishlist/KBC.BR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("TICKER_ALREADY_HELD"));
    }

    @Test
    void addWishlistItemReturns400ForABlankTicker() throws Exception {
        when(fnaService.addWishlistItem(" ")).thenThrow(new IllegalArgumentException("ticker is required"));

        mockMvc.perform(put("/api/fna/wishlist/{ticker}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_POSITION"));
    }

    @Test
    void removeWishlistItemReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/fna/wishlist/{id}", POSITION_ID))
                .andExpect(status().isNoContent());

        verify(fnaService).removeWishlistItem(POSITION_ID);
    }

    @Test
    void getLatestBriefingReturnsBriefingWhenPresent() throws Exception {
        BriefingDto briefing = new BriefingDto(POSITION_ID, Instant.parse("2026-03-15T06:30:00Z"), "content", "claude-sonnet-5");
        when(fnaService.getLatestBriefing()).thenReturn(Optional.of(briefing));

        mockMvc.perform(get("/api/fna/briefing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelUsed").value("claude-sonnet-5"));
    }

    @Test
    void getLatestBriefingReturnsNoContentWhenAbsent() throws Exception {
        when(fnaService.getLatestBriefing()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/fna/briefing"))
                .andExpect(status().isNoContent());
    }

    @Test
    void triggerBriefingReturnsBriefingOnSuccess() throws Exception {
        BriefingDto briefing = new BriefingDto(POSITION_ID, Instant.parse("2026-03-15T06:30:00Z"), "content", "claude-sonnet-5");
        when(fnaService.triggerBriefing()).thenReturn(briefing);

        mockMvc.perform(post("/api/fna/briefing/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelUsed").value("claude-sonnet-5"));
    }

    @Test
    void triggerBriefingReturns503WhenAiFails() throws Exception {
        when(fnaService.triggerBriefing()).thenThrow(new BriefingGenerationException(ProviderFailureReason.UNREACHABLE));

        mockMvc.perform(post("/api/fna/briefing/trigger"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("UNREACHABLE"));
    }
}
