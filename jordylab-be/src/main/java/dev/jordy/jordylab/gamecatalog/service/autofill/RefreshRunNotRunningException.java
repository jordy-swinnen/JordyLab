package dev.jordy.jordylab.gamecatalog.service.autofill;

public class RefreshRunNotRunningException extends RuntimeException {

    public RefreshRunNotRunningException() {
        super("The refresh run has already finished");
    }
}
