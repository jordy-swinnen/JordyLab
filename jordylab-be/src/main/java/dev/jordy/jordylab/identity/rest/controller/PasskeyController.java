package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.rest.controller.model.PasskeyView;
import dev.jordy.jordylab.identity.rest.controller.model.PasskeysResponse;
import dev.jordy.jordylab.identity.service.PasskeyNotFoundException;
import dev.jordy.jordylab.identity.service.UserCredentialStore;
import dev.jordy.jordylab.identity.util.IdentityIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The account's own passkeys (spec 019 US2): list and remove. Enrollment and sign-in go through Spring Security's
 * WebAuthn ceremony endpoints; this controller only manages what is already registered, always scoped to the caller.
 */
@RestController
@RequestMapping("/api/identity/passkeys")
@RequiredArgsConstructor
public class PasskeyController {

    private final UserCredentialStore userCredentialStore;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public PasskeysResponse list() {
        UUID userId = requireUserId();

        return new PasskeysResponse(userCredentialStore.findByUserId(userIdBytes(userId)).stream()
                .map(record -> new PasskeyView(record.getCredentialId().toBase64UrlString(), record.getLabel(),
                        record.getCreated(), record.getLastUsed()))
                .toList());
    }

    @DeleteMapping("/{credentialId}")
    public ResponseEntity<Void> delete(@PathVariable String credentialId) {
        UUID userId = requireUserId();
        CredentialRecord record = userCredentialStore.findByCredentialId(Bytes.fromBase64(credentialId));
        if (record == null || !record.getUserEntityUserId().equals(userIdBytes(userId))) {
            throw new PasskeyNotFoundException();
        }
        userCredentialStore.delete(record.getCredentialId());

        return ResponseEntity.noContent().build();
    }

    private UUID requireUserId() {
        return UUID.fromString(currentUserResolver.requireCurrentUserSubject());
    }

    private static Bytes userIdBytes(UUID userId) {
        return new Bytes(IdentityIds.toBytes(userId));
    }
}
