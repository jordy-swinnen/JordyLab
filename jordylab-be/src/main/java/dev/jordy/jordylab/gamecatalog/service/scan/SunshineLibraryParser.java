package dev.jordy.jordylab.gamecatalog.service.scan;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ClientGame;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Parses a Sunshine scan (spec 024 US4). The desktop app reads Sunshine's own
 * {@code apps.json} on the Sunshine host and sends the configured applications
 * as client-grouped {@code games}; {@code ScanService} normally uses those
 * directly, and this parser is the registered fallback for the same payload.
 *
 * <p>Sunshine has no on-disk library layout to infer, so without client games
 * there is nothing to parse.
 */
@Slf4j
@Component("SUNSHINE")
public class SunshineLibraryParser implements LibraryParser {

    @Override
    public SourceType supports() {
        return SourceType.SUNSHINE;
    }

    @Override
    public List<GamePayload> parse(ScanRequest request) {
        List<ClientGame> games = request.games();
        if (games == null || games.isEmpty()) {
            log.info("Sunshine scan from '{}' contained no configured games", request.hostname());

            return List.of();
        }

        return games.stream()
                .map(game -> new GamePayload(game.externalRef(), game.title(), game.platform(), null))
                .toList();
    }
}
