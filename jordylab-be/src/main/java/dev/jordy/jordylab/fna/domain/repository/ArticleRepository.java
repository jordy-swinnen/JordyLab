package dev.jordy.jordylab.fna.domain.repository;

import dev.jordy.jordylab.fna.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

    boolean existsByUrl(String url);

    List<Article> findTop50ByOrderByPublishedAtDesc();
}
