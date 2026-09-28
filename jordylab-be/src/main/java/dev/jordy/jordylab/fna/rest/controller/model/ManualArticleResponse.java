package dev.jordy.jordylab.fna.rest.controller.model;

import dev.jordy.jordylab.fna.domain.Article;

import java.util.UUID;

public record ManualArticleResponse(UUID id, String title, String url) {

    public static ManualArticleResponse from(Article article) {
        return new ManualArticleResponse(article.getId(), article.getTitle(), article.getUrl());
    }
}
