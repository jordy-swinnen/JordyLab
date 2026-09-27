package dev.jordy.jordylab.gamecatalog.rest.client;

/** Raised when the undocumented family endpoint rejects the call or returns an unexpected shape. */
public class SteamFamilyException extends RuntimeException {

    private final String errorCode;

    public SteamFamilyException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
