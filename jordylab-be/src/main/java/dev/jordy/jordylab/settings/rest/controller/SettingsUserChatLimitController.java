package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.rest.controller.model.SavePersonalChatLimitRequest;
import dev.jordy.jordylab.settings.service.AdminAccountProtectedException;
import dev.jordy.jordylab.settings.service.CannotChangeOwnAccountException;
import dev.jordy.jordylab.settings.service.InvalidLimitException;
import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.service.UserChatLimitService;
import dev.jordy.jordylab.settings.service.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * One person's personal chat limit (spec 014 FR-010 to FR-013). A personal limit replaces the default for that person
 * only; clearing it returns them to the default from their next message.
 */
@RestController
@RequestMapping("/api/settings/users/{id}/chat-limit")
@RequiredArgsConstructor
public class SettingsUserChatLimitController {

    private final UserChatLimitService userChatLimitService;

    @PutMapping
    public ResponseEntity<Void> setPersonal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody SavePersonalChatLimitRequest request) {
        userChatLimitService.setPersonal(callerId(jwt), id, request.dailyLimit());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearPersonal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        userChatLimitService.clearPersonal(callerId(jwt), id);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorBody> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("USER_NOT_FOUND"));
    }

    @ExceptionHandler(InvalidLimitException.class)
    public ResponseEntity<ErrorBody> handleInvalidLimit(InvalidLimitException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_LIMIT"));
    }

    @ExceptionHandler(CannotChangeOwnAccountException.class)
    public ResponseEntity<ErrorBody> handleCannotChangeOwnAccount(CannotChangeOwnAccountException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("CANNOT_CHANGE_OWN_ACCOUNT"));
    }

    @ExceptionHandler(AdminAccountProtectedException.class)
    public ResponseEntity<ErrorBody> handleAdminProtected(AdminAccountProtectedException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("ADMIN_PROTECTED"));
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
