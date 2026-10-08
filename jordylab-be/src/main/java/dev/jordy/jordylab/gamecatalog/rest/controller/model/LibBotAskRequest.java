package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * One question to LibBot. {@code conversationId} is an opaque id the browser chooses; the server never uses it alone as a
 * memory key (it is combined with the signed-in user), so it cannot be used to read someone else's conversation.
 */
public record LibBotAskRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+") String conversationId,
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 5) List<UUID> attachedGameIds) {

    public LibBotAskRequest {
        message = message == null ? null : message.trim();
        attachedGameIds = attachedGameIds == null ? List.of() : List.copyOf(attachedGameIds);
    }
}
