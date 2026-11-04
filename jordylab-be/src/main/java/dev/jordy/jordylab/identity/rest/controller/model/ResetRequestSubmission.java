package dev.jordy.jordylab.identity.rest.controller.model;

/** A signed-out user's reset request: who they are and the password they want (spec 019 US6). */
public record ResetRequestSubmission(String username, String newPassword) {
}
