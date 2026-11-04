package dev.jordy.jordylab.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Identity module wiring (spec 019 D2, D9). The encoder bean is the only password-storage implementation in the
 * platform — never hand-roll hashing (FR-002).
 */
@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
