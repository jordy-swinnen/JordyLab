package dev.jordy.jordylab.fna.rest.controller.model;

import java.time.Instant;

/** One news article as a connector tool shows it (FR-015: the fields the Finance News screen shows). */
public record ArticleItem(String title, String url, Instant publishedAt, String feedName) {
}
