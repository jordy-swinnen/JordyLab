package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.identity.UserAccessRemoved;
import dev.jordy.jordylab.identity.UserDeleted;
import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clears a removed account's guest chat data (spec 019 D17): the personal limit and the usage rows. The identity module
 * publishes the events; settings owns the tables, so this listener is the module-boundary-safe replacement for the old
 * direct call inside the Keycloak-backed administration service.
 */
@Component
@RequiredArgsConstructor
public class GuestDataCleanupListener {

    private final GuestChatLimitService guestChatLimitService;
    private final GuestChatUsageRepository guestChatUsageRepository;

    @ApplicationModuleListener
    @Transactional
    void onUserAccessRemoved(UserAccessRemoved event) {
        clear(event.userSubject());
    }

    @ApplicationModuleListener
    @Transactional
    void onUserDeleted(UserDeleted event) {
        clear(event.userSubject());
    }

    private void clear(String userSubject) {
        guestChatLimitService.clearPersonal(userSubject);
        guestChatUsageRepository.deleteByUserSubject(userSubject);
    }
}
