package com.library.service;

/** ISBN-10 / ISBN-13 normalization and checksum validation. */
public final class IsbnUtils {

    private IsbnUtils() {}

    /** Strips hyphens/spaces and uppercases a trailing X. Returns null for null input. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("[\\s\\-]", "").toUpperCase();
    }

    public static boolean isValid(String raw) {
        String s = normalize(raw);
        if (s == null) return false;
        if (s.length() == 10) return validIsbn10(s);
        if (s.length() == 13) return validIsbn13(s);
        return false;
    }

    private static boolean validIsbn10(String s) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = s.charAt(i);
            int v;
            if (c == 'X' && i == 9) v = 10;
            else if (c >= '0' && c <= '9') v = c - '0';
            else return false;
            sum += v * (10 - i);
        }
        return sum % 11 == 0;
    }

    private static boolean validIsbn13(String s) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
            sum += (c - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum % 10 == 0;
    }
}
