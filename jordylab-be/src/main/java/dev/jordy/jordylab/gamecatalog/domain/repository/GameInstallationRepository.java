package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.Presence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameInstallationRepository extends JpaRepository<GameInstallation, UUID> {

    Optional<GameInstallation> findBySourceIdAndExternalRef(UUID sourceId, String externalRef);

    List<GameInstallation> findAllBySourceId(UUID sourceId);

    List<GameInstallation> findAllBySourceIdAndManualFalse(UUID sourceId);

    List<GameInstallation> findAllByGameId(UUID gameId);

    List<GameInstallation> findAllByGameIdIn(Collection<UUID> gameIds);

    List<GameInstallation> findByPresenceAndUninstalledAtBefore(Presence presence, Instant cutoff);

    long countByGameId(UUID gameId);

    @Query("SELECT COUNT(gi) FROM GameInstallation gi WHERE gi.source.id = :sourceId AND gi.presence = 'INSTALLED'")
    long countInstalledBySourceId(@Param("sourceId") UUID sourceId);
}
