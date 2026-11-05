package dev.jordy.jordylab.mcp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.mcp.service.McpToolCallAdvice;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The connector's chain on connector tokens (spec 021 FR-003, FR-004, FR-021; contracts/mcp-endpoint.md): the 401
 * pointer, the protected resource metadata, the admin-only rule, the session-only {@code /api} chain and the per-call
 * account check.
 */
class McpSecurityTest extends McpIntegrationTestSupport {

    @Language("JSON")
    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}""";
    @Language("JSON")
    private static final String TOOLS_LIST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}""";
    @Language("JSON")
    private static final String LIST_PLACES = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_places","arguments":{}}}""";
    private static final Set<String> NINE_TOOLS = Set.of(
            "search_games", "get_game", "list_places", "list_sources", "get_library_status",
            "search_articles", "get_portfolio", "get_wishlist", "get_latest_briefing");

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;

    @Test
    void refusesAMcpCallWithoutATokenWithTheResourceMetadataPointer() throws Exception {
        mockMvc.perform(post("/mcp").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(INITIALIZE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer resource_metadata=\"https://jordylab.be/.well-known/oauth-protected-resource/mcp\""));
    }

    @Test
    void servesTheProtectedResourceMetadataWithoutAToken() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("https://jordylab.be/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("https://jordylab.be"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("jordylab.mcp"))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"))
                .andExpect(jsonPath("$.resource_name").value("JordyLab"))
                .andExpect(jsonPath("$.tls_client_certificate_bound_access_tokens").value(false));
    }

    @Test
    void refusesATokenIssuedForAnotherAudience() throws Exception {
        java.time.Instant now = java.time.Instant.now();
        String token = jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(
                                org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(),
                        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                                .issuer("https://jordylab.be")
                                .subject(java.util.UUID.randomUUID().toString())
                                .audience(java.util.List.of("another-service"))
                                .issuedAt(now)
                                .expiresAt(now.plus(java.time.Duration.ofMinutes(10)))
                                .claim("roles", java.util.List.of("admin"))
                                .build()))
                .getTokenValue();
        mockMvc.perform(mcpPost(token, INITIALIZE)).andExpect(status().isUnauthorized());
    }

    @Test
    void answersInitializeAndListsOnlyTheNineReadOnlyTools() throws Exception {
        String token = adminAccessToken();
        MvcResult initialized = mockMvc.perform(mcpPost(token, INITIALIZE)).andExpect(status().isOk()).andReturn();
        MvcResult listed = mockMvc.perform(mcpPost(token, TOOLS_LIST)).andExpect(status().isOk()).andReturn();

        List<String> names = new ArrayList<>();
        JsonNode initializeResponse = jsonRpc(initialized);
        jsonRpc(listed).path("result").path("tools").forEach(tool -> names.add(tool.path("name").asText()));
        assertSoftly(softly -> {
            softly.assertThat(initializeResponse.path("result").path("serverInfo").path("name").asText())
                    .isEqualTo("jordylab");
            softly.assertThat(names).containsExactlyInAnyOrderElementsOf(NINE_TOOLS);
        });
    }

    @Test
    void refusesAConnectorTokenOnTheSessionOnlyApiChain() throws Exception {
        String token = adminAccessToken();
        mockMvc.perform(get("/api/gamecatalog/games").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesTheNextToolCallWhenTheAdminRoleIsRemoved() throws Exception {
        String token = adminAccessToken();
        UserAccount admin = userAccountRepository.findByUsername(ADMIN_USERNAME).orElseThrow();
        admin.revoke();
        userAccountRepository.save(admin);
        Logger auditLogger = (Logger) LoggerFactory.getLogger("dev.jordy.jordylab.mcp.audit");
        ListAppender<ILoggingEvent> auditEvents = new ListAppender<>();
        auditEvents.start();
        auditLogger.addAppender(auditEvents);
        try {
            MvcResult result = mockMvc.perform(mcpPost(token, LIST_PLACES)).andExpect(status().isOk()).andReturn();
            JsonNode body = jsonRpc(result);
            assertSoftly(softly -> {
                softly.assertThat(body.path("result").path("isError").asBoolean()).isTrue();
                softly.assertThat(body.path("result").path("content").path(0).path("text").asText().strip())
                        .isEqualTo(McpToolCallAdvice.ACCOUNT_REFUSED_MESSAGE);
                softly.assertThat(auditEvents.list)
                        .anyMatch(event -> event.getFormattedMessage().contains("outcome=refused"));
            });
        } finally {
            auditLogger.detachAppender(auditEvents);
        }
    }

    @Test
    void refusesAnUnknownToolWithoutAnOkAuditLine() throws Exception {
        String token = adminAccessToken();
        Logger auditLogger = (Logger) LoggerFactory.getLogger("dev.jordy.jordylab.mcp.audit");
        ListAppender<ILoggingEvent> auditEvents = new ListAppender<>();
        auditEvents.start();
        auditLogger.addAppender(auditEvents);
        try {
            MvcResult result = mockMvc.perform(mcpPost(token,
                            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                                    + "\"params\":{\"name\":\"create_game\",\"arguments\":{}}}"))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = jsonRpc(result);
            assertThat(body.has("error") || body.path("result").path("isError").asBoolean(false)).isTrue();
            assertThat(auditEvents.list)
                    .noneMatch(event -> event.getFormattedMessage().contains("tool=create_game"));
        } finally {
            auditLogger.detachAppender(auditEvents);
        }
    }
}
