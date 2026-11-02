package dev.jordy.jordylab.settings.rest.controller;

import dev.jordy.jordylab.gamecatalog.GameCatalogAccess;
import dev.jordy.jordylab.settings.rest.client.KeycloakUnavailableException;
import dev.jordy.jordylab.settings.service.AppAccessService;
import dev.jordy.jordylab.settings.service.AppNotGrantableException;
import dev.jordy.jordylab.settings.service.AppNotFoundException;
import dev.jordy.jordylab.settings.service.CannotChangeOwnAccountException;
import dev.jordy.jordylab.settings.service.UserNotApprovedException;
import dev.jordy.jordylab.shared.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestSecurityConfig.class)
@WebMvcTest(SettingsUserAppsController.class)
class SettingsUserAppsControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppAccessService appAccessService;

    @Test
    void listsTheAppRegistryWithTheGameCatalogAsTheGrantableDefault() throws Exception {
        mockMvc.perform(get("/api/settings/apps").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apps[0].key").value(GameCatalogAccess.APP_KEY))
                .andExpect(jsonPath("$.apps[0].grantable").value(true))
                .andExpect(jsonPath("$.apps[0].defaultGrant").value(true))
                .andExpect(jsonPath("$.apps[1].key").value("fna"))
                .andExpect(jsonPath("$.apps[1].grantable").value(false))
                .andExpect(jsonPath("$.apps[1].reason").value("Holds the admin's private data"));
    }

    @Test
    void grantReturnsNoContent() throws Exception {
        mockMvc.perform(put("/api/settings/users/{id}/apps/{appKey}", USER_ID, GameCatalogAccess.APP_KEY)
                        .with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(appAccessService).grantApp(ADMIN_ID, USER_ID, GameCatalogAccess.APP_KEY);
    }

    @Test
    void removeReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/settings/users/{id}/apps/{appKey}", USER_ID, GameCatalogAccess.APP_KEY)
                        .with(adminJwt()))
                .andExpect(status().isNoContent());

        verify(appAccessService).removeApp(ADMIN_ID, USER_ID, GameCatalogAccess.APP_KEY);
    }

    @Test
    void anUnknownAppIsNotFound() throws Exception {
        doThrow(new AppNotFoundException("unknown")).when(appAccessService)
                .grantApp(ADMIN_ID, USER_ID, "unknown");

        mockMvc.perform(put("/api/settings/users/{id}/apps/{appKey}", USER_ID, "unknown").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("APP_NOT_FOUND"));
    }

    @Test
    void aNonGrantableAppIsABadRequest() throws Exception {
        doThrow(new AppNotGrantableException("fna")).when(appAccessService).grantApp(ADMIN_ID, USER_ID, "fna");

        mockMvc.perform(put("/api/settings/users/{id}/apps/{appKey}", USER_ID, "fna").with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("APP_NOT_GRANTABLE"));
    }

    @Test
    void grantingToAnUnapprovedPersonIsABadRequest() throws Exception {
        doThrow(new UserNotApprovedException(USER_ID)).when(appAccessService)
                .grantApp(ADMIN_ID, USER_ID, GameCatalogAccess.APP_KEY);

        mockMvc.perform(put("/api/settings/users/{id}/apps/{appKey}", USER_ID, GameCatalogAccess.APP_KEY)
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("USER_NOT_APPROVED"));
    }

    @Test
    void aKeycloakFailureIsAServiceUnavailable() throws Exception {
        doThrow(new KeycloakUnavailableException("down", null)).when(appAccessService)
                .grantApp(ADMIN_ID, USER_ID, GameCatalogAccess.APP_KEY);

        mockMvc.perform(put("/api/settings/users/{id}/apps/{appKey}", USER_ID, GameCatalogAccess.APP_KEY)
                        .with(adminJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("KEYCLOAK_UNAVAILABLE"));
    }

    @Test
    void changingTheAdminsOwnAccountIsABadRequest() throws Exception {
        doThrow(new CannotChangeOwnAccountException(ADMIN_ID)).when(appAccessService)
                .grantApp(ADMIN_ID, ADMIN_ID, GameCatalogAccess.APP_KEY);

        mockMvc.perform(put("/api/settings/users/{id}/apps/{appKey}", ADMIN_ID, GameCatalogAccess.APP_KEY)
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("CANNOT_CHANGE_OWN_ACCOUNT"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(jwt -> jwt.subject(ADMIN_ID.toString()));
    }
}
