package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.IdentitySecurityConfiguration;
import dev.jordy.jordylab.identity.service.IdentityUserDetailsService;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import dev.jordy.jordylab.identity.ManagedApp;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.identity.service.AccountLockedException;
import dev.jordy.jordylab.identity.service.AccountRejectedException;
import dev.jordy.jordylab.identity.service.InvalidCredentialsException;
import dev.jordy.jordylab.identity.service.PasswordResetService;
import dev.jordy.jordylab.identity.service.SessionSignInService;
import dev.jordy.jordylab.identity.service.UserAccountService;
import dev.jordy.jordylab.identity.service.WeakPasswordException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_ID;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.ADMIN_SUBJECT;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.asAdmin;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The auth endpoints against the real session chain (spec 019 T015): registration codes, login/session creation,
 * uniform failures, status refusals, /me, CSRF bootstrapping, own password/display-name changes.
 */
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import(IdentitySecurityConfiguration.class)
@WebMvcTest(AuthenticationController.class)
class AuthenticationControllerTest {

    private static final UUID USER_ID = ADMIN_ID;
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(USER_ID, "jordy", "admin",
            Set.of(ManagedApp.GAME_CATALOG));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityUserDetailsService identityUserDetailsService;

    @MockitoBean
    private MachineTokenService machineTokenService;

    @MockitoBean
    private SessionSignInService sessionSignInService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @Test
    void registerCreatesAPendingAccount() throws Exception {
        when(userAccountService.register("jordy", "correct horse battery", null)).thenReturn(pendingAccount());

        mockMvc.perform(post("/api/identity/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jordy\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("jordy"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void registerMapsAWeakPassword() throws Exception {
        when(userAccountService.register("jordy", "short", null))
                .thenThrow(new WeakPasswordException("The password must be at least 12 characters"));

        mockMvc.perform(post("/api/identity/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jordy\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("WEAK_PASSWORD"));
    }

    @Test
    void loginCreatesASessionAndReturnsTheUser() throws Exception {
        when(sessionSignInService.signIn("jordy", "correct horse battery")).thenReturn(
                new UsernamePasswordAuthenticationToken(ADMIN, null,
                        List.of(new SimpleGrantedAuthority("ROLE_admin"))));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(approvedAdmin()));

        mockMvc.perform(post("/api/identity/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jordy\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT",
                        org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.username").value("jordy"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.appGrants[0]").value("gamecatalog"));
    }

    @Test
    void loginMapsInvalidCredentials() throws Exception {
        when(sessionSignInService.signIn("jordy", "wrong password"))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/identity/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jordy\",\"password\":\"wrong password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginMapsALockedAccount() throws Exception {
        when(sessionSignInService.signIn("jordy", "correct horse battery"))
                .thenThrow(new AccountLockedException(Instant.parse("2026-11-03T12:15:00Z")));

        mockMvc.perform(post("/api/identity/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jordy\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("ACCOUNT_LOCKED"));
    }

    @Test
    void loginMapsARejectedAccount() throws Exception {
        when(sessionSignInService.signIn("jordy", "correct horse battery"))
                .thenThrow(new AccountRejectedException());

        mockMvc.perform(post("/api/identity/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jordy\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("ACCOUNT_REJECTED"));
    }

    @Test
    void meIsUnauthorizedForAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/identity/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheSignedInUser() throws Exception {
        when(currentUserResolver.currentUser()).thenReturn(Optional.of(ADMIN));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(approvedAdmin()));

        mockMvc.perform(get("/api/identity/me").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADMIN_SUBJECT))
                .andExpect(jsonPath("$.username").value("jordy"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        mockMvc.perform(post("/api/identity/auth/logout").with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void csrfEndpointWritesTheCookie() throws Exception {
        mockMvc.perform(get("/api/identity/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void displayNameChangeGoesThroughTheService() throws Exception {
        when(currentUserResolver.currentUser()).thenReturn(Optional.of(ADMIN));

        mockMvc.perform(patch("/api/identity/me").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Jordy Swinnen\"}"))
                .andExpect(status().isNoContent());
        verify(userAccountService).changeOwnDisplayName(USER_ID, "Jordy Swinnen");
    }

    @Test
    void changePasswordNeedsTheCurrentOne() throws Exception {
        when(currentUserResolver.currentUser()).thenReturn(Optional.of(ADMIN));
        org.mockito.Mockito.doThrow(new InvalidCredentialsException())
                .when(userAccountService).changeOwnPassword(USER_ID, "wrong", "another password 12");

        mockMvc.perform(post("/api/identity/auth/change-password").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"another password 12\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("INVALID_CREDENTIALS"));
    }

    @Test
    void aResetRequestIsAcceptedAnonymously() throws Exception {
        mockMvc.perform(post("/api/identity/auth/reset-requests").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jordy\",\"newPassword\":\"a much better password\"}"))
                .andExpect(status().isAccepted());
        verify(passwordResetService).request("jordy", "a much better password");
    }

    @Test
    void aResetRequestWithAWeakPasswordIsRefused() throws Exception {
        doThrow(new WeakPasswordException("The password must be at least 12 characters"))
                .when(passwordResetService).request("jordy", "short");

        mockMvc.perform(post("/api/identity/auth/reset-requests").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jordy\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("WEAK_PASSWORD"));
    }

    private static UserAccount pendingAccount() {
        return UserAccount.builder()
                .id(USER_ID)
                .username("jordy")
                .status(UserStatus.PENDING)
                .build();
    }

    private static UserAccount approvedAdmin() {
        UserAccount account = UserAccount.builder()
                .id(USER_ID)
                .username("jordy")
                .status(UserStatus.APPROVED)
                .role(dev.jordy.jordylab.identity.domain.UserRole.ADMIN)
                .appGrants(Set.of(ManagedApp.GAME_CATALOG))
                .build();
        account.applyPassword("{bcrypt}$2a$10$hash");

        return account;
    }
}
