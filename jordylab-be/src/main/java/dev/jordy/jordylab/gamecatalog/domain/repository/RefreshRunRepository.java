package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.RefreshRun;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunKind;
import dev.jordy.jordylab.gamecatalog.domain.RefreshRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshRunRepository extends JpaRepository<RefreshRun, UUID> {

    Optional<RefreshRun> findFirstByKindOrderByStartedAtDesc(RefreshRunKind kind);

    boolean existsByKindAndStatus(RefreshRunKind kind, RefreshRunStatus status);

    /** After a restart no run is really running any more: mark the leftovers so the UI stops showing progress. */
    @Modifying
    @Query("UPDATE RefreshRun r SET r.status = 'INTERRUPTED', r.finishedAt = :finishedAt WHERE r.status = 'RUNNING'")
    int markRunningAsInterrupted(@Param("finishedAt") Instant finishedAt);
}
