package dev.jordy.jordylab.mcp;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The 61st tool call in a minute is refused and not run (FR-020, SC-012), through the real protocol path and a real
 * connector token. It has its own application context, so its rate-limit state does not leak into other test classes.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class McpRateLimitIntegrationTest extends McpIntegrationTestSupport {

    @Language("JSON")
    private static final String LIST_PLACES = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_places","arguments":{}}}""";

    @Test
    void refusesTheSixtyFirstCallInAMinuteWithoutRunningIt() throws Exception {
        String token = adminAccessToken();
        for (int call = 1; call <= 60; call++) {
            MvcResult result = mockMvc.perform(mcpPost(token, LIST_PLACES)).andExpect(status().isOk()).andReturn();
            assertThat(isError(result)).as("call %d", call).isFalse();
        }

        MvcResult refused = mockMvc.perform(mcpPost(token, LIST_PLACES)).andExpect(status().isOk()).andReturn();
        assertThat(isError(refused)).isTrue();
        assertThat(refusedText(refused).strip()).isEqualTo("Too many requests in one minute. Try again shortly.");
    }

    private static boolean isError(MvcResult result) throws Exception {
        return jsonRpc(result).path("result").path("isError").asBoolean();
    }

    private static String refusedText(MvcResult result) throws Exception {
        return jsonRpc(result).path("result").path("content").path(0).path("text").asText();
    }
}
