package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameLibraryEntryRepository extends JpaRepository<GameLibraryEntry, UUID> {

    Optional<GameLibraryEntry> findByGameIdAndLibrarySource(UUID gameId, LibrarySource librarySource);

    List<GameLibraryEntry> findAllByGameId(UUID gameId);

    List<GameLibraryEntry> findAllByGameIdIn(Collection<UUID> gameIds);

    List<GameLibraryEntry> findAllByLibrarySource(LibrarySource librarySource);

    List<GameLibraryEntry> findAllByLibrarySourceAndRemovedAtIsNull(LibrarySource librarySource);

    boolean existsByGameIdAndRemovedAtIsNull(UUID gameId);

    boolean existsByGameIdAndRemovedAtAfter(UUID gameId, Instant cutoff);

    @Query("SELECT COUNT(e) FROM GameLibraryEntry e WHERE e.librarySource = :source AND e.removedAt IS NULL")
    long countActiveByLibrarySource(@Param("source") LibrarySource source);
}
