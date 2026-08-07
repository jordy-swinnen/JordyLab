package dev.jordy.jordylab.gamecatalog.rest.controller.model;

import java.util.List;

public record ChatResponse(String answer, List<ChatGameRef> games, boolean noMatch) {
}
