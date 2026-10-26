package com.music.echo.offline.files;

import org.junit.Test;
import static org.junit.Assert.*;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class LanTransferCryptoTest {
    @Test public void pairingProofRejectsWrongCode() throws Exception {
        byte[] salt = new byte[16]; for (int i = 0; i < salt.length; i++) salt[i] = (byte) (i + 1);
        SecretKey correct = LanTransfer.sessionKey("ABCDEFGH2345", salt);
        SecretKey wrong = LanTransfer.sessionKey("ABCDEFGH2346", salt);
        assertTrue(MessageDigest.isEqual(LanTransfer.authProof(correct, salt), LanTransfer.authProof(correct, salt)));
        assertFalse(MessageDigest.isEqual(LanTransfer.authProof(correct, salt), LanTransfer.authProof(wrong, salt)));
    }

    @Test public void filenamesRejectTraversalSeparatorsAndControlCharacters() throws Exception {
        assertEquals("song.mp3", LanTransfer.validateFileName("song.mp3"));
        assertRejected("../outside.txt");
        assertRejected("folder\\outside.txt");
        assertRejected("bad\nname.txt");
        assertRejected(".");
        assertRejected("a".repeat(256));
    }

    private static void assertRejected(String name) throws Exception {
        try { LanTransfer.validateFileName(name); fail("Unsafe filename must be rejected: " + name); }
        catch (java.io.IOException expected) { /* rejected */ }
    }

    @Test public void aesGcmRoundTripsAndRejectsTampering() throws Exception {
        byte[] salt = new byte[16]; Arrays.fill(salt, (byte) 7);
        SecretKey key = LanTransfer.sessionKey("QWERTY2345", salt);
        byte[] iv = new byte[12]; Arrays.fill(iv, (byte) 9);
        byte[] plain = "local network file transfer payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = enc.doFinal(plain);
        Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        assertArrayEquals(plain, dec.doFinal(encrypted));
        encrypted[0] ^= 1;
        Cipher tampered = Cipher.getInstance("AES/GCM/NoPadding");
        tampered.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        try { tampered.doFinal(encrypted); fail("Tampered ciphertext must not authenticate"); }
        catch (AEADBadTagException expected) { /* authenticated encryption rejects changes */ }
    }
}
