package dev.jordy.jordylab.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCose;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security's WebAuthn credential store on {@code identity.user_credentials} (spec 019 D16). The framework's own
 * JDBC repository cannot target a schema-qualified table, so this adapter mirrors its SQL contract: the credential id
 * and user handle are stored as base64url/bytes, transports comma-separated, and the WebAuthn flags and attestation
 * material kept as-is. Only public-key material is ever stored; the private key stays on the device.
 */
@Repository
@RequiredArgsConstructor
public class UserCredentialStore implements UserCredentialRepository {

    private static final String INSERT = """
            INSERT INTO identity.user_credentials (credential_id, user_entity_user_id, public_key, signature_count,
                uv_initialized, backup_eligible, authenticator_transports, public_key_credential_type, backup_state,
                attestation_object, attestation_client_data_json, created, last_used, label)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (credential_id) DO UPDATE SET
                signature_count = EXCLUDED.signature_count,
                uv_initialized = EXCLUDED.uv_initialized,
                backup_state = EXCLUDED.backup_state,
                last_used = EXCLUDED.last_used,
                label = EXCLUDED.label
            """;

    private static final String SELECT_COLUMNS = """
            SELECT credential_id, user_entity_user_id, public_key, signature_count, uv_initialized, backup_eligible,
                   authenticator_transports, public_key_credential_type, backup_state, attestation_object,
                   attestation_client_data_json, created, last_used, label
            FROM identity.user_credentials
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(CredentialRecord credential) {
        jdbcTemplate.update(INSERT,
                credential.getCredentialId().toBase64UrlString(),
                credential.getUserEntityUserId().getBytes(),
                credential.getPublicKey().getBytes(),
                credential.getSignatureCount(),
                credential.isUvInitialized(),
                credential.isBackupEligible(),
                transportsToString(credential.getTransports()),
                credential.getCredentialType().getValue(),
                credential.isBackupState(),
                bytesOrNull(credential.getAttestationObject()),
                bytesOrNull(credential.getAttestationClientDataJSON()),
                timestampOrNow(credential.getCreated()),
                timestampOrNow(credential.getLastUsed()),
                credential.getLabel());
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        List<CredentialRecord> records = jdbcTemplate.query(SELECT_COLUMNS + " WHERE credential_id = ?",
                (resultSet, rowNumber) -> mapRecord(resultSet), credentialId.toBase64UrlString());

        return records.isEmpty() ? null : records.getFirst();
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE user_entity_user_id = ?",
                (resultSet, rowNumber) -> mapRecord(resultSet), userId.getBytes());
    }

    @Override
    public void delete(Bytes credentialId) {
        jdbcTemplate.update("DELETE FROM identity.user_credentials WHERE credential_id = ?",
                credentialId.toBase64UrlString());
    }

    private static CredentialRecord mapRecord(ResultSet resultSet) throws SQLException {
        byte[] publicKey = resultSet.getBytes("public_key");
        PublicKeyCose publicKeyCose = () -> publicKey;

        return ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.valueOf(resultSet.getString("public_key_credential_type")))
                .credentialId(Bytes.fromBase64(resultSet.getString("credential_id")))
                .userEntityUserId(new Bytes(resultSet.getBytes("user_entity_user_id")))
                .publicKey(publicKeyCose)
                .signatureCount(resultSet.getLong("signature_count"))
                .uvInitialized(resultSet.getBoolean("uv_initialized"))
                .transports(parseTransports(resultSet.getString("authenticator_transports")))
                .backupEligible(resultSet.getBoolean("backup_eligible"))
                .backupState(resultSet.getBoolean("backup_state"))
                .attestationObject(bytesOrNull(resultSet.getBytes("attestation_object")))
                .attestationClientDataJSON(bytesOrNull(resultSet.getBytes("attestation_client_data_json")))
                .created(instant(resultSet.getTimestamp("created")))
                .lastUsed(instant(resultSet.getTimestamp("last_used")))
                .label(resultSet.getString("label"))
                .build();
    }

    private static Set<AuthenticatorTransport> parseTransports(String stored) {
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(AuthenticatorTransport::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String transportsToString(Set<AuthenticatorTransport> transports) {
        if (transports == null || transports.isEmpty()) {
            return "";
        }

        return transports.stream().map(AuthenticatorTransport::getValue).sorted().collect(Collectors.joining(","));
    }

    private static byte[] bytesOrNull(Bytes bytes) {
        return bytes == null ? null : bytes.getBytes();
    }

    private static Bytes bytesOrNull(byte[] bytes) {
        return bytes == null ? null : new Bytes(bytes);
    }

    private static Timestamp timestampOrNow(Instant instant) {
        return Timestamp.from(instant != null ? instant : Instant.now());
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
