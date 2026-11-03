package dev.jordy.jordylab.identity.service;

/** The machine token is already revoked; a new machine/token is created instead (spec 019 US5). */
public class MachineTokenAlreadyRevokedException extends RuntimeException {
}
