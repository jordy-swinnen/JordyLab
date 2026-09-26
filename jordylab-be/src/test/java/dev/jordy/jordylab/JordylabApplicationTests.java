package dev.jordy.jordylab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test"
})
class JordylabApplicationTests {

    @TestConfiguration
    static class JwtDecoderOverride {

        /**
         * Spring Security needs a {@link JwtDecoder} bean in the context. In
         * dev/prod the resource server discovers the JWKS endpoint from the
         * Keycloak {@code issuer-uri}. The application's full context test
         * can't reach Keycloak, so we substitute a no-op decoder that simply
         * trusts whatever claims the caller passed in. The downstream JWT-
         * validation logic is exercised by the smaller SecurityConfig tests
         * and the per-module ApplicationModuleTests.
         */
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test-user")
                    .claim("realm_access", Map.of("roles", java.util.List.of()))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        }
    }

    @Test
    void contextLoads() {
        //
    }
}
