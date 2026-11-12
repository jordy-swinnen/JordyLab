package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.IdentitySecurityConfiguration;
import dev.jordy.jordylab.identity.domain.MachineToken;
import dev.jordy.jordylab.identity.domain.MachineTokenScope;
import dev.jordy.jordylab.identity.domain.UserTheme;
import dev.jordy.jordylab.identity.service.IdentityUserDetailsService;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import dev.jordy.jordylab.identity.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_ID;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.asAdmin;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.asGuest;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The account-wide theme preference (spec 024 US3, contracts/theme-preference.md). */
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import(IdentitySecurityConfiguration.class)
@WebMvcTest(UserPreferenceController.class)
class UserPreferenceControllerTest {

    private static final String MACHINE_TOKEN = "jlmt_machine-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private MachineTokenService machineTokenService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @MockitoBean
    private IdentityUserDetailsService identityUserDetailsService;

    @BeforeEach
    void resolveAdmin() {
        when(currentUserResolver.requireCurrentUserSubject()).thenReturn(ADMIN_ID.toString());
        when(currentUserResolver.currentUser()).thenReturn(Optional.of(
                new AuthenticatedUser(ADMIN_ID, "admin", "admin", Set.of())));
    }

    @Test
    void returnsTheAccountTheme() throws Exception {
        when(userAccountService.themeOf(ADMIN_ID)).thenReturn(UserTheme.HEATHER);

        mockMvc.perform(get("/api/identity/me/preferences").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("heather"));
    }

    @Test
    void aGuestCanReadTheirOwnPreference() throws Exception {
        when(userAccountService.themeOf(ADMIN_ID)).thenReturn(UserTheme.PAPER);

        mockMvc.perform(get("/api/identity/me/preferences").with(asGuest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("paper"));
    }

    @Test
    void updatesTheAccountTheme() throws Exception {
        mockMvc.perform(put("/api/identity/me/preferences").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"oxblood\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("oxblood"));

        verify(userAccountService).changeOwnTheme(ADMIN_ID, UserTheme.OXBLOOD);
    }

    @Test
    void anUnknownThemeFallsBackToFollowingTheDevice() throws Exception {
        mockMvc.perform(put("/api/identity/me/preferences").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"neon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("system"));
    }

    @Test
    void theMachineReadNeedsAMachineToken() throws Exception {
        mockMvc.perform(get("/api/identity/machines/preferences"))
                .andExpect(status().isUnauthorized());
    }

    private static MachineToken aMachineToken() {
        return MachineToken.builder()
                .id(UUID.fromString("44444444-4444-4444-8444-444444444444"))
                .name("JordyBox")
                .tokenHash("hash-of-jlmt-machine-token")
                .scope(MachineTokenScope.INGEST)
                .createdBy(ADMIN_ID)
                .lastUsedAt(Instant.parse("2026-11-03T12:00:00Z"))
                .build();
    }
}
