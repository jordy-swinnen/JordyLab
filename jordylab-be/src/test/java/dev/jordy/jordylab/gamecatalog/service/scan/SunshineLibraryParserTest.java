package dev.jordy.jordylab.gamecatalog.service.scan;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ClientGame;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SunshineLibraryParserTest {

    private final SunshineLibraryParser parser = new SunshineLibraryParser();

    @Test
    void supportsSunshine() {
        assertThat(parser.supports()).isEqualTo(SourceType.SUNSHINE);
    }

    @Test
    void mapsConfiguredGames() {
        ScanRequest request = new ScanRequest(
                "machine-1",
                "sunshine-host",
                SourceType.SUNSHINE,
                Instant.parse("2026-11-11T10:00:00Z"),
                "sha256:abc",
                false,
                List.of(),
                null,
                List.of(new ClientGame("sunshine:desktop", "Desktop", "Sunshine")));

        List<GamePayload> parsed = parser.parse(request);

        assertThat(parsed).containsExactly(new GamePayload("sunshine:desktop", "Desktop", "Sunshine", null));
    }

    @Test
    void withoutClientGamesThereIsNothingToParse() {
        ScanRequest request = new ScanRequest(
                "machine-1",
                "sunshine-host",
                SourceType.SUNSHINE,
                Instant.parse("2026-11-11T10:00:00Z"),
                "sha256:abc",
                false,
                List.of(),
                null,
                null);

        assertThat(parser.parse(request)).isEmpty();
    }
}
