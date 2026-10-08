package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceNameServiceTest {

    private static final String LOCK_SQL = "select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)";

    @Mock
    private HostRepository hostRepository;

    @Mock
    private ConsoleRepository consoleRepository;

    @Mock
    private EntityManager entityManager;

    @Mock(answer = Answers.RETURNS_SELF)
    private Query lockQuery;

    private PlaceNameService placeNameService;

    @BeforeEach
    void setUp() {
        lenient().when(entityManager.createNativeQuery(LOCK_SQL)).thenReturn(lockQuery);
        placeNameService = new PlaceNameService(hostRepository, consoleRepository, entityManager);
    }

    @Test
    void aFreeNameIsReturnedTrimmedAfterTakingTheLock() {
        when(hostRepository.findAll()).thenReturn(List.of());
        when(consoleRepository.findAll()).thenReturn(List.of());

        String name = placeNameService.requireAvailableHostName("  Living room PC ", null);

        assertThat(name).isEqualTo("Living room PC");
        verify(lockQuery).getSingleResult();
    }

    @Test
    void aBlankNameClearsTheDisplayNameWithoutALockOrLookup() {
        String name = placeNameService.requireAvailableHostName("   ", null);

        assertThat(name).isEmpty();
    }

    @Test
    void aNameTakenByAnotherHostDisplayNameIsRejectedIgnoringCase() {
        Host other = Host.builder().hostname("cachyos-htpc").displayName("Living room").build();
        when(hostRepository.findAll()).thenReturn(List.of(other));
        when(consoleRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> placeNameService.requireAvailableHostName("LIVING ROOM", null))
                .isInstanceOf(NameTakenException.class);
    }

    @Test
    void aNameEqualToAnotherHostsHostnameIsRejectedEvenWhenItShowsADisplayName() {
        Host other = Host.builder().hostname("cachyos-htpc").displayName("Living room").build();
        when(hostRepository.findAll()).thenReturn(List.of(other));
        when(consoleRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> placeNameService.requireAvailableHostName("CachyOS-HTPC", null))
                .isInstanceOf(NameTakenException.class);
    }

    @Test
    void aNameTakenByAConsoleIsRejectedForAHostAndTheOtherWayAround() {
        Console console = Console.builder().platform("Nintendo Switch").name("Living room").build();
        when(hostRepository.findAll()).thenReturn(List.of());
        when(consoleRepository.findAll()).thenReturn(List.of(console));

        assertThatThrownBy(() -> placeNameService.requireAvailableHostName("living room", null))
                .isInstanceOf(NameTakenException.class);
    }

    @Test
    void keepingTheOwnNameIsAllowed() {
        Host own = Host.builder().hostname("cachyos-htpc").displayName("Living room").build();
        when(hostRepository.findAll()).thenReturn(List.of(own));
        when(consoleRepository.findAll()).thenReturn(List.of());

        String name = placeNameService.requireAvailableHostName("Living room", own.getId());

        assertThat(name).isEqualTo("Living room");
    }

    @Test
    void aNameBeyondTheLimitIsRejectedBeforeAnyLookup() {
        String tooLong = "x".repeat(Host.MAX_DISPLAY_NAME_LENGTH + 1);

        assertThatThrownBy(() -> placeNameService.requireAvailableHostName(tooLong, UUID.randomUUID()))
                .isInstanceOf(NameTooLongException.class);
    }

    @Test
    void aConsoleNameUsesTheConsoleLengthLimit() {
        String tooLong = "x".repeat(Console.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> placeNameService.requireAvailableConsoleName(tooLong, null))
                .isInstanceOf(NameTooLongException.class);
    }
}
