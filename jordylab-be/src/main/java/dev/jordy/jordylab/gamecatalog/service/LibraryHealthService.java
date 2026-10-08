package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.repository.LibraryHealthRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.HealthExceptionsResponse;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.LibraryHealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Shows the admin what the auto-fill has not managed to fill in yet (spec 013 FR-021), so a gap is visible, not silent. */
@Service
@RequiredArgsConstructor
public class LibraryHealthService {

    static final int MAX_LISTED_EXCEPTIONS = 200;

    /** What can be missing. */
    public enum Kind {
        COVER,
        DESCRIPTION,
        INDEX
    }

    private final LibraryHealthRepository healthRepository;

    @Transactional(readOnly = true)
    public LibraryHealthResponse health() {
        return new LibraryHealthResponse(healthRepository.countVisible(), healthRepository.countWithoutCover(),
                healthRepository.countWithoutDescription(), healthRepository.countNotIndexed());
    }

    @Transactional(readOnly = true)
    public HealthExceptionsResponse exceptions(Kind kind) {
        PageRequest firstPage = PageRequest.of(0, MAX_LISTED_EXCEPTIONS);
        List<LibraryHealthRepository.GameTitle> games = switch (kind) {
            case COVER -> healthRepository.findWithoutCover(firstPage);
            case DESCRIPTION -> healthRepository.findWithoutDescription(firstPage);
            case INDEX -> healthRepository.findNotIndexed(firstPage);
        };
        long total = switch (kind) {
            case COVER -> healthRepository.countWithoutCover();
            case DESCRIPTION -> healthRepository.countWithoutDescription();
            case INDEX -> healthRepository.countNotIndexed();
        };

        return new HealthExceptionsResponse(kind.name(), total, games.stream()
                .map(game -> new HealthExceptionsResponse.HealthException(game.getId(), game.getTitle())).toList());
    }
}
