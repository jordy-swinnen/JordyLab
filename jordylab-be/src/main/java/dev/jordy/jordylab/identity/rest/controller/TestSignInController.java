package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.identity.rest.controller.model.CurrentUserResponse;
import dev.jordy.jordylab.identity.service.InvalidCredentialsException;
import dev.jordy.jordylab.identity.service.SessionSignInService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * E2E-only session helper (spec 019 US7): the Playwright suite can establish a session for a fixture account without the
 * sign-in form (for example a second person created through the API). The {@code e2e} profile is never active in
 * production — the class is not even a bean there, so the path 404s; {@code ProdTestSignInAbsenceTest} proves it.
 */
@RestController
@RequestMapping("/api/identity/test")
@Profile("e2e")
@RequiredArgsConstructor
public class TestSignInController {

    private final UserAccountRepository userAccountRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/sign-in")
    public CurrentUserResponse signIn(@RequestBody TestSignInRequest request, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        String username = request.username() == null ? "" : request.username().trim().toLowerCase(Locale.ROOT);
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                SessionSignInService.toAuthenticatedUser(account), null,
                SessionSignInService.authoritiesOf(account));
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        return CurrentUserResponse.from(account);
    }

    public record TestSignInRequest(String username) {
    }
}
