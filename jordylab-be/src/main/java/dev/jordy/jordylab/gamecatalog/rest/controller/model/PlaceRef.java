package dev.jordy.jordylab.gamecatalog.rest.controller.model;

/** One place a game lives: a host copy, a Steam library entry or a console. */
public record PlaceRef(String kind, String label, String platform, Boolean installed, String romStatus) {
}
