package dev.jordy.jordylab.feedback.rest.controller;

import dev.jordy.jordylab.feedback.domain.FeedbackApp;
import dev.jordy.jordylab.feedback.domain.FeedbackTicket;
import dev.jordy.jordylab.feedback.domain.FeedbackTicketTestBuilder;
import dev.jordy.jordylab.feedback.domain.FeedbackType;
import dev.jordy.jordylab.feedback.service.FeedbackAppAccessService;
import dev.jordy.jordylab.feedback.service.FeedbackAppNotAllowedException;
import dev.jordy.jordylab.feedback.service.FeedbackLimitReachedException;
import dev.jordy.jordylab.feedback.service.FeedbackSubmissionService;
import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.UserAccountLookup;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The guest API surface (contracts/rest-api.md): the app list, the 201 submit, every error code,
 * and — pinned here — the absolute absence of admin-only fields from anything a guest receives.
 */
@Import({TestSecurityConfig.class, FeedbackAppAccessService.class})
@WebMvcTest(FeedbackTicketController.class)
class FeedbackTicketControllerTest {

    private static final UUID SUBJECT_ID = UUID.fromString("7b5f2d0c-1a2b-4c3d-8e4f-5a6b7c8d9e0f");
    private static final String SUBJECT = SUBJECT_ID.toString();
    private static final String TITLE = "Grid loses my scroll position";
    private static final String DESCRIPTION = "When I open a game and go back, the grid jumps to the top.";
    private static final List<GrantedAuthority> GUEST_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("ROLE_guest"),
            new SimpleGrantedAuthority("ROLE_app-gamecatalog"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedbackSubmissionService submissionService;

    @MockitoBean
    private UserAccountLookup userAccountLookup;

    @org.junit.jupiter.api.BeforeEach
    void resolveDisplayName() {
        when(userAccountLookup.find(SUBJECT_ID)).thenReturn(Optional.of(
                new UserAccountLookup.AccountRef(SUBJECT_ID, "ada.palmer", "Ada Palmer", "guest")));
    }

    @Test
    void getAppsReturnsTheSendersApps() throws Exception {
        mockMvc.perform(get("/api/feedback/apps").with(authentication(guestAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apps.length()").value(1))
                .andExpect(jsonPath("$.apps[0].id").value("GAME_CATALOG"))
                .andExpect(jsonPath("$.apps[0].name").value("Game Catalog"));
    }

    @Test
    void submitReturnsTheGuestViewAndNeverAdminFields() throws Exception {
        when(submissionService.submit(eq(SUBJECT), eq("Ada Palmer"), eq(GUEST_AUTHORITIES),
                eq(FeedbackApp.GAME_CATALOG), eq(FeedbackType.BUG_REPORT), eq(TITLE), eq(DESCRIPTION)))
                .thenReturn(FeedbackTicketTestBuilder.aDefaultFeedbackTicket());

        mockMvc.perform(post("/api/feedback/tickets")
                        .with(authentication(guestAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app":"GAME_CATALOG","type":"BUG_REPORT","title":"%s","description":"%s"}
                                """.formatted(TITLE, DESCRIPTION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.app").value("Game Catalog"))
                .andExpect(jsonPath("$.type").value("Bug report"))
                .andExpect(jsonPath("$.state").value("NEW"))
                .andExpect(jsonPath("$.title").value(TITLE))
                .andExpect(content().string(not(containsString("privateNote"))))
                .andExpect(content().string(not(containsString("backlogTicketUrl"))))
                .andExpect(content().string(not(containsString("acceptError"))))
                .andExpect(content().string(not(containsString("\"sender\""))));
    }

    @Test
    void submitRefusesAnAppTheSenderMayNotUse() throws Exception {
        when(submissionService.submit(eq(SUBJECT), eq("Ada Palmer"), eq(GUEST_AUTHORITIES),
                eq(FeedbackApp.GAME_CATALOG), eq(FeedbackType.BUG_REPORT), eq(TITLE), eq(DESCRIPTION)))
                .thenThrow(new FeedbackAppNotAllowedException("not allowed"));

        mockMvc.perform(post("/api/feedback/tickets")
                        .with(authentication(guestAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app":"GAME_CATALOG","type":"BUG_REPORT","title":"%s","description":"%s"}
                                """.formatted(TITLE, DESCRIPTION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("APP_NOT_ALLOWED"));
    }

    @Test
    void submitMapsTheDailyLimitToTooManyRequests() throws Exception {
        when(submissionService.submit(eq(SUBJECT), eq("Ada Palmer"), eq(GUEST_AUTHORITIES),
                eq(FeedbackApp.GAME_CATALOG), eq(FeedbackType.BUG_REPORT), eq(TITLE), eq(DESCRIPTION)))
                .thenThrow(new FeedbackLimitReachedException("limit"));

        mockMvc.perform(post("/api/feedback/tickets")
                        .with(authentication(guestAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app":"GAME_CATALOG","type":"BUG_REPORT","title":"%s","description":"%s"}
                                """.formatted(TITLE, DESCRIPTION)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("FEEDBACK_LIMIT_REACHED"));
    }

    @Test
    void submitMapsDomainValidationFailuresToValidationCode() throws Exception {
        when(submissionService.submit(eq(SUBJECT), eq("Ada Palmer"), eq(GUEST_AUTHORITIES),
                eq(FeedbackApp.GAME_CATALOG), eq(FeedbackType.BUG_REPORT), eq(TITLE), eq(DESCRIPTION)))
                .thenThrow(new IllegalArgumentException("title is too long"));

        mockMvc.perform(post("/api/feedback/tickets")
                        .with(authentication(guestAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app":"GAME_CATALOG","type":"BUG_REPORT","title":"%s","description":"%s"}
                                """.formatted(TITLE, DESCRIPTION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION"));
    }

    @Test
    void submitWithAnUnknownTypeIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/api/feedback/tickets")
                        .with(authentication(guestAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"app":"GAME_CATALOG","type":"NOT_A_TYPE","title":"x","description":"y"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("VALIDATION"));
    }

    @Test
    void getOwnTicketsReturnsOnlyTheSendersTickets() throws Exception {
        when(submissionService.ownTickets(SUBJECT))
                .thenReturn(List.of(FeedbackTicketTestBuilder.aDefaultFeedbackTicket()));

        mockMvc.perform(get("/api/feedback/tickets").with(authentication(guestAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(1))
                .andExpect(jsonPath("$.tickets[0].state").value("NEW"))
                .andExpect(content().string(not(containsString("privateNote"))));
    }

    private Authentication guestAuthentication() {
        AuthenticatedUser user = new AuthenticatedUser(SUBJECT_ID, "ada.palmer", "guest", Set.of(ManagedApp.GAME_CATALOG));

        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
