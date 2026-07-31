package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.Article;
import dev.jordy.jordylab.fna.domain.Feed;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.FeedRepository;
import dev.jordy.jordylab.fna.rest.client.RssFeedIngester;
import dev.jordy.jordylab.fna.util.ArticleScraper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeedIngestionService {

    private final FeedRepository feedRepository;
    private final ArticleRepository articleRepository;
    private final RssFeedIngester rssFeedIngester;
    private final ArticleScraper articleScraper;

    @Scheduled(cron = "0 0 6,12,18 * * *")
    public int ingestAll() {
        int savedCount = 0;
        List<Feed> feeds = feedRepository.findAllByEnabledTrue();

        for (Feed feed : feeds) {
            for (com.rometools.rome.feed.synd.SyndEntry entry : rssFeedIngester.fetchEntries(feed)) {
                String link = entry.getLink();
                if (link == null || link.isBlank() || articleRepository.existsByUrl(link)) {
                    continue;
                }

                String fullContent = articleScraper.scrape(link);

                Article article = Article.builder()
                        .feed(feed)
                        .title(entry.getTitle())
                        .url(link)
                        .contentHash(Article.contentHash(link, entry.getTitle()))
                        .fullContent(fullContent)
                        .publishedAt(entry.getPublishedDate() != null
                                ? entry.getPublishedDate().toInstant()
                                : null)
                        .scrapedAt(Instant.now())
                        .build();

                articleRepository.save(article);
                savedCount++;
            }
        }

        log.info("Ingested {} new articles from {} feeds", savedCount, feeds.size());

        return savedCount;
    }

    @PostConstruct
    void initialIngest() {
        ingestAll();
    }
}
