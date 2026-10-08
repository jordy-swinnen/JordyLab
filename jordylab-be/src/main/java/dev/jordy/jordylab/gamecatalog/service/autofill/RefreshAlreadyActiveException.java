package dev.jordy.jordylab.gamecatalog.service.autofill;

public class RefreshAlreadyActiveException extends RuntimeException {

    public RefreshAlreadyActiveException() {
        super("A refresh of this kind is already running");
    }
}
