package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.IdentitySecurityConfiguration;
import dev.jordy.jordylab.identity.domain.MachineToken;
import dev.jordy.jordylab.identity.domain.MachineTokenScope;
import dev.jordy.jordylab.identity.service.IdentityUserDetailsService;
import dev.jordy.jordylab.identity.service.MachineTokenAlreadyRevokedException;
import dev.jordy.jordylab.identity.service.MachineTokenService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_ID;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.asAdmin;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.asGuest;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Machine management is admin-only, and the token value is returned exactly once (spec 019 US5). */
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import(IdentitySecurityConfiguration.class)
@WebMvcTest(MachineTokenController.class)
class MachineTokenControllerTest {

    private static final UUID TOKEN_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired
    private MockMvc mockMvc;

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
                new AuthenticatedUser(ADMIN_ID, "admin", "admin", java.util.Set.of())));
    }

    @Test
    void listsTheMachines() throws Exception {
        when(machineTokenService.list()).thenReturn(List.of(aToken()));

        mockMvc.perform(get("/api/identity/machines").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machines[0].name").value("JordyBox"))
                .andExpect(jsonPath("$.machines[0].scope").value("INGEST"));
    }

    @Test
    void createsAMachineAndShowsTheTokenOnce() throws Exception {
        MachineToken token = aToken();
        when(machineTokenService.create(ADMIN_ID, "JordyBox"))
                .thenReturn(new MachineTokenService.CreatedMachineToken(token, "jlmt_secret"));

        mockMvc.perform(post("/api/identity/machines").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"JordyBox\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jlmt_secret"))
                .andExpect(jsonPath("$.name").value("JordyBox"));
    }

    @Test
    void revokesAMachine() throws Exception {
        mockMvc.perform(post("/api/identity/machines/{id}/revoke", TOKEN_ID).with(asAdmin()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void refusesARevokedMachine() throws Exception {
        doThrow(new MachineTokenAlreadyRevokedException()).when(machineTokenService).revoke(TOKEN_ID);

        mockMvc.perform(post("/api/identity/machines/{id}/revoke", TOKEN_ID).with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("ALREADY_REVOKED"));
    }

    @Test
    void guestsCannotManageMachines() throws Exception {
        mockMvc.perform(get("/api/identity/machines").with(asGuest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresASession() throws Exception {
        mockMvc.perform(get("/api/identity/machines")).andExpect(status().isUnauthorized());
    }

    private static MachineToken aToken() {
        return MachineToken.builder()
                .id(TOKEN_ID)
                .name("JordyBox")
                .tokenHash("hash-of-jlmt-abc")
                .scope(MachineTokenScope.INGEST)
                .createdBy(ADMIN_ID)
                .lastUsedAt(Instant.parse("2026-11-03T12:00:00Z"))
                .build();
    }
}
