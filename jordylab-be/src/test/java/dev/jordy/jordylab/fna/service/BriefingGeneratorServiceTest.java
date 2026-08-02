package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.ArticleTestBuilder;
import dev.jordy.jordylab.fna.domain.Briefing;
import dev.jordy.jordylab.fna.domain.PortfolioPositionTestBuilder;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.shared.ai.AiCallResultTestBuilder;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BriefingGeneratorServiceTest {

    private static final String SYSTEM_PROMPT_RESOURCE_PATH = "prompts/fna/briefing-system.st";
    private static final String SYSTEM_PROMPT =
            new SystemPromptTemplate(new ClassPathResource(SYSTEM_PROMPT_RESOURCE_PATH)).render();
    private static final String NO_ARTICLES_FALLBACK = "No articles available yet.";
    private static final String NO_POSITIONS_FALLBACK = "No portfolio positions configured. Provide a general European market briefing.";
    private static final String BELGIAN_INVESTOR_PERSONA = "Belgian retail investor";

    @Mock
    private ResilientAiService aiService;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private PortfolioPositionRepository positionRepository;

    @Mock
    private BriefingRepository briefingRepository;

    private BriefingGeneratorService briefingGeneratorService;

    @BeforeEach
    void setUp() {
        briefingGeneratorService = new BriefingGeneratorService(
                aiService,
                articleRepository,
                positionRepository,
                briefingRepository,
                new ClassPathResource(SYSTEM_PROMPT_RESOURCE_PATH)
        );
    }

    @Test
    void generatesBriefingWithArticlesAndPositions() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc())
                .thenReturn(List.of(ArticleTestBuilder.anArticle()
                        .fullContent("The BEL20 index closed at a record high today")
                        .build()));
        when(positionRepository.findAllByOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aDefaultPortfolioPosition()));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(BriefingGeneratorService.MODULE_NAME), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aDefaultSuccessResult());

        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Briefing result = briefingGeneratorService.generateBriefing();

        assertSoftly(softly -> {
            softly.assertThat(result.getContent()).isEqualTo(AiCallResultTestBuilder.DEFAULT_CONTENT);
            softly.assertThat(result.getModelUsed()).isEqualTo(AiCallResultTestBuilder.DEFAULT_MODEL);
            softly.assertThat(result.getGeneratedAt()).isNotNull();
            softly.assertThat(userPromptCaptor.getValue()).contains(ArticleTestBuilder.DEFAULT_TITLE);
            softly.assertThat(briefingCaptor.getValue()).isSameAs(result);
        });
    }

    @Test
    void handlesEmptyArticlesAndPositionsWithFallbackText() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(BriefingGeneratorService.MODULE_NAME), systemPromptCaptor.capture(), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aSuccessResult("General briefing"));

        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        briefingGeneratorService.generateBriefing();

        assertSoftly(softly -> {
            softly.assertThat(systemPromptCaptor.getValue()).contains(BELGIAN_INVESTOR_PERSONA);
            softly.assertThat(userPromptCaptor.getValue()).contains(NO_ARTICLES_FALLBACK);
            softly.assertThat(userPromptCaptor.getValue()).contains(NO_POSITIONS_FALLBACK);
            softly.assertThat(briefingCaptor.getValue().getContent()).isEqualTo("General briefing");
        });
    }

    @Test
    void throwsAndDoesNotSaveBriefingOnAiFailure() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(BriefingGeneratorService.MODULE_NAME), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aFailureResult(ProviderFailureReason.UNREACHABLE));

        assertThatThrownBy(() -> briefingGeneratorService.generateBriefing())
                .isInstanceOf(BriefingGenerationException.class)
                .extracting(exception -> ((BriefingGenerationException) exception).getFailureReason())
                .isEqualTo(ProviderFailureReason.UNREACHABLE);

        assertThat(userPromptCaptor.getValue()).contains(NO_ARTICLES_FALLBACK);
        verifyNoInteractions(briefingRepository);
    }

    @Test
    void doesNotRetryWithinSameTickOnAiFailure() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(BriefingGeneratorService.MODULE_NAME), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aFailureResult(ProviderFailureReason.TIMEOUT));

        assertThatThrownBy(() -> briefingGeneratorService.generateBriefing())
                .isInstanceOf(BriefingGenerationException.class);

        assertThat(userPromptCaptor.getAllValues()).hasSize(1);
    }
}
