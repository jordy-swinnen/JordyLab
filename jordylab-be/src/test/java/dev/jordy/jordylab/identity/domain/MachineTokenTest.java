package dev.jordy.jordylab.identity.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static dev.jordy.jordylab.identity.domain.MachineTokenTestBuilder.DEFAULT_CREATED_BY;
import static dev.jordy.jordylab.identity.domain.MachineTokenTestBuilder.DEFAULT_ID;
import static dev.jordy.jordylab.identity.domain.MachineTokenTestBuilder.DEFAULT_NAME;
import static dev.jordy.jordylab.identity.domain.MachineTokenTestBuilder.DEFAULT_TOKEN_HASH;
import static dev.jordy.jordylab.identity.domain.MachineTokenTestBuilder.aDefaultMachineToken;
import static dev.jordy.jordylab.identity.domain.MachineTokenTestBuilder.aMachineToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class MachineTokenTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void buildMachineToken() {
        MachineToken token = aMachineToken().build();

        assertSoftly(softly -> {
            softly.assertThat(token.getId()).isEqualTo(DEFAULT_ID);
            softly.assertThat(token.getName()).isEqualTo(DEFAULT_NAME);
            softly.assertThat(token.getTokenHash()).isEqualTo(DEFAULT_TOKEN_HASH);
            softly.assertThat(token.getScope()).isEqualTo(MachineTokenScope.INGEST);
            softly.assertThat(token.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
            softly.assertThat(token.getLastUsedAt()).isNull();
            softly.assertThat(token.getRevokedAt()).isNull();
        });
    }

    @Test
    void buildWithoutName() {
        assertThatThrownBy(() -> MachineToken.builder()
                .tokenHash(DEFAULT_TOKEN_HASH)
                .scope(MachineTokenScope.INGEST)
                .createdBy(DEFAULT_CREATED_BY)
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutTokenHash() {
        assertThatThrownBy(() -> MachineToken.builder()
                .name(DEFAULT_NAME)
                .scope(MachineTokenScope.INGEST)
                .createdBy(DEFAULT_CREATED_BY)
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutScope() {
        assertThatThrownBy(() -> MachineToken.builder()
                .name(DEFAULT_NAME)
                .tokenHash(DEFAULT_TOKEN_HASH)
                .createdBy(DEFAULT_CREATED_BY)
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutCreatedBy() {
        assertThatThrownBy(() -> MachineToken.builder()
                .name(DEFAULT_NAME)
                .tokenHash(DEFAULT_TOKEN_HASH)
                .scope(MachineTokenScope.INGEST)
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeStampsTheTimeOnce() {
        MachineToken token = aDefaultMachineToken();

        token.revoke(NOW);

        assertSoftly(softly -> {
            softly.assertThat(token.isRevoked()).isTrue();
            softly.assertThat(token.getRevokedAt()).isEqualTo(NOW);
        });
        assertThatThrownBy(() -> token.revoke(NOW.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void touchLastUsedAdvancesTheTimestamp() {
        MachineToken token = aDefaultMachineToken();

        token.touchLastUsed(NOW);

        assertThat(token.getLastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(MachineToken.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
