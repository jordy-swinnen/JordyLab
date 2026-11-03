package dev.jordy.jordylab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        "JORDYLAB_GIT_COMMIT=" + ApplicationInfoEndpointTest.BUILT_COMMIT
})
@AutoConfigureMockMvc
class ApplicationInfoEndpointTest {

    static final String BUILT_COMMIT = "9f3c2a1e0b7d4c6a8e5f1b2c3d4e5f6071829304";

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class JwtDecoderOverride {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test-user")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        }
    }

    @Test
    void infoEndpointReportsTheCommitTheImageWasBuiltFromWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commit").value(BUILT_COMMIT));
    }
}
