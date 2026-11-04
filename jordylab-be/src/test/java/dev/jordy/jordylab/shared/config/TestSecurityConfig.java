package dev.jordy.jordylab.shared.config;

import dev.jordy.jordylab.identity.CurrentUserResolver;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security for {@code @WebMvcTest} slices: a permit-all filter chain with CSRF off, so the test exercises
 * controller logic instead of role enforcement (the access matrix itself is covered by {@code IdentityRoleMatrixTest}).
 * After the identity rewrite (spec 019) the slice needs the servlet security auto-configurations, which Boot 4 no longer loads in
 * {@code @WebMvcTest}, plus the {@link CurrentUserResolver} the controllers inject.
 */
@TestConfiguration
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public CurrentUserResolver currentUserResolver() {
        return new CurrentUserResolver();
    }
}
