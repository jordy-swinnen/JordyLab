package dev.jordy.jordylab.feedback.service;

/**
 * A submitted ticket names an app the sender may not use (spec 017 FR-005). The backend refuses
 * it even when the UI never offered it.
 */
public class FeedbackAppNotAllowedException extends RuntimeException {

    public FeedbackAppNotAllowedException(String message) {
        super(message);
    }
}
