package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keeps the permanent test account for the coding agent usable (JOR-86): an approved guest with the Game Catalog grant
 * whose password is reset to the configured value on every startup. Local development supplies defaults in
 * {@code application-local.yaml}; production supplies {@code AGENT_TEST_*} from SOPS. A blank configuration disables
 * the initializer, and an existing admin account is never modified, so a mistyped username cannot reset the owner's
 * password. The password is never logged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentTestAccountInitializer implements ApplicationRunner {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9._-]{3,40}");
    private static final String DISPLAY_NAME = "Agent";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        IdentityProperties.AgentTest agentTest = properties.agentTest();
        if (agentTest == null
                || !StringUtils.hasText(agentTest.username())
                || !StringUtils.hasText(agentTest.password())) {
            return;
        }
        String username = agentTest.username().trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalStateException("AGENT_TEST_USERNAME must be 3 to 40 characters: lowercase letters, "
                    + "digits, dot, dash or underscore");
        }
        if (agentTest.password().length() < properties.password().minLength()) {
            throw new IllegalStateException("AGENT_TEST_PASSWORD must be at least "
                    + properties.password().minLength() + " characters");
        }
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseGet(() -> UserAccount.builder()
                        .username(username)
                        .displayName(DISPLAY_NAME)
                        .build());
        if (account.getRole() == UserRole.ADMIN) {
            throw new IllegalStateException("AGENT_TEST_USERNAME points at an admin account; refusing to change it");
        }
        // Idempotent: also restores a revoked or rejected test account, without touching other people.
        account.approve(UserRole.GUEST);
        account.grantApp(ManagedApp.GAME_CATALOG);
        account.applyPassword(passwordEncoder.encode(agentTest.password()));
        userAccountRepository.save(account);
        log.info("Agent test account ensured: {}", username);
    }
}
