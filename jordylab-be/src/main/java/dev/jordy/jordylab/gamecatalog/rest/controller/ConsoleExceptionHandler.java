package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.service.AlreadyOnConsoleException;
import dev.jordy.jordylab.gamecatalog.service.ConsoleGameLookupException;
import dev.jordy.jordylab.gamecatalog.service.ConsoleNotFoundException;
import dev.jordy.jordylab.gamecatalog.service.GameNotOnConsoleException;
import dev.jordy.jordylab.gamecatalog.service.NameTakenException;
import dev.jordy.jordylab.gamecatalog.service.NameTooLongException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns console failures into the stable error codes of the consoles contract. */
@RestControllerAdvice(assignableTypes = ConsoleController.class)
public class ConsoleExceptionHandler {

    @ExceptionHandler(NameTakenException.class)
    public ResponseEntity<ErrorBody> nameTaken() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("NAME_TAKEN"));
    }

    @ExceptionHandler(NameTooLongException.class)
    public ResponseEntity<ErrorBody> nameTooLong() {
        return ResponseEntity.badRequest().body(new ErrorBody("NAME_TOO_LONG"));
    }

    @ExceptionHandler(AlreadyOnConsoleException.class)
    public ResponseEntity<ErrorBody> alreadyOnConsole() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("ALREADY_ON_CONSOLE"));
    }

    @ExceptionHandler({ConsoleNotFoundException.class, GameNotOnConsoleException.class})
    public ResponseEntity<ErrorBody> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND"));
    }

    @ExceptionHandler(ConsoleGameLookupException.class)
    public ResponseEntity<ErrorBody> lookupFailed(ConsoleGameLookupException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("GAME_NOT_FOUND"));
    }

    public record ErrorBody(String reason) {
    }
}
