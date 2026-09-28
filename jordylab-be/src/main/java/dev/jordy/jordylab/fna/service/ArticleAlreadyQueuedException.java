package dev.jordy.jordylab.fna.service;

/** Spec 007 FR-017: the same URL was already shared/queued. */
public class ArticleAlreadyQueuedException extends RuntimeException {

    public ArticleAlreadyQueuedException(String url) {
        super("Article already queued: " + url);
    }
}
