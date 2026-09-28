package dev.jordy.jordylab.fna.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class ArticleScraper {

    private static final int MAX_CONTENT_LENGTH = 8000;

    /**
     * Reads the page's {@code <title>} for manually-submitted URLs (spec 007 FR-017), which have
     * no RSS entry to supply one. A separate fetch from {@link #scrape(String)} — an acceptable
     * trade-off since this path is admin-triggered and low-frequency, and it keeps
     * {@link #scrape(String)}'s existing callers untouched.
     */
    public String scrapeTitle(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get();
            String title = document.title();

            return StringUtils.hasText(title) ? title : url;
        } catch (Exception exception) {
            log.warn("Failed to scrape title for {}: {}", url, exception.getMessage());

            return url;
        }
    }

    public String scrape(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get();

            Element element = document.selectFirst("article");
            if (element == null) {
                element = document.selectFirst("main");
            }
            if (element == null) {
                element = document.body();
            }

            String text = element.text();

            return text.length() > MAX_CONTENT_LENGTH
                    ? text.substring(0, MAX_CONTENT_LENGTH)
                    : text;
        } catch (Exception exception) {
            log.warn("Failed to scrape {}: {}", url, exception.getMessage());

            return "";
        }
    }
}
