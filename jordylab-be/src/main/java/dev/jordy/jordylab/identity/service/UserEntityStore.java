package dev.jordy.jordylab.identity.service;

import dev.jordy.jordylab.identity.domain.UserAccount;
import dev.jordy.jordylab.identity.domain.repository.UserAccountRepository;
import dev.jordy.jordylab.identity.util.IdentityIds;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;

import java.util.Locale;

/**
 * Presents our accounts as WebAuthn user entities (spec 019 D16): the handle is the account's UUID and the name is the
 * username. A separate {@code user_entities} table is unnecessary — identity already owns the user store, so the
 * framework's registration and authentication ceremonies resolve straight to {@code identity.user_account}.
 */
@Repository
@RequiredArgsConstructor
public class UserEntityStore implements PublicKeyCredentialUserEntityRepository {

    private final UserAccountRepository userAccountRepository;

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        if (id == null || id.getBytes().length != 16) {
            return null;
        }

        return userAccountRepository.findById(IdentityIds.fromBytes(id.getBytes()))
                .map(UserEntityStore::toEntity)
                .orElse(null);
    }

    @Override
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        if (username == null) {
            return null;
        }

        return userAccountRepository.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .map(UserEntityStore::toEntity)
                .orElse(null);
    }

    /** Accounts are created by registration/approval only; the relying party never creates one (spec 019 FR-003). */
    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        // Intentionally empty: identity owns the account lifecycle.
    }

    /** Deletion is the identity module's user-deletion flow (US4); the ceremony must not remove accounts. */
    @Override
    public void delete(Bytes id) {
        // Intentionally empty.
    }

    private static PublicKeyCredentialUserEntity toEntity(UserAccount account) {
        String displayName = account.getDisplayName() != null ? account.getDisplayName() : account.getUsername();

        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(new Bytes(IdentityIds.toBytes(account.getId())))
                .name(account.getUsername())
                .displayName(displayName)
                .build();
    }
}
