package dev.jordy.jordylab.fna.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import dev.jordy.jordylab.fna.domain.Article;
import dev.jordy.jordylab.fna.domain.FeedTestBuilder;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.FeedRepository;
import dev.jordy.jordylab.fna.rest.client.RssFeedIngester;
import dev.jordy.jordylab.fna.util.ArticleScraper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedIngestionServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private RssFeedIngester rssFeedIngester;

    @Mock
    private ArticleScraper articleScraper;

    @InjectMocks
    private FeedIngestionService feedIngestionService;

    @Test
    void skipsArticleWhenUrlAlreadyExists() {
        SyndEntry syndEntry = new SyndEntryImpl();
        syndEntry.setLink("http://example.com/1");
        syndEntry.setTitle("Existing Article");

        when(feedRepository.findAllByEnabledTrue()).thenReturn(List.of(FeedTestBuilder.aDefaultFeed()));
        when(rssFeedIngester.fetchEntries(FeedTestBuilder.aDefaultFeed())).thenReturn(List.of(syndEntry));
        when(articleRepository.existsByUrl("http://example.com/1")).thenReturn(true);

        feedIngestionService.ingestAll();

        verify(articleRepository).existsByUrl("http://example.com/1");
        verifyNoMoreInteractions(articleRepository);
    }

    @Test
    void savesNewArticleWithScrapedContent() {
        SyndEntry syndEntry = new SyndEntryImpl();
        syndEntry.setLink("http://example.com/1");
        syndEntry.setTitle("New Article");
        syndEntry.setPublishedDate(new Date());

        when(feedRepository.findAllByEnabledTrue()).thenReturn(List.of(FeedTestBuilder.aDefaultFeed()));
        when(rssFeedIngester.fetchEntries(FeedTestBuilder.aDefaultFeed())).thenReturn(List.of(syndEntry));
        when(articleRepository.existsByUrl("http://example.com/1")).thenReturn(false);
        when(articleScraper.scrape("http://example.com/1")).thenReturn("scraped content here");

        feedIngestionService.ingestAll();

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());

        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().getFullContent()).isEqualTo("scraped content here");
            softly.assertThat(captor.getValue().getUrl()).isEqualTo("http://example.com/1");
        });
    }
}
