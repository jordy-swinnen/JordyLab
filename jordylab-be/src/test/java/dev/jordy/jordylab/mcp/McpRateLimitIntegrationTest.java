package dev.jordy.jordylab.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.settings.KeycloakIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The 61st tool call in a minute is refused and not run (FR-020, SC-012), through the real protocol path. It has its own
 * application context, so its rate-limit state does not leak into other test classes.
 */
@TestPropertySource(properties = "jordylab.mcp.public-base-url=https://jordylab.be")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class McpRateLimitIntegrationTest extends KeycloakIntegrationTest {

    private static final String LIST_PLACES = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_places","arguments":{}}}""";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Test
    void refusesTheSixtyFirstCallInAMinuteWithoutRunningIt() throws Exception {
        String adminToken = accessToken();

        for (int call = 1; call <= 60; call++) {
            MvcResult result = mockMvc.perform(mcpCall(adminToken)).andExpect(status().isOk()).andReturn();
            assertThat(isError(result)).as("call %d", call).isFalse();
        }

        MvcResult refused = mockMvc.perform(mcpCall(adminToken)).andExpect(status().isOk()).andReturn();

        assertThat(isError(refused)).isTrue();
        assertThat(refusedText(refused).strip()).isEqualTo("Too many requests in one minute. Try again shortly.");
    }

    private MockHttpServletRequestBuilder mcpCall(String token) {
        return post("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("MCP-Protocol-Version", "2025-06-18")
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(LIST_PLACES);
    }

    private static boolean isError(MvcResult result) throws Exception {
        return McpConnectorIntegrationTest.jsonRpc(result).path("result").path("isError").asBoolean();
    }

    private static String refusedText(MvcResult result) throws Exception {
        return McpConnectorIntegrationTest.jsonRpc(result).path("result").path("content").path(0).path("text").asText();
    }

    private String accessToken() throws Exception {
        String form = "grant_type=password&client_id=test-mcp"
                + "&username=" + URLEncoder.encode("admin-user", StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode("admin-password", StandardCharsets.UTF_8);
        String response = RestClient.create()
                .post()
                .uri(issuerUri + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return MAPPER.readTree(response).get("access_token").asText();
    }
}
