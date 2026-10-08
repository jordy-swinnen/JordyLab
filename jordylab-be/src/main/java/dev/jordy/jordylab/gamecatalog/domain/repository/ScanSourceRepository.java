package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanSourceRepository extends JpaRepository<ScanSource, UUID> {

    Optional<ScanSource> findBySourceKey(String sourceKey);

    Optional<ScanSource> findByHostIdAndSourceType(UUID hostId, SourceType sourceType);

    List<ScanSource> findAllByHostId(UUID hostId);

    Optional<ScanSource> findByMachineIdAndSourceType(String machineId, SourceType sourceType);
}
