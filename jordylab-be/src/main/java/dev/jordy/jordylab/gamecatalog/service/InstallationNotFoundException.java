package dev.jordy.jordylab.gamecatalog.service;

public class InstallationNotFoundException extends RuntimeException {

    public InstallationNotFoundException() {
        super("No such copy of this game");
    }
}
