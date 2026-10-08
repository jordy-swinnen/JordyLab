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

    @Test
    void equalRankedScansDoNotRenameEachOtherButALibraryOrAPersonCan() {
        assertThat(TitleSource.ROM.replaces(TitleSource.MANIFEST)).isFalse();
        assertThat(TitleSource.MANIFEST.replaces(TitleSource.MANIFEST)).isFalse();
        assertThat(TitleSource.LIBRARY.replaces(TitleSource.LIBRARY)).isTrue();
        assertThat(TitleSource.MANUAL.replaces(TitleSource.MANUAL)).isTrue();
        assertThat(TitleSource.MANIFEST.replaces(null)).isTrue();
        assertThat(TitleSource.LIBRARY.replaces(TitleSource.ROM)).isTrue();
        assertThat(TitleSource.ROM.replaces(TitleSource.LIBRARY)).isFalse();
    }
}
