package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Serializes scans of the same source (host + library type). A scan runs in one long transaction (reconcile, artwork,
 * Steam metadata, AI enrichment — minutes for a big library), so a second scan arriving meanwhile can't see the first
 * one's rows yet and inserts the same installations: a duplicate-key 500 on {@code uq_game_installation_source_ref}
 * (spec 011 BUG-048). The Postgres transaction-scoped advisory lock makes the second scan wait until the first commits;
 * it then finds the stored payload hash and answers {@code NO_CHANGE}. Released automatically at commit or rollback.
 */
@Component
public class ScanLock {

    @PersistenceContext
    private EntityManager entityManager;

    /** Blocks until no other transaction holds the lock for this source; must run inside the scan's transaction. */
    public void acquire(String hostname, SourceType libraryType) {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)")
                .setParameter("key", "gamecatalog-scan:" + hostname + ":" + libraryType)
                .getSingleResult();
    }
}
