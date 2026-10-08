package dev.jordy.jordylab.gamecatalog.service.libbot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.jordy.jordylab.gamecatalog.domain.MarkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * What the first model call extracts from a question (spec 013 research A1): the intent, the language, the question with
 * the conversation resolved, and the plain <em>facts</em> the person stated. The model never decides constraints: the Java
 * {@link ConstraintDeriver} turns these facts into them. Validated right after parsing (AI rule: never act on unchecked
 * model output); a reply is required whenever LibBot is not going to search.
 */
public record QuestionInterpretation(
        @NotNull @JsonPropertyDescription("What the person wants") Intent intent,
        @NotNull @JsonPropertyDescription("Language of the question: en, nl or other") Language language,
        @Size(max = 1000) @JsonPropertyDescription("The question rewritten so it stands alone, with 'those' and 'it' resolved")
        String standaloneQuestion,
        @Valid @JsonPropertyDescription("Facts the person stated; null for anything not stated") Facts facts,
        @Size(max = 1000) @JsonPropertyDescription("Required for NEEDS_CLARIFICATION and OUT_OF_SCOPE: the reply, in the person's language")
        String reply) {

    public QuestionInterpretation {
        facts = facts == null ? Facts.none() : facts;
    }

    public enum Intent {
        LIBRARY_QUERY,
        GAME_QUESTION,
        FOLLOW_UP,
        NEEDS_CLARIFICATION,
        OUT_OF_SCOPE
    }

    @JsonIgnore
    @AssertTrue(message = "reply is required for NEEDS_CLARIFICATION and OUT_OF_SCOPE")
    public boolean isReplyPresentWhenRequired() {
        boolean replyRequired = intent == Intent.NEEDS_CLARIFICATION || intent == Intent.OUT_OF_SCOPE;

        return !replyRequired || StringUtils.hasText(reply);
    }

    /** True when LibBot answers without searching the library. */
    @JsonIgnore
    public boolean endsWithoutSearch() {
        return intent == Intent.NEEDS_CLARIFICATION || intent == Intent.OUT_OF_SCOPE || language == Language.OTHER;
    }

    public record Facts(
            @Min(1) @Max(1000) @JsonPropertyDescription("People who will play together; null when not stated") Integer partySize,
            @JsonPropertyDescription("True when the person plays alone") Boolean playingAlone,
            @JsonPropertyDescription("True when they want to play online with others") Boolean online,
            @JsonPropertyDescription("Platform names mentioned, for example PlayStation 5") List<String> platforms,
            @JsonPropertyDescription("Host or console names mentioned") List<String> places,
            @JsonPropertyDescription("INSTALLED or NOT_INSTALLED when asked for") InstallFilter installStatus,
            @JsonPropertyDescription("Genres mentioned") List<String> genres,
            @Min(1950) @Max(2100) Integer releaseYearMin,
            @Min(1950) @Max(2100) Integer releaseYearMax,
            @JsonPropertyDescription("A community mark the question filters on") MarkType markFilter,
            @JsonPropertyDescription("MINE or ALL") MarkScope markScope,
            @JsonPropertyDescription("True for 'something like what I liked'") Boolean likeMyLiked,
            @JsonPropertyDescription("Mood or taste words to search by meaning, or null") @Size(max = 300) String semanticQuery,
            @JsonPropertyDescription("Ids of games the conversation refers to, from the earlier answers") List<UUID> referencedGameIds) {

        public Facts {
            platforms = platforms == null ? List.of() : List.copyOf(platforms);
            places = places == null ? List.of() : List.copyOf(places);
            genres = genres == null ? List.of() : List.copyOf(genres);
            referencedGameIds = referencedGameIds == null ? List.of() : List.copyOf(referencedGameIds);
            markScope = markScope == null ? MarkScope.MINE : markScope;
            likeMyLiked = likeMyLiked != null && likeMyLiked;
        }

        public static Facts none() {
            return new Facts(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
