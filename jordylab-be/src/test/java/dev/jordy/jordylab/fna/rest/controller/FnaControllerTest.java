package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.BriefingDto;
import dev.jordy.jordylab.fna.service.BriefingGenerationException;
import dev.jordy.jordylab.fna.service.FnaService;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

@WebMvcTest(FnaController.class)
class FnaControllerTest {

    private static final UUID POSITION_ID = UUID.fromString("77777777-0000-0000-0000-000000000007");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FnaService fnaService;

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
