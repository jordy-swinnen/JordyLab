package dev.jordy.jordylab.mcp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.settings.KeycloakIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The connector's chain against the real identity provider (spec 021, FR-003, FR-021, research R4): the 401 pointer, the
 * metadata document, the audience refusal, the admin-only rule and the protocol path. Tokens are real Keycloak tokens.
 */
@TestPropertySource(properties = "jordylab.mcp.public-base-url=https://jordylab.be")
class McpConnectorIntegrationTest extends KeycloakIntegrationTest {

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}""";
    private static final String TOOLS_LIST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}""";
    private static final String UNKNOWN_TOOL = """
            {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"create_game","arguments":{}}}""";
    private static final Set<String> NINE_TOOLS = Set.of(
            "search_games", "get_game", "list_places", "list_sources", "get_library_status",
            "search_articles", "get_portfolio", "get_wishlist", "get_latest_briefing");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Test
    void refusesAMcpCallWithoutATokenWithTheResourceMetadataPointer() throws Exception {
        mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(INITIALIZE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer resource_metadata=\"https://jordylab.be/.well-known/oauth-protected-resource/mcp\""));
    }

    @Test
    void servesTheProtectedResourceMetadataWithoutAToken() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("https://jordylab.be/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value(issuerUri))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"))
                .andExpect(jsonPath("$.resource_name").value("JordyLab"))
                .andExpect(jsonPath("$.tls_client_certificate_bound_access_tokens").value(false));
    }

    @Test
    void refusesAPlatformTokenWithoutTheConnectorAudience() throws Exception {
        String platformToken = tokenFor("test-public", "admin-user", "admin-password");

        mockMvc.perform(mcpPost(INITIALIZE, platformToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAGuestEvenWithTheConnectorAudience() throws Exception {
        String guestToken = tokenFor("test-mcp", "guest-user", "guest-password");

        mockMvc.perform(mcpPost(INITIALIZE, guestToken)).andExpect(status().isForbidden());
    }

    @Test
    void answersInitializeForAnAdminWithTheConnectorAudience() throws Exception {
        String adminToken = tokenFor("test-mcp", "admin-user", "admin-password");

        MvcResult result = mockMvc.perform(mcpPost(INITIALIZE, adminToken)).andExpect(status().isOk()).andReturn();
        String serverName = jsonRpc(result).path("result").path("serverInfo").path("name").asText();

        assertSoftly(softly -> softly.assertThat(serverName).isEqualTo("jordylab"));
    }

    @Test
    void listsOnlyTheNineReadOnlyToolsToAnAdmin() throws Exception {
        String adminToken = tokenFor("test-mcp", "admin-user", "admin-password");

        MvcResult result = mockMvc.perform(mcpPost(TOOLS_LIST, adminToken)).andExpect(status().isOk()).andReturn();

        List<String> names = new ArrayList<>();
        jsonRpc(result).path("result").path("tools").forEach(tool -> names.add(tool.path("name").asText()));
        assertSoftly(softly -> {
            softly.assertThat(names).hasSize(9);
            softly.assertThat(names).containsExactlyInAnyOrderElementsOf(NINE_TOOLS);
        });
    }

    @Test
    void refusesAnUnknownToolAndRunsNothing() throws Exception {
        String adminToken = tokenFor("test-mcp", "admin-user", "admin-password");
        Logger auditLogger = (Logger) org.slf4j.LoggerFactory.getLogger("dev.jordy.jordylab.mcp.audit");
        ListAppender<ILoggingEvent> auditEvents = new ListAppender<>();
        auditEvents.start();
        auditLogger.addAppender(auditEvents);
        try {
            MvcResult result = mockMvc.perform(mcpPost(UNKNOWN_TOOL, adminToken)).andExpect(status().isOk()).andReturn();
            JsonNode body = jsonRpc(result);
            boolean refused = body.has("error") || body.path("result").path("isError").asBoolean(false);

            assertSoftly(softly -> {
                softly.assertThat(refused).isTrue();
                softly.assertThat(auditEvents.list)
                        .noneMatch(event -> event.getFormattedMessage().contains("tool=create_game"));
            });
        } finally {
            auditLogger.detachAppender(auditEvents);
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder mcpPost(String body, String token) {
        return post("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("MCP-Protocol-Version", "2025-06-18")
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    static JsonNode jsonRpc(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        String json = body.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .findFirst()
                .orElse(body);
        return MAPPER.readTree(json);
    }

    private String tokenFor(String clientId, String username, String password) {
        String form = "grant_type=password"
                + "&client_id=" + clientId
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        String response = RestClient.create()
                .post()
                .uri(issuerUri + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        try {
            return MAPPER.readTree(response).get("access_token").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Keycloak token response did not contain an access token", exception);
        }
    }
}
