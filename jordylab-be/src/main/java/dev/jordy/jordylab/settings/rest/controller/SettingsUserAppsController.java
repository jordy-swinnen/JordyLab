package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.rest.controller.model.AppsResponse;
import dev.jordy.jordylab.settings.service.AppAccessService;
import dev.jordy.jordylab.settings.service.AppNotGrantableException;
import dev.jordy.jordylab.settings.service.AppNotFoundException;
import dev.jordy.jordylab.settings.service.CannotChangeOwnAccountException;
import dev.jordy.jordylab.settings.service.UserNotFoundException;
import dev.jordy.jordylab.settings.service.UserNotApprovedException;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Per-app access for one person (spec 014 US1/US2, contracts/user-management-api.md). Path-based {@code /api/settings/**}
 * already requires {@code admin} in {@code SecurityConfig}.
 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsUserAppsController {

    private final AppAccessService appAccessService;

    @GetMapping("/apps")
    public AppsResponse getApps() {
        return AppsResponse.from(ManagedApp.values());
    }

    @PutMapping("/users/{id}/apps/{appKey}")
    public ResponseEntity<Void> grantApp(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @PathVariable String appKey) {
        appAccessService.grantApp(callerId(jwt), id, appKey);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/apps/{appKey}")
    public ResponseEntity<Void> removeApp(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @PathVariable String appKey) {
        appAccessService.removeApp(callerId(jwt), id, appKey);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorBody> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("USER_NOT_FOUND"));
    }

    @ExceptionHandler(AppNotFoundException.class)
    public ResponseEntity<ErrorBody> handleAppNotFound(AppNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("APP_NOT_FOUND"));
    }

    @ExceptionHandler(AppNotGrantableException.class)
    public ResponseEntity<ErrorBody> handleAppNotGrantable(AppNotGrantableException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("APP_NOT_GRANTABLE"));
    }

    @ExceptionHandler(UserNotApprovedException.class)
    public ResponseEntity<ErrorBody> handleUserNotApproved(UserNotApprovedException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("USER_NOT_APPROVED"));
    }

    @ExceptionHandler(CannotChangeOwnAccountException.class)
    public ResponseEntity<ErrorBody> handleCannotChangeOwnAccount(CannotChangeOwnAccountException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("CANNOT_CHANGE_OWN_ACCOUNT"));
    }

    @ExceptionHandler(KeycloakUnavailableException.class)
    public ResponseEntity<ErrorBody> handleKeycloakUnavailable(KeycloakUnavailableException exception) {
        log.warn("Keycloak admin call failed: {}", exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorBody("KEYCLOAK_UNAVAILABLE"));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private record ErrorBody(String reason) {
    }
}
