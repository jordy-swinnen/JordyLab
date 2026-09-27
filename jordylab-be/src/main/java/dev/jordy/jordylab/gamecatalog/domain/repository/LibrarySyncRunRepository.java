package dev.jordy.jordylab.gamecatalog.domain.repository;

import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LibrarySyncRunRepository extends JpaRepository<LibrarySyncRun, UUID> {

    Optional<LibrarySyncRun> findFirstByLibrarySourceOrderByFinishedAtDesc(LibrarySource librarySource);

    Optional<LibrarySyncRun> findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(LibrarySource librarySource,
            LibrarySyncOutcome outcome);
}
