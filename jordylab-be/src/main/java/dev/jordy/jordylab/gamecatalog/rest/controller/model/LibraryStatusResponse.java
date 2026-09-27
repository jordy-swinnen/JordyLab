package dev.jordy.jordylab.gamecatalog.rest.controller.model;

public record LibraryStatusResponse(LibrarySourceStatus owned, LibrarySourceStatus family, boolean ownedConfigured) {
}
