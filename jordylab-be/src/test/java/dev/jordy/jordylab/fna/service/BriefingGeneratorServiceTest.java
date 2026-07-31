package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.ArticleTestBuilder;
import dev.jordy.jordylab.fna.domain.Briefing;
import dev.jordy.jordylab.fna.domain.PortfolioPositionTestBuilder;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BriefingGeneratorServiceTest {

    @Mock
    private ResilientAiService aiService;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private PortfolioPositionRepository positionRepository;

    @Mock
    private BriefingRepository briefingRepository;

    @InjectMocks
    private BriefingGeneratorService briefingGeneratorService;

    @Test
    void generatesBriefingWithArticlesAndPositions() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc())
                .thenReturn(List.of(ArticleTestBuilder.anArticle()
                        .fullContent("The BEL20 index closed at a record high today")
                        .build()));
        when(positionRepository.findAllByOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aDefaultPortfolioPosition()));
        when(aiService.call(
                eq("You are a financial analyst assistant for a Belgian retail investor using Bolero (KBC) as their broker.\n"
                        + "Be concise, factual, and actionable. Focus on European and Belgian markets (BEL20, Euronext Brussels).\n"
                        + "Structure your response with exactly these three sections:\n"
                        + "## Portfolio Impact\n"
                        + "## European Market Summary\n"
                        + "## Watchlist Suggestion"),
                contains(ArticleTestBuilder.DEFAULT_TITLE)))
                .thenReturn("AI briefing content");
        when(aiService.getLastUsedModel()).thenReturn("claude-sonnet-4-20250514");

        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Briefing result = briefingGeneratorService.generateBriefing();

        assertSoftly(softly -> {
            softly.assertThat(result.getContent()).isEqualTo("AI briefing content");
            softly.assertThat(result.getModelUsed()).isEqualTo("claude-sonnet-4-20250514");
            softly.assertThat(result.getGeneratedAt()).isNotNull();
        });
    }

    @Test
    void handlesEmptyArticlesWithFallbackText() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(systemCaptor.capture(), userCaptor.capture())).thenReturn("General briefing");
        when(aiService.getLastUsedModel()).thenReturn("claude-sonnet-4-20250514");

        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        briefingGeneratorService.generateBriefing();

        assertSoftly(softly -> {
            softly.assertThat(userCaptor.getValue()).contains("No articles available yet.");
            softly.assertThat(userCaptor.getValue()).contains("No portfolio positions configured.");
            softly.assertThat(systemCaptor.getValue()).contains("Belgian retail investor");
            softly.assertThat(briefingCaptor.getValue().getContent()).isEqualTo("General briefing");
        });
    }
}
