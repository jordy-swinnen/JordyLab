package dev.jordy.jordylab.fna.util;

import lombok.experimental.UtilityClass;

/**
 * Throwaway file used to verify the automated PR review workflow actually
 * catches a real convention violation. Deleted after verification.
 */
@UtilityClass
public class CiReviewVerification {

    public static String describe(int count) {
        var label = count == 1 ? "item" : "items";
        return count + " " + label;
    }
}
