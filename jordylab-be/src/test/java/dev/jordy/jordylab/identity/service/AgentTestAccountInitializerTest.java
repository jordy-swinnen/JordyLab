package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.IdentityProperties;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentTestAccountInitializerTest {

    private static final String USERNAME = "agent";
    private static final String PASSWORD = "agent-password-12";
    private static final String ENCODED = "{bcrypt}$2a$10$agent";

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void blankConfigurationIsIgnored() {
        initializerWith("", "").run(null);

        verifyNoInteractions(userAccountRepository, passwordEncoder);
    }

    @Test
    void aTooShortConfiguredPasswordFailsFast() {
        assertThatThrownBy(() -> initializerWith(USERNAME, "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGENT_TEST_PASSWORD");
        verifyNoInteractions(userAccountRepository, passwordEncoder);
    }

    @Test
    void anInvalidConfiguredUsernameFailsFast() {
        assertThatThrownBy(() -> initializerWith("Not A Username", PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGENT_TEST_USERNAME");
        verifyNoInteractions(userAccountRepository, passwordEncoder);
    }

    @Test
    void createsAnApprovedGuestWithTheGameCatalogGrant() {
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED);
        ArgumentCaptor<UserAccount> savedAccount = ArgumentCaptor.forClass(UserAccount.class);

        initializerWith(USERNAME, PASSWORD).run(null);

        verify(userAccountRepository).save(savedAccount.capture());
        UserAccount account = savedAccount.getValue();
        assertSoftly(softly -> {
            softly.assertThat(account.getUsername()).isEqualTo(USERNAME);
            softly.assertThat(account.getDisplayName()).isEqualTo("Agent");
            softly.assertThat(account.getStatus()).isEqualTo(UserStatus.APPROVED);
            softly.assertThat(account.getRole()).isEqualTo(UserRole.GUEST);
            softly.assertThat(account.getAppGrants()).containsExactly(ManagedApp.GAME_CATALOG);
            softly.assertThat(account.getPasswordHash()).isEqualTo(ENCODED);
        });
    }

    @Test
    void reEnsuresARevokedAccount() {
        UserAccount account = UserAccount.builder()
                .username(USERNAME)
                .displayName("Agent")
                .status(UserStatus.REVOKED)
                .build();
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED);

        initializerWith(USERNAME, PASSWORD).run(null);

        verify(userAccountRepository).save(account);
        assertSoftly(softly -> {
            softly.assertThat(account.getStatus()).isEqualTo(UserStatus.APPROVED);
            softly.assertThat(account.getRole()).isEqualTo(UserRole.GUEST);
            softly.assertThat(account.getAppGrants()).containsExactly(ManagedApp.GAME_CATALOG);
            softly.assertThat(account.getPasswordHash()).isEqualTo(ENCODED);
        });
    }

    @Test
    void refusesToTouchAnExistingAdminAccount() {
        UserAccount admin = UserAccount.builder()
                .username(USERNAME)
                .status(UserStatus.APPROVED)
                .role(UserRole.ADMIN)
                .build();
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> initializerWith(USERNAME, PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin account");
        verifyNoInteractions(passwordEncoder);
    }

    private AgentTestAccountInitializer initializerWith(String username, String password) {
        return new AgentTestAccountInitializer(userAccountRepository, passwordEncoder,
                new IdentityProperties(
                        new IdentityProperties.Password(12),
                        new IdentityProperties.Lockout(5, 15),
                        new IdentityProperties.Bootstrap("", ""),
                        new IdentityProperties.AgentTest(username, password),
                        new IdentityProperties.WebAuthn("localhost", "JordyLab", List.of())));
    }
}
