package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.identity.UserAccessRemoved;
import dev.jordy.jordylab.identity.UserDeleted;
import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The event-driven cleanup that replaces the old direct call in the settings admin service (spec 019 D17). */
class GuestDataCleanupListenerTest {

    private static final String SUBJECT = "22222222-2222-4222-8222-222222222222";

    private final GuestChatLimitService guestChatLimitService = mock(GuestChatLimitService.class);
    private final GuestChatUsageRepository guestChatUsageRepository = mock(GuestChatUsageRepository.class);
    private final GuestDataCleanupListener listener =
            new GuestDataCleanupListener(guestChatLimitService, guestChatUsageRepository);

    @Test
    void clearsThePersonalLimitAndUsageWhenAccessIsRemoved() {
        listener.onUserAccessRemoved(new UserAccessRemoved(SUBJECT));

        verify(guestChatLimitService).clearPersonal(SUBJECT);
        verify(guestChatUsageRepository).deleteByUserSubject(SUBJECT);
    }

    @Test
    void clearsThePersonalLimitAndUsageWhenTheAccountIsDeleted() {
        listener.onUserDeleted(new UserDeleted(SUBJECT));

        verify(guestChatLimitService).clearPersonal(SUBJECT);
        verify(guestChatUsageRepository).deleteByUserSubject(SUBJECT);
    }
}
