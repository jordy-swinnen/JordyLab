package dev.jordy.jordylab.gamecatalog.service.libbot;

/** An attached game does not exist, is not visible, or more than the allowed number were attached. */
public class LibBotAttachmentException extends RuntimeException {

    public LibBotAttachmentException(String message) {
        super(message);
    }
}
