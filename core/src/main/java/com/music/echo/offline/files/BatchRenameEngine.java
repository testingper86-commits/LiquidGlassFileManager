package com.music.echo.offline.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure rename planning and transactional execution for the Selection & Batch Rename Studio. */
final class BatchRenameEngine {
    static final int CASE_KEEP = 0, CASE_LOWER = 1, CASE_UPPER = 2, CASE_TITLE = 3;
    static final class Options {
        String prefix = "", suffix = "", find = "", replacement = "";
        int capitalization = CASE_KEEP, start = 1, increment = 1, padding = 1;
        boolean numbering, preserveExtension = true;
    }
    static final class Item {
        final File source, target;
        final String problem;
        Item(File source, File target, String problem) { this.source=source; this.target=target; this.problem=problem; }
        boolean valid() { return problem == null; }
    }
    static final class Result {
        final List<Item> completed = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        boolean success() { return errors.isEmpty() && !completed.isEmpty(); }
    }
    private BatchRenameEngine() { }

    static List<Item> plan(List<File> sources, Options options) {
        List<Item> out = new ArrayList<>();
        if (options.numbering && (options.start < 0 || options.increment < 1 || options.padding < 1 || options.padding > 9)) {
            for (File source : sources) out.add(new Item(source, null, "Numbering requires start ≥ 0, increment ≥ 1 and padding from 1 to 9"));
            return out;
        }
        Set<String> sourcePaths = new HashSet<>(), targets = new HashSet<>();
        for (File f : sources) if (f != null) sourcePaths.add(key(f));
        long number = options.start;
        for (File source : sources) {
            if (source == null || !source.exists()) { out.add(new Item(source, null, "Source file is missing")); number += options.increment; continue; }
            String name = source.getName();
            String ext = "", base = name;
            if (!source.isDirectory() && options.preserveExtension) {
                int dot = name.lastIndexOf('.');
                if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
            }
            base = options.prefix + base + options.suffix;
            if (!options.find.isEmpty()) base = base.replace(options.find, options.replacement);
            base = applyCase(base, options.capitalization);
            if (options.numbering) base += " " + padded(number, options.padding);
            number += options.increment;
            String next = base + ext;
            String invalid = validateName(next);
            File target = null;
            if (invalid == null) {
                try { target = resolveChild(source.getParentFile(), next); }
                catch (IOException invalidPath) { invalid = "Destination escapes its parent folder"; }
            }
            if (invalid == null && samePath(source, target)) invalid = "Name is unchanged";
            if (invalid == null && !targets.add(key(target))) invalid = "Duplicate destination in this batch";
            if (invalid == null && target.exists() && !sourcePaths.contains(key(target))) invalid = "Destination already exists";
            if (invalid == null && target.exists() && sourcePaths.contains(key(target))) invalid = "Destination belongs to another selected item";
            out.add(new Item(source, target, invalid));
        }
        return out;
    }

