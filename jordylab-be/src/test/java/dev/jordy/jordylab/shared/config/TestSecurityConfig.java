package dev.jordy.jordylab.shared.config;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security for {@code @WebMvcTest} slices. Import this into any controller test
 * that needs the Spring Boot 4 servlet security auto-configurations (which the test slice no
 * longer loads) and a permit-all filter chain so the test exercises controller logic instead
 * of role enforcement.
 */
@TestConfiguration
@ImportAutoConfiguration({ ServletWebSecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class })
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri("https://localhost:8180/realms/jordylab/protocol/openid-connect/certs")
                .build();
    }
}
