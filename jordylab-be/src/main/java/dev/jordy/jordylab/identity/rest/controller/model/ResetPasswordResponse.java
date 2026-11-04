package dev.jordy.jordylab.identity.rest.controller.model;

/** An admin direct reset's generated password — returned once and never stored in plaintext (spec 019 US6). */
public record ResetPasswordResponse(String temporaryPassword) {
}
