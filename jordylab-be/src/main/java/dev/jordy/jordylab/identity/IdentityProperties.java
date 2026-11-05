package dev.jordy.jordylab.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for the identity module (spec 019 D9, D16). Values come from {@code application.yaml} with
 * environment overrides; the bootstrap credentials are ignored once any account exists.
 */
@ConfigurationProperties(prefix = "jordylab.identity")
public record IdentityProperties(Password password, Lockout lockout, Bootstrap bootstrap, AgentTest agentTest,
        WebAuthn webauthn) {

    /** Minimum length and the not-equal-to-username rule (today's realm policy, without the email rule). */
    public record Password(int minLength) {
    }

    /** Basic brute-force protection: consecutive failures and the lock duration. */
    public record Lockout(int maxFailures, int lockMinutes) {
    }

    /** First-boot admin, created only while the user table is empty. Blank values disable the bootstrap. */
    public record Bootstrap(String username, String password) {
    }

    /**
     * The permanent test account for the coding agent (JOR-86). Re-ensured on every startup as an approved guest with
     * the Game Catalog grant; blank values disable it, and an existing admin account is refused.
     */
    public record AgentTest(String username, String password) {
    }

    /** WebAuthn relying party (spec 019 D16): the production domain, or localhost in dev/e2e. */
    public record WebAuthn(String rpId, String rpName, List<String> allowedOrigins) {
    }
}
