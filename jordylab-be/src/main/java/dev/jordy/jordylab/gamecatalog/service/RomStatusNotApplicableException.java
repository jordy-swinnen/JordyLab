package dev.jordy.jordylab.gamecatalog.service;

public class RomStatusNotApplicableException extends RuntimeException {

    public RomStatusNotApplicableException() {
        super("ROM status only applies to copies found by an emulation scan");
    }
}
