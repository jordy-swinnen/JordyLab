package dev.jordy.jordylab.identity.domain;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
class PasswordResetRequestTestBuilder {

    public static final UUID DEFAULT_ID = UUID.fromString("9c8b7a65-4d3e-4f2a-8b1c-0d9e8f7a6b5c");
    public static final UUID DEFAULT_USER_ACCOUNT_ID =
            UUID.fromString("e6b2c9f1-3a4d-4f5e-8a9b-1c2d3e4f5a6b");
    public static final UUID DEFAULT_DECIDED_BY = UUID.fromString("7f6e5d4c-3b2a-4190-8f7e-6d5c4b3a2f1e");
    public static final String DEFAULT_REQUESTED_HASH = "{bcrypt}$2a$10$bcdefghijklmnopqrstuvw";

    public static PasswordResetRequest aDefaultPasswordResetRequest() {
        return aPasswordResetRequest().build();
    }

    public static PasswordResetRequest.PasswordResetRequestBuilder aPasswordResetRequest() {
        return PasswordResetRequest.builder()
                .id(DEFAULT_ID)
                .userAccountId(DEFAULT_USER_ACCOUNT_ID)
                .requestedPasswordHash(DEFAULT_REQUESTED_HASH);
    }
}
