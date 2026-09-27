package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameInstallation;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameInstallationRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatGameRef;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatResponse;
import dev.jordy.jordylab.gamecatalog.util.ArtworkUrls;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String MODULE_NAME = "gamecatalog";
    private static final int MAX_FILTER_TEXT_LENGTH = 100;
    private static final int MAX_LOCAL_PLAYERS_UPPER_BOUND = 64;
    private static final int MAX_FILTER_PLATFORMS = 10;
    private static final int MAX_FILTER_HOSTS = 10;
    private static final int MAX_FILTER_LIBRARY_SOURCES = 3;
    private static final int MAX_ATTACHED_GAMES = 5;
    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("titleSearch", "genre", "genresSearch",
            "developerSearch", "releaseYearMin", "releaseYearMax", "minLocalPlayers", "onlineMultiplayer",
            "singlePlayer", "platforms", "hosts", "installStatus", "librarySources", "localMultiplayer");
    private static final Set<String> ALLOWED_INSTALL_STATUSES = Set.of("INSTALLED", "NOT_INSTALLED");
    private static final List<String> ALLOWED_LIBRARY_SOURCES = List.of("OWNED", "FAMILY", "LOCAL");

    static final String TRANSLATION_SYSTEM_PROMPT = """
            You translate questions about a personal video game catalog into a strict JSON filter.
            Respond with ONLY a JSON object — no markdown, no prose — using at most these fields:
            {
              "titleSearch": "case-insensitive substring of the game title, or null",
              "genre": "exact genre name, or null",
              "genresSearch": "case-insensitive substring of the game's genres, or null",
              "developerSearch": "case-insensitive substring of the developer name, or null",
              "releaseYearMin": "integer minimum release year, or null",
              "releaseYearMax": "integer maximum release year, or null",
              "minLocalPlayers": "integer 1-64, minimum simultaneous local players, or null",
              "onlineMultiplayer": "true/false, or null",
              "singlePlayer": "true/false, or null",
              "platforms": "array of platform names from the provided platform list, or null",
              "hosts": "array of host names from the provided host list, or null",
              "installStatus": "INSTALLED or NOT_INSTALLED, or null for no constraint",
              "librarySources": "array of OWNED, FAMILY, LOCAL, or null for no constraint",
              "localMultiplayer": "true to require local/couch multiplayer support, or null"
            }
            Every field you do not need must be null. Never invent fields, platform names, or host names.
            """;

    static final String COMPOSITION_SYSTEM_PROMPT = """
            You answer questions about a personal video game catalog.
            Use ONLY the catalog rows provided by the user — never invent games, never use general knowledge.
            Answer concisely in one short paragraph and name the matching games.
            """;

    private static final String NO_MATCH_ANSWER =
            "No games in your catalog match that question.";

    private final GameRepository gameRepository;
    private final GameInstallationRepository gameInstallationRepository;
    private final ResilientAiService aiService;
    private final ObjectMapper objectMapper;
    private final GameCatalogProperties properties;

    public ChatResponse ask(String question, List<UUID> gameIds) {
        List<Game> attachedGames = loadAttachedGames(gameIds);
        List<String> visiblePlatforms = gameRepository.findVisiblePlatforms();
        List<String> visibleHosts = gameRepository.findVisibleHosts();
        ChatFilter filter = translate(question, visiblePlatforms, visibleHosts);

        List<Game> rows = filter.isEmpty() && !attachedGames.isEmpty()
                ? List.of()
                : gameRepository.findForChatFilter(filter.titleSearch(), filter.genre(), filter.genresSearch(),
                        filter.developerSearch(), filter.releaseYearMin(), filter.releaseYearMax(),
                        filter.minLocalPlayers(), filter.onlineMultiplayer(), filter.singlePlayer(),
                        filter.platforms(), filter.hosts(),
                        filter.installStatus() == null ? "ALL" : filter.installStatus(), filter.librarySources(),
                        filter.localMultiplayer(),
                        PageRequest.of(0, properties.chat().maxResultGames()));

        List<Game> contextRows = mergeContext(attachedGames, rows, properties.chat().maxResultGames());
        if (contextRows.isEmpty()) {
            return new ChatResponse(NO_MATCH_ANSWER, List.of(), true);
        }

        String answer = compose(question, contextRows, attachedGames);

        return new ChatResponse(answer, contextRows.stream().map(this::toRef).toList(), false);
    }

    private List<Game> loadAttachedGames(List<UUID> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return List.of();
        }
        if (gameIds.size() > MAX_ATTACHED_GAMES) {
            throw new ChatAttachmentException("at most " + MAX_ATTACHED_GAMES + " games may be attached");
        }

        List<Game> attached = new ArrayList<>();
        for (UUID gameId : gameIds) {
            Game game = gameRepository.findVisibleById(gameId)
                    .orElseThrow(() -> new ChatAttachmentException("attached game is not visible: " + gameId));
            attached.add(game);
        }

        return attached;
    }

    private List<Game> mergeContext(List<Game> attached, List<Game> rows, int maxRows) {
        Map<UUID, Game> merged = new LinkedHashMap<>();
        attached.forEach(game -> merged.put(game.getId(), game));
        rows.forEach(game -> merged.putIfAbsent(game.getId(), game));

        return merged.values().stream().limit(maxRows).toList();
    }

    private ChatFilter translate(String question, List<String> visiblePlatforms, List<String> visibleHosts) {
        String userPrompt = "Question: " + question + "\n\nVisible platforms in the catalog: "
                + String.join(", ", visiblePlatforms) + "\n\nVisible hosts in the catalog: "
                + String.join(", ", visibleHosts);
        AiCallResult result = aiService.call(MODULE_NAME, TRANSLATION_SYSTEM_PROMPT, userPrompt);
        if (!result.success()) {
            log.warn("Chat translation AI call failed: {}", result.failureReason());
            throw new ChatUnavailableException("chat translation failed: " + result.failureReason());
        }

        return parseFilter(result.content(), visiblePlatforms, visibleHosts)
                .orElseThrow(() -> new ChatUnavailableException("chat translation output invalid"));
    }

    private String compose(String question, List<Game> contextRows, List<Game> attachedGames) {
        Set<UUID> attachedIds = attachedGames.stream().map(Game::getId).collect(Collectors.toSet());
        AiCallResult result = aiService.call(MODULE_NAME, COMPOSITION_SYSTEM_PROMPT,
                buildCompositionPrompt(question, contextRows, attachedIds));
        if (!result.success()) {
            log.warn("Chat composition AI call failed: {}", result.failureReason());
            throw new ChatUnavailableException("chat composition failed: " + result.failureReason());
        }

        return result.content();
    }

    private String buildCompositionPrompt(String question, List<Game> rows, Set<UUID> attachedIds) {
        Map<UUID, String> hostnames = hostnamesByGameId(rows);
        StringBuilder prompt = new StringBuilder("Question: ").append(question).append("\n\nCatalog rows:\n");
        for (Game row : rows) {
            boolean attached = attachedIds.contains(row.getId());
            prompt.append("- ").append(row.getTitle())
                    .append(" (").append(row.getPlatform()).append(")");
            if (attached) {
                prompt.append(" [attached — the user is asking about this game]");
            }
            prompt.append(" | genre: ").append(row.getGenre())
                    .append(" | genres: ").append(row.getGenres())
                    .append(" | developer: ").append(row.getDeveloper())
                    .append(" | publisher: ").append(row.getPublisher())
                    .append(" | releaseYear: ").append(row.getReleaseYear())
                    .append(" | hosts: ").append(hostnames.getOrDefault(row.getId(), ""))
                    .append(" | maxLocalPlayers: ").append(row.getMaxLocalPlayers())
                    .append(" | onlineMultiplayer: ").append(row.getOnlineMultiplayer())
                    .append(" | singlePlayer: ").append(row.getSinglePlayer());
            if (attached && StringUtils.hasText(row.getDescription())) {
                prompt.append(" | description: ").append(row.getDescription());
            }
            prompt.append('\n');
        }

        return prompt.toString();
    }

    private Map<UUID, String> hostnamesByGameId(List<Game> rows) {
        List<UUID> gameIds = rows.stream().map(Game::getId).toList();
        if (gameIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<String>> hosts = new LinkedHashMap<>();
        for (GameInstallation installation : gameInstallationRepository.findAllByGameIdIn(gameIds)) {
            if (!installation.isInstalled() || !installation.getSource().isEnabled()) {
                continue;
            }
            hosts.computeIfAbsent(installation.getGame().getId(), id -> new ArrayList<>())
                    .add(installation.getSource().getHostname());
        }

        return hosts.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> String.join(", ", new HashSet<>(entry.getValue())), (first, second) -> first));
    }

    private Optional<ChatFilter> parseFilter(String content, List<String> visiblePlatforms,
            List<String> visibleHosts) {
        try {
            JsonNode node = objectMapper.readTree(extractJson(content));
            if (!node.isObject() || hasUnknownFields(node)) {
                return Optional.empty();
            }

            String titleSearch = optionalText(node, "titleSearch");
            String genre = optionalText(node, "genre");
            String genresSearch = optionalText(node, "genresSearch");
            String developerSearch = optionalText(node, "developerSearch");
            Integer releaseYearMin = optionalBoundedInt(node, "releaseYearMin", 1950, 2028);
            Integer releaseYearMax = optionalBoundedInt(node, "releaseYearMax", 1950, 2028);
            Integer minLocalPlayers = optionalBoundedInt(node, "minLocalPlayers", 1, MAX_LOCAL_PLAYERS_UPPER_BOUND);
            Boolean onlineMultiplayer = optionalBoolean(node, "onlineMultiplayer");
            Boolean singlePlayer = optionalBoolean(node, "singlePlayer");
            List<String> platforms = optionalMembers(node, "platforms", visiblePlatforms, MAX_FILTER_PLATFORMS);
            List<String> hosts = optionalMembers(node, "hosts", visibleHosts, MAX_FILTER_HOSTS);
            String installStatus = optionalEnumText(node, "installStatus", ALLOWED_INSTALL_STATUSES);
            List<String> librarySources = optionalMembers(node, "librarySources", ALLOWED_LIBRARY_SOURCES,
                    MAX_FILTER_LIBRARY_SOURCES);
            Boolean localMultiplayer = optionalBoolean(node, "localMultiplayer");

            return Optional.of(new ChatFilter(titleSearch, genre, genresSearch, developerSearch, releaseYearMin,
                    releaseYearMax, minLocalPlayers, onlineMultiplayer, singlePlayer, platforms, hosts, installStatus,
                    librarySources, localMultiplayer));
        } catch (Exception exception) {
            log.warn("Chat filter parse failed: {}", exception.getMessage());

            return Optional.empty();
        }
    }

    private boolean hasUnknownFields(JsonNode node) {
        for (Iterator<String> fields = node.fieldNames(); fields.hasNext();) {
            if (!ALLOWED_FILTER_FIELDS.contains(fields.next())) {
                return true;
            }
        }

        return false;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || !StringUtils.hasText(value.asText())
                || value.asText().length() > MAX_FILTER_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " must be a short text");
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

    private String optionalEnumText(JsonNode node, String field, Set<String> allowed) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || !allowed.contains(value.asText())) {
            throw new IllegalArgumentException(field + " must be one of " + allowed);
        }

        return value.asText();
    }

    private List<String> optionalMembers(JsonNode node, String field, List<String> visibleMembers, int maxSize) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray() || value.size() > maxSize) {
            throw new IllegalArgumentException(field + " must be a bounded array");
        }

        List<String> members = new ArrayList<>();
        for (JsonNode entry : value) {
            if (!entry.isTextual() || !visibleMembers.contains(entry.asText())) {
                throw new IllegalArgumentException("unknown member in " + field + " filter");
            }
            members.add(entry.asText());
        }

        return members;
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }

        return trimmed;
    }

    private ChatGameRef toRef(Game game) {
        return new ChatGameRef(game.getId(), game.getTitle(), game.getPlatform(),
                ArtworkUrls.externalCoverUrl(game), ArtworkUrls.localCoverEndpoint(game));
    }

    private record ChatFilter(String titleSearch, String genre, String genresSearch, String developerSearch,
            Integer releaseYearMin, Integer releaseYearMax, Integer minLocalPlayers, Boolean onlineMultiplayer,
            Boolean singlePlayer, List<String> platforms, List<String> hosts, String installStatus,
            List<String> librarySources, Boolean localMultiplayer) {

        boolean isEmpty() {
            return titleSearch == null && genre == null && genresSearch == null && developerSearch == null
                    && releaseYearMin == null && releaseYearMax == null && minLocalPlayers == null
                    && onlineMultiplayer == null && singlePlayer == null && platforms == null && hosts == null
                    && installStatus == null && librarySources == null && localMultiplayer == null;
        }
    }
}
