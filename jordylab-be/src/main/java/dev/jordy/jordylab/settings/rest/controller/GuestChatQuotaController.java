package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.service.GuestChatQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The allowance shown above LibBot's composer. It lives in {@code settings} because the allowance does (the {@code
 * gamecatalog} module must not depend on {@code settings}), but it is served under LibBot's path, where the page looks
 * for it.
 */
@RestController
@RequestMapping("/api/gamecatalog/libbot/quota")
@RequiredArgsConstructor
public class GuestChatQuotaController {

    private static final String ADMIN_AUTHORITY = "ROLE_admin";

    private final GuestChatQuotaService quotaService;

    @GetMapping
    public QuotaResponse quota(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        GuestChatQuotaService.Quota quota = quotaService.quotaFor(jwt.getSubject(), admin);

        return new QuotaResponse(quota.limit(), quota.remaining(), quota.resetsAt(), quota.exempt());
    }

    public record QuotaResponse(Integer limit, Integer remaining, java.time.Instant resetsAt, boolean exempt) {
    }
}
