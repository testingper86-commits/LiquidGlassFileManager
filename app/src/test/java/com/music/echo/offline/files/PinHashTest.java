package com.music.echo.offline.files;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class PinHashTest {
    @Test public void saltedHashesDifferAndVerify() {
        String first = PinHash.create("0042");
        String second = PinHash.create("0042");
        assertNotEquals(first, second);
        assertTrue(PinHash.verify("0042", first, "lgapplock:"));
        assertFalse(PinHash.verify("0043", first, "lgapplock:"));
    }

    @Test public void migratesLegacyAppLockAndVaultHashes() throws Exception {
        assertTrue(PinHash.verify("1234", legacy("lgapplock:", "1234"), "lgapplock:"));
        assertTrue(PinHash.verify("9876", legacy("lgvault:", "9876"), "lgvault:"));
        assertFalse(PinHash.verify("0000", legacy("lgvault:", "9876"), "lgvault:"));
    }

    @Test public void rejectsMalformedStoredHashes() {
        assertFalse(PinHash.verify("1234", "pbkdf2$broken", "lgvault:"));
        assertFalse(PinHash.verify("1234", "", "lgvault:"));
        assertFalse(PinHash.verify("1234", null, "lgvault:"));
    }

    private static String legacy(String prefix, String pin) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((prefix + pin).getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }
}
