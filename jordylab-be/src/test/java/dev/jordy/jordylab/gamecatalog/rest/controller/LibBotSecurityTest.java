package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.service.GameQueryService;
import dev.jordy.jordylab.gamecatalog.service.libbot.ConversationStore;
import dev.jordy.jordylab.gamecatalog.service.libbot.LibBotService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LibBot's endpoints against the real {@link SecurityConfig} (spec 013 US1): open to admins and guests, closed to
 * anonymous callers and to accounts without a role. The full matrix over the running app is {@code RoleMatrixTest}.
 */
@Import({ SecurityConfig.class, LibBotSecurityTest.JwtDecoderConfig.class })
@WebMvcTest(LibBotController.class)
class LibBotSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibBotService libBotService;

    @MockitoBean
    private ConversationStore conversationStore;

    @MockitoBean
    private GameQueryService gameQueryService;

    @Test
    void guestsAndAdminsMayForgetTheirConversation() throws Exception {
        mockMvc.perform(delete("/api/gamecatalog/libbot/conversations/c1").with(role("guest")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/gamecatalog/libbot/conversations/c1").with(role("admin")))
                .andExpect(status().isNoContent());
    }

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mockMvc.perform(delete("/api/gamecatalog/libbot/conversations/c1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/gamecatalog/libbot/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"c1\",\"message\":\"hi\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAccountWithoutAnAppRoleIsRefused() throws Exception {
        mockMvc.perform(delete("/api/gamecatalog/libbot/conversations/c1").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gamecatalog/libbot/ask").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"c1\",\"message\":\"hi\"}")).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
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
