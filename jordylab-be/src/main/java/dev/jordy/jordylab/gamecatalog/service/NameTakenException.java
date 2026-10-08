package dev.jordy.jordylab.gamecatalog.service;

/** A host display name or console name is already used (ignoring letter case) by another host or console. */
public class NameTakenException extends RuntimeException {

    public NameTakenException(String name) {
        super("The name '" + name + "' is already used by another host or console");
    }
}
