package dev.jordy.jordylab.gamecatalog.rest.controller;

import dev.jordy.jordylab.gamecatalog.service.SwitchGameAlreadyPresentException;
import dev.jordy.jordylab.gamecatalog.service.SwitchGameNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link SwitchGameController}'s domain errors to the statuses in the 009 switch-api contract: invalid input
 * (missing title, unknown IGDB id, too many pasted lines) is {@code 400}, an unknown game is {@code 404}, a game
 * already on the Switch is {@code 409}. Without this all three surfaced as {@code 500} (spec 011 BUG-041).
 */
@RestControllerAdvice(assignableTypes = SwitchGameController.class)
public class SwitchGameExceptionHandler {

    @ExceptionHandler(SwitchGameNotFoundException.class)
    public ProblemDetail handleNotFound(SwitchGameNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalid(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(SwitchGameAlreadyPresentException.class)
    public ProblemDetail handleDuplicate(SwitchGameAlreadyPresentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
