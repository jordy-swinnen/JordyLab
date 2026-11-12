package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.domain.MachineToken;
import dev.jordy.jordylab.identity.domain.UserTheme;
import dev.jordy.jordylab.identity.rest.controller.model.ThemePreferenceRequest;
import dev.jordy.jordylab.identity.rest.controller.model.ThemePreferenceResponse;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import dev.jordy.jordylab.identity.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The account-wide theme preference (spec 024 US3): both surfaces read the same
 * value, and the desktop app reads it with its machine token because that token
 * belongs to the admin who created it.
 */
@RestController
@RequiredArgsConstructor
public class UserPreferenceController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserAccountService userAccountService;
    private final MachineTokenService machineTokenService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/api/identity/me/preferences")
    public ThemePreferenceResponse ownPreferences() {
        UUID userId = UUID.fromString(currentUserResolver.requireCurrentUserSubject());

        return new ThemePreferenceResponse(userAccountService.themeOf(userId).key());
    }

    @PutMapping("/api/identity/me/preferences")
    public ThemePreferenceResponse updateOwnPreferences(@RequestBody ThemePreferenceRequest request) {
        UUID userId = UUID.fromString(currentUserResolver.requireCurrentUserSubject());
        UserTheme theme = UserTheme.fromKey(request == null ? null : request.theme());
        userAccountService.changeOwnTheme(userId, theme);

        return new ThemePreferenceResponse(theme.key());
    }

    /**
     * The theme of the account that created this machine token — used by the
     * desktop app, which holds no browser session.
     */
    @GetMapping("/api/identity/machines/preferences")
    public ThemePreferenceResponse machinePreferences(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        MachineToken token = machineTokenService.authenticate(header.substring(BEARER_PREFIX.length()).trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        UserTheme theme = userAccountService.themeOf(token.getCreatedBy());

        return new ThemePreferenceResponse(theme.key());
    }
}
