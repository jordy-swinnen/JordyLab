package dev.jordy.jordylab.identity.domain;

/**
 * The lifecycle of a password reset request (spec 019 US6, D15). A pending request never changes the account until an
 * admin approves it; superseded requests are deleted by the service.
 */
public enum ResetRequestStatus {

    PENDING,

    APPROVED,

    REJECTED
}
