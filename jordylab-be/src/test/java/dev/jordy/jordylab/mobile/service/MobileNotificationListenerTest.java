package dev.jordy.jordylab.mobile.service;

import dev.jordy.jordylab.fna.BriefingReady;
import dev.jordy.jordylab.mobile.MobileProperties;
import dev.jordy.jordylab.mobile.rest.client.NtfyClient;
import dev.jordy.jordylab.settings.UserSignUpPending;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Proves one Ntfy call per event with the correct App-Link click URL (spec 007 D9) — a
 * duplicate-push regression here would mean two notifications for one sign-up/briefing.
 */
@ExtendWith(MockitoExtension.class)
class MobileNotificationListenerTest {

    @Mock
    private NtfyClient ntfyClient;

    private MobileNotificationListener listener;

    @BeforeEach
    void setUp() {
        MobileProperties properties = new MobileProperties(
                new MobileProperties.Release(null, null),
                new MobileProperties.DownloadLink(null, 5),
                new MobileProperties.App(null, "jordylab.example"),
                null);
        listener = new MobileNotificationListener(ntfyClient, properties);
    }

    @Test
    void userSignUpPendingProducesExactlyOnePushWithTheSettingsUsersScreen() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<String> clickUrlCaptor = ArgumentCaptor.forClass(String.class);

        listener.on(new UserSignUpPending(userId, "friend@example.org", "Ada Palmer"));

        verify(ntfyClient).publish(eq("New JordyLab sign-up"), contains("Ada Palmer"), clickUrlCaptor.capture());
        assertThat(clickUrlCaptor.getValue()).isEqualTo("https://jordylab.example/mobile/open?screen=settings-users");
    }

    @Test
    void briefingReadyProducesExactlyOnePushWithTheFnaBriefingScreen() {
        Instant generatedAt = Instant.parse("2026-09-28T06:30:00Z");
        ArgumentCaptor<String> clickUrlCaptor = ArgumentCaptor.forClass(String.class);

        listener.on(new BriefingReady(UUID.randomUUID(), generatedAt));

        verify(ntfyClient).publish(eq("Today's briefing is ready"), contains(generatedAt.toString()),
                clickUrlCaptor.capture());
        assertThat(clickUrlCaptor.getValue()).isEqualTo("https://jordylab.example/mobile/open?screen=fna-briefing");
    }
}
