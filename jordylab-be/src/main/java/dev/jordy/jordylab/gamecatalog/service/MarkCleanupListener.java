package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.repository.GameMarkRepository;
import dev.jordy.jordylab.shared.event.UserAccessRemoved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Deletes every mark of an account that lost its access, so its votes stop counting (spec 013 FR-046). */
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
}
