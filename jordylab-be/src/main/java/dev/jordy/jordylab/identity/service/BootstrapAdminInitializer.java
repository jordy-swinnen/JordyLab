package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
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

/**
 * Creates the first admin on a fresh database (spec 019 D9), the platform's own first admin. Runs only
 * while the account table is empty; afterwards the credentials in configuration are inert. The password is never
 * logged, and a too-short configured password fails fast instead of creating a weak admin.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = properties.bootstrap().username();
        String password = properties.bootstrap().password();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return;
        }
        if (userAccountRepository.count() > 0) {
            log.info("Bootstrap admin skipped: accounts already exist");

            return;
        }
        if (password.length() < properties.password().minLength()) {
            throw new IllegalStateException("IDENTITY_BOOTSTRAP_ADMIN_PASSWORD must be at least "
                    + properties.password().minLength() + " characters");
        }
        UserAccount admin = UserAccount.builder()
                .username(username.trim().toLowerCase(Locale.ROOT))
                .displayName("Jordy")
                .status(UserStatus.APPROVED)
                .role(UserRole.ADMIN)
                .build();
        admin.applyPassword(passwordEncoder.encode(password));
        userAccountRepository.save(admin);
        log.info("Bootstrap admin created: {}", admin.getUsername());
    }
}
