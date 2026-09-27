package dev.jordy.jordylab.gamecatalog.domain;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibrarySyncRunTest {

    @Test
    void buildLibrarySyncRun() {
        LibrarySyncRun run = LibrarySyncRunTestBuilder.aDefaultLibrarySyncRun();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(run.getId()).isNotNull();
            softly.assertThat(run.getLibrarySource()).isEqualTo(LibrarySource.OWNED);
            softly.assertThat(run.getOutcome()).isEqualTo(LibrarySyncOutcome.APPLIED);
            softly.assertThat(run.getStartedAt()).isEqualTo(LibrarySyncRunTestBuilder.DEFAULT_STARTED_AT);
            softly.assertThat(run.getFinishedAt()).isEqualTo(LibrarySyncRunTestBuilder.DEFAULT_FINISHED_AT);
        });
    }

    @Test
    void buildWithoutLibrarySource() {
        assertThatThrownBy(() -> LibrarySyncRunTestBuilder.aLibrarySyncRun().librarySource(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithDerivedLocalSourceIsRejected() {
        assertThatThrownBy(() -> LibrarySyncRunTestBuilder.aLibrarySyncRun()
                .librarySource(LibrarySource.LOCAL).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutOutcome() {
        assertThatThrownBy(() -> LibrarySyncRunTestBuilder.aLibrarySyncRun().outcome(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithoutTimestamps() {
        assertThatThrownBy(() -> LibrarySyncRunTestBuilder.aLibrarySyncRun().startedAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LibrarySyncRunTestBuilder.aLibrarySyncRun().finishedAt(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildWithNegativeCountsIsRejected() {
        assertThatThrownBy(() -> LibrarySyncRunTestBuilder.aLibrarySyncRun().entriesAdded(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals() {
        EqualsVerifier.forClass(LibrarySyncRun.class)
                .usingGetClass()
                .suppress(Warning.SURROGATE_KEY)
                .suppress(Warning.IDENTICAL_COPY_FOR_VERSIONED_ENTITY)
                .suppress(Warning.STRICT_HASHCODE)
                .verify();
    }
}
