package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.CatalogChanged;
import dev.jordy.jordylab.gamecatalog.domain.ArtworkStatus;
import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameListItem;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleSearchResult;
import dev.jordy.jordylab.gamecatalog.util.ArtworkUrls;
import dev.jordy.jordylab.gamecatalog.util.TitleKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Games on a console (spec 013 US6, FR-034 to FR-036): searched on IGDB for the console's own platform, picked or typed,
 * then resolved through {@link GameIdentityService} so the same game on Steam, an emulator or another console is linked,
 * never duplicated. Adding returns at once; covers, facts, the description and the search index fill in afterwards. There
 * is no physical or digital distinction anywhere.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsoleGameService {

    private final ConsoleRepository consoleRepository;
    private final ConsoleGameEntryRepository entryRepository;
    private final GameRepository gameRepository;
    private final GameIdentityService gameIdentityService;
    private final PlaceRemovalService placeRemovalService;
    private final IgdbClient igdbClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<ConsoleGameListItem> list(UUID consoleId) {
        requireConsole(consoleId);

        return entryRepository.findAllByConsoleId(consoleId).stream()
                .map(ConsoleGameEntry::getGame)
                .sorted(java.util.Comparator.comparing(game -> game.getTitle().toLowerCase(java.util.Locale.ROOT)))
                .map(game -> new ConsoleGameListItem(game.getId(), game.getTitle(), game.getReleaseYear(),
                        ArtworkUrls.externalCoverUrl(game), ArtworkUrls.localCoverEndpoint(game)))
                .toList();
    }

    /** IGDB matches on the console's platform; empty when IGDB is not configured or the platform is a custom one. */
    @Transactional(readOnly = true)
    public List<ConsoleSearchResult> search(UUID consoleId, String query) {
        Console console = requireConsole(consoleId);
        Long platformId = PlatformCatalog.entryFor(console.getPlatform()).igdbPlatformId();
        if (platformId == null || !StringUtils.hasText(query)) {
            return List.of();
        }
        Set<String> present = presentOn(consoleId);

        return igdbClient.searchGames(query.trim(), platformId).stream()
                .map(result -> toSearchResult(result, present))
                .toList();
    }

    @Transactional
    public ConsoleGameResponse add(UUID consoleId, Long igdbGameId, String title) {
        Console console = requireConsole(consoleId);
        boolean existedBefore;
        Game game;
        if (igdbGameId != null) {
            IgdbClient.IgdbGameDetails details = igdbClient.fetchGameDetails(igdbGameId)
                    .orElseThrow(() -> new ConsoleGameLookupException("IGDB does not know that game"));
            existedBefore = gameRepository.findByIgdbGameId(String.valueOf(igdbGameId)).isPresent()
                    || !gameRepository.findAllByTitleKeyOrderByCreatedDateAsc(TitleKeys.keyFor(details.title())).isEmpty();
            game = gameIdentityService.resolveOrCreateByIgdbId(String.valueOf(igdbGameId), details.title(),
                    TitleSource.MANIFEST);
            applyDetails(game, details);
        } else if (StringUtils.hasText(title)) {
            String trimmed = title.trim();
            existedBefore = !gameRepository.findAllByTitleKeyOrderByCreatedDateAsc(TitleKeys.keyFor(trimmed)).isEmpty();
            game = gameIdentityService.resolveOrCreateByTitle(trimmed, TitleSource.MANIFEST);
        } else {
            throw new ConsoleGameLookupException("Give an IGDB game or a title");
        }
        if (entryRepository.existsByGameIdAndConsoleId(game.getId(), consoleId)) {
            throw new AlreadyOnConsoleException();
        }
        entryRepository.save(ConsoleGameEntry.builder().game(game).console(console).build());
        eventPublisher.publishEvent(new CatalogChanged("console game"));

        return new ConsoleGameResponse(game.getId(), game.getTitle(), existedBefore);
    }

    /** Points a console game at the right IGDB match; when that match is already in the catalog the two games become one. */
    @Transactional
    public ConsoleGameResponse relink(UUID consoleId, UUID gameId, long igdbGameId) {
        requireConsole(consoleId);
        ConsoleGameEntry entry = entryRepository.findByGameIdAndConsoleId(gameId, consoleId)
                .orElseThrow(GameNotOnConsoleException::new);
        IgdbClient.IgdbGameDetails details = igdbClient.fetchGameDetails(igdbGameId)
                .orElseThrow(() -> new ConsoleGameLookupException("IGDB does not know that game"));
        Game current = entry.getGame();
        String igdbId = String.valueOf(igdbGameId);
        Optional<Game> existing = gameRepository.findByIgdbGameId(igdbId).filter(game -> !game.getId().equals(gameId));
        if (existing.isPresent()) {
            Game target = existing.get();
            entryRepository.delete(entry);
            entryRepository.flush();
            if (!entryRepository.existsByGameIdAndConsoleId(target.getId(), consoleId)) {
                entryRepository.save(ConsoleGameEntry.builder().game(target).console(entry.getConsole()).build());
            }
            placeRemovalService.releaseGameIfOrphaned(current.getId());
            eventPublisher.publishEvent(new CatalogChanged("console game"));

            return new ConsoleGameResponse(target.getId(), target.getTitle(), true);
        }
        current.setIgdbGameId(igdbId);
        current.updateCatalogInfo(details.title(), TitleSource.MANUAL);
        applyDetails(current, details);
        gameRepository.save(current);
        eventPublisher.publishEvent(new CatalogChanged("console game"));

        return new ConsoleGameResponse(current.getId(), current.getTitle(), false);
    }

    /** Takes the game off this console; it stays in the catalog while another place holds it. */
    @Transactional
    public void remove(UUID consoleId, UUID gameId) {
        requireConsole(consoleId);
        ConsoleGameEntry entry = entryRepository.findByGameIdAndConsoleId(gameId, consoleId)
                .orElseThrow(GameNotOnConsoleException::new);
        entryRepository.delete(entry);
        entryRepository.flush();
        placeRemovalService.releaseGameIfOrphaned(gameId);
    }

    /** True when IGDB id or title key is already on the console (for the bulk review). */
    @Transactional(readOnly = true)
    public boolean isPresent(UUID consoleId, Long igdbGameId, String title) {
        Set<String> present = presentOn(consoleId);

        return (igdbGameId != null && present.contains("igdb:" + igdbGameId))
                || (StringUtils.hasText(title) && present.contains("title:" + TitleKeys.keyFor(title)));
    }

    private Set<String> presentOn(UUID consoleId) {
        Set<String> present = new HashSet<>();
        for (ConsoleGameEntry entry : entryRepository.findAllByConsoleId(consoleId)) {
            Game game = entry.getGame();
            present.add("title:" + game.getTitleKey());
            if (game.getIgdbGameId() != null) {
                present.add("igdb:" + game.getIgdbGameId());
            }
        }

        return present;
    }

    private ConsoleSearchResult toSearchResult(IgdbClient.IgdbSearchResult result, Set<String> present) {
        boolean already = present.contains("igdb:" + result.igdbGameId())
                || present.contains("title:" + TitleKeys.keyFor(result.title()));

        return new ConsoleSearchResult(result.igdbGameId(), result.title(), result.releaseYear(), result.genres(),
                result.developer(), result.coverUrl(), result.bannerUrl(), already);
    }

    /** What IGDB tells us when a person picked the game: fill only what is still empty, so richer data is never lost. */
    private void applyDetails(Game game, IgdbClient.IgdbGameDetails details) {
        game.applyDeterministicMetadata(details.genres().isEmpty() ? null : String.join(", ", details.genres()),
                details.developer(), null, details.releaseYear());
        if (game.getCoverStatus() == ArtworkStatus.PENDING || game.getCoverStatus() == ArtworkStatus.PLACEHOLDER) {
            if (StringUtils.hasText(details.coverUrl())) {
                game.applyCoverArtwork(ArtworkStatus.EXTERNAL_URL, details.coverUrl());
            }
        }
        if (game.getBannerStatus() == ArtworkStatus.PENDING || game.getBannerStatus() == ArtworkStatus.PLACEHOLDER) {
            if (StringUtils.hasText(details.bannerUrl())) {
                game.applyBannerArtwork(ArtworkStatus.EXTERNAL_URL, details.bannerUrl());
            }
        }
        IgdbClient.MultiplayerMode mode = details.multiplayerMode();
        if (mode != null && game.getMultiplayerSource() == MultiplayerSource.UNKNOWN) {
            game.applyDeterministicMultiplayerFlags(null, mode.onlineMultiplayer());
            game.applyDeterministicMultiplayer(mode.localMultiplayer(), mode.splitScreen(), mode.maxLocalPlayers(),
                    MultiplayerSource.IGDB);
        }
        game.markMetadataFetched();
    }

    private Console requireConsole(UUID consoleId) {
        return consoleRepository.findById(consoleId).orElseThrow(ConsoleNotFoundException::new);
    }
}
