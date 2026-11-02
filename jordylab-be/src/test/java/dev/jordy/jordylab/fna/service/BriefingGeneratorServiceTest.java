package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.ArticleTestBuilder;
import dev.jordy.jordylab.fna.domain.Briefing;
import dev.jordy.jordylab.fna.domain.PortfolioPositionTestBuilder;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.shared.ai.AiCallResultTestBuilder;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ProviderFailureReason;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import dev.jordy.jordylab.shared.rest.client.NtfyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BriefingGeneratorServiceTest {

    private static final String SYSTEM_PROMPT_RESOURCE_PATH = "prompts/fna/briefing-system.st";
    private static final String SYSTEM_PROMPT =
            new SystemPromptTemplate(new ClassPathResource(SYSTEM_PROMPT_RESOURCE_PATH)).render();
    private static final String NO_ARTICLES_FALLBACK = "No articles available yet.";
    private static final String NO_POSITIONS_FALLBACK = "No portfolio positions configured. Provide a general European market briefing.";
    private static final String NO_WISHLIST_FALLBACK = "No wishlist stocks configured.";
    private static final String WISHLIST_HEADING = "My wishlist (stocks I do not own yet, considering):";
    private static final String BELGIAN_INVESTOR_PERSONA = "Belgian retail investor";

    @Mock
    private ResilientAiService aiService;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private PortfolioPositionRepository positionRepository;

    @Mock
    private BriefingRepository briefingRepository;

    @Mock
    private NtfyClient ntfyClient;

    private BriefingGeneratorService briefingGeneratorService;

    @BeforeEach
    void setUp() {
        briefingGeneratorService = new BriefingGeneratorService(
                aiService,
                articleRepository,
                positionRepository,
                briefingRepository,
                ntfyClient
        );
        briefingGeneratorService.systemPromptResource = new ClassPathResource(SYSTEM_PROMPT_RESOURCE_PATH);
        briefingGeneratorService.init();
    }

    @Test
    void generatesBriefingWithArticlesAndPositions() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc())
                .thenReturn(List.of(ArticleTestBuilder.anArticle()
                        .fullContent("The BEL20 index closed at a record high today")
                        .build()));
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aDefaultPortfolioPosition()));
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(AiFeature.FNA_BRIEFING), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aDefaultSuccessResult());

        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<String> notificationBodyCaptor = ArgumentCaptor.forClass(String.class);

        Briefing result = briefingGeneratorService.generateBriefing();

        verify(ntfyClient).publish(eq("Your monthly briefing is ready"), notificationBodyCaptor.capture(),
                eq("/fna/briefing"));
        assertSoftly(softly -> {
            softly.assertThat(result.getContent()).isEqualTo(AiCallResultTestBuilder.DEFAULT_CONTENT);
            softly.assertThat(result.getModelUsed()).isEqualTo(AiCallResultTestBuilder.DEFAULT_MODEL);
            softly.assertThat(result.getGeneratedAt()).isNotNull();
            softly.assertThat(userPromptCaptor.getValue()).contains(ArticleTestBuilder.DEFAULT_TITLE);
            softly.assertThat(briefingCaptor.getValue()).isSameAs(result);
            // spec 018 US3: the admin push names the generation time and opens the briefing screen.
            softly.assertThat(notificationBodyCaptor.getValue()).contains(result.getGeneratedAt().toString());
        });
    }

    @Test
    void includesWishlistStocksAsTheirOwnGroupWithPriceAndTimestamp() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aDefaultPortfolioPosition()));
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aDefaultWishlistPosition()));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(AiFeature.FNA_BRIEFING), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aDefaultSuccessResult());
        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        briefingGeneratorService.generateBriefing();

        String userPrompt = userPromptCaptor.getValue();
        assertSoftly(softly -> {
            softly.assertThat(userPrompt).contains(WISHLIST_HEADING);
            softly.assertThat(userPrompt).contains(PortfolioPositionTestBuilder.DEFAULT_WISHLIST_TICKER);
            softly.assertThat(userPrompt).contains("€" + PortfolioPositionTestBuilder.DEFAULT_LAST_PRICE);
            softly.assertThat(userPrompt).contains(PortfolioPositionTestBuilder.DEFAULT_LAST_PRICE_FETCHED_AT.toString());
            softly.assertThat(userPrompt).doesNotContain(NO_WISHLIST_FALLBACK);
            softly.assertThat(userPrompt).contains("My portfolio:");
            softly.assertThat(userPrompt).contains(PortfolioPositionTestBuilder.DEFAULT_TICKER);
        });
    }

    @Test
    void statesThatNoWishlistStocksAreConfiguredWhenTheWishlistIsEmpty() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(AiFeature.FNA_BRIEFING), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aSuccessResult("General briefing"));
        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        briefingGeneratorService.generateBriefing();

        assertSoftly(softly -> {
            softly.assertThat(userPromptCaptor.getValue()).contains(NO_WISHLIST_FALLBACK);
            softly.assertThat(userPromptCaptor.getValue()).contains(NO_POSITIONS_FALLBACK);
            softly.assertThat(briefingCaptor.getValue().getContent()).isEqualTo("General briefing");
        });
    }

    @Test
    void notesAWishlistStockWhosePriceIsUnavailable() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aWishlistPosition()
                        .lastPrice(null)
                        .lastPriceFetchedAt(null)
                        .build()));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(AiFeature.FNA_BRIEFING), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aDefaultSuccessResult());
        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        briefingGeneratorService.generateBriefing();

        assertSoftly(softly -> {
            softly.assertThat(userPromptCaptor.getValue()).contains(PortfolioPositionTestBuilder.DEFAULT_WISHLIST_TICKER);
            softly.assertThat(userPromptCaptor.getValue()).contains("price unavailable");
            softly.assertThat(briefingCaptor.getValue().getContent()).isNotNull();
        });
    }

    @Test
    void asksForAWishlistEvaluationAndADiscoveryTickerInTheWatchlistSection() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc())
                .thenReturn(List.of(PortfolioPositionTestBuilder.aDefaultWishlistPosition()));

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(AiFeature.FNA_BRIEFING), systemPromptCaptor.capture(), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aDefaultSuccessResult());
        ArgumentCaptor<Briefing> briefingCaptor = ArgumentCaptor.forClass(Briefing.class);
        when(briefingRepository.save(briefingCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        briefingGeneratorService.generateBriefing();

        assertSoftly(softly -> {
            softly.assertThat(systemPromptCaptor.getValue()).contains("## Portfolio Impact");
            softly.assertThat(systemPromptCaptor.getValue()).contains("## European Market Summary");
            softly.assertThat(systemPromptCaptor.getValue()).contains("## Watchlist Suggestion");
            softly.assertThat(systemPromptCaptor.getValue()).contains("wishlist");
            softly.assertThat(userPromptCaptor.getValue()).contains("My wishlist");
            softly.assertThat(briefingCaptor.getValue().getContent()).isNotNull();
        });
    }

    @Test
    void handlesEmptyArticlesAndPositionsWithFallbackText() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(AiFeature.FNA_BRIEFING), systemPromptCaptor.capture(), userPromptCaptor.capture()))
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
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(AiFeature.FNA_BRIEFING), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aFailureResult(ProviderFailureReason.UNREACHABLE));

        assertThatThrownBy(() -> briefingGeneratorService.generateBriefing())
                .isInstanceOf(BriefingGenerationException.class)
                .extracting(exception -> ((BriefingGenerationException) exception).getFailureReason())
                .isEqualTo(ProviderFailureReason.UNREACHABLE);

        assertThat(userPromptCaptor.getValue()).contains(NO_ARTICLES_FALLBACK);
        verifyNoInteractions(briefingRepository);
        verifyNoInteractions(ntfyClient);
    }

    @Test
    void doesNotRetryWithinSameTickOnAiFailure() {
        when(articleRepository.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNotNullOrderByTickerAsc()).thenReturn(List.of());
        when(positionRepository.findAllByShareCountIsNullOrderByTickerAsc()).thenReturn(List.of());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.call(eq(AiFeature.FNA_BRIEFING), eq(SYSTEM_PROMPT), userPromptCaptor.capture()))
                .thenReturn(AiCallResultTestBuilder.aFailureResult(ProviderFailureReason.TIMEOUT));

        assertThatThrownBy(() -> briefingGeneratorService.generateBriefing())
                .isInstanceOf(BriefingGenerationException.class);

        assertThat(userPromptCaptor.getAllValues()).hasSize(1);
    }

    @Test
    void isScheduledOnTheFirstOfEveryMonthAtHalfPastSix() throws NoSuchMethodException {
        Scheduled scheduled = BriefingGeneratorService.class.getMethod("generateBriefing")
                .getAnnotation(Scheduled.class);
        String defaultCron = scheduled.cron().replaceFirst("^\\$\\{[^:]+:(.*)}$", "$1");
        ZonedDateTime afterFirstRun = ZonedDateTime.of(2026, 10, 1, 7, 0, 0, 0, ZoneId.of("Europe/Brussels"));

        ZonedDateTime nextRun = CronExpression.parse(defaultCron).next(afterFirstRun);

        assertThat(nextRun).isEqualTo(ZonedDateTime.of(2026, 11, 1, 6, 30, 0, 0, ZoneId.of("Europe/Brussels")));
    }
}
