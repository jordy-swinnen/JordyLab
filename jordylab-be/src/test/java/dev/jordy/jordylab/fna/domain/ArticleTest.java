package dev.jordy.jordylab.fna.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleTest {

    @Test
    void buildArticle() {
        Feed feed = Feed.builder().name("HLN Economie").url("https://hln.be/rss").enabled(true).build();
        Instant publishedAt = Instant.parse("2026-03-15T10:00:00Z");

        Article article = Article.builder()
                .feed(feed)
                .title("BEL20 hits record high")
                .url("https://hln.be/article-1")
                .publishedAt(publishedAt)
                .build();

        assertThat(article.getId()).isNotNull();
        assertThat(article.getFeed()).isEqualTo(feed);
        assertThat(article.getTitle()).isEqualTo("BEL20 hits record high");
        assertThat(article.getUrl()).isEqualTo("https://hln.be/article-1");
        assertThat(article.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void buildWithoutFeed() {
        assertThatThrownBy(() -> Article.builder()
                .title("Title")
                .url("https://hln.be/article-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutTitle() {
        Feed feed = Feed.builder().name("HLN Economie").url("https://hln.be/rss").enabled(true).build();
        assertThatThrownBy(() -> Article.builder()
                .feed(feed)
                .url("https://hln.be/article-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankTitle() {
        Feed feed = Feed.builder().name("HLN Economie").url("https://hln.be/rss").enabled(true).build();
        assertThatThrownBy(() -> Article.builder()
                .feed(feed)
                .title("  ")
                .url("https://hln.be/article-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutUrl() {
        Feed feed = Feed.builder().name("HLN Economie").url("https://hln.be/rss").enabled(true).build();
        assertThatThrownBy(() -> Article.builder()
                .feed(feed)
                .title("BEL20 hits record high")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankUrl() {
        Feed feed = Feed.builder().name("HLN Economie").url("https://hln.be/rss").enabled(true).build();
        assertThatThrownBy(() -> Article.builder()
                .feed(feed)
                .title("BEL20 hits record high")
                .url(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contentHashProducesConsistentSha256Hex() {
        String hash1 = Article.contentHash("http://example.com/1", "Title");
        String hash2 = Article.contentHash("http://example.com/1", "Title");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    @Test
    void contentHashProducesDifferentHashesForDifferentInputs() {
        String hash1 = Article.contentHash("http://example.com/1", "Title A");
        String hash2 = Article.contentHash("http://example.com/2", "Title B");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(Article.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
