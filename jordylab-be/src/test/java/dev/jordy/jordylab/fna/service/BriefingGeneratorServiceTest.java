package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.ArticleTestBuilder;
import dev.jordy.jordylab.fna.domain.Briefing;
import dev.jordy.jordylab.fna.domain.PortfolioPositionTestBuilder;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BriefingGeneratorServiceTest {

    private static final String SYSTEM_PROMPT = """
            You are a financial analyst assistant for a Belgian retail investor using Bolero (KBC) as their broker.
            Be concise, factual, and actionable. Focus on European and Belgian markets (BEL20, Euronext Brussels).
            Structure your response with exactly these three sections:
            ## Portfolio Impact
            ## European Market Summary
            ## Watchlist Suggestion""";

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
                eq("fna"),
                eq(SYSTEM_PROMPT),
                contains(ArticleTestBuilder.DEFAULT_TITLE)))
                .thenReturn(AiCallResult.success("fna", "anthropic", "claude-sonnet-4-20250514", "AI briefing content"));

        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Briefing result = briefingGeneratorService.generateBriefing();

        assertThat(result.getContent()).isEqualTo("AI briefing content");
        assertThat(result.getModelUsed()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(result.getGeneratedAt()).isNotNull();
    }

    @Test
    void handlesEmptyArticlesWithFallbackText() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByOrderByTickerAsc()).thenReturn(List.of());
        when(aiService.call(
                eq("fna"),
                ArgumentCaptor.forClass(String.class).capture(),
                ArgumentCaptor.forClass(String.class).capture()))
                .thenReturn(AiCallResult.success("fna", "anthropic", "claude-sonnet-4-20250514", "General briefing"));

        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        briefingGeneratorService.generateBriefing();

        assertThat(briefingCaptor.getValue().getContent()).isEqualTo("General briefing");
    }

    @Test
    void doesNotSaveBriefingOnAiFailure() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByOrderByTickerAsc()).thenReturn(List.of());
        when(aiService.call(eq("fna"), ArgumentCaptor.forClass(String.class).capture(), ArgumentCaptor.forClass(String.class).capture()))
                .thenReturn(AiCallResult.failure("fna", "anthropic", "claude-sonnet-4-20250514", ProviderFailureReason.UNREACHABLE));

        Briefing result = briefingGeneratorService.generateBriefing();

        assertThat(result).isNull();
        verify(briefingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotRetryOnAiFailure() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByOrderByTickerAsc()).thenReturn(List.of());
        when(aiService.call(eq("fna"), ArgumentCaptor.forClass(String.class).capture(), ArgumentCaptor.forClass(String.class).capture()))
                .thenReturn(AiCallResult.failure("fna", "anthropic", "claude-sonnet-4-20250514", ProviderFailureReason.TIMEOUT));

        briefingGeneratorService.generateBriefing();

        verify(aiService).call(eq("fna"), ArgumentCaptor.forClass(String.class).capture(), ArgumentCaptor.forClass(String.class).capture());
    }
}
