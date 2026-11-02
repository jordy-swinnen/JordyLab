package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.settings.SettingsProperties;
import dev.jordy.jordylab.settings.domain.GuestChatLimit;
import dev.jordy.jordylab.settings.domain.repository.GuestChatLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestChatLimitServiceTest {

    private static final String GUEST = "guest-subject";
    private static final String ADMIN = "admin-subject";

    @Mock
    private GuestChatLimitRepository guestChatLimitRepository;

    private GuestChatLimitService service;

    @BeforeEach
    void setUp() {
        SettingsProperties properties = new SettingsProperties(new SettingsProperties.GuestChat(20, "UTC"), null);
        service = new GuestChatLimitService(guestChatLimitRepository, properties);
    }

    @Test
    void aPersonalLimitBeatsTheSavedDefaultAndTheEnvironment() {
        when(guestChatLimitRepository.findByUserSubject(GUEST))
                .thenReturn(Optional.of(personal(35)));

        GuestChatLimitService.EffectiveGuestChatLimit effective = service.effectiveFor(GUEST);

        assertSoftly(softly -> {
            softly.assertThat(effective.limit()).isEqualTo(35);
            softly.assertThat(effective.source()).isEqualTo(GuestChatLimitService.LimitSource.PERSONAL);
        });
    }

    @Test
    void theSavedDefaultBeatsTheEnvironmentValue() {
        when(guestChatLimitRepository.findByUserSubject(GUEST)).thenReturn(Optional.empty());
        when(guestChatLimitRepository.findDefault()).thenReturn(Optional.of(savedDefault(12)));

        GuestChatLimitService.EffectiveGuestChatLimit effective = service.effectiveFor(GUEST);

        assertSoftly(softly -> {
            softly.assertThat(effective.limit()).isEqualTo(12);
            softly.assertThat(effective.source()).isEqualTo(GuestChatLimitService.LimitSource.DEFAULT_SAVED);
        });
    }

    @Test
    void theEnvironmentValueAppliesUntilADefaultIsSaved() {
        when(guestChatLimitRepository.findByUserSubject(GUEST)).thenReturn(Optional.empty());
        when(guestChatLimitRepository.findDefault()).thenReturn(Optional.empty());

        GuestChatLimitService.EffectiveGuestChatLimit effective = service.effectiveFor(GUEST);

        assertSoftly(softly -> {
            softly.assertThat(effective.limit()).isEqualTo(20);
            softly.assertThat(effective.source()).isEqualTo(GuestChatLimitService.LimitSource.ENVIRONMENT);
        });
    }

    /** FR-011: a saved 0 is a real limit that blocks chat, unlike the environment value's fallback to 20. */
    @Test
    void aSavedZeroIsKeptAndIsNotCoercedToTwenty() {
        when(guestChatLimitRepository.findByUserSubject(GUEST)).thenReturn(Optional.of(personal(0)));

        assertSoftly(softly -> {
            softly.assertThat(service.effectiveFor(GUEST).limit()).isZero();
            softly.assertThat(service.describeDefault().dailyLimit()).isEqualTo(20);
        });
    }

    @Test
    void savingTheDefaultCreatesTheSingleDefaultRow() {
        when(guestChatLimitRepository.findDefault()).thenReturn(Optional.empty());
        ArgumentCaptor<GuestChatLimit> captor = ArgumentCaptor.forClass(GuestChatLimit.class);

        service.saveDefault(15, ADMIN);

        verify(guestChatLimitRepository).save(captor.capture());
        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().getDailyLimit()).isEqualTo(15);
            softly.assertThat(captor.getValue().isDefault()).isTrue();
            softly.assertThat(captor.getValue().getUpdatedBySubject()).isEqualTo(ADMIN);
        });
    }

    @Test
    void savingTheDefaultAgainUpdatesTheExistingRow() {
        GuestChatLimit existing = savedDefault(12);
        when(guestChatLimitRepository.findDefault()).thenReturn(Optional.of(existing));

        service.saveDefault(15, ADMIN);

        assertSoftly(softly -> {
            softly.assertThat(existing.getDailyLimit()).isEqualTo(15);
            softly.assertThat(existing.getUpdatedBySubject()).isEqualTo(ADMIN);
        });
    }

    @Test
    void aPersonalLimitIsSavedForThatPersonOnly() {
        when(guestChatLimitRepository.findByUserSubject(GUEST)).thenReturn(Optional.empty());
        ArgumentCaptor<GuestChatLimit> captor = ArgumentCaptor.forClass(GuestChatLimit.class);

        service.savePersonal(GUEST, 40, ADMIN);

        verify(guestChatLimitRepository).save(captor.capture());
        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().getUserSubject()).isEqualTo(GUEST);
            softly.assertThat(captor.getValue().getDailyLimit()).isEqualTo(40);
            softly.assertThat(captor.getValue().isDefault()).isFalse();
        });
    }

    @Test
    void clearingAPersonalLimitDeletesOnlyThatRow() {
        service.clearPersonal(GUEST);

        verify(guestChatLimitRepository).deleteByUserSubject(GUEST);
    }

    @Test
    void aNegativeLimitIsRefused() {
        assertThatThrownBy(() -> service.saveDefault(-1, ADMIN))
                .isInstanceOf(InvalidLimitException.class);
        assertThatThrownBy(() -> service.savePersonal(GUEST, -5, ADMIN))
                .isInstanceOf(InvalidLimitException.class);
    }

    private GuestChatLimit personal(int dailyLimit) {
        return GuestChatLimit.builder()
                .userSubject(GUEST)
                .dailyLimit(dailyLimit)
                .updatedBySubject(ADMIN)
                .build();
    }

    private GuestChatLimit savedDefault(int dailyLimit) {
        return GuestChatLimit.builder()
                .dailyLimit(dailyLimit)
                .updatedBySubject(ADMIN)
                .build();
    }
}
