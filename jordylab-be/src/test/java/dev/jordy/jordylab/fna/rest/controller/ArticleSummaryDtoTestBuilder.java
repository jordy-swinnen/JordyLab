package dev.jordy.jordylab.fna.rest.controller;

import dev.jordy.jordylab.fna.rest.controller.model.ArticleSummaryDto;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
class ArticleSummaryDtoTestBuilder {

    static final UUID DEFAULT_ID = UUID.fromString("55555555-0000-0000-0000-000000000005");
    static final String DEFAULT_TITLE = "BEL20 hits record high";
    static final String DEFAULT_URL = "https://hln.be/article-1";
    static final Instant DEFAULT_PUBLISHED_AT = Instant.parse("2026-03-15T10:00:00Z");
    static final String DEFAULT_FEED_NAME = "HLN Economie";

    static ArticleSummaryDto aDefaultArticleSummaryDto() {
        return new ArticleSummaryDto(DEFAULT_ID, DEFAULT_TITLE, DEFAULT_URL, DEFAULT_PUBLISHED_AT, DEFAULT_FEED_NAME);
    }
}
