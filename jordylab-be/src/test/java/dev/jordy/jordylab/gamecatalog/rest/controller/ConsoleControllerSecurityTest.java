package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.service.ConsoleBulkService;
import dev.jordy.jordylab.gamecatalog.service.ConsoleGameService;
import dev.jordy.jordylab.gamecatalog.service.ConsoleService;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role matrix for the console endpoints against the real {@link SecurityConfig} (spec 013 US6): every one is admin only,
 * so a guest gets 403 and the services are never reached.
 */
@Import({ SecurityConfig.class, ConsoleControllerSecurityTest.JwtDecoderConfig.class })
@WebMvcTest(ConsoleController.class)
class ConsoleControllerSecurityTest {

    private static final UUID ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsoleService consoleService;

    @MockitoBean
    private ConsoleGameService consoleGameService;

    @MockitoBean
    private ConsoleBulkService consoleBulkService;

    @Test
    void guestCannotUseAnyConsoleEndpoint() throws Exception {
        mockMvc.perform(get("/api/gamecatalog/consoles").with(guest())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/gamecatalog/consoles/known").with(guest())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gamecatalog/consoles").with(guest()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"platform\":\"Nintendo Switch\"}")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/gamecatalog/consoles/{id}", ID).with(guest()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/gamecatalog/consoles/{id}", ID).with(guest())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/gamecatalog/consoles/{id}/search", ID).param("q", "x").with(guest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games", ID).with(guest())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/gamecatalog/consoles/{id}/games/{gameId}", ID, ID).with(guest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games/bulk/preview", ID).with(guest())
                .contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[\"x\"]}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gamecatalog/consoles/{id}/games/bulk/confirm", ID).with(guest())
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")).andExpect(status().isForbidden());

        verifyNoInteractions(consoleService, consoleGameService, consoleBulkService);
    }

    @Test
    void adminCanListConsolesAndAnonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/gamecatalog/consoles").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/gamecatalog/consoles")).andExpect(status().isUnauthorized());
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
