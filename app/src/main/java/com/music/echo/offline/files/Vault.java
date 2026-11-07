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
import java.io.FilterOutputStream;
import java.io.ByteArrayOutputStream;
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

    static synchronized void setPin(Context c, String pin) {
        sp(c).edit().putString("pinhash", PinHash.create(pin)).putInt("failed_attempts", 0).putLong("blocked_until", 0L).apply();
    }

    static boolean isLockedOut(Context c) { return System.currentTimeMillis() < sp(c).getLong("blocked_until", 0L); }

    static synchronized boolean checkPin(Context c, String pin) {
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

    static synchronized void clearPin(Context c) { sp(c).edit().remove("pinhash").remove("failed_attempts").remove("blocked_until").apply(); }

    static File root(Context c) {
        File r = new File(c.getFilesDir(), "vault");
        if (!r.exists()) r.mkdirs();
        return r;
    }

    private static String encode(String path) { return Base64.encodeToString(path.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING); }
    private static String decode(String enc) { return new String(Base64.decode(enc, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)); }

    // ---------------- Keystore-backed AES-GCM ----------------
    private static SecretKey key(Context context) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KS_ALIAS)) {
            File vaultDirectory = new File(context.getFilesDir(), "vault");
            if (vaultDirectory.exists() && !vaultDirectory.isDirectory())
                throw new IOException("Vault storage is unavailable; existing data was preserved");
            File[] existing = vaultDirectory.exists() ? vaultDirectory.listFiles() : new File[0];
            if (existing == null) throw new IOException("Vault storage could not be inspected; existing data was preserved");
            if (existing.length > 0)
                throw new IOException("Vault encryption key is missing; existing encrypted data was preserved");
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        SecretKey result = (SecretKey) ks.getKey(KS_ALIAS, null);
        if (result == null) throw new IOException("Vault encryption key is unavailable; existing data was preserved");
        return result;
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream output) { super(output); }
        @Override public void close() throws IOException { flush(); }
    }

    /** Streams src → encrypted dest. Never touches more than one buffer's worth of plaintext in memory. */
    private static void encryptFile(File src, File dest, Context context) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(context));
        byte[] iv = c.getIV();
        try (InputStream in = new FileInputStream(src); FileOutputStream rawOut = new FileOutputStream(dest)) {
            rawOut.write(iv.length);
            rawOut.write(iv);
            try (CipherOutputStream cos = new CipherOutputStream(new NonClosingOutputStream(rawOut), c)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) { FileOps.checkCancelled(); cos.write(buf, 0, n); }
            }
            rawOut.getFD().sync();
        }
    }

    /** Authenticates AES-GCM before publishing plaintext at the destination path. */
    private static void decryptFile(File src, File dest, Context context) throws Exception {
        File parent = dest.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) throw new IOException("Unable to create restore folder");
        File pending = new File(parent, ".toxifiles-decrypt-" + java.util.UUID.randomUUID() + ".part");
        boolean published = false;
        try (InputStream rawIn = new FileInputStream(src)) {
            int ivLen = rawIn.read();
            if (ivLen != 12) throw new IOException("Invalid vault IV");
            byte[] iv = new byte[ivLen]; int off = 0;
            while (off < ivLen) { int r = rawIn.read(iv, off, ivLen - off); if (r < 0) throw new IOException("Truncated vault file"); off += r; }
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(context), new GCMParameterSpec(128, iv));
            try (FileOutputStream out = new FileOutputStream(pending)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = rawIn.read(buf)) != -1) {
                    FileOps.checkCancelled();
                    byte[] plain = c.update(buf, 0, n);
                    if (plain != null && plain.length > 0) out.write(plain);
                }
                byte[] finalPlain = c.doFinal(); // throws if the GCM authentication tag is invalid
                if (finalPlain.length > 0) out.write(finalPlain);
                out.flush();
                out.getFD().sync();
            }
            if (dest.exists()) throw new IOException("Restore destination already exists");
            java.nio.file.Files.move(pending.toPath(), dest.toPath());
            published = true;
        } catch (Exception decryptError) {
            pending.delete();
            if (published) dest.delete();
            throw decryptError;
        }
    }

    private static final String META_FILE = ".vault_meta";

    private static void writeMetadata(File wrapper, String originalPath, Context context) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(context));
        byte[] iv = cipher.getIV();
        File meta = new File(wrapper, META_FILE);
        try (FileOutputStream raw = new FileOutputStream(meta)) {
            raw.write(iv.length); raw.write(iv);
            try (CipherOutputStream out = new CipherOutputStream(new NonClosingOutputStream(raw), cipher)) {
                out.write(originalPath.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            raw.getFD().sync();
        }
    }

    private static String readMetadata(File meta, Context context) throws Exception {
        try (InputStream raw = new FileInputStream(meta)) {
            int ivLen = raw.read();
            if (ivLen < 12 || ivLen > 32) throw new IOException("Invalid encrypted Vault metadata");
            byte[] iv = new byte[ivLen]; int off = 0;
            while (off < ivLen) { int n = raw.read(iv, off, ivLen - off); if (n < 0) throw new IOException("Truncated Vault metadata"); off += n; }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(context), new GCMParameterSpec(128, iv));
            ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
            byte[] buf = new byte[1024]; int n;
            while ((n = raw.read(buf)) != -1) {
                if (encrypted.size() + n > 64 * 1024 + 16) throw new IOException("Vault metadata too large");
                encrypted.write(buf, 0, n);
            }
            byte[] plain = cipher.doFinal(encrypted.toByteArray());
            if (plain.length == 0 || plain.length > 64 * 1024) throw new IOException("Invalid Vault metadata size");
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void encryptTree(File src, File destRoot, Context context) throws Exception {
        FileOps.checkCancelled();
        if (java.nio.file.Files.isSymbolicLink(src.toPath())) throw new IOException("Symbolic links cannot be stored in Vault");
        if (src.isDirectory()) {
            if (!destRoot.mkdirs() && !destRoot.isDirectory()) throw new IOException("mkdir failed");
            File[] kids = src.listFiles();
            if (kids == null) throw new IOException("Can't read folder " + src.getName());
            for (File k : kids) encryptTree(k, new File(destRoot, k.getName()), context);
        } else {
            encryptFile(src, destRoot, context);
        }
    }

    private static void decryptTree(File src, File destRoot, Context context) throws Exception {
        FileOps.checkCancelled();
        if (java.nio.file.Files.isSymbolicLink(src.toPath())) throw new IOException("Unexpected symbolic link in Vault");
        if (src.isDirectory()) {
            if (!destRoot.mkdirs() && !destRoot.isDirectory()) throw new IOException("mkdir failed");
            File[] kids = src.listFiles();
            if (kids == null) throw new IOException("Can't read encrypted folder " + src.getName());
            for (File k : kids) decryptTree(k, new File(destRoot, k.getName()), context);
        } else {
            decryptFile(src, destRoot, context);
        }
    }

    static void moveIn(final MainActivity a, final List<File> src, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
            int ok = 0, fail = 0, cleanupFailed = 0;
            for (File s : src) {
                if (OperationCancellation.isCancelled()) { fail++; break; }
                File staging = null;
                try {
                    File parent = s.getParentFile();
                    if (parent == null) { fail++; continue; }
                    if (META_FILE.equals(s.getName())) throw new IOException("This filename is reserved by the Vault format");
                    File vaultRoot = root(a);
                    key(a); // Generate a key only for a genuinely empty Vault; never replace a missing key for existing data.
                    staging = new File(vaultRoot, ".pending-" + java.util.UUID.randomUUID());
                    if (!staging.mkdir()) throw new IOException("Unable to create private Vault staging folder");
                    File payloadRoot = new File(staging, "payload");
                    if (!payloadRoot.mkdir()) throw new IOException("Unable to create Vault payload folder");
                    File target = new File(payloadRoot, s.getName());
                    encryptTree(s, target, a);
                    writeMetadata(staging, parent.getAbsolutePath(), a);
                    FileOps.checkCancelled();
                    File wrapper = new File(vaultRoot, java.util.UUID.randomUUID().toString());
                    if (wrapper.exists()) throw new IOException("Vault item identifier collision");
                    try { java.nio.file.Files.move(staging.toPath(), wrapper.toPath()); }
                    catch (IOException commitError) { throw new IOException("Unable to commit encrypted Vault item", commitError); }
                    staging = null;
                    if (OperationCancellation.isCancelled()) { cleanupFailed++; fail++; continue; }
                    if (FileOps.deleteTree(s)) ok++;
                    else { cleanupFailed++; fail++; }
                } catch (Exception ex) {
                    if (staging != null) FileOps.deleteTree(staging);
                    fail++;
                }
            }
            final int fok = ok, ffail = fail, fcleanup = cleanupFailed;
            final String msg = ffail == 0 ? "Encrypted and moved " + fok + " item" + (fok == 1 ? "" : "s") + " to Vault"
                    : "Encrypted/moved " + fok + ", failed " + ffail + (fcleanup > 0 ? "; encrypted copies are safe, but some originals may remain" : "");
            a.runOnUiThread(() -> { a.busy(false); d.run(msg); });
        
            } finally { a.cancellation.finish(operationToken); }
        });
    }

    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        File[] wraps = root(c).listFiles();
        if (wraps == null) return out;
        for (File w : wraps) {
            if (!w.isDirectory() || w.getName().startsWith(".pending-")) continue;
            String nm = w.getName();
            String origPath = "";
            File meta = new File(w, META_FILE);
            if (meta.isFile()) {
                try { origPath = readMetadata(meta, c); } catch (Exception invalidMetadata) { origPath = ""; }
            } else {
                // Backward compatibility for vault entries created before metadata encryption.
                int i = nm.lastIndexOf('~');
                try { if (i > 0) origPath = decode(nm.substring(0, i)); } catch (Exception ignored) { }
            }
            File payload = null;
            File payloadRoot = new File(w, "payload");
            if (java.nio.file.Files.isSymbolicLink(w.toPath()) || java.nio.file.Files.isSymbolicLink(payloadRoot.toPath())) continue;
            if (payloadRoot.isDirectory()) {
                File[] payloads = payloadRoot.listFiles();
                if (payloads == null || payloads.length != 1) continue;
                payload = payloads[0];
            } else {
                File[] kids = w.listFiles();
                if (kids == null) continue;
                List<File> payloads = new ArrayList<>();
                for (File child : kids) if (!META_FILE.equals(child.getName())) payloads.add(child);
                if (payloads.size() != 1) continue; // Preserve malformed legacy wrappers; never guess which payload is authoritative.
                payload = payloads.get(0);
            }
            out.add(new Item(payload, w, origPath.isEmpty() ? null : new File(origPath), payload.isDirectory()));
        }
        return out;
    }

    static void restoreOut(final MainActivity a, final Item it, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
            String msg;
            try {
                File destDir = it.originalParent;
                boolean fellBack = destDir == null || !destDir.isDirectory();
                if (fellBack) {
                    destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!destDir.isDirectory() && !destDir.mkdirs()) throw new IOException("Unable to create Downloads folder for restore");
                }
                File target = FileOps.unique(destDir, it.file.getName());
                File privateStage = new File(a.getCacheDir(), "vault-restore-" + java.util.UUID.randomUUID());
                if (!privateStage.mkdir()) throw new IOException("Unable to create private restore staging folder");
                try {
                    File stagedPayload = new File(privateStage, it.file.getName());
                    decryptTree(it.file, stagedPayload, a); // Authenticate the entire tree in app-private storage first.
                    FileOps.copy(stagedPayload, target); // Publish only after every encrypted file has passed GCM authentication.
                } finally {
                    FileOps.deleteTree(privateStage);
                }
                if (OperationCancellation.isCancelled()) {
                    msg = "Restored a copy; the encrypted Vault item was retained because the operation was cancelled";
                } else {
                    if (!FileOps.deleteTree(it.wrapper)) throw new IOException("Decrypted file is available, but encrypted Vault cleanup was incomplete");
                    msg = fellBack ? "Decrypted to Downloads (original folder is gone)" : "Decrypted and moved out: " + target.getName();
                }
            } catch (Exception ex) { msg = "Failed: " + ex.getMessage(); }
            final String m = msg;
            a.runOnUiThread(() -> { a.busy(false); d.run(m); });
        
            } finally { a.cancellation.finish(operationToken); }
        });
    }

    static void deleteForever(final MainActivity a, final Item it, final FileOps.Done d) {
        a.busy(true);
        a.ops.execute(() -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
            boolean ok = !OperationCancellation.isCancelled() && FileOps.deleteTree(it.wrapper);
            a.runOnUiThread(() -> { a.busy(false); d.run(ok ? "Deleted" : "Delete failed"); });
        
            } finally { a.cancellation.finish(operationToken); }
        });
    }
}
