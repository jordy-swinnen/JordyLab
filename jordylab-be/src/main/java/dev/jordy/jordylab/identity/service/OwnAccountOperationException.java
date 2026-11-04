package dev.jordy.jordylab.identity.service;

/** The admin cannot run this operation on their own account (spec 019 US4, parity with spec 014). */
public class OwnAccountOperationException extends RuntimeException {
}
