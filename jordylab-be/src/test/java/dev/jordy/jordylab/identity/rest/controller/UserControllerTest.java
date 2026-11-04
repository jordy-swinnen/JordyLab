package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.IdentitySecurityConfiguration;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.service.IdentityUserDetailsService;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.service.InvalidRequestException;
import dev.jordy.jordylab.identity.service.LastAdminProtectedException;
import dev.jordy.jordylab.identity.service.OwnAccountOperationException;
import dev.jordy.jordylab.identity.service.PasswordResetService;
import dev.jordy.jordylab.identity.service.ResetRequestNotFoundException;
import dev.jordy.jordylab.identity.service.ResetRequestNotPendingException;
import dev.jordy.jordylab.identity.service.UserAccountService;
import dev.jordy.jordylab.identity.service.UserDeletionService;
import dev.jordy.jordylab.identity.service.UserSessionService;
import dev.jordy.jordylab.identity.service.UserNotFoundException;
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
import java.util.Set;
import java.util.UUID;

import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_ID;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.asAdmin;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.asGuest;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin user management against the real session chain (spec 019 T016): gates, status codes, contract codes. */
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import(IdentitySecurityConfiguration.class)
@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final UUID USER_ID = UUID.fromString("e6b2c9f1-3a4d-4f5e-8a9b-1c2d3e4f5a6b");
    private static final UUID REQUEST_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityUserDetailsService identityUserDetailsService;

    @MockitoBean
    private MachineTokenService machineTokenService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private UserSessionService userSessionService;

    @MockitoBean
    private UserDeletionService userDeletionService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @org.junit.jupiter.api.BeforeEach
    void resolveAdmin() {
        org.mockito.Mockito.when(currentUserResolver.requireCurrentUserSubject())
                .thenReturn(dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_SUBJECT);
    }

    @Test
    void guestsCannotReachUserManagement() throws Exception {
        mockMvc.perform(get("/api/identity/users").with(asGuest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/identity/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsUsersWithTheirGrants() throws Exception {
        when(userAccountService.listUsers(null)).thenReturn(List.of(approvedGuest()));

        mockMvc.perform(get("/api/identity/users").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].username").value("guest-account"))
                .andExpect(jsonPath("$.users[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.users[0].appGrants[0]").value("gamecatalog"))
                .andExpect(jsonPath("$.users[0].pendingResetRequest").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void filtersByStatus() throws Exception {
        when(userAccountService.listUsers(UserStatus.PENDING)).thenReturn(List.of());

        mockMvc.perform(get("/api/identity/users").param("status", "PENDING").with(asAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/identity/users").param("status", "NOPE").with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_REQUEST"));
    }

    @Test
    void pendingCount() throws Exception {
        when(userAccountService.pendingCount()).thenReturn(3L);

        mockMvc.perform(get("/api/identity/users/pending-count").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(3));
    }

    @Test
    void approveDefaultsToGuest() throws Exception {
        when(userAccountService.approve(USER_ID, null)).thenReturn(approvedGuest());

        mockMvc.perform(post("/api/identity/users/{id}/approve", USER_ID).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(userAccountService).approve(USER_ID, null);
    }

    @Test
    void approveWithAnExplicitRole() throws Exception {
        when(userAccountService.approve(USER_ID, UserRole.ADMIN)).thenReturn(approvedGuest());

        mockMvc.perform(post("/api/identity/users/{id}/approve", USER_ID).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isNoContent());
        verify(userAccountService).approve(USER_ID, UserRole.ADMIN);
    }

    @Test
    void rejectAndRevoke() throws Exception {
        when(userAccountService.reject(USER_ID)).thenReturn(approvedGuest());
        when(userAccountService.revoke(USER_ID)).thenReturn(approvedGuest());

        mockMvc.perform(post("/api/identity/users/{id}/reject", USER_ID).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/identity/users/{id}/revoke", USER_ID).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        when(userAccountService.reject(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));

        mockMvc.perform(post("/api/identity/users/{id}/reject", USER_ID).with(asAdmin()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("USER_NOT_FOUND"));
    }

    @Test
    void lastAdminIsProtected() throws Exception {
        when(userAccountService.reject(USER_ID)).thenThrow(new LastAdminProtectedException());

        mockMvc.perform(post("/api/identity/users/{id}/reject", USER_ID).with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("LAST_ADMIN_PROTECTED"));
    }

    @Test
    void aMutatingRequestWithoutCsrfIsRefused() throws Exception {
        mockMvc.perform(post("/api/identity/users/{id}/reject", USER_ID).with(asAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGetsTheirOwnIdentity() throws Exception {
        when(userAccountService.listUsers(null)).thenReturn(List.of(approvedGuest()));

        mockMvc.perform(get("/api/identity/users").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].createdAt").exists());
    }

    @Test
    void grantsAndRevokesTheGameCatalog() throws Exception {
        when(userAccountService.grantApp(USER_ID, ManagedApp.GAME_CATALOG)).thenReturn(approvedGuest());
        when(userAccountService.revokeApp(USER_ID, ManagedApp.GAME_CATALOG)).thenReturn(approvedGuest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/identity/users/{id}/apps/gamecatalog", USER_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/identity/users/{id}/apps/gamecatalog", USER_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void refusesANonGrantableApp() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/identity/users/{id}/apps/fna", USER_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_REQUEST"));
    }

    @Test
    void aDirectResetReturnsTheTemporaryPasswordOnce() throws Exception {
        when(passwordResetService.resetDirectly(USER_ID)).thenReturn("temporary-password-20ch");

        mockMvc.perform(post("/api/identity/users/{id}/reset-password", USER_ID).with(asAdmin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value("temporary-password-20ch"));
    }

    @Test
    void approvesAndRejectsResetRequests() throws Exception {
        mockMvc.perform(post("/api/identity/users/{id}/reset-requests/{requestId}/approve", USER_ID, REQUEST_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/identity/users/{id}/reset-requests/{requestId}/reject", USER_ID, REQUEST_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(passwordResetService).approve(ADMIN_ID, USER_ID, REQUEST_ID);
        verify(passwordResetService).reject(ADMIN_ID, USER_ID, REQUEST_ID);
    }

    @Test
    void mapsAMissingResetRequest() throws Exception {
        doThrow(new ResetRequestNotFoundException()).when(passwordResetService)
                .approve(ADMIN_ID, USER_ID, REQUEST_ID);

        mockMvc.perform(post("/api/identity/users/{id}/reset-requests/{requestId}/approve", USER_ID, REQUEST_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void mapsAnAlreadyDecidedResetRequest() throws Exception {
        doThrow(new ResetRequestNotPendingException()).when(passwordResetService)
                .reject(ADMIN_ID, USER_ID, REQUEST_ID);

        mockMvc.perform(post("/api/identity/users/{id}/reset-requests/{requestId}/reject", USER_ID, REQUEST_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("REQUEST_NOT_PENDING"));
    }

    @Test
    void listsAndEndsSessions() throws Exception {
        when(userSessionService.list(dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_ID, USER_ID))
                .thenReturn(List.of(new UserSessionService.SessionInfo("session-1",
                        java.time.Instant.parse("2026-11-03T12:00:00Z"),
                        java.time.Instant.parse("2026-11-03T12:05:00Z"))));

        mockMvc.perform(get("/api/identity/users/{id}/sessions", USER_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].sessionId").value("session-1"));

        mockMvc.perform(post("/api/identity/users/{id}/sessions/{sessionId}/end", USER_ID, "session-1")
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/identity/users/{id}/sessions/end-all", USER_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void refusesToActOnTheCallersOwnAccount() throws Exception {
        org.mockito.Mockito.doThrow(new OwnAccountOperationException())
                .when(userDeletionService)
                .delete(dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_ID, USER_ID);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/identity/users/{id}", USER_ID)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("OWN_ACCOUNT"));
    }

    private static UserAccount approvedGuest() {
        UserAccount account = UserAccount.builder()
                .id(USER_ID)
                .username("guest-account")
                .status(UserStatus.APPROVED)
                .role(UserRole.GUEST)
                .appGrants(Set.of(ManagedApp.GAME_CATALOG))
                .build();
        account.applyPassword("{bcrypt}$2a$10$hash");
        org.springframework.test.util.ReflectionTestUtils.setField(account, "createdDate",
                Instant.parse("2026-11-03T10:00:00Z"));

        return account;
    }
}
