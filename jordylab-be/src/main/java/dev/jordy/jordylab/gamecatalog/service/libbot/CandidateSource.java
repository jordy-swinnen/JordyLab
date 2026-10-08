package dev.jordy.jordylab.gamecatalog.service.libbot;

import java.util.List;
import java.util.UUID;

/**
 * Where LibBot gets its facts about the library. The real one is {@link CandidateRetriever} (SQL and vectors); the seam
 * lets the golden replay tests run the whole pipeline against a small in-memory library.
 */
public interface CandidateSource {

    LibraryVocabulary vocabulary();

    long visibleGameCount();

    /** {@code userSubject} is the asker, whose own marks shape the result; null when there is none. */
    RetrievalResult retrieve(Constraints constraints, String userSubject);

    /** The visible games among {@code gameIds} as confirmed candidates, carrying the asker's own marks. */
    List<Candidate> describeVisible(List<UUID> gameIds, String userSubject);
}
