package dev.jordy.jordylab.identity.util;

import lombok.experimental.UtilityClass;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * UUID ↔ 16-byte conversions for WebAuthn's {@code Bytes} user handles (spec 019 D16). The user handle is the account's
 * UUID, so a credential can always be traced back to the identity module's account.
 */
@UtilityClass
public class IdentityIds {

    public static byte[] toBytes(UUID id) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());

        return buffer.array();
    }

    public static UUID fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
