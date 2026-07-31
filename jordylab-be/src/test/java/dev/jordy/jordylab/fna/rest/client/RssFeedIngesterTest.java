package dev.jordy.jordylab.fna.rest.client;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.rometools.rome.feed.synd.SyndEntry;
import dev.jordy.jordylab.fna.domain.FeedTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@WireMockTest(httpPort = 9997)
class RssFeedIngesterTest {

    private RssFeedIngester rssFeedIngester;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.builder().build();
        rssFeedIngester = new RssFeedIngester(restClient);
    }

    @Test
    void parsesValidRssFeedAndReturnsEntries() {
        String rssXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Test Feed</title>
                    <item>
                      <title>Test Article</title>
                      <link>http://example.com/article-1</link>
                    </item>
                  </channel>
                </rss>
                """;

        stubFor(get(urlEqualTo("/feed.xml"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(rssXml)));

        List<SyndEntry> entries = rssFeedIngester.fetchEntries(
                FeedTestBuilder.aFeed().url("http://localhost:9997/feed.xml").build());

        assertThat(entries).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(entries.getFirst().getTitle()).isEqualTo("Test Article");
            softly.assertThat(entries.getFirst().getLink()).isEqualTo("http://example.com/article-1");
        });
    }

    @Test
    void returnsEmptyListOnHttpError() {
        stubFor(get(urlEqualTo("/feed-error.xml"))
                .willReturn(aResponse().withStatus(500)));

        List<SyndEntry> entries = rssFeedIngester.fetchEntries(
                FeedTestBuilder.aFeed().url("http://localhost:9997/feed-error.xml").build());

        assertThat(entries).isEmpty();
    }
}
