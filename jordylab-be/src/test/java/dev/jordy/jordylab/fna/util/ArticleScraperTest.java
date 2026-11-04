package dev.jordy.jordylab.fna.util;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 9998)
class ArticleScraperTest {

    private final ArticleScraper scraperService = new ArticleScraper();

    @Test
    void extractsTextFromArticleElement() {
        stubFor(get(urlEqualTo("/page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><article>Article content here</article></body></html>")));

        String result = scraperService.scrape("http://localhost:9998/page");

        assertThat(result).isEqualTo("Article content here");
    }

    @Test
    void fallsBackToMainWhenNoArticle() {
        stubFor(get(urlEqualTo("/page-main"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><main>Main content here</main></body></html>")));

        String result = scraperService.scrape("http://localhost:9998/page-main");

        assertThat(result).isEqualTo("Main content here");
    }

    @Test
    void truncatesContentTo8000Chars() {
        String longContent = "A".repeat(10000);
        stubFor(get(urlEqualTo("/page-long"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><article>" + longContent + "</article></body></html>")));

        String result = scraperService.scrape("http://localhost:9998/page-long");

        assertThat(result).hasSize(8000);
    }

    @Test
    void returnsEmptyStringOnConnectionFailure() {
        stubFor(get(urlEqualTo("/page-error"))
                .willReturn(aResponse().withStatus(500)));

        String result = scraperService.scrape("http://localhost:9998/page-error");

        assertThat(result).isEmpty();
    }

    @Test
    void readsThePageTitle() {
        stubFor(get(urlEqualTo("/page-title"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Market wraps higher</title></head><body></body></html>")));

        assertThat(scraperService.scrapeTitle("http://localhost:9998/page-title"))
                .isEqualTo("Market wraps higher");
    }

    @Test
    void fallsBackToTheUrlWhenTheTitleIsMissingOrTheFetchFails() {
        stubFor(get(urlEqualTo("/page-no-title"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head></head><body></body></html>")));

        assertThat(scraperService.scrapeTitle("http://localhost:9998/page-no-title"))
                .isEqualTo("http://localhost:9998/page-no-title");
        assertThat(scraperService.scrapeTitle("http://localhost:9998/does-not-exist"))
                .isEqualTo("http://localhost:9998/does-not-exist");
    }

    @Test
    void fallsBackToTheBodyWhenThereIsNoArticleOrMain() {
        stubFor(get(urlEqualTo("/page-body"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><p>Body only content here</p></body></html>")));

        assertThat(scraperService.scrape("http://localhost:9998/page-body"))
                .isEqualTo("Body only content here");
    }
}
