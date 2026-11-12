package dev.jordy.jordylab.feedback.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The kind of feedback a guest sends (spec 017 FR-006). Each value maps one-to-one onto the
 * Linear Type label set up by spec 015; a "question" is filed as {@link #IDEA}.
 */
@Getter
@RequiredArgsConstructor
public enum FeedbackType {

    BUG_REPORT("Bug report", "Bug"),
    FEATURE_REQUEST("Feature request", "Feature"),
    IMPROVEMENT("Improvement", "Improvement"),
    IDEA("Idea", "Idea");

    private final String displayName;
    private final String backlogLabelName;
}
