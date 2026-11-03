package dev.jordy.jordylab.identity.service;

/** Admin accounts can never be deleted from the users panel (spec 019 US4, parity with spec 014). */
public class AdminAccountProtectedException extends RuntimeException {
}
