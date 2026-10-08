package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.gamecatalog.LibBotMessageAnswered;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GuestChatUsageListenerTest {

    @Mock
    private GuestChatQuotaService quotaService;

    @InjectMocks
    private GuestChatUsageListener listener;

    @Test
    void anAnsweredGuestMessageIsCounted() {
        ReflectionTestUtils.invokeMethod(listener, "on", new LibBotMessageAnswered("guest", false));

        verify(quotaService).countAnsweredMessage("guest");
    }

    @Test
    void anAdminMessageIsNeverCounted() {
        ReflectionTestUtils.invokeMethod(listener, "on", new LibBotMessageAnswered("root", true));

        verifyNoInteractions(quotaService);
    }
}
