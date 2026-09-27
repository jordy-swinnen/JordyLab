package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameLibraryEntry;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySource;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.LibrarySyncRun;
import dev.jordy.jordylab.gamecatalog.domain.TitleSource;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameLibraryEntryRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.LibrarySyncRunRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamFamilyClient;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamFamilyException;
import dev.jordy.jordylab.gamecatalog.rest.client.SteamOwnedGamesClient;
import dev.jordy.jordylab.gamecatalog.util.ToolExclusion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owned and family Steam library sync. A game reported by the library is resolved through the
 * same {@link ReconciliationService} path as a scan, so an existing game is adopted and only a
 * library link is added — never a metadata lookup, artwork resolution or AI enrichment
 * (FR-002, FR-003, SC-001). An unchanged sync short-circuits as {@code NO_CHANGE} (FR-004,
 * SC-002); an empty or suspicious response changes nothing (FR-016, SC-008).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteamLibrarySyncService {

    private static final String STEAM_SYNC_FAILED = "STEAM_SYNC_FAILED";

    private final SteamOwnedGamesClient ownedGamesClient;
    private final SteamFamilyClient familyClient;
    private final ReconciliationService reconciliationService;
    private final GameRepository gameRepository;
    private final GameLibraryEntryRepository libraryEntryRepository;
    private final LibrarySyncRunRepository librarySyncRunRepository;
    private final ArtworkService artworkService;
    private final SteamMetadataService steamMetadataService;
    private final EnrichmentService enrichmentService;
    private final MultiplayerService multiplayerService;
    private final GameCatalogProperties properties;

    /**
     * Owned-library sync, triggerable manually. Requires the Steam account to be configured;
     * an unconfigured request is a configuration state, not a failure — it records nothing and
     * throws {@link SteamNotConfiguredException} (FR-008).
     */
    @Transactional
    public LibrarySyncRun syncOwned(boolean force) {
        if (!ownedGamesClient.isConfigured()) {
            throw new SteamNotConfiguredException(
                    "Steam account is not configured (STEAM_WEB_API_KEY / STEAM_ID)");
        }

        return syncOwned(force, properties.library().storeMinIntervalMs());
    }

    /** Family library sync from a short-lived access token; the token is used for this call only. */
    @Transactional
    public LibrarySyncRun syncFamily(String accessToken, boolean force) {
        Instant startedAt = Instant.now();
        List<SteamFamilyClient.FamilyGame> familyGames;
        try {
            familyGames = familyClient.fetchSharedLibrary(accessToken);
        } catch (SteamFamilyException exception) {
            log.warn("Family library sync failed: {}", exception.getErrorCode());

            return recordOutcome(LibrarySource.FAMILY, startedAt, LibrarySyncOutcome.FAILED, null,
                    exception.getErrorCode());
        }
        List<LibraryGame> games = familyGames.stream()
                .filter(SteamFamilyClient.FamilyGame::isShareable)
                .map(game -> new LibraryGame(game.appId(), game.name(), joinOwners(game.ownerIds())))
                .toList();

        return applyLibrary(LibrarySource.FAMILY, games, force, startedAt,
                properties.library().storeMinIntervalMs());
    }

    /**
     * Runs the owned sync only when the last successful run is older than the configured interval.
     * Invoked inline after an applied Steam scan — no scheduler (FR-009). Pacing is disabled here
     * because the scan request already has a bounded read-timeout budget.
     */
    @Transactional
    public void syncOwnedIfDue() {
        if (!ownedGamesClient.isConfigured()) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.library().minIntervalMinutes(), ChronoUnit.MINUTES);
        Instant lastSuccess = librarySyncRunRepository
                .findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(LibrarySource.OWNED,
                        LibrarySyncOutcome.APPLIED)
                .map(LibrarySyncRun::getFinishedAt)
                .orElse(null);
        if (lastSuccess != null && lastSuccess.isAfter(cutoff)) {
            return;
        }
        log.info("Owned Steam library sync due; running after applied scan");
        syncOwned(false, 0L);
    }

    private LibrarySyncRun syncOwned(boolean force, long storeMinIntervalMs) {
        Instant startedAt = Instant.now();
        Optional<List<SteamOwnedGamesClient.OwnedGame>> fetched = ownedGamesClient.fetchOwnedGames();
        if (fetched.isEmpty()) {
            return recordOutcome(LibrarySource.OWNED, startedAt, LibrarySyncOutcome.FAILED, null, STEAM_SYNC_FAILED);
        }
        List<LibraryGame> games = fetched.get().stream()
                .map(game -> new LibraryGame(game.appId(), game.name(), null))
                .toList();

        return applyLibrary(LibrarySource.OWNED, games, force, startedAt, storeMinIntervalMs);
    }

    private LibrarySyncRun applyLibrary(LibrarySource source, List<LibraryGame> reported, boolean force,
            Instant startedAt, long storeMinIntervalMs) {
        Map<String, LibraryGame> byAppId = deduplicate(reported);
        String contentHash = contentHash(byAppId.values());

        Optional<LibrarySyncRun> lastApplied = librarySyncRunRepository
                .findFirstByLibrarySourceAndOutcomeOrderByFinishedAtDesc(source, LibrarySyncOutcome.APPLIED);
        if (lastApplied.map(LibrarySyncRun::getContentHash).filter(contentHash::equals).isPresent()) {
            log.info("{} library sync content unchanged; recording NO_CHANGE", source);

            return recordOutcome(source, startedAt, LibrarySyncOutcome.NO_CHANGE, contentHash, null);
        }

        if (byAppId.isEmpty()) {
            log.warn("{} library sync returned an empty library; not touching catalog data", source);

            return recordOutcome(source, startedAt, LibrarySyncOutcome.FAILED, contentHash, "EMPTY_RESPONSE");
        }

        long active = libraryEntryRepository.countActiveByLibrarySource(source);
        if (isSuspiciousShrink(active, byAppId.size(), force)) {
            log.warn("{} library sync resulting in {} game(s) looks like a shrink from {}; rejecting",
                    source, byAppId.size(), active);

            return recordOutcome(source, startedAt, LibrarySyncOutcome.SUSPICIOUS, contentHash,
                    "SNAPSHOT_SHRINK_SUSPECT");
        }

        int added = reconcileEntries(source, byAppId, startedAt);
        int removed = removeMissingEntries(source, byAppId, startedAt);

        int artworkResolved = artworkService.processLibraryGames(
                gameRepository.findAllBySteamAppIdIn(byAppId.keySet()));
        int metadataCalls = steamMetadataService.fetchPending(properties.metadata().batchSize(), storeMinIntervalMs);
        int aiCalls = enrichmentService.enrichPending(properties.enrichment().batchSize());
        multiplayerService.derivePending(properties.metadata().batchSize());
        reconciliationService.purgeUninstalledGames();

        LibrarySyncRun run = LibrarySyncRun.builder()
                .librarySource(source)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .outcome(LibrarySyncOutcome.APPLIED)
                .contentHash(contentHash)
                .entriesSubmitted(byAppId.size())
                .entriesAdded(added)
                .entriesRemoved(removed)
                .metadataCalls(metadataCalls)
                .aiCalls(aiCalls)
                .build();
        log.info("{} library sync applied: {} submitted, {} added, {} removed, {} artwork, {} metadata call(s), "
                        + "{} AI call(s)",
                source, byAppId.size(), added, removed, artworkResolved, metadataCalls, aiCalls);

        return librarySyncRunRepository.save(run);
    }

    private int reconcileEntries(LibrarySource source, Map<String, LibraryGame> byAppId, Instant seenAt) {
        int added = 0;
        for (LibraryGame reported : byAppId.values()) {
            Game game = reconciliationService.resolveOrCreateSteamGame(reported.appId(), reported.name(),
                    TitleSource.LIBRARY);
            Optional<GameLibraryEntry> existing = libraryEntryRepository
                    .findByGameIdAndLibrarySource(game.getId(), source);
            if (existing.isEmpty()) {
                libraryEntryRepository.save(GameLibraryEntry.builder()
                        .game(game)
                        .librarySource(source)
                        .firstSeenAt(seenAt)
                        .lastSeenAt(seenAt)
                        .familyOwnerNames(reported.ownerNames())
                        .build());
                added++;
                continue;
            }
            GameLibraryEntry entry = existing.get();
            entry.seenAgain(seenAt);
            if (StringUtils.hasText(reported.ownerNames())) {
                entry.updateFamilyOwnerNames(reported.ownerNames());
            }
        }

        return added;
    }

    private int removeMissingEntries(LibrarySource source, Map<String, LibraryGame> byAppId, Instant removedAt) {
        Map<String, GameLibraryEntry> byAppIdActive = libraryEntryRepository
                .findAllByLibrarySourceAndRemovedAtIsNull(source).stream()
                .collect(Collectors.toMap(entry -> entry.getGame().getSteamAppId(), Function.identity(),
                        (first, duplicate) -> first));
        int removed = 0;
        for (Map.Entry<String, GameLibraryEntry> entry : byAppIdActive.entrySet()) {
            if (!byAppId.containsKey(entry.getKey())) {
                entry.getValue().markRemoved(removedAt);
                removed++;
            }
        }

        return removed;
    }

    private boolean isSuspiciousShrink(long active, int resulting, boolean force) {
        if (force) {
            return false;
        }
        long removed = active - resulting;
        if (removed <= 0) {
            return false;
        }
        long allowed = Math.max(10L, (long) Math.floor(active * properties.scan().maxShrinkFraction()));

        return removed > allowed;
    }

    private Map<String, LibraryGame> deduplicate(List<LibraryGame> reported) {
        return reported.stream()
                .filter(game -> StringUtils.hasText(game.appId()))
                .filter(game -> !ToolExclusion.isToolAppId(game.appId()) && !ToolExclusion.isToolName(game.name()))
                .collect(Collectors.toMap(LibraryGame::appId, Function.identity(), (first, duplicate) -> first,
                        LinkedHashMap::new));
    }

    private String contentHash(Iterable<LibraryGame> games) {
        List<String> lines = new ArrayList<>();
        games.forEach(game -> lines.add(game.appId() + "|" + (game.name() == null ? "" : game.name())));
        lines.sort(Comparator.naturalOrder());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash library content", exception);
        }
    }

    private String joinOwners(List<String> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return null;
        }

        return String.join(", ", ownerIds);
    }

    private LibrarySyncRun recordOutcome(LibrarySource source, Instant startedAt, LibrarySyncOutcome outcome,
            String contentHash, String errorCode) {
        return librarySyncRunRepository.save(LibrarySyncRun.builder()
                .librarySource(source)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .outcome(outcome)
                .contentHash(contentHash)
                .errorCode(errorCode)
                .build());
    }

    private record LibraryGame(String appId, String name, String ownerNames) {
    }
}
