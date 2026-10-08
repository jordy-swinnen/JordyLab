package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostServiceTest {

    private static final UUID HOST_ID = UUID.fromString("5b8e2f4a-9c1d-4e7f-a3b6-0d2c4e6f8a1b");
    private static final String HOSTNAME = "cachyos-htpc";

    @Mock
    private HostRepository hostRepository;

    @Mock
    private PlaceNameService placeNameService;

    private HostService hostService;

    private Host host;

    @BeforeEach
    void setUp() {
        hostService = new HostService(hostRepository, placeNameService);
        host = Host.builder().id(HOST_ID).hostname(HOSTNAME).build();
    }

    @Test
    void namingAHostChangesTheLabelEverywhere() {
        when(hostRepository.findById(HOST_ID)).thenReturn(Optional.of(host));
        when(placeNameService.requireAvailableHostName("Living room PC", HOST_ID)).thenReturn("Living room PC");
        when(hostRepository.save(host)).thenReturn(host);

        HostResponse response = hostService.setDisplayName(HOST_ID, "Living room PC").orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(response.label()).isEqualTo("Living room PC");
            softly.assertThat(response.displayName()).isEqualTo("Living room PC");
            softly.assertThat(response.hostname()).isEqualTo(HOSTNAME);
            softly.assertThat(host.label()).isEqualTo("Living room PC");
        });
    }

    @Test
    void aBlankNameBringsTheHostnameBack() {
        host.rename("Living room PC");
        when(hostRepository.findById(HOST_ID)).thenReturn(Optional.of(host));
        when(placeNameService.requireAvailableHostName("  ", HOST_ID)).thenReturn("");
        when(hostRepository.save(host)).thenReturn(host);

        HostResponse response = hostService.setDisplayName(HOST_ID, "  ").orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(response.displayName()).isNull();
            softly.assertThat(response.label()).isEqualTo(HOSTNAME);
        });
    }

    @Test
    void aNameAnotherHostOrConsoleHasIsRefusedAndNothingIsSaved() {
        when(hostRepository.findById(HOST_ID)).thenReturn(Optional.of(host));
        when(placeNameService.requireAvailableHostName("Nintendo Switch", HOST_ID))
                .thenThrow(new NameTakenException("Nintendo Switch"));

        assertThatThrownBy(() -> hostService.setDisplayName(HOST_ID, "Nintendo Switch"))
                .isInstanceOf(NameTakenException.class);

        assertThat(host.getDisplayName()).isNull();
    }

    @Test
    void aTooLongNameIsRefused() {
        when(hostRepository.findById(HOST_ID)).thenReturn(Optional.of(host));
        String tooLong = "x".repeat(Host.MAX_DISPLAY_NAME_LENGTH + 1);
        when(placeNameService.requireAvailableHostName(tooLong, HOST_ID))
                .thenThrow(new NameTooLongException(Host.MAX_DISPLAY_NAME_LENGTH));

        assertThatThrownBy(() -> hostService.setDisplayName(HOST_ID, tooLong)).isInstanceOf(NameTooLongException.class);
    }

    @Test
    void anUnknownHostGivesNothingBack() {
        UUID unknown = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        when(hostRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThat(hostService.setDisplayName(unknown, "Anything")).isEmpty();

        verify(hostRepository).findById(unknown);
    }
}
