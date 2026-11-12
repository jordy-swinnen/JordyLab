package dev.jordy.jordylab.identity;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.MachineTokenRepository;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import dev.jordy.jordylab.shared.config.IdentityTestAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The machine-token chain end to end (spec 019 US5): a valid token authenticates the scanner on the ingest endpoints, a
 * revoked or unknown token is refused, and a browser session does not work there.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.profiles.active=local")
class MachineTokenAuthenticationIntegrationTest {

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
    private MachineTokenService machineTokenService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private MachineTokenRepository machineTokenRepository;

    @BeforeEach
    void seedAdmin() {
        machineTokenRepository.deleteAll();
        userAccountRepository.deleteAll();
        UserAccount admin = UserAccount.builder()
                .id(ADMIN_ID)
                .username("machine.admin")
                .status(UserStatus.APPROVED)
                .role(UserRole.ADMIN)
                .build();
        admin.applyPassword("{bcrypt}$2a$10$hash");
        userAccountRepository.save(admin);
    }

    @Test
    void aValidTokenAuthenticatesTheIngestEndpoints() throws Exception {
        String token = machineTokenService.create(ADMIN_ID, "JordyBox").plaintext();

        mockMvc.perform(post("/api/gamecatalog/ingest/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(200, 400));
    }

    @Test
    void unknownAndRevokedTokensAreRefused() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/ingest/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jlmt_unknown")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        MachineTokenService.CreatedMachineToken created = machineTokenService.create(ADMIN_ID, "Retired");
        machineTokenService.revoke(created.token().getId());

        mockMvc.perform(post("/api/gamecatalog/ingest/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + created.plaintext())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBrowserSessionDoesNotWorkOnTheIngestChain() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/ingest/check")
                        .with(IdentityTestAuthentication.asAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMachineTokenDoesNotOpenAnyOtherEndpoint() throws Exception {
        String token = machineTokenService.create(ADMIN_ID, "JordyBox").plaintext();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/identity/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMachineReadsTheThemeOfTheAccountThatCreatedIt() throws Exception {
        String token = machineTokenService.create(ADMIN_ID, "JordyBox").plaintext();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/identity/machines/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.theme").value("system"));
    }

    @Test
    void aMalformedBearerHeaderIsRefused() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/ingest/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-machine-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
