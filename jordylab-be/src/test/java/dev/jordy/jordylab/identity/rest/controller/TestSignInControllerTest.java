package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.IdentitySecurityConfiguration;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.identity.service.IdentityUserDetailsService;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The e2e-only session helper (spec 019 US7): it issues the same session a password sign-in would, and CSRF does not apply
 * because only the Playwright setup calls it. The prod/local absence is covered by {@code ProdTestSignInAbsenceTest}.
 */
@ActiveProfiles("e2e")
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import(IdentitySecurityConfiguration.class)
@WebMvcTest(TestSignInController.class)
class TestSignInControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private IdentityUserDetailsService identityUserDetailsService;

    @MockitoBean
    private MachineTokenService machineTokenService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @Test
    void createsASessionWithoutAPasswordOrCsrfToken() throws Exception {
        when(userAccountRepository.findByUsername("e2e-admin")).thenReturn(Optional.of(approvedAdmin()));

        mockMvc.perform(post("/api/identity/test/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"e2e-admin\"}"))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT",
                        org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.username").value("e2e-admin"))
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    void refusesAnUnknownAccount() throws Exception {
        when(userAccountRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/identity/test/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"));
    }

    private static UserAccount approvedAdmin() {
        UserAccount admin = UserAccount.builder()
                .id(ADMIN_ID)
                .username("e2e-admin")
                .status(UserStatus.APPROVED)
                .role(UserRole.ADMIN)
                .build();
        admin.applyPassword("{bcrypt}$2a$10$hash");

        return admin;
    }
}
