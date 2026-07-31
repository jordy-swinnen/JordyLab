package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.Article;
import dev.jordy.jordylab.fna.domain.Briefing;
import dev.jordy.jordylab.fna.domain.PortfolioPosition;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.BriefingRepository;
import dev.jordy.jordylab.fna.domain.repository.PortfolioPositionRepository;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingGeneratorService {

    private static final String SYSTEM_PROMPT = """
            You are a financial analyst assistant for a Belgian retail investor using Bolero (KBC) as their broker.
            Be concise, factual, and actionable. Focus on European and Belgian markets (BEL20, Euronext Brussels).
            Structure your response with exactly these three sections:
            ## Portfolio Impact
            ## European Market Summary
            ## Watchlist Suggestion""";

    private static final int MAX_CONTENT_PREVIEW = 500;

    private final ResilientAiService aiService;
    private final ArticleRepository articleRepository;
    private final PortfolioPositionRepository positionRepository;
    private final BriefingRepository briefingRepository;

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

        String content = aiService.call(SYSTEM_PROMPT, userPrompt);

        return briefingRepository.save(
                Briefing.builder()
                        .generatedAt(Instant.now())
                        .content(content)
                        .modelUsed(aiService.getLastUsedModel())
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
