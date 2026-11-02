package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.rest.controller.model.UserSessionsResponse;
import dev.jordy.jordylab.settings.service.CannotChangeOwnAccountException;
import dev.jordy.jordylab.settings.service.UserNotFoundException;
import dev.jordy.jordylab.settings.service.UserSessionNotFoundException;
import dev.jordy.jordylab.settings.service.UserSessionService;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A person's sessions (spec 014 US5, contracts/user-management-api.md). Ending one session signs out only that device;
 * ending all also kills the phone app's offline session.
 */
@RestController
@RequestMapping("/api/settings/users/{id}/sessions")
@RequiredArgsConstructor
public class SettingsUserSessionsController {

    private final UserSessionService userSessionService;

    @GetMapping
    public UserSessionsResponse list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return UserSessionsResponse.from(userSessionService.listSessions(callerId(jwt), id));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> endOne(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @PathVariable String sessionId) {
        userSessionService.endSession(callerId(jwt), id, sessionId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/end-all")
    public ResponseEntity<Void> endAll(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        userSessionService.endAllSessions(callerId(jwt), id);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorBody> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("USER_NOT_FOUND"));
    }

    @ExceptionHandler(UserSessionNotFoundException.class)
    public ResponseEntity<ErrorBody> handleSessionNotFound(UserSessionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("SESSION_NOT_FOUND"));
    }

    @ExceptionHandler(CannotChangeOwnAccountException.class)
    public ResponseEntity<ErrorBody> handleCannotChangeOwnAccount(CannotChangeOwnAccountException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("CANNOT_CHANGE_OWN_ACCOUNT"));
    }

    @ExceptionHandler(KeycloakUnavailableException.class)
    public ResponseEntity<ErrorBody> handleKeycloakUnavailable(KeycloakUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorBody("KEYCLOAK_UNAVAILABLE"));
    }

    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private record ErrorBody(String reason) {
    }
}
