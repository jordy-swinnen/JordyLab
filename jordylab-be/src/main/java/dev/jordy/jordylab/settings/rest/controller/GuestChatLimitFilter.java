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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Pre-checks the guest daily LibBot limit (FR-009, spec 013 research A8) on {@code POST /api/gamecatalog/libbot/ask}:
 * a guest at the limit gets a {@code 429} before any stream opens. Nothing is counted here: an event stream is
 * {@code 200} long before its answer exists, so the count moves to {@code GuestChatUsageListener}, which reacts to the
 * answered-message event and therefore never counts a failed or abandoned question.
 * Declared as a {@code @Bean} in {@link dev.jordy.jordylab.settings.SettingsConfiguration} rather
 * than {@code @Component} (see that class's javadoc for why), but registered with the same
 * default Spring Boot filter ordering — after the security filter chain — so it only ever runs
 * once the request has already been authenticated and authorized, with a fully populated
 * {@link SecurityContextHolder}. Admins are exempt.
 */
@RequiredArgsConstructor
public class GuestChatLimitFilter extends OncePerRequestFilter {

    private static final String CHAT_PATH = "/api/gamecatalog/libbot/ask";
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
    }

    private boolean isAdmin(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    private void writeLimitReached(HttpServletResponse response, LocalDate today, ZoneId zone) throws IOException {
        Instant resetsAt = today.plusDays(1).atStartOfDay(zone).toInstant();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(new LimitReachedBody("CHAT_LIMIT_REACHED",
                settingsProperties.guestChat().dailyLimit(), resetsAt)));
    }

    private record LimitReachedBody(String reason, int limit, Instant resetsAt) {
    }
}