    static Result execute(List<Item> plan) {
        Result result = new Result();
        for (Item item : plan) if (!item.valid()) result.errors.add(item.source == null ? "Invalid source" : item.source.getName() + ": " + item.problem);
        if (!result.errors.isEmpty()) return result;
        for (Item item : plan) {
            try {
                if (!item.source.exists()) throw new IOException("Source disappeared");
                if (item.target.exists()) throw new IOException("Destination appeared before rename");
                moveChild(item.source, item.target);
                result.completed.add(item);
            } catch (Exception e) {
                result.errors.add(item.source.getName() + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                rollback(result);
                break;
            }
        }
        return result;
    }

    static Result undo(Result prior) {
        Result result = new Result();
        for (int i = prior.completed.size() - 1; i >= 0; i--) {
            Item item = prior.completed.get(i);
            try {
                if (!item.target.exists()) throw new IOException("Renamed item is missing");
                if (item.source.exists()) throw new IOException("Original name is occupied");
                moveChild(item.target, item.source);
                result.completed.add(new Item(item.target, item.source, null));
            } catch (Exception e) {
                result.errors.add(item.target.getName() + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                rollback(result);
                break;
            }
        }
        return result;
    }

    private static void rollback(Result result) {
        for (int i = result.completed.size() - 1; i >= 0; i--) {
            Item done = result.completed.get(i);
            try {
                if (done.target.exists() && !done.source.exists()) {
                    moveChild(done.target, done.source);
                    if (done.target.exists() || !done.source.exists()) throw new IOException("Rollback did not restore the original path");
                } else if (done.target.exists() || !done.source.exists()) {
                    throw new IOException("Both paths are occupied or the renamed item is missing; recovery record retained");
                }
                result.completed.remove(i);
            } catch (Exception e) {
                result.errors.add("Rollback failed for " + done.target.getName() + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
    }
    /** Resolve one direct child with normalized path containment and explicit symlink rejection. */
    static File resolveChild(File parent, String name) throws IOException {
        if (parent == null) throw new IOException("Destination folder is required");
        String invalid = validateName(name);
        if (invalid != null) throw new IOException(invalid);
        File canonicalParent = parent.getCanonicalFile();
        if (!canonicalParent.isDirectory()) throw new IOException("Destination parent is not a directory");
        Path parentPath = canonicalParent.toPath();
        Path candidate = parentPath.resolve(name).normalize();
        if (!parentPath.equals(candidate.getParent()) || !candidate.startsWith(parentPath)) {
            throw new IOException("Destination escapes the selected parent folder");
        }
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path realTarget = candidate.toRealPath();
            if (!parentPath.equals(realTarget.getParent())) {
                throw new IOException("Destination resolves through a symbolic link outside the selected parent");
            }
        }
        return candidate.toFile();
    }

    /** Revalidate both leaf paths immediately before moving; never follow a selected symlink. */
    static void moveChild(File source, File target) throws IOException {
        if (source == null || target == null || source.getParentFile() == null || target.getParentFile() == null) {
            throw new IOException("Source and destination must be direct children of a folder");
        }
        File sourceParent = source.getParentFile().getCanonicalFile();
        File targetParent = target.getParentFile().getCanonicalFile();
        if (!sourceParent.equals(targetParent)) throw new IOException("Rename cannot move an item outside its parent folder");
        File checkedSource = resolveChild(sourceParent, source.getName());
        File checkedTarget = resolveChild(targetParent, target.getName());
        if (!Files.exists(checkedSource.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Source disappeared before rename");
        if (Files.exists(checkedTarget.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new java.nio.file.FileAlreadyExistsException(checkedTarget.getName());
        Files.move(checkedSource.toPath(), checkedTarget.toPath(), LinkOption.NOFOLLOW_LINKS);
        if (Files.exists(checkedSource.toPath(), LinkOption.NOFOLLOW_LINKS)
                || !Files.exists(checkedTarget.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Rename could not be verified after execution");
        }
    }

    static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) return "Filename is empty";
        if (".".equals(name) || "..".equals(name)) return "Reserved filename";
        if (name.getBytes(StandardCharsets.UTF_8).length > 255) return "Filename exceeds the 255-byte filesystem limit";
        for (int i=0;i<name.length();i++) { char c=name.charAt(i); if (c < 32 || c == 127 || c == '/' || c == '\\' || c == 0) return "Filename contains an invalid character"; }
        return null;
    }
    private static String applyCase(String value, int mode) {
        if (mode == CASE_LOWER) return value.toLowerCase(Locale.ROOT);
        if (mode == CASE_UPPER) return value.toUpperCase(Locale.ROOT);
        if (mode == CASE_TITLE) {
            StringBuilder b = new StringBuilder(value.length()); boolean start = true;
            for (int i=0;i<value.length();i++) { char c=value.charAt(i); b.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c)); start = Character.isWhitespace(c) || c == '-' || c == '_'; }
            return b.toString();
        }
        return value;
    }
    private static String padded(long n, int width) { String s=String.valueOf(n); StringBuilder b=new StringBuilder(); for(int i=s.length();i<Math.min(9,Math.max(1,width));i++) b.append('0'); return b.append(s).toString(); }
    private static boolean samePath(File a, File b) { try { return a.getCanonicalPath().equals(b.getCanonicalPath()); } catch (IOException e) { return a.getAbsolutePath().equals(b.getAbsolutePath()); } }
    private static String key(File f) { try { return f.getCanonicalPath(); } catch (IOException e) { return f.getAbsolutePath(); } }
}
