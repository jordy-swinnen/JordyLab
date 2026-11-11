package dev.jordy.jordylab.identity.domain;

import com.google.common.base.Preconditions;
import dev.jordy.jordylab.identity.AppAccessRemoved;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.UserAccessRemoved;
import dev.jordy.jordylab.shared.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One person's account (spec 019 US1, D7). The stored {@code status} replaces the value spec 014 derived from the old provider;
 * {@code appGrants} replaces the {@code app-gamecatalog} role. For migrated users the id is the original subject,
 * so every owner column keeps matching (FR-011).
 */
@Entity
@Table(schema = "identity", name = "user_account")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount extends BaseEntity<UserAccount> {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "display_name")
    private String displayName;

    /** BCrypt hash; {@code null} only for a migrated user until their first approved reset. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    /** The account-wide theme (spec 024 US3); {@link UserTheme#SYSTEM} follows the device. */
    @Convert(converter = UserThemeConverter.class)
    @Column(nullable = false)
    private UserTheme theme;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "identity", name = "user_app_grant",
            joinColumns = @JoinColumn(name = "user_account_id"))
    @Column(name = "app")
    @Enumerated(EnumType.STRING)
    private Set<ManagedApp> appGrants;

    /** Approves a pending or rejected account with a role and the default app grants (spec 014 FR-008 parity). */
    public void approve(UserRole newRole) {
        Preconditions.checkArgument(newRole != null, "role is required");
        this.status = UserStatus.APPROVED;
        this.role = newRole;
        this.appGrants.addAll(ManagedApp.defaultGrants());
    }

    /** Rejects the account; its access and data are cleaned up in response to the event. */
    public void reject() {
        this.status = UserStatus.REJECTED;
        registerEvent(new UserAccessRemoved(this.id.toString()));
    }

    /** Revokes an approved account: no role, no grants, no sign-in (the service ends its sessions). */
    public void revoke() {
        this.status = UserStatus.REVOKED;
        this.role = null;
        this.appGrants.clear();
        registerEvent(new UserAccessRemoved(this.id.toString()));
    }

    public void grantApp(ManagedApp app) {
        Preconditions.checkArgument(app != null, "app is required");
        Preconditions.checkArgument(app.grantable(), "app %s is not grantable", app.key());
        if (this.appGrants.add(app)) {
            registerEvent(new AppAccessRemoved(this.id.toString(), app.key()));
        }
    }

    public void revokeApp(ManagedApp app) {
        Preconditions.checkArgument(app != null, "app is required");
        if (this.appGrants.remove(app)) {
            registerEvent(new AppAccessRemoved(this.id.toString(), app.key()));
        }
    }

    /** Sets a new password hash and clears the lockout state. */
    public void applyPassword(String newPasswordHash) {
        Preconditions.checkArgument(StringUtils.hasText(newPasswordHash), "passwordHash is required");
        this.passwordHash = newPasswordHash;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public void changeDisplayName(String newDisplayName) {
        Preconditions.checkArgument(StringUtils.hasText(newDisplayName), "displayName is required");
        this.displayName = newDisplayName.trim();
    }

    /** Sets the account-wide theme (spec 024 FR-009); unknown values fall back to following the device. */
    public void changeTheme(UserTheme newTheme) {
        this.theme = newTheme == null ? UserTheme.SYSTEM : newTheme;
    }

    public void recordFailedSignIn(Instant now, int maxFailures, Duration lockDuration) {
        Preconditions.checkArgument(now != null, "now is required");
        Preconditions.checkArgument(maxFailures > 0, "maxFailures must be positive");
        this.failedAttempts++;
        if (this.failedAttempts >= maxFailures) {
            this.lockedUntil = now.plus(lockDuration);
            this.failedAttempts = 0;
        }
    }

    public void recordSuccessfulSignIn() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public boolean isLocked(Instant now) {
        return this.lockedUntil != null && this.lockedUntil.isAfter(now);
    }

    public boolean isApproved() {
        return this.status == UserStatus.APPROVED;
    }

    public boolean isPending() {
        return this.status == UserStatus.PENDING;
    }

    /** The role key ({@code admin}/{@code guest}), empty while the account has no role yet. */
    public Optional<String> roleKey() {
        return Optional.ofNullable(this.role).map(UserRole::key);
    }

    public static class UserAccountBuilder {
        public UserAccount build() {
            Preconditions.checkArgument(StringUtils.hasText(username), "username is required");
            username = username.trim().toLowerCase(Locale.ROOT);
            if (status == null) {
                status = UserStatus.PENDING;
            }
            if (appGrants == null) {
                appGrants = new HashSet<>();
            } else {
                appGrants = new HashSet<>(appGrants);
            }
            if (theme == null) {
                theme = UserTheme.SYSTEM;
            }
            if (id == null) {
                id = UUID.randomUUID();
            }

            return new UserAccount(id, username, displayName, passwordHash, role, status, failedAttempts,
                    theme, lockedUntil, appGrants);
        }
    }
}
