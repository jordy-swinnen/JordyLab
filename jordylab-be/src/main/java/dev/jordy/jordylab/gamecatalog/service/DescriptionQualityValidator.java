package dev.jordy.jordylab.gamecatalog.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether an AI-written description reads like a store blurb and is safe to show (spec 013 FR-060): two to four
 * sentences of plain third-person prose of about forty to a hundred words, grounded in what is known. A rejected text is never
 * stored; the caller asks again or gives up. The reason is short enough to hand back to the model as a repair hint.
 */
@Component
public class DescriptionQualityValidator {

    static final int MIN_SENTENCES = 2;
    static final int MAX_SENTENCES = 4;
    static final int MIN_WORDS = 35;
    static final int MAX_WORDS = 120;
    private static final int FILLER_LIMIT = 3;

    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+(?:[\"')\\]]+)?(?=\\s|$)");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*");
    private static final Pattern FIRST_PERSON = Pattern.compile("\\b(I|I'm|I’m|I've|I’ve|my|me|myself|we|we're|our|us)\\b");
    private static final Pattern AI_TALK = Pattern.compile(
            "(?i)\\bas an ai\\b|\\blanguage model\\b|\\bi cannot\\b|\\bi can't\\b|\\bi don't have\\b|\\bi do not have\\b|\\bi'm sorry\\b");
    private static final Pattern SCORE = Pattern.compile(
            "(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:/|out of)\\s*(?:10|5|100)\\b|\\bmetacritic\\b|\\bopencritic\\b|\\bscored\\b|\\brated\\s+\\d");
    private static final Pattern STATED_RELEASE_YEAR = Pattern.compile(
            "(?i)\\b(?:released|launched|debuted|came out|first appeared|originally released|arrived)\\b\\D{0,25}\\b((?:19|20)\\d{2})\\b");
    private static final List<String> FILLER = List.of("stunning", "immersive", "epic", "breathtaking", "unforgettable",
            "masterpiece", "thrilling", "captivating", "ultimate", "unparalleled", "unmissable", "must-play", "must play",
            "groundbreaking", "legendary", "iconic");

    /** {@code accepted} with an empty {@code reason}, or rejected with a one-line reason. */
    public record Verdict(boolean accepted, String reason) {

        static Verdict ok() {
            return new Verdict(true, "");
        }

        static Verdict rejected(String reason) {
            return new Verdict(false, reason);
        }
    }

    /** {@code knownReleaseYear} may be null when the year is not known. */
    public Verdict check(String text, Integer knownReleaseYear) {
        if (!StringUtils.hasText(text)) {
            return Verdict.rejected("the description is empty");
        }
        String trimmed = text.trim();
        int sentences = countSentences(trimmed);
        if (sentences < MIN_SENTENCES) {
            return Verdict.rejected("it must be two to four sentences, this is " + sentences);
        }
        if (sentences > MAX_SENTENCES) {
            return Verdict.rejected("it must be two to four sentences, this is " + sentences);
        }
        int words = countWords(trimmed);
        if (words < MIN_WORDS || words > MAX_WORDS) {
            return Verdict.rejected("it must be about 40 to 110 words, this is " + words);
        }
        if (AI_TALK.matcher(trimmed).find()) {
            return Verdict.rejected("it talks about being an AI instead of describing the game");
        }
        if (FIRST_PERSON.matcher(trimmed).find()) {
            return Verdict.rejected("it must be written in the third person, without I, we or my");
        }
        if (SCORE.matcher(trimmed).find()) {
            return Verdict.rejected("it must not state review scores or ratings");
        }
        if (fillerCount(trimmed) >= FILLER_LIMIT) {
            return Verdict.rejected("it is a list of marketing adjectives; say what the game is and what the player does");
        }
        if (knownReleaseYear != null && contradictsYear(trimmed, knownReleaseYear)) {
            return Verdict.rejected("it states a release year other than " + knownReleaseYear);
        }

        return Verdict.ok();
    }

    private int countSentences(String text) {
        Matcher matcher = SENTENCE_END.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        boolean endsWithoutPunctuation = !SENTENCE_END.matcher(text.substring(Math.max(0, text.length() - 3))).find();

        return endsWithoutPunctuation ? count + 1 : count;
    }

    private int countWords(String text) {
        Matcher matcher = WORD.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }

        return count;
    }

    private int fillerCount(String text) {
        String lower = text.toLowerCase(Locale.ROOT);

        return (int) FILLER.stream().filter(lower::contains).count();
    }

    private boolean contradictsYear(String text, int knownYear) {
        Matcher matcher = STATED_RELEASE_YEAR.matcher(text);
        while (matcher.find()) {
            if (Integer.parseInt(matcher.group(1)) != knownYear) {
                return true;
            }
        }

        return false;
    }
}
