package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.identity.rest.controller.model.ChangePasswordRequest;
import dev.jordy.jordylab.identity.rest.controller.model.CurrentUserResponse;
import dev.jordy.jordylab.identity.rest.controller.model.LoginRequest;
import dev.jordy.jordylab.identity.rest.controller.model.RegisterRequest;
import dev.jordy.jordylab.identity.rest.controller.model.RegisterResponse;
import dev.jordy.jordylab.identity.rest.controller.model.UpdateDisplayNameRequest;
import dev.jordy.jordylab.identity.service.InvalidCredentialsException;
import dev.jordy.jordylab.identity.service.SessionSignInService;
import dev.jordy.jordylab.identity.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sign-in, sign-out, the current user and self-service actions (spec 019 US1/US3, contracts/identity-api.md).
 * A successful login creates the server-side session (fixation-protected) and stores the security context in it;
 * subsequent requests are authenticated by the session cookie, protected by the CSRF token Angular already sends.
 */
@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class AuthenticationController {

    private final SessionSignInService sessionSignInService;
    private final UserAccountService userAccountService;
    private final CurrentUserResolver currentUserResolver;
    private final UserAccountRepository userAccountRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        UserAccount account = userAccountService.register(request.username(), request.password(),
                request.displayName());

        return new RegisterResponse(account.getId(), account.getUsername(), account.getStatus().name());
    }

    @PostMapping("/auth/login")
    public CurrentUserResponse login(@RequestBody LoginRequest request, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = sessionSignInService.signIn(request.username(), request.password());
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        return currentUserResponse(((AuthenticatedUser) authentication.getPrincipal()).id());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        return currentUserResponse(requireUserId());
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateDisplayName(@RequestBody UpdateDisplayNameRequest request) {
        userAccountService.changeOwnDisplayName(requireUserId(), request.displayName());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        userAccountService.changeOwnPassword(requireUserId(), request.currentPassword(), request.newPassword());

        return ResponseEntity.noContent().build();
    }

    /** Writes the {@code XSRF-TOKEN} cookie Angular reads before the first POST (spec 019 D4). */
    @GetMapping("/auth/csrf")
    public ResponseEntity<Void> csrfToken(CsrfToken csrfToken, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        csrfTokenRepository.saveToken(csrfToken, servletRequest, servletResponse);

        return ResponseEntity.noContent().build();
    }

    private UUID requireUserId() {
        return currentUserResolver.currentUser()
                .map(AuthenticatedUser::id)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private CurrentUserResponse currentUserResponse(UUID userId) {
        return userAccountRepository.findById(userId)
                .map(CurrentUserResponse::from)
                .orElseThrow(InvalidCredentialsException::new);
    }
}
