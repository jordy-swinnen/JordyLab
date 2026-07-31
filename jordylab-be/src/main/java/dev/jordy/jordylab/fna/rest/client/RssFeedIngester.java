package dev.jordy.jordylab.fna.rest.client;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import dev.jordy.jordylab.fna.domain.Feed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class RssFeedIngester {

    private final RestClient restClient;

    public List<SyndEntry> fetchEntries(Feed feed) {
        try {
            byte[] bytes = restClient.get()
                    .uri(feed.getUrl())
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .body(byte[].class);

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed syndFeed = input.build(new XmlReader(new ByteArrayInputStream(Objects.requireNonNull(bytes))));

            return syndFeed.getEntries();
        } catch (Exception exception) {
            log.warn("Failed to fetch feed '{}': {}", feed.getName(), exception.getMessage());

            return List.of();
        }
    }
}
