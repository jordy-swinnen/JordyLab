package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.service.FnaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FnaController.class)
class FnaControllerTest {

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
    void upsertPositionReturnsPosition() throws Exception {
        when(fnaService.upsertPosition("KBC.BR", new BigDecimal("10")))
                .thenReturn(PortfolioPositionDtoTestBuilder.aDefaultPortfolioPositionDto());

        mockMvc.perform(put("/api/fna/portfolio/KBC.BR").param("shares", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value(PortfolioPositionDtoTestBuilder.DEFAULT_TICKER));
    }
}
