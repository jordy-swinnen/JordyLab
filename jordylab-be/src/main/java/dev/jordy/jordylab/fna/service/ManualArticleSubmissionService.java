package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.Article;
import dev.jordy.jordylab.fna.domain.Feed;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.FeedRepository;
import dev.jordy.jordylab.fna.util.ArticleScraper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Queues a shared link as an article candidate for the next briefing (spec 007 FR-017,
 * research D6) — the Android app's "Save to FNA" share destination calls this directly; there is
 * no {@code mobile}-module involvement, since queuing an article has nothing mobile-specific
 * about it (research D6). Every manually-submitted article attaches to a single, disabled
 * "Manual Submissions" feed (seeded by migration) so {@link Article}'s existing {@code feed}
 * requirement needs no schema change; {@link BriefingGeneratorService} selects candidates by
 * {@code publishedAt}, not {@code feed.enabled}, so these are picked up like any other article.
 */
@Service
@RequiredArgsConstructor
public class ManualArticleSubmissionService {

    private static final String MANUAL_SUBMISSIONS_FEED_URL = "manual://submissions";

    private final ArticleRepository articleRepository;
    private final FeedRepository feedRepository;
    private final ArticleScraper articleScraper;
    private final Clock clock;

    public Article queueArticle(String url) {
        if (articleRepository.existsByUrl(url)) {
            throw new ArticleAlreadyQueuedException(url);
        }
        Feed manualSubmissionsFeed = feedRepository.findByUrl(MANUAL_SUBMISSIONS_FEED_URL)
                .orElseThrow(() -> new IllegalStateException(
                        "Manual Submissions feed is missing — check the V20260928005 migration ran"));

        String title = articleScraper.scrapeTitle(url);
        String fullContent = articleScraper.scrape(url);

        Article article = Article.builder()
                .feed(manualSubmissionsFeed)
                .title(title)
                .url(url)
                .contentHash(Article.contentHash(url, title))
                .fullContent(fullContent)
                .publishedAt(clock.instant())
                .scrapedAt(clock.instant())
                .build();

        return articleRepository.save(article);
    }
}
