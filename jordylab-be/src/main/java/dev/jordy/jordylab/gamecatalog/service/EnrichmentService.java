package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentService {

    private static final String MODULE_NAME = "gamecatalog";
    private static final int MAX_GENRE_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_LOCAL_PLAYERS_UPPER_BOUND = 64;

    private static final String SYSTEM_PROMPT = """
            You are a video game metadata expert. The user gives you a game title and platform.
            Respond with ONLY a JSON object in exactly this shape — no markdown, no prose:
            {
              "genre": "primary genre, max 100 characters",
              "maxLocalPlayers": "integer 1-64 for max simultaneous local players, or null if none/unknown",
              "onlineMultiplayer": "true/false if the game has online multiplayer, or null if unknown",
              "singlePlayer": "true/false if the game has a single-player mode, or null if unknown",
              "description": "one short paragraph about the game, max 4000 characters"
            }
            """;

    private final GameRepository gameRepository;
    private final ResilientAiService aiService;
    private final ObjectMapper objectMapper;
    private final GameCatalogProperties properties;

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    public void enrichPendingGames() {
        List<Game> pending = gameRepository.findByEnrichmentStatusOrderByFirstSeenAtAsc(
                EnrichmentStatus.PENDING, PageRequest.of(0, properties.enrichment().batchSize()));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Enriching {} pending game(s)", pending.size());
        pending.forEach(this::enrichOne);
    }

    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    public void resetFailedEnrichments() {
        List<Game> failed = gameRepository.findByEnrichmentStatus(EnrichmentStatus.FAILED);
        if (failed.isEmpty()) {
            return;
        }
        failed.forEach(Game::resetEnrichmentForRetry);
        log.info("Reset {} FAILED enrichment(s) for retry", failed.size());
    }

    private void enrichOne(Game game) {
        AiCallResult result = aiService.call(MODULE_NAME, SYSTEM_PROMPT, buildUserPrompt(game));
        if (!result.success()) {
            log.warn("Enrichment AI call failed for '{}': {}", game.getTitle(), result.failureReason());
            game.recordEnrichmentFailure(properties.enrichment().maxAttempts());

            return;
        }

        Optional<EnrichmentFacts> facts = parseAndValidate(result.content());
        if (facts.isEmpty()) {
            log.warn("Enrichment output invalid for '{}'", game.getTitle());
            game.recordEnrichmentFailure(properties.enrichment().maxAttempts());

            return;
        }

        game.applyEnrichment(facts.get().genre(), facts.get().maxLocalPlayers(), facts.get().onlineMultiplayer(),
                facts.get().singlePlayer(), facts.get().description());
    }

    private String buildUserPrompt(Game game) {
        return "Game: " + game.getTitle() + "\nPlatform: " + game.getPlatform();
    }

    private Optional<EnrichmentFacts> parseAndValidate(String content) {
        try {
            JsonNode node = objectMapper.readTree(extractJson(content));
            String genre = requiredText(node, "genre", MAX_GENRE_LENGTH);
            String description = requiredText(node, "description", MAX_DESCRIPTION_LENGTH);
            Integer maxLocalPlayers = optionalBoundedInt(node, "maxLocalPlayers", 1, MAX_LOCAL_PLAYERS_UPPER_BOUND);
            Boolean onlineMultiplayer = optionalBoolean(node, "onlineMultiplayer");
            Boolean singlePlayer = optionalBoolean(node, "singlePlayer");
            if (genre == null || description == null) {
                return Optional.empty();
            }

            return Optional.of(new EnrichmentFacts(genre, maxLocalPlayers, onlineMultiplayer, singlePlayer,
                    description));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }

        return trimmed;
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())
                || value.asText().length() > maxLength) {
            return null;
        }

        return value.asText();
    }

    private Integer optionalBoundedInt(JsonNode node, String field, int min, int max) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || value.asInt() < min || value.asInt() > max) {
            throw new IllegalArgumentException(field + " out of bounds");
        }

        return value.asInt();
    }

    private Boolean optionalBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }

        return value.asBoolean();
    }

    private record EnrichmentFacts(String genre, Integer maxLocalPlayers, Boolean onlineMultiplayer,
            Boolean singlePlayer, String description) {
    }
}
