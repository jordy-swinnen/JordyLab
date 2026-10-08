package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.SettingsProperties;
import dev.jordy.jordylab.settings.domain.GuestChatUsage;
import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestChatQuotaServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-09-27");

    @Mock
    private GuestChatUsageRepository guestChatUsageRepository;

    private GuestChatQuotaService service;

    @BeforeEach
    void setUp() {
        SettingsProperties properties = new SettingsProperties(new SettingsProperties.GuestChat(20, "UTC"), null);
        service = new GuestChatQuotaService(guestChatUsageRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aGuestSeesWhatIsLeftTodayAndWhenItResets() {
        when(guestChatUsageRepository.findByUserSubjectAndUsageDate("guest", TODAY)).thenReturn(Optional.of(
                GuestChatUsage.builder().id(UUID.randomUUID()).userSubject("guest").usageDate(TODAY).messageCount(6)
                        .build()));

        GuestChatQuotaService.Quota quota = service.quotaFor("guest", false);

        assertSoftly(softly -> {
            softly.assertThat(quota.limit()).isEqualTo(20);
            softly.assertThat(quota.remaining()).isEqualTo(14);
            softly.assertThat(quota.exempt()).isFalse();
            softly.assertThat(quota.resetsAt()).isEqualTo(Instant.parse("2026-09-28T00:00:00Z"));
        });
    }

    @Test
    void aGuestWithoutUsageHasTheFullAllowance() {
        when(guestChatUsageRepository.findByUserSubjectAndUsageDate("guest", TODAY)).thenReturn(Optional.empty());

        assertThat(service.quotaFor("guest", false).remaining()).isEqualTo(20);
    }

    @Test
    void anAdminIsExemptAndNothingIsLookedUp() {
        GuestChatQuotaService.Quota quota = service.quotaFor("root", true);

        assertSoftly(softly -> {
            softly.assertThat(quota.exempt()).isTrue();
            softly.assertThat(quota.limit()).isNull();
            softly.assertThat(quota.remaining()).isNull();
        });
        verifyNoInteractions(guestChatUsageRepository);
    }

    @Test
    void countingAnAnsweredMessageIncrementsTodaysRow() {
        service.countAnsweredMessage("guest");

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(guestChatUsageRepository).incrementUsage(idCaptor.capture(), eq("guest"), eq(TODAY));
        assertThat(idCaptor.getValue()).isNotNull();
    }
}
