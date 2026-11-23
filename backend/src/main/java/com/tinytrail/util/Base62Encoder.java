package com.tinytrail.util;

/**
 * Base62 of the DB auto-increment ID. Why not random strings: IDs are unique by construction,
 * so there is no collision check/retry loop, and codes stay as short as possible.
 * (Trade-off: codes are guessable/sequential, fine for public links.)
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {}

    public static String encode(long id) {
        if (id < 0) throw new IllegalArgumentException("id must be >= 0");
        if (id == 0) return String.valueOf(ALPHABET.charAt(0));
        StringBuilder sb = new StringBuilder();
        for (long n = id; n > 0; n /= BASE) {
            sb.append(ALPHABET.charAt((int) (n % BASE)));
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        if (code == null || code.isEmpty()) throw new IllegalArgumentException("code must not be empty");
        long result = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("invalid Base62 character: " + c);
            result = Math.addExact(Math.multiplyExact(result, (long) BASE), digit);
        }
        return result;
    }
}
