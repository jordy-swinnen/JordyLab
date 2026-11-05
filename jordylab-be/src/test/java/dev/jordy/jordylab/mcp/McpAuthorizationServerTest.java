package dev.jordy.jordylab.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jordy.jordylab.identity.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The connector's sign-in (spec 021 FR-002, FR-004; contracts/oauth-authorization-server.md): the login bridge, PKCE,
 * the token claims, refresh rotation and the guest refusal. It drives the same requests Claude makes, against the real
 * authorization server and the identity module's e2e session helper.
 */
class McpAuthorizationServerTest extends McpIntegrationTestSupport {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository registeredClientRepository;

    @Autowired
    private org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator<? extends org.springframework.security.oauth2.core.OAuth2Token> tokenGenerator;

    @Autowired
    private org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizationService;

    @Test
    void generatesARefreshTokenForThePublicClient() {
        org.springframework.security.oauth2.server.authorization.client.RegisteredClient client =
                registeredClientRepository.findByClientId(CLIENT_ID);
        org.springframework.security.core.Authentication principal =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("account-id", null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin")));
        org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext context =
                org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext.builder()
                        .registeredClient(client)
                        .principal(principal)
                        .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrant(new org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken(
                                client, org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE, null))
                        .tokenType(org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN)
                        .build();
        assertThat(tokenGenerator.generate(context))
                .isInstanceOf(org.springframework.security.oauth2.core.OAuth2RefreshToken.class);
    }

    @Test
    void registersTheDesktopAndLocalAgentCallbacks() {
        org.springframework.security.oauth2.server.authorization.client.RegisteredClient client =
                registeredClientRepository.findByClientId(CLIENT_ID);

        assertThat(client.getRedirectUris()).containsExactlyInAnyOrder(
                "https://claude.ai/api/mcp/auth_callback",
                "http://localhost:19877/callback",
                "http://127.0.0.1:19877/callback",
                "http://127.0.0.1:19876/mcp/oauth/callback",
                "http://localhost:19876/mcp/oauth/callback");
    }

    @Test
    void servesTheAuthorizationServerMetadata() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("https://jordylab.be"))
                .andExpect(jsonPath("$.authorization_endpoint").value("https://jordylab.be/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("https://jordylab.be/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value("https://jordylab.be/oauth2/jwks"))
                .andExpect(jsonPath("$.code_challenge_methods_supported", hasItem("S256")));
    }

    @Test
    void sendsAnUnauthenticatedAuthorizationRequestToTheLoginPageWithTheExactRequest() throws Exception {
        MvcResult result = authorizeWithoutSession();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/login?returnUrl=");
        String returnUrl = location.substring("/login?returnUrl=".length());
        String decoded = URLDecoder.decode(returnUrl, StandardCharsets.UTF_8);
        assertSoftly(softly -> {
            softly.assertThat(decoded).startsWith("/oauth2/authorize");
            softly.assertThat(decoded).contains("state=" + STATE);
            softly.assertThat(decoded).contains("code_challenge_method=S256");
        });
    }

    @Test
    void issuesAnAccessTokenWithTheConnectorAudienceTheAdminRoleAndTheAccountId() throws Exception {
        UUID adminId = userAccountRepository.findByUsername(ADMIN_USERNAME).map(UserAccount::getId).orElseThrow();
        MvcResult authorization = authorize(signIn(ADMIN_USERNAME));
        String code = redirectParameters(authorization).getFirst("code");
        JsonNode tokens = tokenResponse(code);

        Jwt accessToken = jwtDecoder.decode(tokens.path("access_token").asText());
        assertSoftly(softly -> {
            softly.assertThat(accessToken.getAudience()).containsExactly("jordylab-mcp");
            softly.assertThat(accessToken.getSubject()).isEqualTo(adminId.toString());
            softly.assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("admin");
            softly.assertThat(accessToken.getClaimAsStringList("scope")).containsExactly("jordylab.mcp");
            softly.assertThat(Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()))
                    .isEqualTo(Duration.ofMinutes(10));
            softly.assertThat(tokens.path("refresh_token").asText()).isNotBlank();
            softly.assertThat(tokens.path("token_type").asText()).isEqualTo("Bearer");
        });
    }

    @Test
    void rotatesTheRefreshTokenAndRefusesTheReusedOne() throws Exception {
        JsonNode first = tokenResponse(redirectParameters(authorize(signIn(ADMIN_USERNAME))).getFirst("code"));
        String firstRefreshToken = first.path("refresh_token").asText();

        JsonNode second = refresh(firstRefreshToken);
        MvcResult reused = refreshWithStatus(firstRefreshToken, 400);
        String reusedBody = reused.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertSoftly(softly -> {
            softly.assertThat(second.path("access_token").asText()).isNotBlank();
            softly.assertThat(second.path("refresh_token").asText()).isNotEqualTo(firstRefreshToken);
            softly.assertThat(reusedBody).contains("invalid_grant");
        });
    }

    @Test
    void refusesAGuestAtTheAuthorizationRequest() throws Exception {
        MvcResult authorization = authorize(signIn(GUEST_USERNAME));
        MultiValueMap<String, String> parameters = redirectParameters(authorization);

        assertSoftly(softly -> {
            softly.assertThat(parameters.getFirst("error")).isEqualTo("access_denied");
            softly.assertThat(parameters.getFirst("code")).isNull();
        });
    }

    @Test
    void refusesAPlainPkceChallenge() throws Exception {
        MvcResult authorization = authorize(signIn(ADMIN_USERNAME), "plain");
        MultiValueMap<String, String> parameters = redirectParameters(authorization);

        assertSoftly(softly -> {
            softly.assertThat(parameters.getFirst("error")).isEqualTo("invalid_request");
            softly.assertThat(parameters.getFirst("code")).isNull();
        });
    }
}
