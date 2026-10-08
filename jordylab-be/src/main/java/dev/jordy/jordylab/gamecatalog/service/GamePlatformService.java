package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A game has no platform of its own any more; its platforms are the platforms of its places (spec 013 FR-023): the
 * platform of each installed copy, "Steam" for an active library entry, and the platform of each console it is on.
 * Hidden places (an uninstalled copy, a disabled source, a removed library entry) do not contribute.
 */
@Service
@RequiredArgsConstructor
public class GamePlatformService {

    private final GameInstallationRepository gameInstallationRepository;
    private final GameLibraryEntryRepository gameLibraryEntryRepository;
    private final ConsoleGameEntryRepository consoleGameEntryRepository;

    @Transactional(readOnly = true)
    public List<String> platformsOf(UUID gameId) {
        return platformsOf(List.of(gameId)).getOrDefault(gameId, List.of());
    }

    /** Platforms per game id, in a stable order (alphabetical), for several games with three queries in total. */
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> platformsOf(Collection<UUID> gameIds) {
        if (gameIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<String>> byGame = new HashMap<>();
        for (GameInstallation installation : gameInstallationRepository.findAllByGameIdIn(gameIds)) {
            if (installation.isInstalled() && installation.getSource().isEnabled()) {
                add(byGame, installation.getGame().getId(), installation.getPlatform());
            }
        }
        for (GameLibraryEntry entry : gameLibraryEntryRepository.findAllByGameIdIn(gameIds)) {
            if (entry.isActive()) {
                add(byGame, entry.getGame().getId(), PlatformCatalog.STEAM);
            }
        }
        consoleGameEntryRepository.findAllByGameIdIn(gameIds)
                .forEach(entry -> add(byGame, entry.getGame().getId(), entry.getConsole().getPlatform()));
        Map<UUID, List<String>> sorted = new HashMap<>();
        byGame.forEach((gameId, platforms) -> sorted.put(gameId, platforms.stream().sorted().toList()));

        return sorted;
    }

    private void add(Map<UUID, Set<String>> byGame, UUID gameId, String platform) {
        byGame.computeIfAbsent(gameId, key -> new LinkedHashSet<>()).add(PlatformCatalog.canonical(platform));
    }
}
