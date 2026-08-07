package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.ScanSource;
import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.domain.SyncOutcome;
import dev.jordy.jordylab.gamecatalog.domain.SyncReport;
import dev.jordy.jordylab.gamecatalog.domain.repository.ScanSourceRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.SyncReportRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.EntryRejection;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.EntryRejectionReason;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanEntry;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SyncCounts;
import dev.jordy.jordylab.gamecatalog.service.scan.LibraryParser;
import dev.jordy.jordylab.gamecatalog.util.TextSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanSourceRepository scanSourceRepository;
    private final SyncReportRepository syncReportRepository;
    private final ReconciliationService reconciliationService;
    private final ArtworkService artworkService;
    private final GameCatalogProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, LibraryParser> parsers;

    /**
     * Entry point for {@code POST /api/gamecatalog/ingest/scan}. Resolves or
     * auto-creates the {@link ScanSource} for the script's
     * {@code (hostname, libraryType)} pair, hashes the payload, dispatches
     * to the matching {@link LibraryParser}, and reconciles the resulting
     * games into the catalog.
     *
     * <p>Idempotency: when the payload hash matches the source's
     * {@code lastPayloadHash}, the call returns {@link SyncOutcome#NO_CHANGE}
     * without touching the games table. Repeated runs of the same script
     * against an unchanged library therefore produce no DB churn.
     */
    @Transactional
    public ScanResponse submitScan(ScanRequest request) {
        if (estimatedPayloadBytes(request) > properties.scan().maxPayloadBytes()) {
            log.warn("Rejecting scan from '{}': payload exceeds byte cap", request.hostname());
            return rejected(SyncOutcome.REJECTED, "PAYLOAD_TOO_LARGE");
        }

        Instant receivedAt = Instant.now();
        ScanSource source = announceSource(request);
        String payloadHash = sha256(request);
        if (payloadHash.equals(source.getLastPayloadHash())) {
            source.recordAttempt(SyncOutcome.NO_CHANGE, receivedAt);
            persistReport(source, request, SyncOutcome.NO_CHANGE, receivedAt, payloadHash,
                    new ReconciliationCounts(0, 0, 0), 0, 0);

            return new ScanResponse(SyncOutcome.NO_CHANGE, source.isEnabled(),
                    new SyncCounts(0, 0, 0, 0, 0), List.of());
        }

        List<GamePayload> parsed = parseOrEmpty(request);
        if (parsed.size() > properties.scan().maxGamesPerSource()) {
            log.warn("Rejecting scan from '{}': {} parsed games exceeds cap {}", request.hostname(), parsed.size(),
                    properties.scan().maxGamesPerSource());
            return rejected(SyncOutcome.REJECTED, "TOO_MANY_GAMES");
        }

        List<EntryRejection> rejections = new ArrayList<>();
        List<GamePayload> valid = validate(parsed, rejections);
        ReconciliationCounts counts = reconciliationService.applySnapshot(source, valid, receivedAt);
        artworkService.processArtworkAfterSync(source, valid);
        source.recordApplied(payloadHash);
        source.recordAttempt(SyncOutcome.APPLIED, receivedAt);
        persistReport(source, request, SyncOutcome.APPLIED, receivedAt, payloadHash, counts, valid.size(),
                rejections.size());

        return new ScanResponse(SyncOutcome.APPLIED, source.isEnabled(),
                new SyncCounts(valid.size(), counts.added(), counts.updated(), counts.removed(),
                        rejections.size()), rejections);
    }

    private ScanSource announceSource(ScanRequest request) {
        return scanSourceRepository.findByHostnameAndSourceType(request.hostname(), request.libraryType())
                .orElseGet(() -> scanSourceRepository.save(ScanSource.builder()
                        .hostname(request.hostname())
                        .sourceType(request.libraryType())
                        .enabled(true)
                        .build()));
    }

    private List<GamePayload> parseOrEmpty(ScanRequest request) {
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
            valid.add(new GamePayload(entry.externalRef(), TextSanitizer.sanitizeTitle(entry.title()), entry.platform(),
                    entry.localArtworkAvailable()));
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
        return new ScanResponse(outcome, false, new SyncCounts(0, 0, 0, 0, 0), List.of());
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

        return bytes;
    }

    private String sha256(ScanRequest request) {
        try {
            // Use the JSON serialisation of the whole request so the script's
            // choice of paths/manifest contents is captured in the hash.
            byte[] bytes = objectMapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);

            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash scan payload", exception);
        }
    }
}
