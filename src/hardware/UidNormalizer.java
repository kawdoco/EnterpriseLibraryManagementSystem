package com.library.hardware;

import java.util.regex.Pattern;

/**
 * Single place that defines what a "normalized UID" is: uppercase hexadecimal, no spaces,
 * colons, dashes or other separators, 4 to 10 bytes (8 to 20 hex chars), the range of real NFC UIDs.
 * Used by the serial listener AND by the forms that assign cards/tags, so both always agree.
 */
public final class UidNormalizer {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s:\\-]");
    private static final Pattern HEX = Pattern.compile("^[0-9A-F]{8,20}$");

    private UidNormalizer() {}

    /** @return normalized UID, or null if the input is null/blank/not a valid even-length hex UID. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = SEPARATORS.matcher(raw.trim()).replaceAll("").toUpperCase();
        if (s.isEmpty() || s.length() % 2 != 0 || !HEX.matcher(s).matches()) return null;
        return s;
    }

    /** Blank form input means "no UID". Invalid non-blank input raises IllegalArgumentException. */
    public static String normalizeOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String n = normalize(raw);
        if (n == null) {
            throw new IllegalArgumentException("Invalid UID. Use 4-10 hexadecimal bytes (8-20 hex characters), e.g. 04A1B2C3.");
        }
        return n;
    }
}
