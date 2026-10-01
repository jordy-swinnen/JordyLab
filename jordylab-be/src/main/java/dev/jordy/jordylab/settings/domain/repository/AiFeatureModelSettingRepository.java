package dev.jordy.jordylab.settings.domain.repository;

import dev.jordy.jordylab.settings.domain.AiFeatureModelSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiFeatureModelSettingRepository extends JpaRepository<AiFeatureModelSetting, UUID> {

    Optional<AiFeatureModelSetting> findByFeatureKey(String featureKey);
}
