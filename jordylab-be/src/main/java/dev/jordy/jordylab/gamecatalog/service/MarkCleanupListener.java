package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogAccess;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.shared.event.AppAccessRemoved;
import dev.jordy.jordylab.shared.event.UserAccessRemoved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Deletes every mark of an account that lost its access, so its votes stop counting (spec 013 FR-046). Since spec 014 a
 * person can also lose just the Game Catalog while keeping their other apps; that removes their votes too, without
 * touching their session (clarification 2026-11-02).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkCleanupListener {

    private final GameMarkRepository gameMarkRepository;

    @ApplicationModuleListener
    void on(UserAccessRemoved event) {
        int removed = gameMarkRepository.deleteAllByUserSubject(event.userSubject());
        log.info("Removed {} marks of an account that lost access", removed);
    }

    @ApplicationModuleListener
    void on(AppAccessRemoved event) {
        if (!GameCatalogAccess.APP_KEY.equals(event.appKey())) {
            return;
        }
        int removed = gameMarkRepository.deleteAllByUserSubject(event.userSubject());
        log.info("Removed {} marks of an account that lost the Game Catalog", removed);
    }
}
