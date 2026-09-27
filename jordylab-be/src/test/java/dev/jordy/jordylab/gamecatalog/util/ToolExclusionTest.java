package dev.jordy.jordylab.gamecatalog.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExclusionTest {

    @Test
    void knownRuntimeAppIdsAreTools() {
        assertThat(ToolExclusion.isToolAppId("228980")).isTrue();
        assertThat(ToolExclusion.isToolAppId("1070560")).isTrue();
        assertThat(ToolExclusion.isToolAppId("1391110")).isTrue();
        assertThat(ToolExclusion.isToolAppId("1628350")).isTrue();
        assertThat(ToolExclusion.isToolAppId("1493710")).isTrue();
    }

    @Test
    void regularGameAppIdsAreNotTools() {
        assertThat(ToolExclusion.isToolAppId("620")).isFalse();
        assertThat(ToolExclusion.isToolAppId(null)).isFalse();
    }

    @Test
    void toolNamesAreMatchedCaseInsensitively() {
        assertThat(ToolExclusion.isToolName("Proton Experimental")).isTrue();
        assertThat(ToolExclusion.isToolName("Steam Linux Runtime 3.0 (sniper)")).isTrue();
        assertThat(ToolExclusion.isToolName("Steamworks Common Redistributables")).isTrue();
        assertThat(ToolExclusion.isToolName("Portal 2")).isFalse();
        assertThat(ToolExclusion.isToolName(null)).isFalse();
    }
}
