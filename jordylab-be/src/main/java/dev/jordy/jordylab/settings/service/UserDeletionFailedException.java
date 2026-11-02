package dev.jordy.jordylab.settings.service;

/**
 * A deletion stopped at a named stage (spec 014 FR-017). Everything before that stage stands, the person is disabled,
 * and the panel can tell the admin where it stopped before they retry.
 */
public class UserDeletionFailedException extends RuntimeException {

    public enum Stage {
        DISABLE, END_SESSIONS, LOCAL_CLEANUP, DELETE_ACCOUNT
    }

    private final Stage stage;

    public UserDeletionFailedException(Stage stage, Throwable cause) {
        super("Deleting the account failed at " + stage, cause);
        this.stage = stage;
    }

    public Stage stage() {
        return stage;
    }
}
