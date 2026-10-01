package dev.jordy.jordylab.settings.domain.repository;

import dev.jordy.jordylab.settings.domain.AiFeatureLastRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiFeatureLastRunRepository extends JpaRepository<AiFeatureLastRun, UUID> {

    Optional<AiFeatureLastRun> findByFeatureKey(String featureKey);
}
