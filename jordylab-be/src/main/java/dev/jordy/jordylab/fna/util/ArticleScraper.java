package dev.jordy.jordylab.fna.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ArticleScraper {

    private static final int MAX_CONTENT_LENGTH = 8000;

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
