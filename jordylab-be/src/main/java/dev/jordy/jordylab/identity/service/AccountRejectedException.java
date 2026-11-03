package dev.jordy.jordylab.identity.service;

/** A rejected account cannot sign in (spec 019 D7). */
public class AccountRejectedException extends RuntimeException {
}
