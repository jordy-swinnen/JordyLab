package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.rest.controller.model.PendingCountResponse;
import dev.jordy.jordylab.settings.rest.controller.model.ResetPasswordResponse;
import dev.jordy.jordylab.settings.rest.controller.model.UserResponse;
import dev.jordy.jordylab.settings.rest.controller.model.PersonChatLimit;
import dev.jordy.jordylab.settings.rest.controller.model.UsersResponse;
import dev.jordy.jordylab.settings.service.AppUser;
import dev.jordy.jordylab.settings.service.GuestChatLimitService;
import dev.jordy.jordylab.settings.service.AdminAccountProtectedException;
import dev.jordy.jordylab.settings.service.CannotChangeOwnAccountException;
import dev.jordy.jordylab.settings.service.KeycloakUserAdministrationService;
import dev.jordy.jordylab.settings.service.LastAdminProtectedException;
import dev.jordy.jordylab.settings.service.UserDeletionFailedException;
import dev.jordy.jordylab.settings.service.UserDeletionService;
import dev.jordy.jordylab.settings.service.UserNotApprovedException;
import dev.jordy.jordylab.settings.service.UserNotFoundException;
import dev.jordy.jordylab.settings.service.UserSessionService;
import dev.jordy.jordylab.settings.service.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Admin-only user administration over Keycloak
 * (contracts/settings-users-api.md, spec FR-001–FR-010). {@code /api/settings/**} already
 * requires {@code admin} in {@code SecurityConfig} — no method-level checks needed here.
 */
@Slf4j
@RestController
@RequestMapping("/api/settings/users")
@RequiredArgsConstructor
public class SettingsUsersController {

    private final KeycloakUserAdministrationService userAdministrationService;
    private final GuestChatLimitService guestChatLimitService;
    private final UserDeletionService userDeletionService;
    private final UserSessionService userSessionService;

    @GetMapping
    public UsersResponse getUsers(@RequestParam(required = false, defaultValue = "ALL") String status) {
        List<UserResponse> users = userAdministrationService.listUsers(parseStatus(status)).stream()
                .map(this::toResponse)
                .toList();

        return new UsersResponse(users);
    }

    private UserResponse toResponse(AppUser user) {
        PersonChatLimit chatLimit = user.realmRoles().contains("admin")
                ? null
                : PersonChatLimit.from(guestChatLimitService.effectiveFor(user.id().toString()));

        return UserResponse.from(user, chatLimit, userSessionService.countFor(user.id()));
    }

    @GetMapping("/pending-count")
    public PendingCountResponse getPendingCount() {
        return new PendingCountResponse(userAdministrationService.pendingCount());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        userAdministrationService.approve(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id) {
        userAdministrationService.reject(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        userAdministrationService.revoke(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable UUID id) {
        return new ResetPasswordResponse(userAdministrationService.resetPassword(id));
    }

    /** Spec 014 FR-014: the panel asks for confirmation; this endpoint deletes. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        userDeletionService.deletePerson(UUID.fromString(jwt.getSubject()), id);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(CannotChangeOwnAccountException.class)
    public ResponseEntity<ErrorBody> handleCannotChangeOwnAccount(CannotChangeOwnAccountException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("CANNOT_CHANGE_OWN_ACCOUNT"));
    }

    @ExceptionHandler(AdminAccountProtectedException.class)
    public ResponseEntity<ErrorBody> handleAdminProtected(AdminAccountProtectedException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("ADMIN_PROTECTED"));
    }

    @ExceptionHandler(UserDeletionFailedException.class)
    public ResponseEntity<DeletionFailureBody> handleDeletionFailed(UserDeletionFailedException exception) {
        log.warn("Deleting an account failed at {}", exception.stage(), exception);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new DeletionFailureBody("KEYCLOAK_UNAVAILABLE", exception.stage().name()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorBody> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("USER_NOT_FOUND"));
    }

    @ExceptionHandler(LastAdminProtectedException.class)
    public ResponseEntity<ErrorBody> handleLastAdminProtected(LastAdminProtectedException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("LAST_ADMIN_PROTECTED"));
    }

    @ExceptionHandler(UserNotApprovedException.class)
    public ResponseEntity<ErrorBody> handleUserNotApproved(UserNotApprovedException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("USER_NOT_APPROVED"));
    }

    @ExceptionHandler(KeycloakUnavailableException.class)
    public ResponseEntity<ErrorBody> handleKeycloakUnavailable(KeycloakUnavailableException exception) {
        // The message names only the Admin REST method and path (never tokens or secrets); without it a
        // permission gap like BUG-033 surfaced as a bare 503 with nothing in the log.
        log.warn("Keycloak admin call failed: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorBody("KEYCLOAK_UNAVAILABLE"));
    }

    private UserStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }

        return UserStatus.valueOf(status.toUpperCase(Locale.ROOT));
    }

    private record ErrorBody(String reason) {
    }

    private record DeletionFailureBody(String reason, String stage) {
    }
}
