package dev.jordy.jordylab.identity.util;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;

/**
 * Generates a random temporary password that satisfies the password policy (at least 12 characters and not equal to
 * the username, spec 019 D15) regardless of who it is assigned to — a 20-character random string cannot coincidentally
 * equal a username.
 */
@UtilityClass
public class TemporaryPasswordGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }

        return password.toString();
    }
}
