package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.UUID;

public record ChatGameRef(UUID id, String title, String platform) {
}
