package com.music.echo.offline.files;

import java.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted, deliberately expensive PIN verifier with legacy hash migration. */
final class PinHash {
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private PinHash() {}

    static String create(String pin) {
        if (pin == null || pin.length() > 256) throw new IllegalArgumentException("PIN must contain at most 256 characters");
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(pin, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().withoutPadding().encodeToString(salt) + "$" + Base64.getEncoder().withoutPadding().encodeToString(hash);
    }

    static boolean verify(String pin, String stored, String legacyPrefix) {
        if (pin == null || pin.length() > 256 || stored == null || stored.isEmpty() || stored.length() > 128) return false;
        try {
            if (stored.startsWith("pbkdf2$")) {
                String[] parts = stored.split("\\$", -1);
                if (parts.length != 4) return false;
                int iterations = Integer.parseInt(parts[1]);
                if (iterations < 10_000 || iterations > 1_000_000) return false;
                if (parts[2].length() > 24 || parts[3].length() > 48) return false;
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                if (salt.length != SALT_BYTES || expected.length != HASH_BITS / 8) return false;
                return MessageDigest.isEqual(expected, derive(pin, salt, iterations));
            }
            // One-time compatibility with the old fixed-prefix SHA-256 verifier.
            if (stored.length() != 64) return false;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] actual = md.digest((legacyPrefix).concat(pin).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : actual) hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return MessageDigest.isEqual(hex.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), stored.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { return false; }
    }

    private static byte[] derive(String pin, byte[] salt, int iterations) {
        char[] password = pin.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Secure PIN hashing is unavailable", e); }
        finally { spec.clearPassword(); java.util.Arrays.fill(password, '\0'); }
    }
}
