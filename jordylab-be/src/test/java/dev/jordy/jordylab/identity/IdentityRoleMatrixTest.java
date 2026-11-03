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

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The executable access matrix after Keycloak (spec 019 T017, contracts/access-matrix.md): the real security chains on
 * a real Postgres, no Keycloak container. Replaces {@code settings/RoleMatrixTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.profiles.active=local")
class IdentityRoleMatrixTest {

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
    void seedAccounts() {
        userAccountRepository.deleteAll();
        userAccountRepository.save(account(IdentityTestAuthentication.ADMIN_ID, "admin-account", UserRole.ADMIN));
        userAccountRepository.save(account(IdentityTestAuthentication.GUEST_ID, "guest-account", UserRole.GUEST));
        userAccountRepository.save(pendingAccount(IdentityTestAuthentication.PENDING_ID, "pending-account"));
    }

    @Test
    void anonymousCallersAreRefusedEverywhere() throws Exception {
        mockMvc.perform(get("/api/identity/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/identity/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gamecatalog/games")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/fna/portfolio")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/gamecatalog/ingest/check").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendingAccountsReachOnlyTheirOwnIdentity() throws Exception {
        mockMvc.perform(get("/api/identity/me").with(IdentityTestAuthentication.asPending()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/gamecatalog/games").with(IdentityTestAuthentication.asPending()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/identity/users").with(IdentityTestAuthentication.asPending()))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvedGuestWithoutTheCatalogGrantIsRefusedTheCatalog() throws Exception {
        mockMvc.perform(get("/api/identity/me").with(IdentityTestAuthentication.asGuestWithoutCatalog()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/gamecatalog/games").with(IdentityTestAuthentication.asGuestWithoutCatalog()))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvedGuestWithTheCatalogGrantReadsTheCatalogOnly() throws Exception {
        mockMvc.perform(get("/api/gamecatalog/games").with(IdentityTestAuthentication.asGuest()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/gamecatalog/libbot/quota").with(IdentityTestAuthentication.asGuest()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/settings/ai-models").with(IdentityTestAuthentication.asGuest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/identity/users").with(IdentityTestAuthentication.asGuest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReachesAdministrationAndTheCatalog() throws Exception {
        mockMvc.perform(get("/api/identity/users").with(IdentityTestAuthentication.asAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/settings/ai-models").with(IdentityTestAuthentication.asAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/gamecatalog/games").with(IdentityTestAuthentication.asAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/fna/portfolio").with(IdentityTestAuthentication.asAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void csrfIsRequiredForSessionMutations() throws Exception {
        String unknownUser = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/identity/users/{id}/reject", unknownUser)
                        .with(IdentityTestAuthentication.asAdmin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/identity/users/{id}/reject", unknownUser)
                        .with(IdentityTestAuthentication.asAdmin()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/identity/me")
                        .with(IdentityTestAuthentication.asAdmin())
                        .contentType("application/json")
                        .content("{\"displayName\":\"Jordy\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void machineTokenEndpointsDoNotAcceptASession() throws Exception {
        mockMvc.perform(post("/api/gamecatalog/ingest/check")
                        .with(IdentityTestAuthentication.asAdmin()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private static UserAccount account(UUID id, String username, UserRole role) {
        UserAccount account = UserAccount.builder()
                .id(id)
                .username(username)
                .status(UserStatus.APPROVED)
                .role(role)
                .appGrants(role == UserRole.GUEST ? Set.of(ManagedApp.GAME_CATALOG) : Set.of())
                .build();
        account.applyPassword("{bcrypt}$2a$10$not-a-real-hash");

        return account;
    }

    private static UserAccount pendingAccount(UUID id, String username) {
        UserAccount account = UserAccount.builder()
                .id(id)
                .username(username)
                .status(UserStatus.PENDING)
                .build();
        account.applyPassword("{bcrypt}$2a$10$not-a-real-hash");

        return account;
    }
}
