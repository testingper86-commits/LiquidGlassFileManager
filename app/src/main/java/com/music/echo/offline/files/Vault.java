package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Private folder gated by a PIN, with file CONTENTS encrypted at rest using AES-256-GCM.
 * The AES key lives only inside Android's hardware-backed Keystore (alias "lg_vault_aes_key")
 * — it is generated on first use, never derived from the PIN, and never leaves the Keystore
 * in plaintext. The PIN is a separate UI access gate, not the encryption key itself, so a weak
 * PIN does not weaken the crypto.
 *
 * Still honest about limits: this protects file bytes against anyone reading the raw storage
 * (a backup, a pulled image, another app trying to peek at app-private files). It does NOT
 * protect against an attacker with root who can call the same Keystore API this app calls
 * while the device is unlocked — no on-device-only scheme can fully prevent that.
 */
final class Vault {
    private static final String KS_ALIAS = "lg_vault_aes_key";

    static final class Item {
        final File file;
        final File wrapper;
        final File originalParent;
        final boolean dir;
        Item(File file, File wrapper, File originalParent, boolean dir) {
            this.file = file; this.wrapper = wrapper; this.originalParent = originalParent; this.dir = dir;
        }
    }

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("lg_vault", Context.MODE_PRIVATE); }

    static boolean hasPin(Context c) { return sp(c).contains("pinhash"); }

    static void setPin(Context c, String pin) {
        sp(c).edit().putString("pinhash", PinHash.create(pin)).putInt("failed_attempts", 0).putLong("blocked_until", 0L).apply();
    }

    static boolean isLockedOut(Context c) { return System.currentTimeMillis() < sp(c).getLong("blocked_until", 0L); }

    static boolean checkPin(Context c, String pin) {
        SharedPreferences prefs = sp(c);
        long now = System.currentTimeMillis();
        if (now < prefs.getLong("blocked_until", 0L)) return false;
        String stored = prefs.getString("pinhash", "");
        if (!hasPin(c) || !PinHash.verify(pin, stored, "lgvault:")) {
            int failures = prefs.getInt("failed_attempts", 0) + 1;
            long delay = failures < 5 ? 0L : Math.min(15 * 60_000L, (long) Math.pow(2, Math.min(10, failures - 4)) * 1_000L);
            prefs.edit().putInt("failed_attempts", failures).putLong("blocked_until", now + delay).apply();
            return false;
        }
        if (!stored.startsWith("pbkdf2$")) prefs.edit().putString("pinhash", PinHash.create(pin)).apply();
        prefs.edit().putInt("failed_attempts", 0).putLong("blocked_until", 0L).apply();
        return true;
    }

    static void clearPin(Context c) { sp(c).edit().remove("pinhash").remove("failed_attempts").remove("blocked_until").apply(); }

    static File root(Context c) {
        File r = new File(c.getFilesDir(), "vault");
        if (!r.exists()) r.mkdirs();
        return r;
    }

    private static String encode(String path) { return Base64.encodeToString(path.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING); }
    private static String decode(String enc) { return new String(Base64.decode(enc, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)); }

    // ---------------- Keystore-backed AES-GCM ----------------
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KS_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(KS_ALIAS, null);
    }

    /** Streams src → encrypted dest. Never touches more than one buffer's worth of plaintext in memory. */
    private static void encryptFile(File src, File dest) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        try (InputStream in = new FileInputStream(src); OutputStream rawOut = new FileOutputStream(dest)) {
            rawOut.write(iv.length);
            rawOut.write(iv);
            try (CipherOutputStream cos = new CipherOutputStream(rawOut, c)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) cos.write(buf, 0, n);
            }
        }
    }

    /** Streams encrypted src → plaintext dest. */
    private static void decryptFile(File src, File dest) throws Exception {
        try (InputStream rawIn = new FileInputStream(src)) {
            int ivLen = rawIn.read();
            if (ivLen <= 0) throw new IOException("Not a vault file");
            byte[] iv = new byte[ivLen];
            int off = 0;
            while (off < ivLen) { int r = rawIn.read(iv, off, ivLen - off); if (r < 0) throw new IOException("Truncated vault file"); off += r; }
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            try (CipherInputStream cis = new CipherInputStream(rawIn, c); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = cis.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }

    private static final String META_FILE = ".vault_meta";

    private static void writeMetadata(File wrapper, String originalPath) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        File meta = new File(wrapper, META_FILE);
        try (OutputStream raw = new FileOutputStream(meta)) {
            raw.write(iv.length); raw.write(iv);
            try (CipherOutputStream out = new CipherOutputStream(raw, cipher)) {
                out.write(originalPath.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private static String readMetadata(File meta) throws Exception {
        try (InputStream raw = new FileInputStream(meta)) {
            int ivLen = raw.read();
            if (ivLen < 12 || ivLen > 32) throw new IOException("Invalid encrypted Vault metadata");
            byte[] iv = new byte[ivLen]; int off = 0;
            while (off < ivLen) { int n = raw.read(iv, off, ivLen - off); if (n < 0) throw new IOException("Truncated Vault metadata"); off += n; }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            try (CipherInputStream in = new CipherInputStream(raw, cipher); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[1024]; int n;
                while ((n = in.read(buf)) > 0) { if (out.size() + n > 64 * 1024) throw new IOException("Vault metadata too large"); out.write(buf, 0, n); }
                return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }

    private static void encryptTree(File src, File destRoot) throws Exception {
        FileOps.checkCancelled();
        if (java.nio.file.Files.isSymbolicLink(src.toPath())) throw new IOException("Symbolic links cannot be stored in Vault");
        if (src.isDirectory()) {
            if (!destRoot.mkdirs() && !destRoot.isDirectory()) throw new IOException("mkdir failed");
            File[] kids = src.listFiles();
            if (kids == null) throw new IOException("Can't read folder " + src.getName());
            for (File k : kids) encryptTree(k, new File(destRoot, k.getName()));
        } else {
            encryptFile(src, destRoot);
        }
    }

    private static void decryptTree(File src, File destRoot) throws Exception {
        FileOps.checkCancelled();
        if (java.nio.file.Files.isSymbolicLink(src.toPath())) throw new IOException("Unexpected symbolic link in Vault");
        if (src.isDirectory()) {
            if (!destRoot.mkdirs() && !destRoot.isDirectory()) throw new IOException("mkdir failed");
            File[] kids = src.listFiles();
            if (kids == null) throw new IOException("Can't read encrypted folder " + src.getName());
            for (File k : kids) decryptTree(k, new File(destRoot, k.getName()));
        } else {
            decryptFile(src, destRoot);
        }
    }

    static void moveIn(final MainActivity a, final List<File> src, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            FileOps.cancelRequested = false;
            int ok = 0, fail = 0;
            for (File s : src) {
                if (FileOps.cancelRequested) { fail++; break; }
                try {
                    File parent = s.getParentFile();
                    if (parent == null) { fail++; continue; }
                    File wrapper = new File(root(a), java.util.UUID.randomUUID().toString());
                    if (!wrapper.mkdirs()) { fail++; continue; }
                    File target = new File(wrapper, s.getName());
                    try {
                        encryptTree(s, target);               // never delete the original until encryption fully succeeds
                        writeMetadata(wrapper, parent.getAbsolutePath());
                    } catch (Exception encryptError) { FileOps.deleteTree(wrapper); throw encryptError; }
                    FileOps.deleteTree(s);
                    ok++;
                } catch (Exception ex) { fail++; }
            }
            final int fok = ok, ffail = fail;
            final String msg = ffail == 0 ? "Encrypted and moved " + fok + " item" + (fok == 1 ? "" : "s") + " to Vault" : "Moved " + fok + ", failed " + ffail;
            a.runOnUiThread(() -> { a.busy(false); d.run(msg); });
        });
    }

    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        File[] wraps = root(c).listFiles();
        if (wraps == null) return out;
        for (File w : wraps) {
            if (!w.isDirectory()) continue;
            String nm = w.getName();
            String origPath = "";
            File meta = new File(w, META_FILE);
            if (meta.isFile()) {
                try { origPath = readMetadata(meta); } catch (Exception invalidMetadata) { continue; }
            } else {
                // Backward compatibility for vault entries created before metadata encryption.
                int i = nm.lastIndexOf('~');
                try { if (i > 0) origPath = decode(nm.substring(0, i)); } catch (Exception ignored) { }
            }
            File[] kids = w.listFiles();
            File payload = null;
            if (kids != null) for (File child : kids) if (!META_FILE.equals(child.getName())) { payload = child; break; }
            if (payload == null) { FileOps.deleteTree(w); continue; }
            out.add(new Item(payload, w, new File(origPath), payload.isDirectory()));
        }
        return out;
    }

    static void restoreOut(final MainActivity a, final Item it, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            String msg;
            try {
                File destDir = it.originalParent;
                boolean fellBack = destDir == null || !destDir.isDirectory();
                if (fellBack) destDir = Environment.getExternalStorageDirectory();
                File target = FileOps.unique(destDir, it.file.getName());
                try { decryptTree(it.file, target); } catch (Exception decryptError) { FileOps.deleteTree(target); throw decryptError; } // no partial plaintext on failure
                FileOps.deleteTree(it.wrapper);            // only remove the encrypted copy once that succeeds
                msg = fellBack ? "Decrypted to internal storage (original folder is gone)" : "Decrypted and moved out: " + target.getName();
            } catch (Exception ex) { msg = "Failed: " + ex.getMessage(); }
            final String m = msg;
            a.runOnUiThread(() -> { a.busy(false); d.run(m); });
        });
    }

    static void deleteForever(final MainActivity a, final Item it, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            boolean ok = FileOps.deleteTree(it.wrapper);
            a.runOnUiThread(() -> { a.busy(false); d.run(ok ? "Deleted" : "Delete failed"); });
        });
    }
}
