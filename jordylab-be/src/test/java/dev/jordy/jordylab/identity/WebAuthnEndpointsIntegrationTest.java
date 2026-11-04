package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.shared.config.IdentityTestAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Security's WebAuthn ceremony endpoints (spec 019 T039): verification options are public (usernameless
 * passkeys), registration options require a session, and both are CSRF-protected. The successful assertion ceremony is
 * covered by the Playwright virtual-authenticator journey (US7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.profiles.active=local")
class WebAuthnEndpointsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void seedGuest() {
        userAccountRepository.deleteAll();
        UserAccount guest = UserAccount.builder()
                .id(IdentityTestAuthentication.GUEST_ID)
                .username("guest")
                .status(UserStatus.APPROVED)
                .role(UserRole.GUEST)
                .build();
        guest.applyPassword("{bcrypt}$2a$10$hash");
        userAccountRepository.save(guest);
    }

    @Test
    void verificationOptionsArePublicAndUsernameless() throws Exception {
        mockMvc.perform(post("/webauthn/authenticate/options").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andExpect(jsonPath("$.allowCredentials").isArray());
    }

    @Test
    void verificationOptionsRequireCsrf() throws Exception {
        mockMvc.perform(post("/webauthn/authenticate/options"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationOptionsRequireASession() throws Exception {
        // The framework's registration-options filter rejects an unauthenticated caller with 400: it cannot build
        // creation options without a user, and nothing about the account is leaked.
        mockMvc.perform(post("/webauthn/register/options").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationOptionsForASignedInUserCarryTheRelyingPartyAndUser() throws Exception {
        mockMvc.perform(post("/webauthn/register/options")
                        .with(IdentityTestAuthentication.asGuest()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andExpect(jsonPath("$.rp.id").value("localhost"))
                .andExpect(jsonPath("$.user.name").value("guest"));
    }
}
