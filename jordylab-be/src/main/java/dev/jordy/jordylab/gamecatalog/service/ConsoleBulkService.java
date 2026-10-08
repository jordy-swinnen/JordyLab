package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Console;
import dev.jordy.jordylab.gamecatalog.domain.repository.ConsoleRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkConfirmRequest;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkPreviewResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleBulkSummaryResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleGameResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ConsoleSearchResult;
import dev.jordy.jordylab.gamecatalog.util.TitleKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pasting a list of titles onto a console (spec 013 US6): a review step that matches every line against IGDB on the
 * console's platform and flags what is already there, then a confirm step that adds the ticked lines one by one. One bad
 * line never stops the others, and nothing is saved by the review.
 */
@Service
@RequiredArgsConstructor
public class ConsoleBulkService {

    static final int MAX_LINES = 100;

    private final ConsoleRepository consoleRepository;
    private final ConsoleGameService consoleGameService;

    public ConsoleBulkPreviewResponse preview(UUID consoleId, List<String> rawLines) {
        Console console = consoleRepository.findById(consoleId).orElseThrow(ConsoleNotFoundException::new);
        List<String> lines = rawLines.stream().filter(StringUtils::hasText).map(String::trim).distinct()
                .limit(MAX_LINES).toList();
        List<ConsoleBulkPreviewResponse.Line> reviewed = new ArrayList<>();
        for (String line : lines) {
            reviewed.add(review(console.getId(), line));
        }

        return new ConsoleBulkPreviewResponse(reviewed);
    }

    public ConsoleBulkSummaryResponse confirm(UUID consoleId, List<ConsoleBulkConfirmRequest.Item> items) {
        consoleRepository.findById(consoleId).orElseThrow(ConsoleNotFoundException::new);
        List<ConsoleGameResponse> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<ConsoleBulkSummaryResponse.Failure> failed = new ArrayList<>();
        for (ConsoleBulkConfirmRequest.Item item : items.stream().limit(MAX_LINES).toList()) {
            String label = StringUtils.hasText(item.line()) ? item.line() : item.title();
            try {
                added.add(consoleGameService.add(consoleId, item.igdbGameId(), item.title()));
            } catch (AlreadyOnConsoleException exception) {
                skipped.add(label);
            } catch (RuntimeException exception) {
                failed.add(new ConsoleBulkSummaryResponse.Failure(label, exception.getMessage()));
            }
        }

        return new ConsoleBulkSummaryResponse(added, skipped, failed);
    }

    private ConsoleBulkPreviewResponse.Line review(UUID consoleId, String line) {
        if (consoleGameService.isPresent(consoleId, null, line)) {
            return new ConsoleBulkPreviewResponse.Line(line, ConsoleBulkPreviewResponse.Status.ALREADY_PRESENT, null);
        }
        List<ConsoleSearchResult> candidates = consoleGameService.search(consoleId, line);
        String wanted = TitleKeys.keyFor(line);
        ConsoleSearchResult exact = candidates.stream()
                .filter(candidate -> TitleKeys.keyFor(candidate.title()).equals(wanted)).findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.get(0));
        if (exact == null) {
            return new ConsoleBulkPreviewResponse.Line(line, ConsoleBulkPreviewResponse.Status.NO_MATCH, null);
        }

        return new ConsoleBulkPreviewResponse.Line(line,
                exact.alreadyOnConsole() ? ConsoleBulkPreviewResponse.Status.ALREADY_PRESENT
                        : ConsoleBulkPreviewResponse.Status.MATCHED, exact);
    }
}
