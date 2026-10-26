package com.music.echo.offline.files;

import com.music.echo.offline.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Enumeration;
import java.util.List;

/**
 * Minimal same-network (Wi-Fi) file transfer over a plain TCP socket — no device discovery,
 * no QR pairing, no pause/resume. Deliberately scoped to what can be reasoned about correctly
 * without real multi-device testing, rather than a bigger protocol that might silently hang
 * or corrupt a transfer in a situation this environment can't reproduce.
 *
 * Protocol (all blocking, one connection per transfer):
 *   challenge/response pairing, then int fileCount, long totalBytes and file headers
 *   receiver shows the verified file names/sizes and asks for explicit consent
 *   after consent, each file body is AES-GCM encrypted and SHA-256 verified
 */
final class LanTransfer {
    static final int PORT = 52017;

    private static final class IncomingFile {
        final String name; final long length; final String sha256;
        IncomingFile(String name, long length, String sha256) { this.name = name; this.length = length; this.sha256 = sha256; }
    }

    static String validateFileName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.length() > 255 || ".".equals(name) || "..".equals(name)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new IOException("Unsafe incoming filename rejected");
        for (int i = 0; i < name.length(); i++) { char c = name.charAt(i); if (c < 0x20 || c == 0x7f) throw new IOException("Filename contains control characters"); }
        return name;
    }

    interface Listener {
        void onWaiting(String ip, int port, String pairingCode);
        void onIncoming(String fromIp, int count, long totalBytes, List<String> names, Runnable accept, Runnable reject);
        void onFileStart(int idx, int total, String name);
        void onDone(String msg);
        void onError(String msg);
    }

    static String localIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String sha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static String hex(MessageDigest md) {
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static String newPairingCode() {
        SecureRandom random = new SecureRandom(); StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        return code.toString();
    }
    static SecretKey sessionKey(String code, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(code.toCharArray(), salt, 200_000, 256);
        try { return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES"); }
        finally { spec.clearPassword(); }
    }
    static byte[] authProof(SecretKey key, byte[] salt) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
        mac.update("ToxiFiles LAN pairing v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return mac.doFinal(salt);
    }
    private static void readFully(DataInputStream in, byte[] data) throws IOException { in.readFully(data); }

    static volatile ServerSocket activeServer;
    static volatile Socket activeSocket;

    static void cancel() {
        try { if (activeSocket != null) activeSocket.close(); } catch (Exception ignored) { }
        try { if (activeServer != null) activeServer.close(); } catch (Exception ignored) { }
    }

    // ---------------- RECEIVER ----------------
    static void receive(final File destDir, final Listener l) {
        final String pairingCode = newPairingCode();
        new Thread(() -> {
            ServerSocket server = null;
            try {
                server = new ServerSocket(PORT);
                activeServer = server;
                String ip = localIp();
                l.onWaiting(ip != null ? ip : "unknown — check your Wi-Fi connection", PORT, pairingCode);

                server.setSoTimeout(120_000);
                Socket sock = server.accept();
                activeSocket = sock;
                sock.setSoTimeout(300_000);
                DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));

                byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
                out.writeByte(salt.length); out.write(salt); out.flush();
                SecretKey key = sessionKey(pairingCode, salt);
                byte[] suppliedProof = new byte[32]; readFully(in, suppliedProof);
                if (!MessageDigest.isEqual(authProof(key, salt), suppliedProof)) throw new IOException("Pairing code is incorrect");

                int count = in.readInt();
                long totalBytes = in.readLong();
                if (count < 1 || count > 500) throw new IOException("Invalid transfer file count");
                if (totalBytes < 0 || totalBytes > 8L * 1024 * 1024 * 1024) throw new IOException("Transfer exceeds the 8 GB session limit");
                if (!destDir.exists() && !destDir.mkdirs() && !destDir.isDirectory())
                    throw new IOException("Destination folder is unavailable");
                if (totalBytes > destDir.getUsableSpace()) throw new IOException("Not enough free space for this transfer");

                List<IncomingFile> incomingFiles = new java.util.ArrayList<>();
                List<String> incomingNames = new java.util.ArrayList<>();
                long declaredBytes = 0L;
                for (int i = 0; i < count; i++) {
                    String name = validateFileName(in.readUTF());
                    long len = in.readLong();
                    String expectHash = in.readUTF();
                    if (len < 0 || len > totalBytes - declaredBytes) throw new IOException("Invalid incoming file size");
                    if (!expectHash.matches("[0-9a-fA-F]{64}")) throw new IOException("Invalid incoming file checksum header");
                    declaredBytes += len;
                    incomingFiles.add(new IncomingFile(name, len, expectHash));
                    incomingNames.add(name);
                }
                if (declaredBytes != totalBytes) throw new IOException("Transfer size did not match its header");

                final Object lock = new Object();
                final boolean[] decided = {false};
                final boolean[] accepted = {false};
                Runnable accept = () -> { synchronized (lock) { accepted[0] = true; decided[0] = true; lock.notifyAll(); } };
                Runnable reject = () -> { synchronized (lock) { accepted[0] = false; decided[0] = true; lock.notifyAll(); } };
                l.onIncoming(sock.getInetAddress().getHostAddress(), count, totalBytes, incomingNames, accept, reject);
                synchronized (lock) {
                    long deadline = System.currentTimeMillis() + 60_000L;
                    while (!decided[0]) {
                        long waitMs = deadline - System.currentTimeMillis();
                        if (waitMs <= 0) throw new IOException("Transfer confirmation timed out");
                        lock.wait(waitMs);
                    }
                }

                out.writeBoolean(accepted[0]);
                out.flush();
                if (!accepted[0]) { l.onError("Declined"); return; }

                for (int i = 0; i < incomingFiles.size(); i++) {
                    IncomingFile header = incomingFiles.get(i);
                    String name = header.name;
                    long len = header.length;
                    String expectHash = header.sha256;
                    l.onFileStart(i + 1, count, name);
                    File dest = FileOps.unique(destDir, name);
                    String rootPath = destDir.getCanonicalPath() + File.separator;
                    if (!dest.getCanonicalPath().startsWith(rootPath)) throw new IOException("Unsafe destination path rejected");
                    byte[] iv = new byte[12]; readFully(in, iv);
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, iv));
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    boolean ok = false;
                    File pending = new File(destDir, ".toxifiles-" + java.util.UUID.randomUUID() + ".part");
                    try (OutputStream fout = new FileOutputStream(pending)) {
                        byte[] buf = new byte[65536]; long encryptedRemaining = len + 16L;
                        while (encryptedRemaining > 0) {
                            int n = in.read(buf, 0, (int) Math.min(buf.length, encryptedRemaining));
                            if (n < 0) throw new EOFException("Connection closed during encrypted file data");
                            byte[] plain = cipher.update(buf, 0, n);
                            if (plain != null && plain.length > 0) { fout.write(plain); md.update(plain); }
                            encryptedRemaining -= n;
                        }
                        byte[] finalPlain = cipher.doFinal();
                        if (finalPlain.length > 0) { fout.write(finalPlain); md.update(finalPlain); }
                        ok = hex(md).equals(expectHash);
                    } catch (Exception decryptError) { pending.delete(); throw decryptError; }
                    if (!ok) { pending.delete(); throw new IOException(name + " failed authentication or checksum verification"); }
                    try {
                        if (!pending.renameTo(dest)) { FileOps.copy(pending, dest); if (!pending.delete()) pending.deleteOnExit(); }
                    } catch (Exception commitError) { FileOps.deleteTree(dest); pending.delete(); throw commitError; }
                    out.writeBoolean(true); out.flush();
                }
                l.onDone("Received " + count + " file" + (count == 1 ? "" : "s") + " into Download/" + destDir.getName());
            } catch (Exception ex) {
                l.onError("Receive failed: " + ex.getMessage());
            } finally {
                try { if (activeSocket != null) activeSocket.close(); } catch (Exception ignored) { }
                try { if (server != null) server.close(); } catch (Exception ignored) { }
                activeServer = null;
                activeSocket = null;
            }
        }).start();
    }

    // ---------------- SENDER ----------------
    static void send(final String ip, final String pairingCode, final List<File> files, final Listener l) {
        new Thread(() -> {
            Socket sock = null;
            try {
                sock = new Socket();
                activeSocket = sock;
                sock.connect(new InetSocketAddress(ip, PORT), 8000);
                sock.setSoTimeout(300_000);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
                DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));

                int saltLen = in.readUnsignedByte();
                if (saltLen != 16) throw new IOException("Invalid pairing challenge");
                byte[] salt = new byte[saltLen]; readFully(in, salt);
                SecretKey key = sessionKey(pairingCode, salt);
                out.write(authProof(key, salt)); out.flush();

                if (files.size() < 1 || files.size() > 500) throw new IOException("Choose between 1 and 500 files");
                long total = 0L;
                long[] lengths = new long[files.size()];
                String[] hashes = new String[files.size()];
                for (int i = 0; i < files.size(); i++) {
                    File f = files.get(i);
                    if (!f.isFile() || java.nio.file.Files.isSymbolicLink(f.toPath())) throw new IOException("Only regular files can be sent");
                    validateFileName(f.getName());
                    lengths[i] = f.length();
                    if (lengths[i] > 8L * 1024 * 1024 * 1024 - total) throw new IOException("Transfer exceeds the 8 GB session limit");
                    total += lengths[i];
                    hashes[i] = sha256(f);
                    if (hashes[i].isEmpty()) throw new IOException("Unable to hash " + f.getName());
                }
                out.writeInt(files.size());
                out.writeLong(total);
                for (int i = 0; i < files.size(); i++) {
                    out.writeUTF(files.get(i).getName());
                    out.writeLong(lengths[i]);
                    out.writeUTF(hashes[i]);
                }
                out.flush();

                if (!in.readBoolean()) { l.onError("The other device declined the transfer"); return; }

                for (int idx = 0; idx < files.size(); idx++) {
                    File f = files.get(idx);
                    l.onFileStart(idx + 1, files.size(), f.getName());
                    long fileLength = lengths[idx];
                    byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv); out.write(iv);
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, iv));
                    try (InputStream fin = new FileInputStream(f)) {
                        byte[] buf = new byte[65536]; int n; long left = fileLength;
                        while (left > 0) {
                            n = fin.read(buf, 0, (int) Math.min(buf.length, left));
                            if (n < 0) throw new EOFException("File changed while sending: " + f.getName());
                            byte[] encrypted = cipher.update(buf, 0, n); if (encrypted != null && encrypted.length > 0) out.write(encrypted);
                            left -= n;
                        }
                        byte[] finalEncrypted = cipher.doFinal(); if (finalEncrypted.length > 0) out.write(finalEncrypted);
                    }
                    out.flush();
                    if (!in.readBoolean()) throw new IOException(f.getName() + " failed checksum verification on the receiving end");
                }
                l.onDone("Sent " + files.size() + " file" + (files.size() == 1 ? "" : "s"));
            } catch (Exception ex) {
                l.onError("Send failed: " + ex.getMessage());
            } finally {
                try { if (sock != null) sock.close(); } catch (Exception ignored) { }
                activeSocket = null;
            }
        }).start();
    }
}
