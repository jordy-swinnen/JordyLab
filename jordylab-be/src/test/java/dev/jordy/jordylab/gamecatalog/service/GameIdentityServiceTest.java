package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameIdentityServiceTest {

    private static final String STEAM_APP_ID = "1145360";
    private static final String IGDB_ID = "119133";

    @Mock
    private GameRepository gameRepository;

    @Mock
    private TitleKeyLock titleKeyLock;

    @InjectMocks
    private GameIdentityService gameIdentityService;

    @Test
    void aKnownSteamAppIdReturnsTheSameGameWithoutTakingTheTitleLock() {
        Game hades = Game.builder().title("Hades").steamAppId(STEAM_APP_ID).titleSource(TitleSource.MANIFEST).build();
        when(gameRepository.findBySteamAppId(STEAM_APP_ID)).thenReturn(Optional.of(hades));

        Game resolved = gameIdentityService.resolveOrCreateSteamGame(STEAM_APP_ID, "Hades", TitleSource.LIBRARY);

        assertSoftly(softly -> {
            softly.assertThat(resolved).isSameAs(hades);
            softly.assertThat(hades.getTitleSource()).isEqualTo(TitleSource.LIBRARY);
        });
        verify(titleKeyLock, never()).acquire("hades");
    }

    @Test
    void aSteamAppJoinsAnExistingGameWithTheSameTitleThatHadNoSteamId() {
        Game scannedRom = Game.builder().title("Hades").titleSource(TitleSource.ROM).build();
        when(gameRepository.findBySteamAppId(STEAM_APP_ID)).thenReturn(Optional.empty());
        when(gameRepository.findAllByTitleKeyOrderByCreatedDateAsc("hades")).thenReturn(List.of(scannedRom));

        Game resolved = gameIdentityService.resolveOrCreateSteamGame(STEAM_APP_ID, "Hades", TitleSource.LIBRARY);

        assertSoftly(softly -> {
            softly.assertThat(resolved).isSameAs(scannedRom);
            softly.assertThat(resolved.getSteamAppId()).isEqualTo(STEAM_APP_ID);
        });
        verify(titleKeyLock).acquire("hades");
        verify(gameRepository, never()).insertSteamGameIfAbsent(scannedRom.getId(), STEAM_APP_ID, "Hades", "hades", "LIBRARY");
    }

    @Test
    void aSteamAppNeverJoinsAGameThatAlreadyHasADifferentSteamId() {
        Game otherHades = Game.builder().title("Hades").steamAppId("999").titleSource(TitleSource.LIBRARY).build();
        Game created = Game.builder().title("Hades").steamAppId(STEAM_APP_ID).titleSource(TitleSource.LIBRARY).build();
        when(gameRepository.findBySteamAppId(STEAM_APP_ID)).thenReturn(Optional.empty(), Optional.empty(),
                Optional.of(created));
        when(gameRepository.findAllByTitleKeyOrderByCreatedDateAsc("hades")).thenReturn(List.of(otherHades));

        Game resolved = gameIdentityService.resolveOrCreateSteamGame(STEAM_APP_ID, "Hades", TitleSource.LIBRARY);

        assertSoftly(softly -> {
            softly.assertThat(resolved).isSameAs(created);
            softly.assertThat(otherHades.getSteamAppId()).isEqualTo("999");
        });
        ArgumentCaptor<java.util.UUID> idCaptor = ArgumentCaptor.forClass(java.util.UUID.class);
        verify(gameRepository).insertSteamGameIfAbsent(idCaptor.capture(), eq(STEAM_APP_ID), eq("Hades"), eq("hades"),
                eq("LIBRARY"));
        assertThat(idCaptor.getValue()).isNotNull();
    }

    @Test
    void aSteamGameCreatedByAConcurrentTransactionWhileWaitingForTheLockIsReused() {
        Game createdMeanwhile = Game.builder().title("Hades").steamAppId(STEAM_APP_ID).titleSource(TitleSource.LIBRARY)
                .build();
        when(gameRepository.findBySteamAppId(STEAM_APP_ID)).thenReturn(Optional.empty(), Optional.of(createdMeanwhile));

        Game resolved = gameIdentityService.resolveOrCreateSteamGame(STEAM_APP_ID, "Hades", TitleSource.LIBRARY);

        assertThat(resolved).isSameAs(createdMeanwhile);
        verify(gameRepository, never()).findAllByTitleKeyOrderByCreatedDateAsc("hades");
    }

    @Test
    void aTitleOnlyGameReusesTheOldestGameWithTheSameNormalisedTitle() {
        Game oldest = Game.builder().title("Super Mario World").titleSource(TitleSource.ROM).build();
        Game newer = Game.builder().title("Super Mario World (USA)").titleSource(TitleSource.ROM).build();
        when(gameRepository.findAllByTitleKeyOrderByCreatedDateAsc("super mario world"))
                .thenReturn(List.of(oldest, newer));

        Game resolved = gameIdentityService.resolveOrCreateByTitle("SUPER MARIO WORLD (USA)", TitleSource.ROM);

        assertThat(resolved).isSameAs(oldest);
        verify(titleKeyLock).acquire("super mario world");
    }

    @Test
    void aTitleOnlyGameWithNoMatchIsCreated() {
        when(gameRepository.findAllByTitleKeyOrderByCreatedDateAsc("celeste")).thenReturn(List.of());

        gameIdentityService.resolveOrCreateByTitle("Celeste", TitleSource.MANIFEST);

        ArgumentCaptor<Game> saved = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(saved.capture());
        assertSoftly(softly -> {
            softly.assertThat(saved.getValue().getTitle()).isEqualTo("Celeste");
            softly.assertThat(saved.getValue().getTitleKey()).isEqualTo("celeste");
            softly.assertThat(saved.getValue().getTitleSource()).isEqualTo(TitleSource.MANIFEST);
        });
    }

    @Test
    void aKnownIgdbIdReturnsThatGame() {
        Game known = Game.builder().title("Hades").igdbGameId(IGDB_ID).titleSource(TitleSource.MANIFEST).build();
        when(gameRepository.findByIgdbGameId(IGDB_ID)).thenReturn(Optional.of(known));

        Game resolved = gameIdentityService.resolveOrCreateByIgdbId(IGDB_ID, "Hades", TitleSource.MANIFEST);

        assertThat(resolved).isSameAs(known);
        verify(gameRepository, never()).findAllByTitleKeyOrderByCreatedDateAsc("hades");
    }

    @Test
    void anIgdbIdAdoptsAnExistingGameWithTheSameTitleThatHadNoIgdbId() {
        Game withoutId = Game.builder().title("Hades").titleSource(TitleSource.ROM).build();
        when(gameRepository.findByIgdbGameId(IGDB_ID)).thenReturn(Optional.empty());
        when(gameRepository.findAllByTitleKeyOrderByCreatedDateAsc("hades")).thenReturn(List.of(withoutId));

        Game resolved = gameIdentityService.resolveOrCreateByIgdbId(IGDB_ID, "Hades", TitleSource.MANIFEST);

        assertSoftly(softly -> {
            softly.assertThat(resolved).isSameAs(withoutId);
            softly.assertThat(resolved.getIgdbGameId()).isEqualTo(IGDB_ID);
        });
    }

    @Test
    void aDifferentIgdbIdWithTheSameTitleIsADifferentGame() {
        Game remake = Game.builder().title("Prince of Persia").igdbGameId("1").titleSource(TitleSource.MANIFEST).build();
        when(gameRepository.findByIgdbGameId("2")).thenReturn(Optional.empty());
        when(gameRepository.findAllByTitleKeyOrderByCreatedDateAsc("prince of persia")).thenReturn(List.of(remake));

        gameIdentityService.resolveOrCreateByIgdbId("2", "Prince of Persia", TitleSource.MANIFEST);

        ArgumentCaptor<Game> saved = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(saved.capture());
        assertSoftly(softly -> {
            softly.assertThat(saved.getValue().getIgdbGameId()).isEqualTo("2");
            softly.assertThat(remake.getIgdbGameId()).isEqualTo("1");
        });
    }
}
