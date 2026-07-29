package com.arshraj.vakilconnect.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Canonical form for reference-data names.
 *
 * The single definition of what "the same name" means. Without it a UNIQUE
 * constraint on a city name is decorative: "Mumbai", "mumbai " and "MUMBAI"
 * are three distinct strings and would all be accepted.
 *
 * The rule, in order:
 *   1. decompose to NFD and drop combining marks  ("Puduchérry" -> "Puducherry")
 *   2. lowercase, using Locale.ROOT              (never the platform default -
 *      Turkish locale maps 'I' to 'ı', which would silently corrupt keys on a
 *      machine with a Turkish locale)
 *   3. trim, then collapse internal whitespace    ("New  Delhi " -> "new delhi")
 *
 * Punctuation is deliberately NOT stripped. The rule stays simple enough to
 * reproduce exactly in SQL seeds and in tests; removing hyphens and periods
 * would create ambiguity ("Vasco da Gama" vs "Vasco-da-Gama") without solving
 * a problem the alias table does not already solve better.
 *
 * The values seeded by V3 were produced by this exact algorithm.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Returns null for null input, so callers can normalise optional fields directly. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return decomposed.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** Whether two names are the same once normalised. */
    public static boolean matches(String a, String b) {
        String left = normalize(a);
        return left != null && left.equals(normalize(b));
    }
}
