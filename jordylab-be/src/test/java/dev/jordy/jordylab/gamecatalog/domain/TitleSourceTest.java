package dev.jordy.jordylab.gamecatalog.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleSourceTest {

    @Test
    void manualOutranksAllOtherSources() {
        assertThat(TitleSource.MANUAL.outranks(TitleSource.LIBRARY)).isTrue();
        assertThat(TitleSource.MANUAL.outranks(TitleSource.MANIFEST)).isTrue();
        assertThat(TitleSource.MANUAL.outranks(TitleSource.ROM)).isTrue();
        assertThat(TitleSource.MANUAL.outranks(null)).isTrue();
    }

    @Test
    void libraryOutranksManifestAndRom() {
        assertThat(TitleSource.LIBRARY.outranks(TitleSource.MANIFEST)).isTrue();
        assertThat(TitleSource.LIBRARY.outranks(TitleSource.ROM)).isTrue();
    }

    @Test
    void manifestDoesNotOutrankLibrary() {
        assertThat(TitleSource.MANIFEST.outranks(TitleSource.LIBRARY)).isFalse();
    }
}
