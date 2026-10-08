package dev.jordy.jordylab.gamecatalog.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serialises "find or create the game with this title" across transactions (spec 013 research B2). A title has no
 * unique index (two different games may share one), so two sources scanning the same new title at the same moment would
 * otherwise create two games. The advisory lock is transaction scoped: released at commit or rollback.
 */
@Component
@RequiredArgsConstructor
public class TitleKeyLock {

    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String titleKey) {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)")
                .setParameter("key", "gamecatalog-title:" + titleKey)
                .getSingleResult();
    }
}
