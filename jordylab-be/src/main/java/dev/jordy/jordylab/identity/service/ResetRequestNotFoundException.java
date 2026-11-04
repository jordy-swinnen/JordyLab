package dev.jordy.jordylab.identity.service;

/** No reset request with that id belongs to the account (spec 019 US6). */
public class ResetRequestNotFoundException extends RuntimeException {
}
