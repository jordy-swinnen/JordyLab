package dev.jordy.jordylab.fna.domain;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class ArticleTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");
    public static final String DEFAULT_TITLE = "BEL20 hits record high";
    public static final String DEFAULT_URL = "https://hln.be/article-1";
    public static final Instant DEFAULT_PUBLISHED_AT = Instant.parse("2026-03-15T10:00:00Z");

    public static Article aDefaultArticle() {
        return anArticle().build();
    }

    public static Article.ArticleBuilder anArticle() {
        return Article.builder()
                .id(DEFAULT_ID)
                .feed(FeedTestBuilder.aDefaultFeed())
                .title(DEFAULT_TITLE)
                .url(DEFAULT_URL)
                .publishedAt(DEFAULT_PUBLISHED_AT);
    }
}
