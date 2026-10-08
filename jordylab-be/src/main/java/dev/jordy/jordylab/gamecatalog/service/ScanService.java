package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.CatalogChanged;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Host;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.SyncReport;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.HostRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.SyncReportRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ClientGame;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.EntryRejection;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.EntryRejectionReason;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanCheckRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanCheckResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SyncCounts;
import dev.jordy.jordylab.gamecatalog.service.scan.LibraryParser;
import dev.jordy.jordylab.gamecatalog.util.TextSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    /**
     * Server-side ingest-logic version. Bump whenever parsing, validation, or
     * normalization changes in a way that makes previously accepted payloads
     * stale. The check endpoint forces a scan when a source's stored version is
     * older than this — the client never controls invalidation.
     */
    static final int CURRENT_INGEST_VERSION = 1;

    private final ScanSourceRepository scanSourceRepository;
    private final HostRepository hostRepository;
    private final SyncReportRepository syncReportRepository;
    private final GameInstallationRepository gameInstallationRepository;
    private final ReconciliationService reconciliationService;
    private final ArtworkService artworkService;
    private final SteamLibrarySyncService steamLibrarySyncService;
    private final ApplicationEventPublisher eventPublisher;
    private final GameCatalogProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, LibraryParser> parsers;
    private final ScanLock scanLock;

    /**
     * Entry point for {@code POST /api/gamecatalog/ingest/scan}. Resolves or
     * auto-creates the {@link ScanSource}, applies the shrink guard, parses the
     * payload (client-provided games take precedence over path inference), and
     * reconciles.
     */
    @Transactional
    public ScanResponse submitScan(ScanRequest request) {
        if (estimatedPayloadBytes(request) > properties.scan().maxPayloadBytes()) {
            log.warn("Rejecting scan from '{}': payload exceeds byte cap", request.hostname());

            return rejected(SyncOutcome.REJECTED, "PAYLOAD_TOO_LARGE");
        }

        scanLock.acquire(request.hostname(), request.libraryType());
        Instant receivedAt = Instant.now();
        ScanSource source = resolveSource(request.hostname(), request.libraryType(), request.machineId());
        clearStaleDigestOnPreCutoverScan(source, request);

        String payloadHash = sha256(request);
        if (payloadHash.equals(source.getLastPayloadHash())) {
            recordDigest(source, request.clientDigest());
            persistReport(source, request, SyncOutcome.NO_CHANGE, receivedAt, payloadHash,
                    new ReconciliationCounts(0, 0, 0), 0, 0);

            return new ScanResponse(SyncOutcome.NO_CHANGE, source.isEnabled(),
                    new SyncCounts(0, 0, 0, 0, 0), List.of(), null);
        }

        List<GamePayload> parsed = parseOrEmpty(request);
        if (parsed.size() > properties.scan().maxGamesPerSource()) {
            log.warn("Rejecting scan from '{}': {} parsed games exceeds cap {}", request.hostname(), parsed.size(),
                    properties.scan().maxGamesPerSource());

            return rejected(SyncOutcome.REJECTED, "TOO_MANY_GAMES");
        }

        List<EntryRejection> rejections = new ArrayList<>();
        List<GamePayload> valid = validate(parsed, rejections);

        if (isSuspiciousShrink(source, valid.size(), Boolean.TRUE.equals(request.force()))) {
            log.warn("Rejecting scan from '{}': resulting installed set of {} game(s) looks like a shrink",
                    request.hostname(), valid.size());

            return rejected(SyncOutcome.REJECTED, "SNAPSHOT_SHRINK_SUSPECT");
        }

        ReconciliationCounts counts = reconciliationService.applySnapshot(source, valid, receivedAt);
        artworkService.processArtworkAfterSync(source, valid);
        reconciliationService.purgeUninstalledGames();
        if (source.getSourceType() == SourceType.STEAM) {
            steamLibrarySyncService.syncOwnedIfDue();
        }
        source.recordApplied(payloadHash);
        recordDigest(source, request.clientDigest());
        persistReport(source, request, SyncOutcome.APPLIED, receivedAt, payloadHash, counts, valid.size(),
                rejections.size());
        // The upload returns now; covers, facts, descriptions and the search index fill in behind it (spec 013 US4).
        eventPublisher.publishEvent(new CatalogChanged("scan"));

        return new ScanResponse(SyncOutcome.APPLIED, source.isEnabled(),
                new SyncCounts(valid.size(), counts.added(), counts.updated(), counts.removed(),
                        rejections.size()), rejections, null);
    }

    /**
     * Entry point for {@code POST /api/gamecatalog/ingest/check}. Records
     * liveness and reports whether a scan is needed. A disabled source always
     * reports {@code scanNeeded=false} so the client uploads nothing.
     */
    @Transactional
    public ScanCheckResponse submitCheck(ScanCheckRequest request) {
        ScanSource source = resolveSource(request.hostname(), request.libraryType(), request.machineId());
        source.recordCheck(Instant.now());

        boolean scanNeeded = source.isEnabled() && isScanNeeded(source, request.clientDigest());

        return new ScanCheckResponse(scanNeeded, source.isEnabled());
    }

    private boolean isScanNeeded(ScanSource source, String clientDigest) {
        if (source.getLastClientDigest() == null) {
            return true;
        }
        if (clientDigest == null || !source.getLastClientDigest().equals(clientDigest)) {
            return true;
        }
        if (source.getIngestVersion() < CURRENT_INGEST_VERSION) {
            return true;
        }

        return source.getLastOutcome() != SyncOutcome.APPLIED && source.getLastOutcome() != SyncOutcome.NO_CHANGE;
    }

    private ScanSource resolveSource(String hostname, SourceType sourceType, String machineId) {
        if (StringUtils.hasText(machineId)) {
            Optional<ScanSource> byMachine = scanSourceRepository.findByMachineIdAndSourceType(machineId, sourceType);
            if (byMachine.isPresent()) {
                return byMachine.get();
            }
            Host host = resolveHost(hostname);
            Optional<ScanSource> byHost = scanSourceRepository.findByHostIdAndSourceType(host.getId(), sourceType);
            if (byHost.isPresent()) {
                ScanSource source = byHost.get();
                source.adoptMachine(machineId);

                return source;
            }

            return scanSourceRepository.save(newSource(host, sourceType, machineId));
        }
        Host host = resolveHost(hostname);

        return scanSourceRepository.findByHostIdAndSourceType(host.getId(), sourceType)
                .orElseGet(() -> scanSourceRepository.save(newSource(host, sourceType, null)));
    }

    /** The host for a reported hostname, created on first sight. A scan never touches the display name (FR-031). */
    private Host resolveHost(String hostname) {
        scanLock.acquireHostCreation(hostname);

        return hostRepository.findByHostnameIgnoreCase(hostname)
                .orElseGet(() -> hostRepository.save(Host.builder().hostname(hostname).build()));
    }

    private ScanSource newSource(Host host, SourceType sourceType, String machineId) {
        return ScanSource.builder()
                .host(host)
                .sourceType(sourceType)
                .machineId(machineId)
                .enabled(true)
                .build();
    }

    private void clearStaleDigestOnPreCutoverScan(ScanSource source, ScanRequest request) {
        if (request.clientDigest() == null && source.getLastClientDigest() != null) {
            log.warn("Pre-cutover scan from '{}' ({}) carries no client digest; clearing the stored digest",
                    request.hostname(), request.libraryType());
            source.clearClientDigest();
        }
    }

    private void recordDigest(ScanSource source, String clientDigest) {
        if (clientDigest != null) {
            source.recordClientDigest(clientDigest, CURRENT_INGEST_VERSION);
        }
    }

    private boolean isSuspiciousShrink(ScanSource source, int resultingCount, boolean force) {
        if (force) {
            return false;
        }
        if (resultingCount == 0) {
            return true;
        }

        long installed = gameInstallationRepository.countInstalledBySourceId(source.getId());
        long removed = installed - resultingCount;
        if (removed <= 0) {
            return false;
        }
        long allowed = Math.max(10L, (long) Math.floor(installed * properties.scan().maxShrinkFraction()));

        return removed > allowed;
    }

    private List<GamePayload> parseOrEmpty(ScanRequest request) {
        if (request.games() != null && !request.games().isEmpty()) {
            return request.games().stream()
                    .map(game -> new GamePayload(game.externalRef(), game.title(), game.platform(), null))
                    .toList();
        }

        LibraryParser parser = parsers.get(request.libraryType().name());
        if (parser == null) {
            log.warn("No parser registered for library type {}", request.libraryType());

            return List.of();
        }
        try {
            return parser.parse(request);
        } catch (RuntimeException exception) {
            log.error("Parser {} failed for hostname '{}'", request.libraryType(), request.hostname(), exception);

            return List.of();
        }
    }

    private List<GamePayload> validate(List<GamePayload> parsed, List<EntryRejection> rejections) {
        List<GamePayload> valid = new ArrayList<>();
        for (GamePayload entry : parsed) {
            EntryRejectionReason reason = validateEntry(entry);
            if (reason != null) {
                rejections.add(new EntryRejection(entry.externalRef(), reason));
                continue;
            }
            valid.add(new GamePayload(normalizeNfc(entry.externalRef()),
                    normalizeNfc(TextSanitizer.sanitizeTitle(entry.title())),
                    normalizeNfc(entry.platform()), entry.localArtworkAvailable()));
        }

        return valid;
    }

    private EntryRejectionReason validateEntry(GamePayload entry) {
        if (entry.externalRef() == null || entry.externalRef().isBlank()) {
            return EntryRejectionReason.REF_BLANK;
        }
        if (entry.externalRef().length() > 500) {
            return EntryRejectionReason.REF_TOO_LONG;
        }
        if (entry.title() == null || entry.title().isBlank()) {
            return EntryRejectionReason.TITLE_BLANK;
        }
        if (entry.title().length() > 200) {
            return EntryRejectionReason.TITLE_TOO_LONG;
        }
        if (entry.platform() == null || entry.platform().isBlank()) {
            return EntryRejectionReason.PLATFORM_BLANK;
        }
        if (entry.platform().length() > 50) {
            return EntryRejectionReason.PLATFORM_TOO_LONG;
        }

        return null;
    }

    private static String normalizeNfc(String value) {
        if (value == null) {
            return null;
        }

        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    private void persistReport(ScanSource source, ScanRequest request, SyncOutcome outcome, Instant receivedAt,
            String payloadHash, ReconciliationCounts counts, int submitted, int rejected) {
        source.recordAttempt(outcome, receivedAt);
        syncReportRepository.save(SyncReport.builder()
                .source(source)
                .receivedAt(receivedAt)
                .outcome(outcome)
                .payloadHash(payloadHash)
                .gamesSubmitted(submitted)
                .gamesAdded(counts.added())
                .gamesUpdated(counts.updated())
                .gamesRemoved(counts.removed())
                .gamesRejected(rejected)
                .build());
    }

    private ScanResponse rejected(SyncOutcome outcome, String reason) {
        return new ScanResponse(outcome, false, new SyncCounts(0, 0, 0, 0, 0), List.of(), reason);
    }

    private long estimatedPayloadBytes(ScanRequest request) {
        long bytes = 0L;
        for (ScanEntry path : request.paths()) {
            bytes += path.relpath().length() + 32L;
        }
        if (request.manifestContents() != null) {
            for (String text : request.manifestContents().values()) {
                bytes += text == null ? 0L : text.length();
            }
        }
        if (request.games() != null) {
            for (ClientGame game : request.games()) {
                bytes += textLength(game.externalRef()) + textLength(game.title()) + textLength(game.platform()) + 16L;
            }
        }

        return bytes;
    }

    private static long textLength(String value) {
        return value == null ? 0L : value.length();
    }

    private String sha256(ScanRequest request) {
        try {
            // Hash only the scan content (the listing, manifest text, and client-grouped
            // games). capturedAt changes on every run, so including it would defeat the
            // NO_CHANGE short-circuit for an unchanged library.
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("paths", request.paths());
            content.put("manifestContents", request.manifestContents());
            content.put("games", request.games());
            byte[] bytes = objectMapper.writeValueAsBytes(content);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);

            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash scan payload", exception);
        }
    }
}
