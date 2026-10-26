package com.music.echo.offline.files;

import com.music.echo.offline.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds true duplicate files: first groups by exact size (cheap, no disk reads), then
 * confirms with a SHA-256 checksum only within same-size groups of 2+ files — so a full
 * library is never hashed end-to-end, only the files that could actually collide.
 */
final class DupFinder {
    static final class Group {
        long size;
        final List<File> files = new ArrayList<>();
    }

    static List<Group> find(List<Entry> all) {
        Map<Long, List<File>> bySize = new HashMap<>();
        for (Entry e : all) {
            if (e.dir || e.len <= 0) continue;
            List<File> l = bySize.get(e.len);
            if (l == null) { l = new ArrayList<>(); bySize.put(e.len, l); }
            l.add(e.f);
        }
        List<Group> out = new ArrayList<>();
        for (Map.Entry<Long, List<File>> en : bySize.entrySet()) {
            if (en.getValue().size() < 2) continue;
            Map<String, Group> byHash = new HashMap<>();
            for (File f : en.getValue()) {
                String h = sha256(f);
                if (h == null) continue;
                Group g = byHash.get(h);
                if (g == null) { g = new Group(); g.size = en.getKey(); byHash.put(h, g); }
                g.files.add(f);
            }
            for (Group g : byHash.values()) if (g.files.size() > 1) out.add(g);
        }
        Collections.sort(out, (x, y) -> Long.compare(y.size * y.files.size(), x.size * x.files.size()));
        return out;
    }

    private static String sha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
