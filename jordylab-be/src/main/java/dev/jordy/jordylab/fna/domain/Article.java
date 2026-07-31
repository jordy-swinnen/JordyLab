package dev.jordy.jordylab.fna.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(schema = "finance", name = "article")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends BaseEntity<Article> {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_id")
    private Feed feed;

    private String title;

    private String url;

    private String contentHash;

    private String fullContent;

    private Instant publishedAt;

    private Instant scrapedAt;

    public static class ArticleBuilder {
        public Article build() {
            Preconditions.checkArgument(feed != null, "feed is required");
            Preconditions.checkArgument(StringUtils.hasText(title), "title is required");
            Preconditions.checkArgument(StringUtils.hasText(url), "url is required");
            if (id == null) id = UUID.randomUUID();

            return new Article(id, feed, title, url, contentHash, fullContent, publishedAt, scrapedAt);
        }
    }

    public static String contentHash(String url, String title) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((url + "|" + title).getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
