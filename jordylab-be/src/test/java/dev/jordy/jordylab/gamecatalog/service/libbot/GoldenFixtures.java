package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.jordy.jordylab.gamecatalog.domain.MarkType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * The golden question set (spec 013 FR-018, research A11): questions with the behaviour expected of LibBot, not its
 * wording. The replay tier runs them against recorded model outputs in CI; the live tier runs the same questions against
 * the real models on demand.
 */
final class GoldenFixtures {

    static final String DESCRIPTIONS_RESOURCE = "/libbot/golden/description-quality.json";
    static final String RESOURCE = "/libbot/golden/golden-questions.json";

    record GoldenFile(Vocabulary vocabulary, List<GoldenGame> library, List<GoldenCase> cases) {
    }

    /** A description-quality sample: a game with known facts and an output the validator must accept or reject. */
    record DescriptionSample(String title, String platform, Integer releaseYear, String recorded, boolean expectAccepted,
            String reasonContains) {
    }

    record DescriptionSamples(List<DescriptionSample> games) {
    }

    record Vocabulary(List<String> platforms, List<String> places) {
    }

    record GoldenGame(UUID id, String title, List<String> platforms, Boolean localMultiplayer, Integer maxLocalPlayers,
            Boolean singlePlayer, Boolean onlineMultiplayer, String genres, Integer releaseYear, boolean installed,
            int wantVotes, int likedVotes, int dislikedVotes, MarkType myMark, List<Candidate.RomCopy> romCopies) {
    }

    record GoldenTurn(String user, String answer, List<Cited> cited, LibBotOutcome outcome) {
    }

    record Cited(UUID id, String title) {
    }

    record GoldenCase(String name, List<String> tags, List<GoldenTurn> conversation, String message,
            List<UUID> attachedGameIds, String library, Recorded recorded, Expect expect) {

        @Override
        public String toString() {
            return name;
        }
    }

    record Recorded(JsonNode interpretation, JsonNode answer) {
    }

    record Expect(LibBotOutcome outcome, String language, List<String> applied, Integer minReferences,
            Integer maxReferences, List<String> referenceTitlesSubsetOf, Integer unknownCount,
            List<String> textContains, List<String> textMustNotContain, Integer composeCalls) {
    }

    private GoldenFixtures() {
    }

    static GoldenFile load() {
        try (InputStream stream = GoldenFixtures.class.getResourceAsStream(RESOURCE)) {
            return new ObjectMapper().readValue(stream, GoldenFile.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + RESOURCE, exception);
        }
    }

    static List<DescriptionSample> loadDescriptionSamples() {
        try (InputStream stream = GoldenFixtures.class.getResourceAsStream(DESCRIPTIONS_RESOURCE)) {
            return new ObjectMapper().readValue(stream, DescriptionSamples.class).games();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + DESCRIPTIONS_RESOURCE, exception);
        }
    }

    static ConversationTurn toTurn(GoldenTurn turn) {
        return new ConversationTurn(turn.user(), turn.answer(), turn.cited().stream()
                .map(cited -> new ConversationTurn.CitedGame(cited.id(), cited.title())).toList(), turn.outcome());
    }
}
