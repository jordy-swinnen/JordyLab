package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** A game to put on a console: picked from IGDB by id, or typed by title (exactly one). */
public record AddConsoleGameRequest(Long igdbGameId, String title) {
}
