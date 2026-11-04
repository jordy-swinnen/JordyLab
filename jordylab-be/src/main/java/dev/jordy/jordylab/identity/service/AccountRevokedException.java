package dev.jordy.jordylab.identity.service;

/** A revoked account cannot sign in (spec 019 D7). */
public class AccountRevokedException extends RuntimeException {
}
