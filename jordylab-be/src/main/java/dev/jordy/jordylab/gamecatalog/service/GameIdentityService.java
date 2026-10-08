package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.util.TitleKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The single place that decides whether something is a game the catalog already has (spec 013 FR-023 to FR-025, research
 * B2): a matching external id means the same game; two known ids of the same kind that differ mean different games;
 * otherwise (no id on at least one side) the same normalised title means the same game. Adding another place for a known
 * game never creates a duplicate and never overwrites richer data: the title only changes by authority, and enrichment,
 * artwork and marks are never touched here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameIdentityService {

    private final GameRepository gameRepository;
    private final TitleKeyLock titleKeyLock;

    /** A Steam game, found by app id; or an existing game of the same title that had no Steam id yet; or a new one. */
    @Transactional
    public Game resolveOrCreateSteamGame(String steamAppId, String title, TitleSource titleSource) {
        Optional<Game> known = gameRepository.findBySteamAppId(steamAppId);
        if (known.isPresent()) {
            known.get().updateCatalogInfo(title, titleSource);

            return known.get();
        }
        String titleKey = TitleKeys.keyFor(title);
        titleKeyLock.acquire(titleKey);

        Optional<Game> createdMeanwhile = gameRepository.findBySteamAppId(steamAppId);
        if (createdMeanwhile.isPresent()) {
            createdMeanwhile.get().updateCatalogInfo(title, titleSource);

            return createdMeanwhile.get();
        }
        Optional<Game> sameTitleWithoutSteamId = gameRepository.findAllByTitleKeyOrderByCreatedDateAsc(titleKey).stream()
                .filter(game -> game.getSteamAppId() == null)
                .findFirst();
        if (sameTitleWithoutSteamId.isPresent()) {
            Game adopted = sameTitleWithoutSteamId.get();
            adopted.assignSteamAppId(steamAppId);
            adopted.updateCatalogInfo(title, titleSource);
            log.info("Steam app {} joined the existing game '{}' ({}) with the same title", steamAppId, adopted.getTitle(),
                    adopted.getId());

            return adopted;
        }
        gameRepository.insertSteamGameIfAbsent(UUID.randomUUID(), steamAppId, title, titleKey, titleSource.name());

        return gameRepository.findBySteamAppId(steamAppId)
                .orElseThrow(() -> new IllegalStateException("Could not create or adopt Steam game " + steamAppId));
    }

    /** A game known only by its title (a scanned ROM, or a console game added by hand): the oldest game with that title key. */
    @Transactional
    public Game resolveOrCreateByTitle(String title, TitleSource titleSource) {
        String titleKey = TitleKeys.keyFor(title);
        titleKeyLock.acquire(titleKey);
        List<Game> sameTitle = gameRepository.findAllByTitleKeyOrderByCreatedDateAsc(titleKey);
        if (!sameTitle.isEmpty()) {
            Game existing = sameTitle.get(0);
            existing.updateCatalogInfo(title, titleSource);

            return existing;
        }

        return gameRepository.save(Game.builder().title(title).titleSource(titleSource).build());
    }

    /**
     * A game identified by its IGDB id (a console game picked from a search): found by id, or an existing game of the same
     * title that had no IGDB id yet (it adopts the id), or a new one. A different IGDB id with the same title is a different game.
     */
    @Transactional
    public Game resolveOrCreateByIgdbId(String igdbGameId, String title, TitleSource titleSource) {
        Optional<Game> known = gameRepository.findByIgdbGameId(igdbGameId);
        if (known.isPresent()) {
            known.get().updateCatalogInfo(title, titleSource);

            return known.get();
        }
        String titleKey = TitleKeys.keyFor(title);
        titleKeyLock.acquire(titleKey);

        Optional<Game> createdMeanwhile = gameRepository.findByIgdbGameId(igdbGameId);
        if (createdMeanwhile.isPresent()) {
            return createdMeanwhile.get();
        }
        Optional<Game> sameTitleWithoutIgdbId = gameRepository.findAllByTitleKeyOrderByCreatedDateAsc(titleKey).stream()
                .filter(game -> game.getIgdbGameId() == null)
                .findFirst();
        if (sameTitleWithoutIgdbId.isPresent()) {
            Game adopted = sameTitleWithoutIgdbId.get();
            adopted.setIgdbGameId(igdbGameId);
            adopted.updateCatalogInfo(title, titleSource);

            return adopted;
        }

        return gameRepository.save(Game.builder().title(title).titleSource(titleSource).igdbGameId(igdbGameId).build());
    }
}
