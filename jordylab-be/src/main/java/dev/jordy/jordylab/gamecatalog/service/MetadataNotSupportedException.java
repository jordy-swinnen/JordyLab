package dev.jordy.jordylab.gamecatalog.service;

/** Thrown when deterministic metadata is requested for a game that has no deterministic source. */
public class MetadataNotSupportedException extends RuntimeException {

    public MetadataNotSupportedException(String message) {
        super(message);
    }
}
