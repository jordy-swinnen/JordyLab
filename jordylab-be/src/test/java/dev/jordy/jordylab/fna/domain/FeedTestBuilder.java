package dev.jordy.jordylab.fna.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class FeedTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    public static final String DEFAULT_NAME = "HLN Economie";
    public static final String DEFAULT_URL = "https://hln.be/rss/economie";

    public static Feed aDefaultFeed() {
        return aFeed().build();
    }

    public static Feed.FeedBuilder aFeed() {
        return Feed.builder()
                .id(DEFAULT_ID)
                .name(DEFAULT_NAME)
                .url(DEFAULT_URL)
                .enabled(true);
    }
}
