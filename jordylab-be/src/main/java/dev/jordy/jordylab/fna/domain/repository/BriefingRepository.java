package dev.jordy.jordylab.fna.domain.repository;

import dev.jordy.jordylab.fna.domain.Briefing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BriefingRepository extends JpaRepository<Briefing, UUID> {

    Optional<Briefing> findTopByOrderByGeneratedAtDesc();
}
