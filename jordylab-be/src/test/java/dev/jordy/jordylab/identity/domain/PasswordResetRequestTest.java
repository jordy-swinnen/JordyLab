package dev.jordy.jordylab.identity.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static dev.jordy.jordylab.identity.domain.PasswordResetRequestTestBuilder.DEFAULT_DECIDED_BY;
import static dev.jordy.jordylab.identity.domain.PasswordResetRequestTestBuilder.DEFAULT_ID;
import static dev.jordy.jordylab.identity.domain.PasswordResetRequestTestBuilder.DEFAULT_REQUESTED_HASH;
import static dev.jordy.jordylab.identity.domain.PasswordResetRequestTestBuilder.DEFAULT_USER_ACCOUNT_ID;
import static dev.jordy.jordylab.identity.domain.PasswordResetRequestTestBuilder.aDefaultPasswordResetRequest;
import static dev.jordy.jordylab.identity.domain.PasswordResetRequestTestBuilder.aPasswordResetRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class PasswordResetRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void buildPasswordResetRequestStartsPending() {
        PasswordResetRequest request = aPasswordResetRequest().build();

        assertSoftly(softly -> {
            softly.assertThat(request.getId()).isEqualTo(DEFAULT_ID);
            softly.assertThat(request.getUserAccountId()).isEqualTo(DEFAULT_USER_ACCOUNT_ID);
            softly.assertThat(request.getRequestedPasswordHash()).isEqualTo(DEFAULT_REQUESTED_HASH);
            softly.assertThat(request.getStatus()).isEqualTo(ResetRequestStatus.PENDING);
            softly.assertThat(request.isPending()).isTrue();
            softly.assertThat(request.getDecidedAt()).isNull();
            softly.assertThat(request.getDecidedBy()).isNull();
        });
    }

    @Test
    void buildWithoutUserAccount() {
        assertThatThrownBy(() -> PasswordResetRequest.builder()
                .requestedPasswordHash(DEFAULT_REQUESTED_HASH)
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutRequestedHash() {
        assertThatThrownBy(() -> PasswordResetRequest.builder()
                .userAccountId(DEFAULT_USER_ACCOUNT_ID)
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approveClosesTheRequest() {
        PasswordResetRequest request = aDefaultPasswordResetRequest();

        request.approve(DEFAULT_DECIDED_BY, NOW);

        assertSoftly(softly -> {
            softly.assertThat(request.getStatus()).isEqualTo(ResetRequestStatus.APPROVED);
            softly.assertThat(request.isPending()).isFalse();
            softly.assertThat(request.getDecidedBy()).isEqualTo(DEFAULT_DECIDED_BY);
            softly.assertThat(request.getDecidedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void rejectKeepsTheOldPasswordInPlace() {
        PasswordResetRequest request = aDefaultPasswordResetRequest();

        request.reject(DEFAULT_DECIDED_BY, NOW);

        assertSoftly(softly -> {
            softly.assertThat(request.getStatus()).isEqualTo(ResetRequestStatus.REJECTED);
            softly.assertThat(request.isPending()).isFalse();
        });
    }

    @Test
    void decideTwiceFails() {
        PasswordResetRequest request = aDefaultPasswordResetRequest();
        request.approve(DEFAULT_DECIDED_BY, NOW);

        assertThatThrownBy(() -> request.reject(DEFAULT_DECIDED_BY, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(PasswordResetRequest.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
