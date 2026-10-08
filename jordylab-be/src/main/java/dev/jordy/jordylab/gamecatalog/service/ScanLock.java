package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes scans of the same source (host + library type). A scan runs in one long transaction (reconcile, artwork,
 * Steam metadata, AI enrichment — minutes for a big library), so a second scan arriving meanwhile can't see the first
 * one's rows yet and inserts the same installations: a duplicate-key 500 on {@code uq_game_installation_source_ref}
 * (spec 011 BUG-048). The Postgres transaction-scoped advisory lock makes the second scan wait until the first commits;
 * it then finds the stored payload hash and answers {@code NO_CHANGE}. Released automatically at commit or rollback.
 */
@Component
@RequiredArgsConstructor
public class ScanLock {

    private final EntityManager entityManager;

    /** Serialises the creation of a host: two sources of one new machine can be announced at the same moment. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireHostCreation(String hostname) {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)")
                .setParameter("key", "gamecatalog-host:" + hostname.toLowerCase(java.util.Locale.ROOT))
                .getSingleResult();
    }

    /** Blocks until no other transaction holds the lock for this source; must run inside the scan's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String hostname, SourceType libraryType) {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)")
                .setParameter("key", "gamecatalog-scan:" + hostname + ":" + libraryType)
                .getSingleResult();
    }
}
