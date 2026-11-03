package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.domain.MachineToken;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates the scanner's machine-token chain (spec 019 US5, D5): a valid {@code Bearer jlmt_…} grants
 * {@code ROLE_gamecatalog-scanner} and advances the throttled last-used time. Unknown or revoked tokens are left
 * anonymous, so the stateless chain's entry point answers 401.
 */
@RequiredArgsConstructor
public class MachineTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MachineTokenService machineTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            machineTokenService.authenticate(header.substring(BEARER_PREFIX.length()).trim())
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(MachineToken token) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "machine:" + token.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_gamecatalog-scanner")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        machineTokenService.recordUse(token);
    }
}
