package dev.jordy.jordylab.settings.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.settings.SettingsProperties;
import dev.jordy.jordylab.settings.domain.GuestChatUsage;
import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Enforces the guest daily chat limit (FR-009, research D5) on {@code POST /api/gamecatalog/chat}.
 * Registered with Spring Boot's default filter ordering, which places a plain {@code @Component}
 * filter bean after the security filter chain — so it only ever runs once the request has
 * already been authenticated and authorized, with a fully populated {@link SecurityContextHolder}.
 * Admins are exempt; the check runs before the call, the increment only after a 2xx response, so
 * a failed AI call never burns a guest's budget.
 */
@Component
@RequiredArgsConstructor
public class GuestChatLimitFilter extends OncePerRequestFilter {

    private static final String CHAT_PATH = "/api/gamecatalog/chat";
    private static final String ADMIN_AUTHORITY = "ROLE_admin";

    private final GuestChatUsageRepository guestChatUsageRepository;
    private final SettingsProperties settingsProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && CHAT_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || isAdmin(jwtAuthentication)) {
            chain.doFilter(request, response);

            return;
        }

        String subject = jwtAuthentication.getToken().getSubject();
        ZoneId zone = ZoneId.of(settingsProperties.guestChat().zone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int currentCount = guestChatUsageRepository.findByUserSubjectAndUsageDate(subject, today)
                .map(GuestChatUsage::getMessageCount)
                .orElse(0);

        if (currentCount >= settingsProperties.guestChat().dailyLimit()) {
            writeLimitReached(response, today, zone);

            return;
        }

        chain.doFilter(request, response);

        if (response.getStatus() >= HttpStatus.OK.value() && response.getStatus() < HttpStatus.MULTIPLE_CHOICES.value()) {
            guestChatUsageRepository.incrementUsage(UUID.randomUUID(), subject, today);
        }
    }

    private boolean isAdmin(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    private void writeLimitReached(HttpServletResponse response, LocalDate today, ZoneId zone) throws IOException {
        Instant resetsAt = today.plusDays(1).atStartOfDay(zone).toInstant();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(new LimitReachedBody("CHAT_LIMIT_REACHED", resetsAt)));
    }

    private record LimitReachedBody(String reason, Instant resetsAt) {
    }
}
