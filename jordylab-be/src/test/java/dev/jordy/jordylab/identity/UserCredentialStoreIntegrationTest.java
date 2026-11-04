package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.service.UserCredentialStore;
import dev.jordy.jordylab.identity.util.IdentityIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** The passkey credential store on {@code identity.user_credentials} (spec 019 D16). */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.profiles.active=local")
class UserCredentialStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserCredentialStore store;

    @Test
    void roundTripsACredentialRecord() {
        UUID userId = UUID.randomUUID();
        Bytes userIdBytes = new Bytes(IdentityIds.toBytes(userId));
        Bytes credentialId = Bytes.random();
        CredentialRecord credential = record(credentialId, userIdBytes, 0, "MacBook");

        store.save(credential);

        CredentialRecord loaded = store.findByCredentialId(credentialId);
        assertSoftly(softly -> {
            softly.assertThat(loaded).isNotNull();
            softly.assertThat(loaded.getUserEntityUserId()).isEqualTo(userIdBytes);
            softly.assertThat(loaded.getSignatureCount()).isZero();
            softly.assertThat(loaded.getLabel()).isEqualTo("MacBook");
            softly.assertThat(loaded.getTransports()).containsExactlyInAnyOrder(
                    AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID);
            softly.assertThat(loaded.getCredentialType()).isEqualTo(PublicKeyCredentialType.PUBLIC_KEY);
        });
        assertThat(store.findByUserId(userIdBytes)).hasSize(1);

        // Authentication updates the signature counter and last-used time on the same row.
        store.save(record(credentialId, userIdBytes, 7, "MacBook"));
        assertSoftly(softly -> {
            softly.assertThat(store.findByCredentialId(credentialId).getSignatureCount()).isEqualTo(7);
            softly.assertThat(store.findByUserId(userIdBytes)).hasSize(1);
        });

        store.delete(credentialId);
        assertSoftly(softly -> {
            softly.assertThat(store.findByCredentialId(credentialId)).isNull();
            softly.assertThat(store.findByUserId(userIdBytes)).isEmpty();
        });
    }

    private static CredentialRecord record(Bytes credentialId, Bytes userId, long signatureCount, String label) {
        Instant now = Instant.parse("2026-11-03T12:00:00Z");

        return ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(credentialId)
                .userEntityUserId(userId)
                .publicKey(() -> new byte[]{1, 2, 3, 4})
                .signatureCount(signatureCount)
                .uvInitialized(true)
                .transports(Set.of(AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID))
                .backupEligible(true)
                .backupState(false)
                .attestationObject(new Bytes(new byte[]{5, 6}))
                .attestationClientDataJSON(new Bytes(new byte[]{7, 8}))
                .created(now)
                .lastUsed(now)
                .label(label)
                .build();
    }
}
