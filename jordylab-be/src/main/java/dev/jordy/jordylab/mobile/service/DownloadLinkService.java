package dev.jordy.jordylab.mobile.service;

import dev.jordy.jordylab.mobile.MobileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues and verifies short-lived, single-purpose signed download tokens (spec FR-002, research
 * D8) — not persisted rows, so there is nothing to clean up. A token is
 * {@code base64url(payload) "." base64url(HMAC-SHA256(payload))}, where the payload is
 * {@code releaseId|subject|expiresAtEpochSeconds}. The subject is carried for audit/debug
 * logging only; verification checks the signature and expiry, not the caller.
 */
@Component
@RequiredArgsConstructor
public class DownloadLinkService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final MobileProperties properties;
    private final Clock clock;

    public IssuedDownloadLink issue(UUID releaseId, String subject) {
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(properties.downloadLink().ttlMinutes()));
        String payload = releaseId + "|" + subject + "|" + expiresAt.getEpochSecond();
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String token = encodedPayload + "." + sign(encodedPayload);

        return new IssuedDownloadLink(token, expiresAt);
    }

    /** Returns the authorized release id, or throws {@link DownloadLinkInvalidException}. */
    public UUID verify(String token) {
        int separator = token == null ? -1 : token.indexOf('.');
        if (separator < 0) {
            throw new DownloadLinkInvalidException("Malformed download token");
        }
        String encodedPayload = token.substring(0, separator);
        String providedSignature = token.substring(separator + 1);
        if (!constantTimeEquals(sign(encodedPayload), providedSignature)) {
            throw new DownloadLinkInvalidException("Download token signature mismatch");
        }

        String[] fields = decodePayload(encodedPayload);
        Instant expiresAt = parseExpiry(fields[2]);
        if (clock.instant().isAfter(expiresAt)) {
            throw new DownloadLinkInvalidException("Download token has expired");
        }

        return parseReleaseId(fields[0]);
    }

    private String sign(String encodedPayload) {
        String secret = properties.downloadLink().secret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("jordylab.mobile.download-link.secret must be configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));

            return ENCODER.encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to compute download-link signature", exception);
        }
    }

    private String[] decodePayload(String encodedPayload) {
        try {
            String payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", 3);
            if (fields.length != 3) {
                throw new DownloadLinkInvalidException("Malformed download token payload");
            }

            return fields;
        } catch (IllegalArgumentException notBase64) {
            throw new DownloadLinkInvalidException("Malformed download token payload");
        }
    }

    private UUID parseReleaseId(String rawReleaseId) {
        try {
            return UUID.fromString(rawReleaseId);
        } catch (IllegalArgumentException notAUuid) {
            throw new DownloadLinkInvalidException("Malformed download token release id");
        }
    }

    private Instant parseExpiry(String rawEpochSeconds) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(rawEpochSeconds));
        } catch (NumberFormatException notANumber) {
            throw new DownloadLinkInvalidException("Malformed download token expiry");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedDownloadLink(String token, Instant expiresAt) {
    }
}
