package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.service.SwitchBulkService;
import dev.jordy.jordylab.gamecatalog.service.SwitchGameService;
import dev.jordy.jordylab.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role matrix for the Switch write endpoints against the real {@link SecurityConfig} (spec 009 T053): every
 * Switch endpoint is admin-only, so a guest gets 403 and the service is never reached.
 */
@Import({ SecurityConfig.class, SwitchGameControllerSecurityTest.JwtDecoderConfig.class })
@WebMvcTest(SwitchGameController.class)
class SwitchGameControllerSecurityTest {

    private static final UUID GAME_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SwitchGameService switchGameService;

    @MockitoBean
    private SwitchBulkService switchBulkService;

    @Test
    void guestCannotSearchIgdb() throws Exception {
        mockMvc.perform(get("/api/gamecatalog/switch/search").param("query", "mario").with(guest()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(switchGameService);
    }

    @Test
    void guestCannotAddASwitchGame() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/switch/games").with(guest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "My Custom Game", "format": "DIGITAL"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(switchGameService);
    }

    @Test
    void guestCannotEditASwitchGame() throws Exception {
        mockMvc.perform(patch("/api/gamecatalog/switch/games/{id}", GAME_ID).with(guest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format": "PHYSICAL"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(switchGameService);
    }

    @Test
    void guestCannotRemoveASwitchGame() throws Exception {
        mockMvc.perform(delete("/api/gamecatalog/switch/games/{id}", GAME_ID).with(guest()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(switchGameService);
    }

    @Test
    void guestCannotBulkAdd() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/switch/bulk/preview").with(guest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Pikmin 4"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gamecatalog/switch/bulk/confirm").with(guest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"line": "Pikmin 4", "igdbGameId": 111, "format": "DIGITAL"}]}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(switchBulkService);
    }

    @Test
    void adminCanRemoveASwitchGame() throws Exception {
        mockMvc.perform(delete("/api/gamecatalog/switch/games/{id}", GAME_ID).with(admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(delete("/api/gamecatalog/switch/games/{id}", GAME_ID))
                .andExpect(status().isUnauthorized());
    }

    // The Android app's WebView runs on https://localhost, so the Switch edit (PATCH) is a cross-origin
    // request there and needs PATCH in the CORS preflight answer (spec 011 BUG-039).
    @Test
    void corsPreflightAllowsPatchForTheSwitchEdit() throws Exception {
        mockMvc.perform(options("/api/gamecatalog/switch/games/{id}", GAME_ID)
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods",
                        containsString("PATCH")));
    }

    private static RequestPostProcessor guest() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_guest"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }

    @TestConfiguration
    @ImportAutoConfiguration({ ServletWebSecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class })
    static class JwtDecoderConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withJwkSetUri("https://localhost:8180/realms/jordylab/protocol/openid-connect/certs")
                    .build();
        }
    }
}
