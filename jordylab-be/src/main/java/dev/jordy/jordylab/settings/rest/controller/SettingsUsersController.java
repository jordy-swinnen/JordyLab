package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.rest.controller.model.PendingCountResponse;
import dev.jordy.jordylab.settings.rest.controller.model.ResetPasswordResponse;
import dev.jordy.jordylab.settings.rest.controller.model.UserResponse;
import dev.jordy.jordylab.settings.rest.controller.model.UsersResponse;
import dev.jordy.jordylab.settings.service.KeycloakUserAdministrationService;
import dev.jordy.jordylab.settings.service.LastAdminProtectedException;
import dev.jordy.jordylab.settings.service.UserNotApprovedException;
import dev.jordy.jordylab.settings.service.UserNotFoundException;
import dev.jordy.jordylab.settings.service.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public UsersResponse getUsers(@RequestParam(required = false, defaultValue = "ALL") String status) {
        List<UserResponse> users = userAdministrationService.listUsers(parseStatus(status)).stream()
                .map(UserResponse::from)
                .toList();

        return new UsersResponse(users);
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
}
