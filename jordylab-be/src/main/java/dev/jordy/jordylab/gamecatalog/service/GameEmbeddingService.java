package dev.jordy.jordylab.gamecatalog.service;

import dev.jordy.jordylab.gamecatalog.domain.Game;
import dev.jordy.jordylab.gamecatalog.domain.GameEmbedding;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameEmbeddingRepository;
import dev.jordy.jordylab.gamecatalog.domain.repository.GameRepository;
import dev.jordy.jordylab.shared.ai.AiEmbeddingResult;
import dev.jordy.jordylab.shared.ai.ResilientAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the semantic index current (spec 013 research A4): one embedded text per game, built from the facts a person
 * would search by, written to {@code game_embedding}. Unchanged games are skipped by content hash; a changed embedding
 * model re-embeds everything. The model call happens outside any transaction; each row is written in its own short one.
 * A failed call leaves the row stale and is retried by the next sweep, and never logs the text.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameEmbeddingService {

    /** Embedding inputs per gateway call. */
    static final int BATCH_SIZE = 32;

    private static final int MAX_DESCRIPTION_CHARACTERS = 1200;

    private final GameRepository gameRepository;
    private final GameEmbeddingRepository gameEmbeddingRepository;
    private final GamePlatformService gamePlatformService;
    private final ResilientAiService aiService;
    private final TransactionOperations transactions;
    private final Clock clock;

    /** The embedding model vectors are made with; a stored vector from another model is stale. */
    public String embeddingModel() {
        return aiService.embeddingModelName();
    }

    /** Result of one pass: how many rows were written, were already current, or could not be written. */
    public record Outcome(int embedded, int unchanged, int failed) {
    }

    /** Embeds the given games that are missing, stale or made with another model. */
    public Outcome embed(List<UUID> gameIds) {
        String model = aiService.embeddingModelName();
        List<Game> games = gameRepository.findAllById(gameIds);
        Map<UUID, List<String>> platforms = gamePlatformService.platformsOf(gameIds);
        List<Pending> pending = new ArrayList<>();
        int unchanged = 0;
        for (Game game : games) {
            String document = documentFor(game, platforms.getOrDefault(game.getId(), List.of()));
            String hash = contentHash(document);
            Optional<GameEmbedding> existing = gameEmbeddingRepository.findById(game.getId());
            if (existing.isPresent() && !existing.get().isStale(model, hash)) {
                unchanged++;
            } else {
                pending.add(new Pending(game.getId(), document, hash));
            }
        }
        int embedded = 0;
        int failed = 0;
        for (int start = 0; start < pending.size(); start += BATCH_SIZE) {
            List<Pending> batch = pending.subList(start, Math.min(start + BATCH_SIZE, pending.size()));
            AiEmbeddingResult result = aiService.embed(batch.stream().map(Pending::document).toList());
            if (!result.success()) {
                log.warn("Embedding {} game(s) failed: {}", batch.size(), result.failureReason());
                failed += batch.size();
                continue;
            }
            for (int index = 0; index < batch.size(); index++) {
                store(batch.get(index), result.vectors().get(index), model);
                embedded++;
            }
        }

        return new Outcome(embedded, unchanged, failed);
    }

    /** The vector for a question's taste text, or empty when embedding is not possible right now. */
    public Optional<float[]> embedQuery(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        AiEmbeddingResult result = aiService.embed(List.of(text));

        return result.success() ? Optional.of(result.vectors().get(0)) : Optional.empty();
    }

    /** pgvector's text form of a vector, for example {@code [0.1,0.2]}. */
    public static String vectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(vector[index]);
        }

        return literal.append(']').toString();
    }

    /** The text that is embedded: facts in words, so a question like "co-op for four" lands near the right games. */
    String documentFor(Game game, List<String> platforms) {
        StringBuilder document = new StringBuilder(game.getTitle());
        if (!platforms.isEmpty()) {
            document.append(". Platforms: ").append(String.join(", ", platforms));
        }
        if (StringUtils.hasText(game.getGenres())) {
            document.append(". Genres: ").append(game.getGenres());
        } else if (StringUtils.hasText(game.getGenre())) {
            document.append(". Genre: ").append(game.getGenre());
        }
        if (StringUtils.hasText(game.getDeveloper())) {
            document.append(". Developer: ").append(game.getDeveloper());
        }
        if (game.getReleaseYear() != null) {
            document.append(". Released: ").append(game.getReleaseYear());
        }
        multiplayerSentence(game).ifPresent(sentence -> document.append(". ").append(sentence));
        if (StringUtils.hasText(game.getDescription())) {
            String description = game.getDescription();
            document.append(". ").append(description.length() > MAX_DESCRIPTION_CHARACTERS
                    ? description.substring(0, MAX_DESCRIPTION_CHARACTERS) : description);
        }

        return document.toString();
    }

    private Optional<String> multiplayerSentence(Game game) {
        List<String> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(game.getLocalMultiplayer())) {
            parts.add(game.getMaxLocalPlayers() != null
                    ? "supports up to " + game.getMaxLocalPlayers() + " players locally on one screen"
                    : "supports local multiplayer");
        }
        if (Boolean.TRUE.equals(game.getSplitScreen())) {
            parts.add("has split-screen");
        }
        if (Boolean.TRUE.equals(game.getOnlineMultiplayer())) {
            parts.add("has online multiplayer");
        }
        if (Boolean.TRUE.equals(game.getSinglePlayer())) {
            parts.add("has a single-player mode");
        }

        return parts.isEmpty() ? Optional.empty() : Optional.of("Multiplayer: " + String.join("; ", parts));
    }

    static String contentHash(String document) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }

    private void store(Pending pending, float[] vector, String model) {
        transactions.executeWithoutResult(status -> gameEmbeddingRepository.upsert(pending.gameId(), model,
                pending.hash(), vectorLiteral(vector), clock.instant()));
    }

    private record Pending(UUID gameId, String document, String hash) {
    }
}
