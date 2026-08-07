package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScanSourceRepository extends JpaRepository<ScanSource, UUID> {

    Optional<ScanSource> findBySourceKey(String sourceKey);

    Optional<ScanSource> findByHostnameAndSourceType(String hostname, SourceType sourceType);
}
