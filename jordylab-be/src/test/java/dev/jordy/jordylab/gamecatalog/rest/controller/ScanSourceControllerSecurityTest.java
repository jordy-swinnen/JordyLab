package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.HealthExceptionsResponse;
import dev.jordy.jordylab.gamecatalog.service.LibraryHealthService;
import dev.jordy.jordylab.gamecatalog.service.ScanSourceService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Sources page endpoints against the real {@link SecurityConfig}: admin only, so a guest gets 403 and the services
 * are never reached, including the player-count lists (spec 014 FR-013).
 */
@Import({ SecurityConfig.class, ScanSourceControllerSecurityTest.JwtDecoderConfig.class })
@WebMvcTest(ScanSourceController.class)
class ScanSourceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanSourceService scanSourceService;

    @MockitoBean
    private LibraryHealthService libraryHealthService;

    @Test
    void aGuestCannotReadTheSourcesOrTheGamesBehindAPlayerCountNumber() throws Exception {
        mockMvc.perform(get("/api/gamecatalog/sources").with(guest())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/gamecatalog/sources/health/exceptions").param("kind", "PLAYER_COUNT")
                .param("reason", "ENTRIES_DISAGREE").with(guest())).andExpect(status().isForbidden());

        verifyNoInteractions(scanSourceService, libraryHealthService);
    }

    @Test
    void anAdminCanReadThePlayerCountListAndAnonymousIsRejected() throws Exception {
        when(libraryHealthService.exceptions(LibraryHealthService.Kind.PLAYER_COUNT, null))
                .thenReturn(new HealthExceptionsResponse("PLAYER_COUNT", 0, List.of()));

        mockMvc.perform(get("/api/gamecatalog/sources/health/exceptions").param("kind", "PLAYER_COUNT").with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/gamecatalog/sources/health/exceptions").param("kind", "PLAYER_COUNT"))
                .andExpect(status().isUnauthorized());
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
