package dev.jordy.jordylab.settings.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.jordy.jordylab.settings.SettingsProperties;
import dev.jordy.jordylab.settings.domain.GuestChatUsage;
import dev.jordy.jordylab.settings.domain.repository.GuestChatUsageRepository;
import dev.jordy.jordylab.settings.service.GuestChatLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestChatLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.parse("2026-09-27");
    private static final String GUEST_SUBJECT = "44444444-4444-4444-8444-444444444444";
    private static final String ADMIN_SUBJECT = "55555555-5555-4555-8555-555555555555";

    @Mock
    private GuestChatUsageRepository guestChatUsageRepository;

    @Mock
    private GuestChatLimitService guestChatLimitService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private GuestChatLimitFilter filter;

    @BeforeEach
    void setUp() {
        SettingsProperties properties = new SettingsProperties(
                new SettingsProperties.GuestChat(3, "UTC"), null);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        lenient().when(guestChatLimitService.effectiveFor(GUEST_SUBJECT))
                .thenReturn(new GuestChatLimitService.EffectiveGuestChatLimit(3,
                        GuestChatLimitService.LimitSource.ENVIRONMENT));
        filter = new GuestChatLimitFilter(guestChatUsageRepository, guestChatLimitService, new CurrentUserResolver(),
                properties, objectMapper, CLOCK);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/gamecatalog/libbot/ask");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminIsExemptFromTheLimitAndNeverCounted() throws Exception {
        authenticateAs(ADMIN_SUBJECT, "ROLE_admin");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(guestChatUsageRepository);
    }

    @Test
    void guestUnderTheLimitProceedsAndTheFilterNeverCounts() throws Exception {
        authenticateAs(GUEST_SUBJECT, "ROLE_guest");
        when(guestChatUsageRepository.findByUserSubjectAndUsageDate(GUEST_SUBJECT, TODAY))
                .thenReturn(Optional.of(usageWithCount(1)));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(guestChatUsageRepository, never()).incrementUsage(idCaptor.capture(), eq(GUEST_SUBJECT), eq(TODAY));
    }

    @Test
    void guestAtTheLimitIsRejectedWith429AndTheChainNeverRuns() throws Exception {
        authenticateAs(GUEST_SUBJECT, "ROLE_guest");
        when(guestChatUsageRepository.findByUserSubjectAndUsageDate(GUEST_SUBJECT, TODAY))
                .thenReturn(Optional.of(usageWithCount(3)));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, chain);

        assertSoftly(softly -> {
            softly.assertThat(body.toString()).contains("\"reason\":\"CHAT_LIMIT_REACHED\"");
            softly.assertThat(body.toString()).contains("2026-09-28T00:00:00Z");
            softly.assertThat(body.toString()).contains("\"limit\":3");
        });
        verify(response).setStatus(429);
        verify(chain, never()).doFilter(request, response);
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(guestChatUsageRepository, never()).incrementUsage(idCaptor.capture(), eq(GUEST_SUBJECT), eq(TODAY));
    }

    @Test
    void aFailedChatCallIsNotCounted() throws Exception {
        authenticateAs(GUEST_SUBJECT, "ROLE_guest");
        when(guestChatUsageRepository.findByUserSubjectAndUsageDate(GUEST_SUBJECT, TODAY))
                .thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(guestChatUsageRepository, never()).incrementUsage(idCaptor.capture(), eq(GUEST_SUBJECT), eq(TODAY));
    }

    @Test
    void aNonChatRequestIsNeverInspected() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/gamecatalog/games");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(guestChatUsageRepository);
    }

    private GuestChatUsage usageWithCount(int count) {
        return GuestChatUsage.builder()
                .id(UUID.randomUUID())
                .userSubject(GUEST_SUBJECT)
                .usageDate(TODAY)
                .messageCount(count)
                .build();
    }

    private void authenticateAs(String subject, String authority) {
        String role = "ROLE_admin".equals(authority) ? "admin" : "guest";
        AuthenticatedUser user = new AuthenticatedUser(UUID.fromString(subject), "user-" + role, role, Set.of());
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }
}
