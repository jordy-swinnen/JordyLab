package dev.jordy.jordylab.gamecatalog;

/**
 * Published after something changed what the catalog should know about its games: an applied scan, a Steam library sync,
 * a game added to a console. The background auto-fill picks it up and fills in covers, facts, descriptions and the search
 * index (spec 013 US4), so nothing that changed the catalog has to wait on a slow lookup or a model.
 */
public record CatalogChanged(String reason) {
}
