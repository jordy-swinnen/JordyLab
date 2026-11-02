package dev.jordy.jordylab.feedback.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An app a guest may send feedback about (spec 017). The guest-visible set comes from
 * {@code FeedbackAppAccessService} — today every guest uses the Game Catalog, so it is the only
 * entry; it widens automatically once per-app access exists.
 */
@Getter
@RequiredArgsConstructor
public enum FeedbackApp {

    GAME_CATALOG("Game Catalog", "Game Catalog");

    private final String displayName;
    private final String linearLabelName;
}
