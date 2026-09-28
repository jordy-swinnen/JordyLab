package dev.jordy.jordylab.fna.service;

import dev.jordy.jordylab.fna.domain.Article;
import dev.jordy.jordylab.fna.domain.Feed;
import dev.jordy.jordylab.fna.domain.repository.ArticleRepository;
import dev.jordy.jordylab.fna.domain.repository.FeedRepository;
import dev.jordy.jordylab.fna.util.ArticleScraper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualArticleSubmissionServiceTest {

    private static final String URL = "https://store.steampowered.com/app/12345";
    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private ArticleScraper articleScraper;

    private ManualArticleSubmissionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ManualArticleSubmissionService(articleRepository, feedRepository, articleScraper, clock);
    }

    @Test
    void queuesTheUrlUnderTheManualSubmissionsFeed() {
        Feed manualFeed = Feed.builder().name("Manual Submissions").url("manual://submissions").enabled(false)
                .build();
        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        when(articleRepository.existsByUrl(URL)).thenReturn(false);
        when(feedRepository.findByUrl("manual://submissions")).thenReturn(Optional.of(manualFeed));
        when(articleScraper.scrapeTitle(URL)).thenReturn("A great co-op game");
        when(articleScraper.scrape(URL)).thenReturn("Full page text");
        when(articleRepository.save(articleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Article result = service.queueArticle(URL);

        Article saved = articleCaptor.getValue();
        assertSoftly(softly -> {
            softly.assertThat(saved.getFeed()).isEqualTo(manualFeed);
            softly.assertThat(saved.getTitle()).isEqualTo("A great co-op game");
            softly.assertThat(saved.getUrl()).isEqualTo(URL);
            softly.assertThat(saved.getFullContent()).isEqualTo("Full page text");
            softly.assertThat(saved.getPublishedAt()).isEqualTo(NOW);
            softly.assertThat(result).isSameAs(saved);
        });
    }

    @Test
    void rejectsAUrlThatIsAlreadyQueued() {
        when(articleRepository.existsByUrl(URL)).thenReturn(true);

        assertThatThrownBy(() -> service.queueArticle(URL))
                .isInstanceOf(ArticleAlreadyQueuedException.class);
    }

    @Test
    void failsFastWhenTheManualSubmissionsFeedIsMissing() {
        when(articleRepository.existsByUrl(URL)).thenReturn(false);
        when(feedRepository.findByUrl("manual://submissions")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.queueArticle(URL))
                .isInstanceOf(IllegalStateException.class);
    }
}
