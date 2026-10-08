package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.RomStatus;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlaceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RomStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameInstallationRepository installationRepository;

    private RomStatusService service;
    private Game game;

    @BeforeEach
    void setUp() {
        service = new RomStatusService(gameRepository, installationRepository);
        game = Game.builder().title("Chrono Trigger").build();
    }

    private GameInstallation copyOn(String hostname, SourceType type, boolean enabled) {
        ScanSource source = ScanSource.builder().host(Host.builder().hostname(hostname).build()).sourceType(type)
                .enabled(enabled).build();

        return GameInstallation.builder().game(game).source(source).externalRef("snes/ct.sfc").platform("SNES")
                .firstSeenAt(NOW).lastSeenAt(NOW).build();
    }

    private void visible() {
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.of(game));
    }

    @Test
    void marksAnEmulatedCopyBrokenAndReturnsThePlaceWithItsStatus() {
        visible();
        GameInstallation copy = copyOn("htpc", SourceType.EMUDECK, true);
        when(installationRepository.findByIdAndGameId(copy.getId(), game.getId())).thenReturn(Optional.of(copy));
        when(installationRepository.save(copy)).thenReturn(copy);

        PlaceResponse place = service.setStatus(game.getId(), copy.getId(), RomStatus.BROKEN);

        assertSoftly(softly -> {
            softly.assertThat(place.romStatus()).isEqualTo(RomStatus.BROKEN);
            softly.assertThat(place.label()).isEqualTo("htpc");
            softly.assertThat(place.installationId()).isEqualTo(copy.getId());
            softly.assertThat(copy.getRomStatus()).isEqualTo(RomStatus.BROKEN);
        });
    }

    @Test
    void theLastWriteWinsAndNoneIsMarkedUnknownAgain() {
        visible();
        GameInstallation copy = copyOn("htpc", SourceType.EMUDECK, true);
        when(installationRepository.findByIdAndGameId(copy.getId(), game.getId())).thenReturn(Optional.of(copy));
        when(installationRepository.save(copy)).thenReturn(copy);

        service.setStatus(game.getId(), copy.getId(), RomStatus.VALIDATED);
        service.setStatus(game.getId(), copy.getId(), RomStatus.BROKEN);
        PlaceResponse cleared = service.setStatus(game.getId(), copy.getId(), RomStatus.UNKNOWN);

        assertThat(cleared.romStatus()).isEqualTo(RomStatus.UNKNOWN);
    }

    @Test
    void aSteamCopyHasNoRomStatus() {
        visible();
        GameInstallation steamCopy = copyOn("htpc", SourceType.STEAM, true);
        when(installationRepository.findByIdAndGameId(steamCopy.getId(), game.getId())).thenReturn(Optional.of(steamCopy));

        assertThatThrownBy(() -> service.setStatus(game.getId(), steamCopy.getId(), RomStatus.VALIDATED))
                .isInstanceOf(RomStatusNotApplicableException.class);

        verify(installationRepository, never()).save(steamCopy);
    }

    @Test
    void aCopyOfAnotherGameIsNotFound() {
        visible();
        UUID someoneElsesCopy = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        when(installationRepository.findByIdAndGameId(someoneElsesCopy, game.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setStatus(game.getId(), someoneElsesCopy, RomStatus.BROKEN))
                .isInstanceOf(InstallationNotFoundException.class);
    }

    @Test
    void aCopyOnADisabledSourceIsNotFoundBecauseThePageDoesNotShowIt() {
        visible();
        GameInstallation hidden = copyOn("oldbox", SourceType.EMUDECK, false);
        when(installationRepository.findByIdAndGameId(hidden.getId(), game.getId())).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.setStatus(game.getId(), hidden.getId(), RomStatus.BROKEN))
                .isInstanceOf(InstallationNotFoundException.class);
    }

    @Test
    void aGameThatIsNotVisibleIsNotFound() {
        when(gameRepository.findVisibleById(game.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setStatus(game.getId(), UUID.randomUUID(), RomStatus.BROKEN))
                .isInstanceOf(InstallationNotFoundException.class);
    }
}
