package dev.jordy.jordylab.gamecatalog.service.autofill;

public class RefreshRunNotFoundException extends RuntimeException {

    public RefreshRunNotFoundException() {
        super("No such refresh run");
    }
}
