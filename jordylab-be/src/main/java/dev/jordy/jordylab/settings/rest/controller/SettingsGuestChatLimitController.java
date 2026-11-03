package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.rest.controller.model.GuestChatLimitResponse;
import dev.jordy.jordylab.settings.rest.controller.model.SaveGuestChatLimitRequest;
import dev.jordy.jordylab.settings.service.GuestChatLimitService;
import dev.jordy.jordylab.settings.service.InvalidLimitException;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The saved default guest chat limit (spec 014 FR-009, contracts/user-management-api.md). Until the admin saves a value
 * here, the environment value applies; after that, this one does, without a restart.
 */
@RestController
@RequestMapping("/api/settings/guest-chat-limit")
@RequiredArgsConstructor
public class SettingsGuestChatLimitController {

    private final GuestChatLimitService guestChatLimitService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public GuestChatLimitResponse getDefault() {
        return GuestChatLimitResponse.from(guestChatLimitService.describeDefault());
    }

    @PutMapping
    public GuestChatLimitResponse saveDefault(@RequestBody SaveGuestChatLimitRequest request) {
        return GuestChatLimitResponse.from(
                guestChatLimitService.saveDefault(request.dailyLimit(), currentUserResolver.requireCurrentUserSubject()));
    }

    @ExceptionHandler(InvalidLimitException.class)
    public ResponseEntity<ErrorBody> handleInvalidLimit(InvalidLimitException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_LIMIT"));
    }

    private record ErrorBody(String reason) {
    }
}
