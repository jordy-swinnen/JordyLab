package dev.jordy.jordylab.fna.domain.repository;

import dev.jordy.jordylab.fna.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DataJpaTest
@Testcontainers
class FnaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private PortfolioPositionRepository positionRepository;

    @Autowired
    private BriefingRepository briefingRepository;

    @BeforeEach
    void cleanSeededData() {
        articleRepository.deleteAll();
        feedRepository.deleteAll();
        positionRepository.deleteAll();
        briefingRepository.deleteAll();
    }

    @Test
    void findAllByEnabledTrueReturnsOnlyEnabledFeeds() {
        Feed enabled = feedRepository.save(FeedTestBuilder.aFeed().enabled(true).build());
        feedRepository.save(FeedTestBuilder.aFeed()
                .id(UUID.fromString("11111111-0000-0000-0000-000000000002"))
                .url("https://disabled.com/feed")
                .enabled(false)
                .build());

        List<Feed> result = feedRepository.findAllByEnabledTrue();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(enabled.getId());
    }

    @Test
    void existsByUrlReturnsTrueForExistingArticle() {
        Feed savedFeed = feedRepository.save(FeedTestBuilder.aDefaultFeed());
        articleRepository.save(ArticleTestBuilder.anArticle().feed(savedFeed).build());

        assertSoftly(softly -> {
            softly.assertThat(articleRepository.existsByUrl(ArticleTestBuilder.DEFAULT_URL)).isTrue();
            softly.assertThat(articleRepository.existsByUrl("https://example.com/nonexistent")).isFalse();
        });
    }

    @Test
    void findTop50ByOrderByPublishedAtDescReturnsInDescendingOrder() {
        Feed savedFeed = feedRepository.save(FeedTestBuilder.aDefaultFeed());
        Instant base = Instant.parse("2026-03-15T10:00:00Z");

        articleRepository.save(ArticleTestBuilder.anArticle()
                .feed(savedFeed)
                .id(UUID.fromString("22222222-0000-0000-0000-000000000020"))
                .url("https://hln.be/oldest")
                .title("Oldest")
                .publishedAt(base.minus(2, ChronoUnit.DAYS))
                .build());
        articleRepository.save(ArticleTestBuilder.anArticle()
                .feed(savedFeed)
                .id(UUID.fromString("22222222-0000-0000-0000-000000000021"))
                .url("https://hln.be/middle")
                .title("Middle")
                .publishedAt(base.minus(1, ChronoUnit.DAYS))
                .build());
        articleRepository.save(ArticleTestBuilder.anArticle()
                .feed(savedFeed)
                .id(UUID.fromString("22222222-0000-0000-0000-000000000022"))
                .url("https://hln.be/newest")
                .title("Newest")
                .publishedAt(base)
                .build());

        List<Article> result = articleRepository.findTop50ByOrderByPublishedAtDesc();

        assertThat(result).extracting(Article::getTitle).containsExactly("Newest", "Middle", "Oldest");
    }

    @Test
    void findAllByOrderByTickerAscReturnsSortedPositions() {
        positionRepository.save(PortfolioPositionTestBuilder.aPortfolioPosition()
                .id(UUID.fromString("33333333-0000-0000-0000-000000000031"))
                .ticker("KBC.BR")
                .build());
        positionRepository.save(PortfolioPositionTestBuilder.aPortfolioPosition()
                .id(UUID.fromString("33333333-0000-0000-0000-000000000032"))
                .ticker("INGA.AS")
                .build());
        positionRepository.save(PortfolioPositionTestBuilder.aPortfolioPosition()
                .id(UUID.fromString("33333333-0000-0000-0000-000000000033"))
                .ticker("ACKB.BR")
                .build());

        List<PortfolioPosition> result = positionRepository.findAllByOrderByTickerAsc();

        assertThat(result).extracting(PortfolioPosition::getTicker).containsExactly("ACKB.BR", "INGA.AS", "KBC.BR");
    }

    @Test
    void findByTickerReturnsPresentForExistingAndEmptyForMissing() {
        positionRepository.save(PortfolioPositionTestBuilder.aDefaultPortfolioPosition());

        assertSoftly(softly -> {
            softly.assertThat(positionRepository.findByTicker(PortfolioPositionTestBuilder.DEFAULT_TICKER)).isPresent();
            softly.assertThat(positionRepository.findByTicker("NONE")).isEmpty();
        });
    }

    @Test
    void findTopByOrderByGeneratedAtDescReturnsMostRecentBriefing() {
        Instant base = Instant.parse("2026-03-15T06:30:00Z");
        briefingRepository.save(BriefingTestBuilder.aBriefing()
                .id(UUID.fromString("44444444-0000-0000-0000-000000000041"))
                .content("Older briefing")
                .generatedAt(base.minus(1, ChronoUnit.DAYS))
                .build());
        briefingRepository.save(BriefingTestBuilder.aBriefing()
                .id(UUID.fromString("44444444-0000-0000-0000-000000000042"))
                .content("Newer briefing")
                .generatedAt(base)
                .build());

        Optional<Briefing> result = briefingRepository.findTopByOrderByGeneratedAtDesc();

        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo("Newer briefing");
    }
}
