package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.AuthenticatedUser;
import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.IdentitySecurityConfiguration;
import dev.jordy.jordylab.identity.service.IdentityUserDetailsService;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import dev.jordy.jordylab.identity.service.UserCredentialStore;
import dev.jordy.jordylab.identity.util.IdentityIds;
import dev.jordy.jordylab.shared.config.IdentityTestAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.GUEST_ID;
import static dev.jordy.jordylab.shared.config.IdentityTestAuthentication.asGuest;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The account's own passkeys: list and remove, always scoped to the caller (spec 019 US2). */
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import(IdentitySecurityConfiguration.class)
@WebMvcTest(PasskeyController.class)
class PasskeyControllerTest {

    private static final AuthenticatedUser GUEST = new AuthenticatedUser(GUEST_ID, "guest", "guest", java.util.Set.of());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityUserDetailsService identityUserDetailsService;

    @MockitoBean
    private MachineTokenService machineTokenService;

    @MockitoBean
    private UserCredentialStore userCredentialStore;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @BeforeEach
    void resolveGuest() {
        when(currentUserResolver.currentUser()).thenReturn(Optional.of(GUEST));
        when(currentUserResolver.requireCurrentUserSubject()).thenReturn(GUEST_ID.toString());
    }

    @Test
    void listsTheCallersPasskeys() throws Exception {
        when(userCredentialStore.findByUserId(new Bytes(IdentityIds.toBytes(GUEST_ID))))
                .thenReturn(List.of(credential("MacBook")));

        mockMvc.perform(get("/api/identity/passkeys").with(asGuest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passkeys[0].label").value("MacBook"))
                .andExpect(jsonPath("$.passkeys[0].credentialId").isNotEmpty());
    }

    @Test
    void deletesAnOwnedPasskey() throws Exception {
        CredentialRecord record = credential("MacBook");
        when(userCredentialStore.findByCredentialId(record.getCredentialId())).thenReturn(record);

        mockMvc.perform(delete("/api/identity/passkeys/{id}", record.getCredentialId().toBase64UrlString())
                        .with(asGuest()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void refusesToDeleteAnotherAccountsPasskey() throws Exception {
        Bytes foreignId = new Bytes(new byte[]{9, 9});
        when(userCredentialStore.findByCredentialId(foreignId)).thenReturn(credentialForAnotherUser());

        mockMvc.perform(delete("/api/identity/passkeys/{id}", foreignId.toBase64UrlString())
                        .with(asGuest()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("PASSKEY_NOT_FOUND"));
    }

    @Test
    void requiresASession() throws Exception {
        mockMvc.perform(get("/api/identity/passkeys")).andExpect(status().isUnauthorized());
    }

    private static CredentialRecord credential(String label) {
        return credentialFor(label, new Bytes(IdentityIds.toBytes(GUEST_ID)));
    }

    private static CredentialRecord credentialForAnotherUser() {
        return credentialFor("Other", new Bytes(IdentityIds.toBytes(IdentityTestAuthentication.ADMIN_ID)));
    }

    private static CredentialRecord credentialFor(String label, Bytes userId) {
        Instant now = Instant.parse("2026-11-03T12:00:00Z");

        return ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(Bytes.random())
                .userEntityUserId(userId)
                .publicKey(() -> new byte[]{1})
                .signatureCount(0)
                .uvInitialized(true)
                .transports(java.util.Set.of())
                .backupEligible(true)
                .backupState(false)
                .attestationObject(new Bytes(new byte[]{2}))
                .attestationClientDataJSON(new Bytes(new byte[]{3}))
                .created(now)
                .lastUsed(now)
                .label(label)
                .build();
    }
}
