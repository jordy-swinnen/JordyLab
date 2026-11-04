package dev.jordy.jordylab.identity.rest.controller.model;

import java.util.UUID;

/** The one and only time the machine token value is returned (spec 019 US5). */
public record CreateMachineResponse(UUID id, String name, String token) {
}
