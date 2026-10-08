package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.ConsoleGameEntry;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.PlatformEntry;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleGameEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleImpactResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.KnownConsoleResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.PlatformChip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Consoles an admin registered (spec 013 US6, FR-032 to FR-035): added from the well-known list or by any custom name,
 * renamed, listed with their game counts, and removed with the games that live nowhere else. A console's name is unique
 * across hosts and consoles, ignoring case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsoleService {

    private final ConsoleRepository consoleRepository;
    private final ConsoleGameEntryRepository entryRepository;
    private final PlaceNameService placeNameService;
    private final PlaceRemovalService placeRemovalService;

    /** Consoles from the fifth generation onward whose name or an alias contains {@code query}, oldest generation first. */
    public List<KnownConsoleResponse> known(String query) {
        String needle = StringUtils.hasText(query) ? query.trim().toLowerCase(Locale.ROOT) : "";

        return PlatformCatalog.knownConsoles().stream()
                .filter(entry -> needle.isEmpty() || matches(entry, needle))
                .map(entry -> new KnownConsoleResponse(entry.name(), entry.family(), entry.generation(), entry.handheld()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConsoleResponse> list() {
        return consoleRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ConsoleResponse add(String platform, String name) {
        String trimmedPlatform = platform == null ? "" : platform.trim();
        String wanted = StringUtils.hasText(name) ? name.trim() : PlatformCatalog.canonical(trimmedPlatform);
        String available = placeNameService.requireAvailableConsoleName(wanted, null);
        Console console = consoleRepository.save(Console.builder().platform(trimmedPlatform).name(available).build());
        log.info("Console '{}' ({}) added", console.getName(), console.getPlatform());

        return toResponse(console);
    }

    @Transactional
    public ConsoleResponse rename(UUID id, String newName) {
        Console console = consoleRepository.findById(id).orElseThrow(ConsoleNotFoundException::new);
        console.rename(placeNameService.requireAvailableConsoleName(newName, id));

        return toResponse(consoleRepository.save(console));
    }

    @Transactional(readOnly = true)
    public ConsoleImpactResponse impact(UUID id) {
        consoleRepository.findById(id).orElseThrow(ConsoleNotFoundException::new);
        List<UUID> gameIds = entryRepository.findAllByConsoleId(id).stream().map(entry -> entry.getGame().getId()).toList();
        long elsewhere = gameIds.stream().filter(gameId -> placeRemovalService.isHeldOutsideConsole(gameId, id)).count();

        return new ConsoleImpactResponse(gameIds.size(), elsewhere, gameIds.size() - elsewhere);
    }

    @Transactional
    public void remove(UUID id) {
        Console console = consoleRepository.findById(id).orElseThrow(ConsoleNotFoundException::new);
        List<UUID> gameIds = entryRepository.findAllByConsoleId(id).stream().map(ConsoleGameEntry::getGame)
                .map(game -> game.getId()).toList();
        entryRepository.deleteAllByConsoleId(id);
        entryRepository.flush();
        gameIds.forEach(placeRemovalService::releaseGameIfOrphaned);
        consoleRepository.delete(console);
        log.info("Console '{}' removed with {} game entr(ies)", console.getName(), gameIds.size());
    }

    private boolean matches(PlatformEntry entry, String needle) {
        return entry.name().toLowerCase(Locale.ROOT).contains(needle)
                || entry.aliases().stream().anyMatch(alias -> alias.toLowerCase(Locale.ROOT).contains(needle));
    }

    private ConsoleResponse toResponse(Console console) {
        PlatformEntry entry = PlatformCatalog.entryFor(console.getPlatform());

        return new ConsoleResponse(console.getId(), console.getPlatform(), entry.family(),
                PlatformChip.of(console.getPlatform()), console.getName(), console.label(),
                entryRepository.countByConsoleId(console.getId()));
    }
}
