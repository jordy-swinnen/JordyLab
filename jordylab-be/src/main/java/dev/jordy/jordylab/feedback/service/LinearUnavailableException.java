package dev.jordy.jordylab.feedback.service;

/**
 * Linear is unreachable, timed out, rejected the credential, or the workspace setup is missing a
 * team/state/label (spec 017 FR-019). The ticket keeps its state, records the failure and stays
 * retryable.
 */
public class LinearUnavailableException extends RuntimeException {

    public LinearUnavailableException(String message) {
        super(message);
    }
}
