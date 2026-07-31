package dev.jordy.jordylab.fna.domain.repository;

import dev.jordy.jordylab.fna.domain.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

    List<Feed> findAllByEnabledTrue();
}
