package dev.jordy.jordylab.mcp;

import dev.jordy.jordylab.fna.rest.controller.FnaMcpTools;
import dev.jordy.jordylab.gamecatalog.rest.controller.GameCatalogMcpTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class McpToolSurfaceTest {

    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "search_games", "get_game", "list_places", "list_sources", "get_library_status",
            "search_articles", "get_portfolio", "get_wishlist", "get_latest_briefing");
    private static final List<String> WRITE_VERBS = List.of(
            "create", "update", "delete", "approve", "reject", "scan", "refresh", "sync", "generate", "post", "send",
            "trigger", "reset", "end");

    @Test
    void exposesExactlyTheNineReadOnlyTools() {
        List<McpTool> tools = toolAnnotations();

        assertSoftly(softly -> {
            softly.assertThat(tools.stream().map(McpTool::name).toList())
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);
            softly.assertThat(tools.stream().allMatch(tool -> tool.annotations().readOnlyHint())).isTrue();
            softly.assertThat(tools.stream().allMatch(tool -> tool.description().endsWith("Read-only."))).isTrue();
        });
    }

    @Test
    void noToolNameUsesAWriteVerb() {
        List<String> names = toolAnnotations().stream().map(McpTool::name).toList();

        assertSoftly(softly -> names.forEach(name -> WRITE_VERBS.forEach(verb ->
                softly.assertThat(name).doesNotContain(verb))));
    }

    private static List<McpTool> toolAnnotations() {
        return Stream.of(GameCatalogMcpTools.class, FnaMcpTools.class)
                .flatMap(toolClass -> Arrays.stream(toolClass.getDeclaredMethods()))
                .map(method -> method.getAnnotation(McpTool.class))
                .filter(Objects::nonNull)
                .toList();
    }
}
