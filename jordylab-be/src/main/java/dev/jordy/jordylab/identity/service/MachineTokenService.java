package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.domain.MachineToken;
import dev.jordy.jordylab.identity.domain.MachineTokenScope;
import dev.jordy.jordylab.identity.domain.repository.MachineTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-machine ingestion tokens (spec 019 US5, D8): a {@code jlmt_} token with 256 bits of randomness, shown exactly once
 * and stored only as a SHA-256 hash, scoped to ingestion, revocable, with a last-used time throttled to one write a
 * minute. The scanner's own client cache path is unchanged.
 */
@Service
@RequiredArgsConstructor
public class MachineTokenService {

    private static final String TOKEN_PREFIX = "jlmt_";
    private static final int TOKEN_BYTES = 32;
    private static final Duration LAST_USED_THROTTLE = Duration.ofMinutes(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MachineTokenRepository machineTokenRepository;
    private final Clock clock;

    @Transactional
    public CreatedMachineToken create(UUID createdBy, String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new InvalidRequestException("A machine name is required");
        }
        byte[] random = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(random);
        String plaintext = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        MachineToken saved = machineTokenRepository.save(MachineToken.builder()
                .name(rawName.trim())
                .tokenHash(hash(plaintext))
                .scope(MachineTokenScope.INGEST)
                .createdBy(createdBy)
                .build());

        return new CreatedMachineToken(saved, plaintext);
    }

    public List<MachineToken> list() {
        return machineTokenRepository.findAllByOrderByCreatedDateDesc();
    }

    @Transactional
    public MachineToken revoke(UUID machineTokenId) {
        MachineToken token = machineTokenRepository.findById(machineTokenId)
                .orElseThrow(MachineTokenNotFoundException::new);
        if (token.isRevoked()) {
            throw new MachineTokenAlreadyRevokedException();
        }
        token.revoke(clock.instant());

        return machineTokenRepository.save(token);
    }

    /** Resolves a presented bearer token; revoked and unknown tokens resolve to empty. */
    public Optional<MachineToken> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }

        return machineTokenRepository.findByTokenHash(hash(rawToken)).filter(token -> !token.isRevoked());
    }

    /** Advances the last-used time, at most once per minute per token. */
    @Transactional
    public void recordUse(MachineToken token) {
        Instant now = clock.instant();
        if (token.getLastUsedAt() == null || token.getLastUsedAt().plus(LAST_USED_THROTTLE).isBefore(now)) {
            token.touchLastUsed(now);
            machineTokenRepository.save(token);
        }
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CreatedMachineToken(MachineToken token, String plaintext) {
    }
}
