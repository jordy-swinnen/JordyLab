package dev.jordy.jordylab.identity.domain;

import dev.jordy.jordylab.identity.AppAccessRemoved;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.UserAccessRemoved;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static dev.jordy.jordylab.identity.domain.UserAccountTestBuilder.DEFAULT_DISPLAY_NAME;
import static dev.jordy.jordylab.identity.domain.UserAccountTestBuilder.DEFAULT_ID;
import static dev.jordy.jordylab.identity.domain.UserAccountTestBuilder.DEFAULT_PASSWORD_HASH;
import static dev.jordy.jordylab.identity.domain.UserAccountTestBuilder.DEFAULT_USERNAME;
import static dev.jordy.jordylab.identity.domain.UserAccountTestBuilder.aDefaultUserAccount;
import static dev.jordy.jordylab.identity.domain.UserAccountTestBuilder.aPendingUserAccount;
import static dev.jordy.jordylab.identity.domain.UserAccountTestBuilder.aUserAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class UserAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void buildUserAccount() {
        UserAccount account = aUserAccount().build();

        assertSoftly(softly -> {
            softly.assertThat(account.getId()).isEqualTo(DEFAULT_ID);
            softly.assertThat(account.getUsername()).isEqualTo(DEFAULT_USERNAME);
            softly.assertThat(account.getDisplayName()).isEqualTo(DEFAULT_DISPLAY_NAME);
            softly.assertThat(account.getPasswordHash()).isEqualTo(DEFAULT_PASSWORD_HASH);
            softly.assertThat(account.getRole()).isEqualTo(UserRole.GUEST);
            softly.assertThat(account.getStatus()).isEqualTo(UserStatus.APPROVED);
            softly.assertThat(account.getAppGrants()).containsExactly(ManagedApp.GAME_CATALOG);
        });
    }

    @Test
    void buildWithoutUsername() {
        assertThatThrownBy(() -> UserAccount.builder().build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithBlankUsername() {
        assertThatThrownBy(() -> UserAccount.builder().username(" ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildLowercasesUsername() {
        UserAccount account = UserAccount.builder().username("  Jordy.Swinnen ").build();

        assertThat(account.getUsername()).isEqualTo("jordy.swinnen");
    }

    @Test
    void newAccountIsPendingWithoutRole() {
        UserAccount account = aPendingUserAccount();

        assertSoftly(softly -> {
            softly.assertThat(account.isPending()).isTrue();
            softly.assertThat(account.isApproved()).isFalse();
            softly.assertThat(account.getRole()).isNull();
            softly.assertThat(account.roleKey()).isEmpty();
            softly.assertThat(account.getAppGrants()).isEmpty();
        });
    }

    @Test
    void approveGrantsRoleAndDefaultApps() {
        UserAccount account = aPendingUserAccount();

        account.approve(UserRole.GUEST);

        assertSoftly(softly -> {
            softly.assertThat(account.isApproved()).isTrue();
            softly.assertThat(account.getRole()).isEqualTo(UserRole.GUEST);
            softly.assertThat(account.roleKey()).contains("guest");
            softly.assertThat(account.getAppGrants()).containsExactly(ManagedApp.GAME_CATALOG);
        });
    }

    @Test
    void rejectRegistersUserAccessRemoved() {
        UserAccount account = aPendingUserAccount();

        account.reject();

        assertSoftly(softly -> {
            softly.assertThat(account.getStatus()).isEqualTo(UserStatus.REJECTED);
            softly.assertThat(eventsOf(account)).containsExactly(new UserAccessRemoved(DEFAULT_ID.toString()));
        });
    }

    @Test
    void revokeClearsRoleAndGrants() {
        UserAccount account = aDefaultUserAccount();

        account.revoke();

        assertSoftly(softly -> {
            softly.assertThat(account.getStatus()).isEqualTo(UserStatus.REVOKED);
            softly.assertThat(account.getRole()).isNull();
            softly.assertThat(account.getAppGrants()).isEmpty();
            softly.assertThat(eventsOf(account)).containsExactly(new UserAccessRemoved(DEFAULT_ID.toString()));
        });
    }

    @Test
    void grantAppRegistersAppAccessRemovedOnce() {
        UserAccount account = aPendingUserAccount();

        account.grantApp(ManagedApp.GAME_CATALOG);
        account.grantApp(ManagedApp.GAME_CATALOG);

        assertSoftly(softly -> {
            softly.assertThat(account.getAppGrants()).containsExactly(ManagedApp.GAME_CATALOG);
            softly.assertThat(eventsOf(account))
                    .containsExactly(new AppAccessRemoved(DEFAULT_ID.toString(), ManagedApp.GAME_CATALOG.key()));
        });
    }

    @Test
    void revokeAppRegistersAppAccessRemoved() {
        UserAccount account = aDefaultUserAccount();

        account.revokeApp(ManagedApp.GAME_CATALOG);

        assertSoftly(softly -> {
            softly.assertThat(account.getAppGrants()).isEmpty();
            softly.assertThat(eventsOf(account))
                    .containsExactly(new AppAccessRemoved(DEFAULT_ID.toString(), ManagedApp.GAME_CATALOG.key()));
        });
    }

    @Test
    void grantNonGrantableAppFails() {
        UserAccount account = aPendingUserAccount();

        assertThatThrownBy(() -> account.grantApp(ManagedApp.FNA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyPasswordClearsLockout() {
        UserAccount account = aDefaultUserAccount();
        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));
        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));
        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));
        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));
        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));

        account.applyPassword("{bcrypt}$2a$10$newhash");

        assertSoftly(softly -> {
            softly.assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$newhash");
            softly.assertThat(account.getFailedAttempts()).isZero();
            softly.assertThat(account.getLockedUntil()).isNull();
            softly.assertThat(account.isLocked(NOW)).isFalse();
        });
    }

    @Test
    void recordFailedSignInLocksAfterMaxFailures() {
        UserAccount account = aDefaultUserAccount();

        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));
        for (int attempt = 0; attempt < 3; attempt++) {
            account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));
        }

        assertThat(account.isLocked(NOW)).isFalse();

        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));

        assertSoftly(softly -> {
            softly.assertThat(account.isLocked(NOW)).isTrue();
            softly.assertThat(account.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
            softly.assertThat(account.getFailedAttempts()).isZero();
            softly.assertThat(account.isLocked(NOW.plus(Duration.ofMinutes(16)))).isFalse();
        });
    }

    @Test
    void recordSuccessfulSignInClearsLockoutCounters() {
        UserAccount account = aDefaultUserAccount();
        account.recordFailedSignIn(NOW, 5, Duration.ofMinutes(15));

        account.recordSuccessfulSignIn();

        assertSoftly(softly -> {
            softly.assertThat(account.getFailedAttempts()).isZero();
            softly.assertThat(account.getLockedUntil()).isNull();
        });
    }

    @Test
    void changeThemeStoresTheAccountPreference() {
        UserAccount account = aDefaultUserAccount();

        account.changeTheme(UserTheme.HEATHER);

        assertThat(account.getTheme()).isEqualTo(UserTheme.HEATHER);
    }

    @Test
    void changeThemeWithNullFallsBackToSystem() {
        UserAccount account = aDefaultUserAccount();

        account.changeTheme(null);

        assertThat(account.getTheme()).isEqualTo(UserTheme.SYSTEM);
    }

    @Test
    void newAccountFollowsTheDeviceByDefault() {
        assertThat(UserAccount.builder().username("new.person").build().getTheme())
                .isEqualTo(UserTheme.SYSTEM);
    }

    private static Collection<Object> eventsOf(UserAccount account) {
        return ReflectionTestUtils.invokeMethod(account, "domainEvents");
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(UserAccount.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }

    @Test
    void exposesRoleKeys() {
        assertSoftly(softly -> {
            softly.assertThat(List.of(UserRole.values()))
                    .extracting(UserRole::key)
                    .containsExactly("admin", "guest");
        });
    }
}
