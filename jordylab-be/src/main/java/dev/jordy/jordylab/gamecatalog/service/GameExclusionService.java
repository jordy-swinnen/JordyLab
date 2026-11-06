package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GameExclusionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The admin's exclusion list (JOR-97): a game is hidden from every catalog surface without deleting its installations or
 * library entries, so a re-scan does not bring it back. The list is the only place an excluded game can be shown again.
 */
@Service
@RequiredArgsConstructor
public class GameExclusionService {

    private final GameRepository gameRepository;
    private final GamePlatformService platformService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<GameExclusionResponse> list() {
        List<Game> excluded = gameRepository.findByExcludedTrueOrderByTitleAsc();
        Map<UUID, List<String>> platforms = platformService.platformsOf(
                excluded.stream().map(Game::getId).toList());

        return excluded.stream()
                .map(game -> new GameExclusionResponse(game.getId(), game.getTitle(),
                        platforms.getOrDefault(game.getId(), List.of()), game.getExcludedAt()))
                .toList();
    }

    @Transactional
    public boolean exclude(UUID gameId) {
        Optional<Game> game = gameRepository.findById(gameId);
        if (game.isEmpty()) {
            return false;
        }
        game.get().exclude(clock.instant());
        gameRepository.save(game.get());

        return true;
    }

    @Transactional
    public boolean include(UUID gameId) {
        Optional<Game> game = gameRepository.findById(gameId);
        if (game.isEmpty()) {
            return false;
        }
        game.get().include();
        gameRepository.save(game.get());

        return true;
    }
}
