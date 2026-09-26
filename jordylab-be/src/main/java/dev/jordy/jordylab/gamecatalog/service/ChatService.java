package dev.jordy.jordylab.gamecatalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.GameCatalogProperties;
import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatGameRef;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ChatResponse;
import dev.jordy.jordylab.shared.ai.AiCallResult;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String MODULE_NAME = "gamecatalog";
    private static final int MAX_FILTER_TEXT_LENGTH = 100;
    private static final int MAX_LOCAL_PLAYERS_UPPER_BOUND = 64;
    private static final int MAX_FILTER_PLATFORMS = 10;
    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of("titleSearch", "genre", "minLocalPlayers",
            "onlineMultiplayer", "singlePlayer", "platforms");

    static final String TRANSLATION_SYSTEM_PROMPT = """
            You translate questions about a personal video game catalog into a strict JSON filter.
            Respond with ONLY a JSON object — no markdown, no prose — using at most these fields:
            {
              "titleSearch": "case-insensitive substring of the game title, or null",
              "genre": "exact genre name, or null",
              "minLocalPlayers": "integer 1-64, minimum simultaneous local players, or null",
              "onlineMultiplayer": "true/false, or null",
              "singlePlayer": "true/false, or null",
              "platforms": "array of platform names from the provided platform list, or null"
            }
            Every field you do not need must be null. Never invent fields or platform names.
            """;

    static final String COMPOSITION_SYSTEM_PROMPT = """
            You answer questions about a personal video game catalog.
            Use ONLY the catalog rows provided by the user — never invent games, never use general knowledge.
            Answer concisely in one short paragraph and name the matching games.
            """;

    private static final String NO_MATCH_ANSWER =
            "No games in your catalog match that question.";

    private final GameRepository gameRepository;
    private final ResilientAiService aiService;
    private final ObjectMapper objectMapper;
    private final GameCatalogProperties properties;

    public ChatResponse ask(String question) {
        List<String> visiblePlatforms = gameRepository.findVisiblePlatforms();
        ChatFilter filter = translate(question, visiblePlatforms);

        List<Game> rows = gameRepository.findForChatFilter(filter.titleSearch(), filter.genre(),
                filter.minLocalPlayers(), filter.onlineMultiplayer(), filter.singlePlayer(), filter.platforms(),
                PageRequest.of(0, properties.chat().maxResultGames()));

        if (rows.isEmpty()) {
            return new ChatResponse(NO_MATCH_ANSWER, List.of(), true);
        }

        String answer = compose(question, rows);

        return new ChatResponse(answer, rows.stream().map(this::toRef).toList(), false);
    }

    private ChatFilter translate(String question, List<String> visiblePlatforms) {
        String userPrompt = "Question: " + question + "\n\nVisible platforms in the catalog: "
                + String.join(", ", visiblePlatforms);
        AiCallResult result = aiService.call(MODULE_NAME, TRANSLATION_SYSTEM_PROMPT, userPrompt);
        if (!result.success()) {
            log.warn("Chat translation AI call failed: {}", result.failureReason());
            throw new ChatUnavailableException("chat translation failed: " + result.failureReason());
        }

        return parseFilter(result.content(), visiblePlatforms)
                .orElseThrow(() -> new ChatUnavailableException("chat translation output invalid"));
    }

    private String compose(String question, List<Game> rows) {
        AiCallResult result = aiService.call(MODULE_NAME, COMPOSITION_SYSTEM_PROMPT,
                buildCompositionPrompt(question, rows));
        if (!result.success()) {
            log.warn("Chat composition AI call failed: {}", result.failureReason());
            throw new ChatUnavailableException("chat composition failed: " + result.failureReason());
        }

        return result.content();
    }

    private String buildCompositionPrompt(String question, List<Game> rows) {
        StringBuilder prompt = new StringBuilder("Question: ").append(question).append("\n\nCatalog rows:\n");
        for (Game row : rows) {
            prompt.append("- ").append(row.getTitle())
                    .append(" (").append(row.getPlatform()).append(")")
                    .append(" | genre: ").append(row.getGenre())
                    .append(" | maxLocalPlayers: ").append(row.getMaxLocalPlayers())
                    .append(" | onlineMultiplayer: ").append(row.getOnlineMultiplayer())
                    .append(" | singlePlayer: ").append(row.getSinglePlayer())
                    .append('\n');
        }

        return prompt.toString();
    }

    private Optional<ChatFilter> parseFilter(String content, List<String> visiblePlatforms) {
        try {
            JsonNode node = objectMapper.readTree(extractJson(content));
            if (!node.isObject() || hasUnknownFields(node)) {
                return Optional.empty();
            }

            String titleSearch = optionalText(node, "titleSearch");
            String genre = optionalText(node, "genre");
            Integer minLocalPlayers = optionalBoundedInt(node, "minLocalPlayers");
            Boolean onlineMultiplayer = optionalBoolean(node, "onlineMultiplayer");
            Boolean singlePlayer = optionalBoolean(node, "singlePlayer");
            List<String> platforms = optionalPlatforms(node, visiblePlatforms);

            return Optional.of(new ChatFilter(titleSearch, genre, minLocalPlayers, onlineMultiplayer,
                    singlePlayer, platforms));
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

    private Integer optionalBoundedInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > MAX_LOCAL_PLAYERS_UPPER_BOUND) {
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

    private List<String> optionalPlatforms(JsonNode node, List<String> visiblePlatforms) {
        JsonNode value = node.get("platforms");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray() || value.size() > MAX_FILTER_PLATFORMS) {
            throw new IllegalArgumentException("platforms must be a bounded array");
        }

        List<String> platforms = new ArrayList<>();
        for (JsonNode entry : value) {
            if (!entry.isTextual() || !visiblePlatforms.contains(entry.asText())) {
                throw new IllegalArgumentException("unknown platform in filter");
            }
            platforms.add(entry.asText());
        }

        return platforms;
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }

        return trimmed;
    }

    private ChatGameRef toRef(Game game) {
        return new ChatGameRef(game.getId(), game.getTitle(), game.getPlatform());
    }

    private record ChatFilter(String titleSearch, String genre, Integer minLocalPlayers, Boolean onlineMultiplayer,
            Boolean singlePlayer, List<String> platforms) {
    }
}
