package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BootstrapAdminInitializerTest {

    private static final String USERNAME = "jordy";
    private static final String PASSWORD = "bootstrap-password-12";
    private static final String ENCODED = "{bcrypt}$2a$10$bootstrap";

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void blankConfigurationIsIgnored() {
        initializerWith("", "").run(null);

        verifyNoInteractions(userAccountRepository, passwordEncoder);
    }

    @Test
    void skipsWhenAccountsAlreadyExist() {
        when(userAccountRepository.count()).thenReturn(1L);

        initializerWith(USERNAME, PASSWORD).run(null);

        verify(userAccountRepository).count();
        verifyNoMoreInteractions(userAccountRepository);
    }

    @Test
    void createsTheFirstAdminOnAnEmptyDatabase() {
        when(userAccountRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED);
        ArgumentCaptor<UserAccount> savedAccount = ArgumentCaptor.forClass(UserAccount.class);

        initializerWith(USERNAME, PASSWORD).run(null);

        verify(userAccountRepository).save(savedAccount.capture());
        UserAccount admin = savedAccount.getValue();
        assertSoftly(softly -> {
            softly.assertThat(admin.getUsername()).isEqualTo(USERNAME);
            softly.assertThat(admin.getStatus()).isEqualTo(UserStatus.APPROVED);
            softly.assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            softly.assertThat(admin.getPasswordHash()).isEqualTo(ENCODED);
        });
    }

    @Test
    void aTooShortConfiguredPasswordFailsFast() {
        when(userAccountRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> initializerWith(USERNAME, "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_BOOTSTRAP_ADMIN_PASSWORD");
        verify(userAccountRepository).count();
        verifyNoMoreInteractions(userAccountRepository);
        verifyNoInteractions(passwordEncoder);
    }

    private BootstrapAdminInitializer initializerWith(String username, String password) {
        return new BootstrapAdminInitializer(userAccountRepository, passwordEncoder,
                new IdentityProperties(
                        new IdentityProperties.Password(12),
                        new IdentityProperties.Lockout(5, 15),
                        new IdentityProperties.Bootstrap(username, password),
                        new IdentityProperties.WebAuthn("localhost", "JordyLab", List.of())));
    }
}
