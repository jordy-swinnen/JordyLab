package dev.jordy.jordylab.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.UserRole;
import dev.jordy.jordylab.identity.domain.UserStatus;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import jakarta.servlet.http.Cookie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for the connector's integration tests (spec 021). A real application context, Testcontainers Postgres and
 * the identity module's e2e sign-in helper create the session Claude gets after the browser sign-in. The tests then do
 * what Claude does: authorize with PKCE, exchange the code and call {@code /mcp} with the connector token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "e2e"})
@TestPropertySource(properties = "jordylab.mcp.public-base-url=https://jordylab.be")
abstract class McpIntegrationTestSupport {

    static final String ADMIN_USERNAME = "mcp.admin";
    static final String GUEST_USERNAME = "mcp.guest";
    static final String CLIENT_ID = "jordylab-mcp-connector";
    static final String REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback";
    static final String SCOPE = "jordylab.mcp";
    static final String STATE = "state-1";
    static final String CODE_VERIFIER = "mcp-connector-pkce-verifier-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String CODE_CHALLENGE = codeChallenge(CODE_VERIFIER);
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    static {
        // One database for every connector test class in this JVM; the hook stops it when the test run ends.
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserAccountRepository userAccountRepository;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void seedAccounts() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        userAccountRepository.deleteAll();
        userAccountRepository.save(account(ADMIN_USERNAME, UserRole.ADMIN));
        userAccountRepository.save(account(GUEST_USERNAME, UserRole.GUEST));
    }

    protected static UserAccount account(String username, UserRole role) {
        return UserAccount.builder()
                .username(username)
                .status(UserStatus.APPROVED)
                .role(role)
                .build();
    }

    /** The e2e helper creates the same session a password sign-in would (spec 019 US7). */
    protected Cookie signIn(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/identity/test/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).as("session cookie created by the test sign-in").isNotNull();

        return session;
    }

    protected MvcResult authorize(Cookie session) throws Exception {
        return authorize(session, "S256");
    }

    protected MvcResult authorize(Cookie session, String codeChallengeMethod) throws Exception {
        return mockMvc.perform(authorizeRequest(codeChallengeMethod).cookie(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    protected MvcResult authorizeWithoutSession() throws Exception {
        return mockMvc.perform(authorizeRequest("S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    protected String adminAccessToken() throws Exception {
        return accessTokenFor(ADMIN_USERNAME);
    }

    protected String accessTokenFor(String username) throws Exception {
        MvcResult authorization = authorize(signIn(username));
        String code = redirectParameters(authorization).getFirst("code");
        assertThat(code).as("authorization code").isNotNull();

        return tokenResponse(code).path("access_token").asText();
    }

    protected JsonNode tokenResponse(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code", code)
                        .param("code_verifier", CODE_VERIFIER))
                .andExpect(status().isOk())
                .andReturn();

        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", CLIENT_ID)
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andReturn();

        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    protected MvcResult refreshWithStatus(String refreshToken, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", CLIENT_ID)
                        .param("refresh_token", refreshToken))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected MockHttpServletRequestBuilder mcpPost(String token, String body) {
        return post("/mcp")
                .header("Authorization", "Bearer " + token)
                .header("MCP-Protocol-Version", "2025-06-18")
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    protected static JsonNode jsonRpc(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String json = body.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .findFirst()
                .orElse(body);

        return OBJECT_MAPPER.readTree(json);
    }

    protected static MultiValueMap<String, String> redirectParameters(MvcResult result) {
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).as("redirect location").isNotNull();

        return UriComponentsBuilder.fromUriString(location).build().getQueryParams();
    }

    private MockHttpServletRequestBuilder authorizeRequest(String codeChallengeMethod) {
        return get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", SCOPE)
                .queryParam("state", STATE)
                .queryParam("code_challenge", "plain".equals(codeChallengeMethod) ? CODE_VERIFIER : CODE_CHALLENGE)
                .queryParam("code_challenge_method", codeChallengeMethod)
                .accept(MediaType.TEXT_HTML);
    }

    private static String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the JDK", impossible);
        }
    }
}
