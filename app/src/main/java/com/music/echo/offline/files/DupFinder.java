package com.music.echo.offline.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Finds exact duplicates without presenting partial results as complete. */
final class DupFinder {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    static final class Group {
        long size;
        final List<File> files = new ArrayList<>();
    }

    static List<Group> find(List<Entry> all) throws IOException {
        if (all == null) throw new IOException("The storage index is unavailable");
        Map<Long, List<Entry>> bySize = new HashMap<>();
        for (Entry entry : all) {
            if (entry == null || entry.dir || entry.len < 0) continue;
            if (entry.f == null) throw new IOException("The storage index contains an invalid file entry");
            List<Entry> candidates = bySize.get(entry.len);
            if (candidates == null) {
                candidates = new ArrayList<>();
                bySize.put(entry.len, candidates);
            }
            candidates.add(entry);
        }

        List<Group> result = new ArrayList<>();
        for (Map.Entry<Long, List<Entry>> sizeGroup : bySize.entrySet()) {
            if (sizeGroup.getValue().size() < 2) continue;
            Map<String, Group> byHash = new HashMap<>();
            for (Entry indexed : sizeGroup.getValue()) {
                verifySnapshot(indexed);
                String hash = sha256(indexed.f);
                verifySnapshot(indexed);
                Group group = byHash.get(hash);
                if (group == null) {
                    group = new Group();
                    group.size = sizeGroup.getKey();
                    byHash.put(hash, group);
                }
                group.files.add(indexed.f);
            }
            for (Group group : byHash.values()) if (group.files.size() > 1) result.add(group);
        }
        Collections.sort(result, (left, right) -> Long.compare(reclaimable(right), reclaimable(left)));
        return result;
    }

    private static void verifySnapshot(Entry entry) throws IOException {
        File file = entry.f;
        if (Files.isSymbolicLink(file.toPath()) || !file.isFile()
                || file.length() != entry.len || file.lastModified() != entry.mod) {
            throw new IOException("File changed or became unavailable during duplicate scan: " + file.getName());
        }
    }

    private static String sha256(File file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("SHA-256 is unavailable", unavailable);
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        } catch (IOException failure) {
            throw new IOException("Unable to read " + file.getName() + " during duplicate scan", failure);
        } catch (SecurityException denied) {
            throw new IOException("Access denied to " + file.getName() + " during duplicate scan", denied);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            int unsigned = value & 0xff;
            result.append(HEX[unsigned >>> 4]).append(HEX[unsigned & 0x0f]);
        }
        return result.toString();
    }

    private static long reclaimable(Group group) {
        long extraCopies = group.files.size() - 1L;
        if (extraCopies <= 0) return 0L;
        return group.size > Long.MAX_VALUE / extraCopies ? Long.MAX_VALUE : group.size * extraCopies;
    }
}
