package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.service.AdminAccountProtectedException;
import dev.jordy.jordylab.settings.service.CannotChangeOwnAccountException;
import dev.jordy.jordylab.settings.service.InvalidLimitException;
import dev.jordy.jordylab.settings.service.UserChatLimitService;
import dev.jordy.jordylab.settings.service.UserNotFoundException;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(SettingsUserChatLimitController.class)
class SettingsUserChatLimitControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserChatLimitService userChatLimitService;

    @Test
    void setsAPersonalLimit() throws Exception {
        mockMvc.perform(put("/api/settings/users/{id}/chat-limit", USER_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"dailyLimit\": 35 }"))
                .andExpect(status().isNoContent());

        verify(userChatLimitService).setPersonal(ADMIN_ID, USER_ID, 35);
    }

    @Test
    void clearsAPersonalLimit() throws Exception {
        mockMvc.perform(delete("/api/settings/users/{id}/chat-limit", USER_ID).with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(userChatLimitService).clearPersonal(ADMIN_ID, USER_ID);
    }

    @Test
    void changingTheAdminsOwnLimitIsABadRequest() throws Exception {
        doThrow(new CannotChangeOwnAccountException(ADMIN_ID)).when(userChatLimitService)
                .setPersonal(ADMIN_ID, ADMIN_ID, 10);

        mockMvc.perform(put("/api/settings/users/{id}/chat-limit", ADMIN_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"dailyLimit\": 10 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("CANNOT_CHANGE_OWN_ACCOUNT"));
    }

    @Test
    void anAdminIsNeverLimited() throws Exception {
        doThrow(new AdminAccountProtectedException(USER_ID)).when(userChatLimitService)
                .setPersonal(ADMIN_ID, USER_ID, 10);

        mockMvc.perform(put("/api/settings/users/{id}/chat-limit", USER_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"dailyLimit\": 10 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ADMIN_PROTECTED"));
    }

    @Test
    void aKeycloakFailureIsAServiceUnavailable() throws Exception {
        doThrow(new KeycloakUnavailableException("down", null)).when(userChatLimitService)
                .setPersonal(ADMIN_ID, USER_ID, 10);

        mockMvc.perform(put("/api/settings/users/{id}/chat-limit", USER_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"dailyLimit\": 10 }"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("KEYCLOAK_UNAVAILABLE"));
    }

    @Test
    void anUnknownPersonIsNotFound() throws Exception {
        doThrow(new UserNotFoundException(USER_ID)).when(userChatLimitService)
                .setPersonal(ADMIN_ID, USER_ID, 10);

        mockMvc.perform(put("/api/settings/users/{id}/chat-limit", USER_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"dailyLimit\": 10 }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("USER_NOT_FOUND"));
    }

    @Test
    void anInvalidLimitIsABadRequest() throws Exception {
        doThrow(new InvalidLimitException(-2)).when(userChatLimitService)
                .setPersonal(ADMIN_ID, USER_ID, -2);

        mockMvc.perform(put("/api/settings/users/{id}/chat-limit", USER_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"dailyLimit\": -2 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_LIMIT"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(jwt -> jwt.subject(ADMIN_ID.toString()));
    }
}
