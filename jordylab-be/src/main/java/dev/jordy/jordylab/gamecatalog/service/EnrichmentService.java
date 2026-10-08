package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.AiAuthorship;
import dev.jordy.jordylab.gamecatalog.domain.EnrichmentStatus;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.MultiplayerSource;
import dev.jordy.jordylab.gamecatalog.domain.PlatformCatalog;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.client.IgdbClient;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.AiFeature;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentService {

    /** One call, and one more with the reason when the first text was not a usable store blurb. */
    private static final int MAX_WRITES_PER_ATTEMPT = 2;
    private static final int MAX_SUMMARY_LENGTH = 1500;
    private static final int MAX_GENRE_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;

    @Value("classpath:prompts/gamecatalog/enrichment.st")
    Resource systemPromptResource;

    private String systemPrompt;


    private static final int MAX_GENRES_LENGTH = 200;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_RELEASE_YEAR = 1950;
    private static final int MAX_RELEASE_YEAR = 2028;

    private final GameRepository gameRepository;
    private final ResilientAiService aiService;
    private final ObjectMapper objectMapper;
    private final GameCatalogProperties properties;
    private final GamePlatformService gamePlatformService;
    private final IgdbClient igdbClient;
    private final DescriptionQualityValidator qualityValidator;
    private final Clock clock;

    /** Plain text: the prompt has no placeholders and its JSON braces must not be read as template tokens. */
    @PostConstruct
    void init() throws IOException {
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Enriches up to {@code maxGames} PENDING games that are eligible for AI: games with an
     * installation (installed or within grace), installed first, and Steam games whose
     * deterministic metadata pass has finished. Library-only games are never selected
     * (FR-022) — they receive deterministic data only.
     */
    public int enrichPending(int maxGames) {
        List<Game> pending = gameRepository.findEnrichmentBacklog(EnrichmentStatus.PENDING,
                PageRequest.of(0, maxGames));
        if (pending.isEmpty()) {
            return 0;
        }
        log.info("Enriching {} pending game(s)", pending.size());
        pending.forEach(this::enrichOne);

        return pending.size();
    }

    /** Force-regenerates one game's AI facts and prose, clearing its failure counter first. */
    public void refresh(Game game) {
        game.resetEnrichmentForRetry();
        enrichOne(game);
    }

    /**
     * Like {@link #refresh} for a bulk run: says why the model produced nothing (a provider failure reason such as
     * {@code INSUFFICIENT_CREDITS}, or {@code INVALID_OUTPUT}), or is empty when the description was written.
     */
    public Optional<String> refreshReporting(Game game) {
        game.resetEnrichmentForRetry();
        Optional<String> failure = applyEnrichmentOf(game);
        gameRepository.save(game);

        return failure;
    }

    /**
     * Asks the model, then saves the game on its own: the model call never runs inside a transaction (spec 013 FR-017),
     * so a slow answer holds no database connection.
     */
    public void enrichOne(Game game) {
        applyEnrichmentOf(game);
        gameRepository.save(game);
    }

    /**
     * Empty when the description was written, otherwise the reason it was not. A text that is not a good store blurb is never
     * stored: the model is asked once more with the reason, and if that fails too the attempt counts as failed.
     */
    private Optional<String> applyEnrichmentOf(Game game) {
        String userPrompt = buildUserPrompt(game);
        String rejection = null;
        for (int attempt = 0; attempt < MAX_WRITES_PER_ATTEMPT; attempt++) {
            String prompt = rejection == null ? userPrompt : userPrompt + "\n\nYour previous description was rejected because "
                    + rejection + ". Write the whole answer again and fix that.";
            AiCallResult result = aiService.call(AiFeature.GAMECATALOG_ENRICHMENT, systemPrompt, prompt);
            if (!result.success()) {
                log.warn("Enrichment AI call failed for '{}': {}", game.getTitle(), result.failureReason());
                game.recordEnrichmentFailure(properties.enrichment().maxAttempts());

                return Optional.of(String.valueOf(result.failureReason()));
            }
            Optional<EnrichmentFacts> facts = parseAndValidate(result.content());
            if (facts.isEmpty()) {
                log.warn("Enrichment output invalid for '{}'", game.getTitle());
                rejection = "it was not the JSON object that was asked for";
                continue;
            }
            DescriptionQualityValidator.Verdict verdict = qualityValidator.check(facts.get().description(),
                    knownReleaseYear(game, facts.get()));
            if (!verdict.accepted()) {
                log.warn("Description for '{}' rejected: {}", game.getTitle(), verdict.reason());
                rejection = verdict.reason();
                continue;
            }
            store(game, facts.get(), result);

            return Optional.empty();
        }
        game.recordEnrichmentFailure(properties.enrichment().maxAttempts());

        return Optional.of("INVALID_OUTPUT");
    }

    private void store(Game game, EnrichmentFacts facts, AiCallResult result) {
        game.applyEnrichment(facts.genre(), facts.onlineMultiplayer(), facts.singlePlayer(), facts.description(),
                AiAuthorship.of(result.answeredModel(), result.model(), clock.instant()));
        game.applyDeterministicMetadata(facts.genres(), facts.developer(), facts.publisher(), facts.releaseYear());
        if (game.getSteamAppId() == null) {
            // ROMs have no Steam metadata pass; AI is their deterministic-metadata authority.
            game.markMetadataFetched();
        }
    }

    /** A release year the library already knows beats the one the model named. */
    private Integer knownReleaseYear(Game game, EnrichmentFacts facts) {
        return game.getReleaseYear() != null ? game.getReleaseYear() : facts.releaseYear();
    }

    /** What IGDB says about the game, as grounding for the model; empty when IGDB is not configured or knows nothing. */
    private Optional<String> igdbSummary(Game game) {
        if (!igdbClient.isConfigured()) {
            return Optional.empty();
        }
        try {
            Long platformId = gamePlatformService.platformsOf(game.getId()).stream()
                    .map(platform -> PlatformCatalog.entryFor(platform).igdbPlatformId())
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);

            return igdbClient.findGame(game.getTitle(), platformId).flatMap(found -> igdbClient.fetchFacts(found.id()))
                    .map(IgdbClient.IgdbFacts::summary).filter(StringUtils::hasText);
        } catch (RuntimeException exception) {
            log.debug("No IGDB summary for '{}': {}", game.getTitle(), exception.getMessage());

            return Optional.empty();
        }
    }

    private String buildUserPrompt(Game game) {
        StringBuilder prompt = new StringBuilder("Game: ").append(game.getTitle())
                .append("\nPlatform: ").append(String.join(", ", gamePlatformService.platformsOf(game.getId())));
        if (game.getMultiplayerSource() != null && game.getMultiplayerSource() != MultiplayerSource.UNKNOWN) {
            prompt.append("\nKnown multiplayer facts (use verbatim, do not contradict):");
            if (game.getLocalMultiplayer() != null) {
                prompt.append("\n- Local multiplayer: ").append(game.getLocalMultiplayer() ? "yes" : "no");
            }
            if (game.getSplitScreen() != null) {
                prompt.append("\n- Split-screen: ").append(game.getSplitScreen() ? "yes" : "no");
            }
            if (game.getMaxLocalPlayers() != null) {
                prompt.append("\n- Max local players: ").append(game.getMaxLocalPlayers());
            }
        }
        if (game.getReleaseYear() != null) {
            prompt.append("\nKnown release year: ").append(game.getReleaseYear());
        }
        igdbSummary(game).ifPresent(summary -> prompt.append("\nSummary for grounding (say it in your own words):\n")
                .append(summary.length() > MAX_SUMMARY_LENGTH ? summary.substring(0, MAX_SUMMARY_LENGTH) : summary));

        return prompt.toString();
    }

    private Optional<EnrichmentFacts> parseAndValidate(String content) {
        try {
            JsonNode node = objectMapper.readTree(extractJson(content));
            String genre = requiredText(node, "genre", MAX_GENRE_LENGTH);
            String description = requiredText(node, "description", MAX_DESCRIPTION_LENGTH);
            String genres = optionalText(node, "genres", MAX_GENRES_LENGTH);
            String developer = optionalText(node, "developer", MAX_NAME_LENGTH);
            String publisher = optionalText(node, "publisher", MAX_NAME_LENGTH);
            Integer releaseYear = optionalBoundedInt(node, "releaseYear", MIN_RELEASE_YEAR, MAX_RELEASE_YEAR);
            Boolean onlineMultiplayer = optionalBoolean(node, "onlineMultiplayer");
            Boolean singlePlayer = optionalBoolean(node, "singlePlayer");
            if (genre == null || description == null) {
                return Optional.empty();
            }

            return Optional.of(new EnrichmentFacts(genre, genres, developer, publisher, releaseYear,
                    onlineMultiplayer, singlePlayer, description));
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

    private String optionalText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || !StringUtils.hasText(value.asText()) || value.asText().length() > maxLength) {
            throw new IllegalArgumentException(field + " must be text within bounds");
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

    private record EnrichmentFacts(String genre, String genres, String developer, String publisher,
            Integer releaseYear, Boolean onlineMultiplayer, Boolean singlePlayer,
            String description) {
    }
}
