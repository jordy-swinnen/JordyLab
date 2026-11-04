package dev.jordy.jordylab.identity.rest.controller.model;

import java.util.UUID;

public record RegisterResponse(UUID id, String username, String status) {
}
