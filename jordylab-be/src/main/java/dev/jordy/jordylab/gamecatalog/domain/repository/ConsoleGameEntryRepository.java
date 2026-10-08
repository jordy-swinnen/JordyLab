package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsoleGameEntryRepository extends JpaRepository<ConsoleGameEntry, UUID> {

    List<ConsoleGameEntry> findAllByConsoleId(UUID consoleId);

    List<ConsoleGameEntry> findAllByGameId(UUID gameId);

    List<ConsoleGameEntry> findAllByGameIdIn(Collection<UUID> gameIds);

    Optional<ConsoleGameEntry> findByGameIdAndConsoleId(UUID gameId, UUID consoleId);

    boolean existsByGameIdAndConsoleId(UUID gameId, UUID consoleId);

    boolean existsByGameId(UUID gameId);

    long countByConsoleId(UUID consoleId);

    long countByGameId(UUID gameId);

    void deleteAllByConsoleId(UUID consoleId);

    /** Consoles that hold at least one game. */
    @Query("SELECT DISTINCT e.console FROM ConsoleGameEntry e")
    List<Console> findConsolesWithGames();
}
