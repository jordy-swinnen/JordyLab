package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sessions are real rows in {@code identity.spring_session} (spec 019 US3, T050): a login serializes the security
 * context — the principal must be {@link java.io.Serializable} — and stores the username (not the record's toString) as
 * the principal name, which the admin session panel will look up later. Logout deletes the row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.profiles.active=local")
class SessionPersistenceIntegrationTest {

    private static final String USERNAME = "session.admin";
    private static final String PASSWORD = "session-password-12";

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedAdmin() {
        userAccountRepository.deleteAll();
        UserAccount admin = UserAccount.builder()
                .username(USERNAME)
                .status(UserStatus.APPROVED)
                .role(UserRole.ADMIN)
                .build();
        admin.applyPassword(passwordEncoder.encode(PASSWORD));
        userAccountRepository.save(admin);
    }

    @Test
    void loginStoresASerializableSessionAndLogoutRemovesIt() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/identity/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Integer sessions = jdbcTemplate.queryForObject("SELECT count(*) FROM identity.spring_session", Integer.class);
        String principalName = jdbcTemplate.queryForObject(
                "SELECT principal_name FROM identity.spring_session", String.class);
        Cookie sessionCookie = login.getResponse().getCookie("SESSION");

        assertSoftly(softly -> {
            softly.assertThat(sessions).isEqualTo(1);
            softly.assertThat(principalName).isEqualTo(USERNAME);
            softly.assertThat(sessionCookie).isNotNull();
        });

        mockMvc.perform(get("/api/identity/me").cookie(sessionCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/identity/auth/logout").cookie(sessionCookie).with(csrf()))
                .andExpect(status().isNoContent());

        Integer remaining = jdbcTemplate.queryForObject("SELECT count(*) FROM identity.spring_session", Integer.class);
        assertThat(remaining).isZero();
    }
}
