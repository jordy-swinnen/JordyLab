package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.domain.MachineToken;
import dev.jordy.jordylab.identity.domain.MachineTokenScope;
import dev.jordy.jordylab.identity.domain.repository.MachineTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MachineTokenServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TOKEN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    private final MachineTokenRepository machineTokenRepository = mock(MachineTokenRepository.class);
    private final MachineTokenService service = new MachineTokenService(machineTokenRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsATokenShownOnceAndStoredOnlyAsAHash() {
        ArgumentCaptor<MachineToken> saved = ArgumentCaptor.forClass(MachineToken.class);
        when(machineTokenRepository.save(saved.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MachineTokenService.CreatedMachineToken created = service.create(ADMIN_ID, "  JordyBox ");

        MachineToken stored = saved.getValue();
        assertSoftly(softly -> {
            softly.assertThat(created.plaintext()).startsWith("jlmt_");
            // prefix + 32 random bytes in base64url (43 chars, no padding)
            softly.assertThat(created.plaintext()).hasSize(5 + 43);
            softly.assertThat(stored.getTokenHash()).isEqualTo(MachineTokenService.hash(created.plaintext()));
            softly.assertThat(stored.getTokenHash()).isNotEqualTo(created.plaintext());
            softly.assertThat(stored.getName()).isEqualTo("JordyBox");
            softly.assertThat(stored.getScope()).isEqualTo(MachineTokenScope.INGEST);
            softly.assertThat(stored.getCreatedBy()).isEqualTo(ADMIN_ID);
        });
    }

    @Test
    void authenticatesOnlyActiveTokens() {
        MachineToken token = aToken();
        when(machineTokenRepository.findByTokenHash(MachineTokenService.hash("jlmt_abc")))
                .thenReturn(Optional.of(token));

        assertThat(service.authenticate("jlmt_abc")).contains(token);
        assertThat(service.authenticate("other")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();

        token.revoke(NOW);
        assertThat(service.authenticate("jlmt_abc")).isEmpty();
    }

    @Test
    void revokesOnce() {
        MachineToken token = aToken();
        when(machineTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
        when(machineTokenRepository.save(token)).thenReturn(token);

        service.revoke(TOKEN_ID);

        assertThat(token.isRevoked()).isTrue();
        assertThatThrownBy(() -> service.revoke(TOKEN_ID))
                .isInstanceOf(MachineTokenAlreadyRevokedException.class);
    }

    @Test
    void refusesAnUnknownMachine() {
        when(machineTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(TOKEN_ID)).isInstanceOf(MachineTokenNotFoundException.class);
    }

    @Test
    void advancesLastUsedAtMostOnceAMinute() {
        MachineToken token = aToken();
        service.recordUse(token);
        assertThat(token.getLastUsedAt()).isEqualTo(NOW);

        MachineToken recentlyUsed = MachineToken.builder()
                .id(UUID.fromString("33333333-3333-4333-8333-333333333333"))
                .name("MacBook")
                .tokenHash(MachineTokenService.hash("jlmt_other"))
                .scope(MachineTokenScope.INGEST)
                .createdBy(ADMIN_ID)
                .build();
        recentlyUsed.touchLastUsed(NOW.minus(Duration.ofSeconds(10)));
        service.recordUse(recentlyUsed);
        assertThat(recentlyUsed.getLastUsedAt()).isEqualTo(NOW.minus(Duration.ofSeconds(10)));
        verify(machineTokenRepository, never()).save(recentlyUsed);
    }

    private static MachineToken aToken() {
        return MachineToken.builder()
                .id(TOKEN_ID)
                .name("JordyBox")
                .tokenHash(MachineTokenService.hash("jlmt_abc"))
                .scope(MachineTokenScope.INGEST)
                .createdBy(ADMIN_ID)
                .build();
    }
}
