package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.service.AppUser;
import dev.jordy.jordylab.settings.service.KeycloakUserAdministrationService;
import dev.jordy.jordylab.settings.service.LastAdminProtectedException;
import dev.jordy.jordylab.settings.service.UserNotApprovedException;
import dev.jordy.jordylab.settings.service.UserNotFoundException;
import dev.jordy.jordylab.settings.service.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(SettingsUsersController.class)
class SettingsUsersControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant CREATED_AT = Instant.parse("2026-09-27T18:40:11Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KeycloakUserAdministrationService userAdministrationService;

    @Test
    void listsUsersWithDerivedStatus() throws Exception {
        AppUser pending = new AppUser(USER_ID, "friend@example.org", "Ada", "Palmer", CREATED_AT, true, Set.of(),
                UserStatus.PENDING);
        when(userAdministrationService.listUsers(UserStatus.PENDING)).thenReturn(List.of(pending));

        mockMvc.perform(get("/api/settings/users").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.users[0].email").value("friend@example.org"))
                .andExpect(jsonPath("$.users[0].status").value("PENDING"));
    }

    @Test
    void defaultStatusListsAllUsers() throws Exception {
        when(userAdministrationService.listUsers(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/settings/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty());
    }

    @Test
    void pendingCountReturnsTheCount() throws Exception {
        when(userAdministrationService.pendingCount()).thenReturn(3L);

        mockMvc.perform(get("/api/settings/users/pending-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void approveReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/settings/users/{id}/approve", USER_ID))
                .andExpect(status().isNoContent());

        verify(userAdministrationService).approve(USER_ID);
    }

    @Test
    void rejectReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/settings/users/{id}/reject", USER_ID))
                .andExpect(status().isNoContent());

        verify(userAdministrationService).reject(USER_ID);
    }

    @Test
    void rejectingTheLastAdminIsABadRequest() throws Exception {
        doThrow(new LastAdminProtectedException()).when(userAdministrationService).reject(USER_ID);

        mockMvc.perform(post("/api/settings/users/{id}/reject", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("LAST_ADMIN_PROTECTED"));
    }

    @Test
    void revokeReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/settings/users/{id}/revoke", USER_ID))
                .andExpect(status().isNoContent());

        verify(userAdministrationService).revoke(USER_ID);
    }

    @Test
    void revokingAUserWithoutGuestIsABadRequest() throws Exception {
        doThrow(new UserNotApprovedException(USER_ID)).when(userAdministrationService).revoke(USER_ID);

        mockMvc.perform(post("/api/settings/users/{id}/revoke", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("USER_NOT_APPROVED"));
    }

    @Test
    void resetPasswordReturnsTheTemporaryPasswordOnce() throws Exception {
        when(userAdministrationService.resetPassword(USER_ID)).thenReturn("temp-pass-123");

        mockMvc.perform(post("/api/settings/users/{id}/reset-password", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value("temp-pass-123"));
    }

    @Test
    void anUnknownUserIsNotFound() throws Exception {
        doThrow(new UserNotFoundException(USER_ID)).when(userAdministrationService).approve(USER_ID);

        mockMvc.perform(post("/api/settings/users/{id}/approve", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("USER_NOT_FOUND"));
    }

    @Test
    void keycloakUnavailableIsAnExplicitServiceUnavailable() throws Exception {
        when(userAdministrationService.listUsers(null))
                .thenThrow(new KeycloakUnavailableException("Keycloak admin GET failed"));

        mockMvc.perform(get("/api/settings/users"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("KEYCLOAK_UNAVAILABLE"));
    }
}
