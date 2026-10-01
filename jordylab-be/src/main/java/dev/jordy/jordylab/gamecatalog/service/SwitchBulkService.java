package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkConfirmRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkConfirmResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkLine;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkPreviewResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchBulkStatus;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.SwitchSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Adds many Switch games from a pasted list (spec 009 US3): a preview that matches every line against IGDB without
 * saving anything, then a confirm that adds the ticked lines through the single-add flows. Each confirmed line runs
 * in its own transaction (through {@link SwitchGameService}'s proxy), so one failing line never undoes the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwitchBulkService {

    static final int MAX_LINES = 100;
    private static final int MAX_CANDIDATES = 5;
    private static final Pattern TRADEMARKS = Pattern.compile("[™®©]");
    private static final Pattern LIST_MARKER = Pattern.compile("^(?:[-*•]|\\d+[.)])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]");
    // Spec 009 edge case: DLC and bundles are flagged for review rather than auto-added.
    private static final Pattern DLC_OR_BUNDLE = Pattern.compile(
            "(?i)\\b(dlc|season pass|expansion pass|expansion|bundle|soundtrack|upgrade pack)\\b|\\s\\+\\s");

    private final SwitchGameService switchGameService;

    public SwitchBulkPreviewResponse preview(String text) {
        List<String> lines = distinctLines(text);
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("Paste at most " + MAX_LINES + " titles at a time (got "
                    + lines.size() + ")");
        }

        return new SwitchBulkPreviewResponse(lines.stream().map(this::review).toList());
    }

    public SwitchBulkConfirmResponse confirm(SwitchBulkConfirmRequest request) {
        List<SwitchGameResponse> added = new ArrayList<>();
        List<String> alreadyPresent = new ArrayList<>();
        List<SwitchBulkConfirmResponse.Skipped> skipped = new ArrayList<>();

        for (SwitchBulkConfirmRequest.Item item : request.items()) {
            Optional<UUID> existing = switchGameService.findSwitchGameId(titleOf(item), item.igdbGameId());
            if (existing.isPresent()) {
                alreadyPresent.add(item.line());
                continue;
            }
            try {
                SwitchGameRequest single = new SwitchGameRequest(item.igdbGameId(), titleOf(item), item.format());
                added.add(item.igdbGameId() != null
                        ? switchGameService.addFromIgdb(single)
                        : switchGameService.addManual(single));
            } catch (SwitchGameAlreadyPresentException duplicate) {
                alreadyPresent.add(item.line());
            } catch (IllegalArgumentException invalid) {
                log.info("Switch bulk add skipped '{}': {}", item.line(), invalid.getMessage());
                skipped.add(new SwitchBulkConfirmResponse.Skipped(item.line(), invalid.getMessage()));
            }
        }

        return new SwitchBulkConfirmResponse(added, alreadyPresent, skipped);
    }

    private SwitchBulkLine review(String line) {
        List<SwitchSearchResult> candidates = switchGameService.search(line).stream()
                .limit(MAX_CANDIDATES)
                .toList();
        Long bestIgdbId = candidates.isEmpty() ? null : candidates.getFirst().igdbGameId();
        Optional<UUID> existing = switchGameService.findSwitchGameId(line, bestIgdbId);
        if (existing.isPresent()) {
            return new SwitchBulkLine(line, SwitchBulkStatus.ALREADY_PRESENT, candidates, existing.get(), false);
        }
        if (DLC_OR_BUNDLE.matcher(line).find()) {
            return new SwitchBulkLine(line, SwitchBulkStatus.NEEDS_REVIEW, candidates, null, false);
        }
        if (candidates.isEmpty()) {
            return new SwitchBulkLine(line, SwitchBulkStatus.NO_MATCH, candidates, null, false);
        }
        boolean sameTitle = comparable(candidates.getFirst().title()).equals(comparable(line));

        return new SwitchBulkLine(line, sameTitle ? SwitchBulkStatus.MATCH : SwitchBulkStatus.NEEDS_REVIEW,
                candidates, null, true);
    }

    /** Cleaned, non-empty lines in paste order; case-insensitive duplicates collapse onto the first one. */
    static List<String> distinctLines(String text) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String cleaned = WHITESPACE.matcher(LIST_MARKER.matcher(TRADEMARKS.matcher(raw).replaceAll("").trim())
                    .replaceFirst("")).replaceAll(" ").trim();
            if (!cleaned.isEmpty()) {
                byKey.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
            }
        }

        return List.copyOf(byKey.values());
    }

    private static String comparable(String title) {
        return NOT_ALPHANUMERIC.matcher(TRADEMARKS.matcher(title).replaceAll("").toLowerCase(Locale.ROOT))
                .replaceAll("");
    }

    private static String titleOf(SwitchBulkConfirmRequest.Item item) {
        return item.title() == null || item.title().isBlank() ? item.line() : item.title().trim();
    }
}
