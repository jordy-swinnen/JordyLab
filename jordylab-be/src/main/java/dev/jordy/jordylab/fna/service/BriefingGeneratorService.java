package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.Article;
import dev.jordy.jordylab.fna.domain.Briefing;
import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BriefingGeneratorService {

    static final String MODULE_NAME = "fna";

    private static final int MAX_CONTENT_PREVIEW = 500;

    private final ResilientAiService aiService;
    private final ArticleRepository articleRepository;
    private final PortfolioPositionRepository positionRepository;
    private final BriefingRepository briefingRepository;
    private final String systemPrompt;

    public BriefingGeneratorService(
            ResilientAiService aiService,
            ArticleRepository articleRepository,
            PortfolioPositionRepository positionRepository,
            BriefingRepository briefingRepository,
            @Value("classpath:prompts/fna/briefing-system.st") Resource systemPromptResource
    ) {
        this.aiService = aiService;
        this.articleRepository = articleRepository;
        this.positionRepository = positionRepository;
        this.briefingRepository = briefingRepository;
        this.systemPrompt = new SystemPromptTemplate(systemPromptResource).render();
    }

    @Scheduled(cron = "0 30 6 * * *")
    public Briefing generateBriefing() {
        List<Article> articles = articleRepository.findTop50ByOrderByPublishedAtDesc();
        List<PortfolioPosition> positions = positionRepository.findAllByOrderByTickerAsc();

        String articleContext = buildArticleContext(articles);
        String portfolioContext = buildPortfolioContext(positions);

        String userPrompt = "Today's financial news:\n" + articleContext
                + "\n\nMy portfolio:\n" + portfolioContext
                + "\n\nAnalyse how today's news affects my portfolio positions, summarise the broader European market themes, "
                + "and suggest one ticker I don't currently hold that looks interesting based on today's news.";

        AiCallResult result = aiService.call(MODULE_NAME, systemPrompt, userPrompt);

        if (!result.success()) {
            log.error("Briefing generation failed: {}", result.failureReason());

            throw new BriefingGenerationException(result.failureReason());
        }

        return briefingRepository.save(
                Briefing.builder()
                        .generatedAt(Instant.now())
                        .content(result.content())
                        .modelUsed(result.model())
                        .build()
        );
    }

    private String buildArticleContext(List<Article> articles) {
        if (articles.isEmpty()) {
            return "No articles available yet.";
        }

        return articles.stream()
                .map(article -> {
                    String feedName = article.getFeed() != null ? article.getFeed().getName() : "feedName unavailable";
                    String preview = article.getFullContent() != null && article.getFullContent().length() > MAX_CONTENT_PREVIEW
                            ? article.getFullContent().substring(0, MAX_CONTENT_PREVIEW)
                            : article.getFullContent();

                    return feedName + " — " + article.getTitle() + "\n" + (preview != null ? preview : "") + "\n";
                })
                .collect(Collectors.joining("\n---\n"));
    }

    private String buildPortfolioContext(List<PortfolioPosition> positions) {
        if (positions.isEmpty()) {
            return "No portfolio positions configured. Provide a general European market briefing.";
        }

        return positions.stream()
                .map(position -> {
                    String fetchedAt = position.getLastPriceFetchedAt() != null
                            ? position.getLastPriceFetchedAt().toString()
                            : "unknown";

                    return position.getTicker() + ": " + position.getShareCount()
                            + " shares, last price €" + position.getLastPrice()
                            + " (as of " + fetchedAt + ")";
                })
                .collect(Collectors.joining("\n"));
    }
}
