package dev.jordy.jordylab.feedback.service;

/**
 * The guest already used the day's submission budget (spec 017 FR-016); it resets at midnight in
 * the configured zone.
 */
public class FeedbackLimitReachedException extends RuntimeException {

    public FeedbackLimitReachedException(String message) {
        super(message);
    }
}
