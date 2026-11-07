package com.music.echo.offline.files;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** Shared local filesystem operations with staging, no-follow traversal and bounded resource use. */
final class FileOperationEngine {
    private FileOperationEngine() { }

    static File unique(File directory, String name) {
        if (directory == null || BatchRenameEngine.validateName(name) != null)
            throw new IllegalArgumentException("Invalid destination filename");
        try {
            File candidate = safeChild(directory, name);
            if (!Files.exists(candidate.toPath(), LinkOption.NOFOLLOW_LINKS)) return candidate;
            String base = name, extension = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0) { base = name.substring(0, dot); extension = name.substring(dot); }
            for (int index = 1; index < 1000; index++) {
                candidate = safeChild(directory, base + " (" + index + ")" + extension);
                if (!Files.exists(candidate.toPath(), LinkOption.NOFOLLOW_LINKS)) return candidate;
            }
            return safeChild(directory, base + "_" + UUID.randomUUID() + extension);
        } catch (IOException invalidParent) {
            throw new IllegalArgumentException("Invalid destination folder", invalidParent);
        }
    }

    static File createUniqueFile(File directory, String name) throws IOException {
        if (directory == null || BatchRenameEngine.validateName(name) != null)
            throw new IOException("Invalid destination filename");
        String base = name, extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); extension = name.substring(dot); }
        for (int index = 0; index < 1000; index++) {
            String candidateName = index == 0 ? name : base + " (" + index + ")" + extension;
            File candidate = safeChild(directory, candidateName);
            try { Files.createFile(candidate.toPath()); return candidate; }
            catch (FileAlreadyExistsException occupied) { /* Try the next collision-free name. */ }
        }
        throw new IOException("Unable to reserve a unique output filename");
    }

    static File createUniqueDirectory(File directory, String name) throws IOException {
        if (directory == null || BatchRenameEngine.validateName(name) != null)
            throw new IOException("Invalid destination folder name");
        for (int index = 0; index < 1000; index++) {
            String candidateName = index == 0 ? name : name + " (" + index + ")";
            File candidate = safeChild(directory, candidateName);
            try { Files.createDirectory(candidate.toPath()); return candidate; }
            catch (FileAlreadyExistsException occupied) { /* Try the next collision-free name. */ }
        }
        throw new IOException("Unable to reserve a unique extraction folder");
    }

    static void copy(File source, File requestedTarget) throws IOException {
        OperationCancellation.checkCurrent();
        if (source == null || requestedTarget == null) throw new IOException("Source and destination are required");
        Path sourcePath = source.toPath();
        final BasicFileAttributes sourceAttributes;
        try { sourceAttributes = Files.readAttributes(sourcePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (IOException | SecurityException unavailable) { throw new IOException("Source is unavailable", unavailable); }
        if (sourceAttributes.isSymbolicLink() || (!sourceAttributes.isDirectory() && !sourceAttributes.isRegularFile()))
            throw new IOException("Only regular files and directories can be copied");

        File requestedParent = requestedTarget.getAbsoluteFile().getParentFile();
        if (requestedParent == null || !requestedParent.isDirectory()) throw new IOException("Destination folder is unavailable");
        File parent = requestedParent.getCanonicalFile();
        if (!Files.isDirectory(parent.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Destination folder is unavailable");
        File target = safeChild(parent, requestedTarget.getName());
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Destination already exists: " + target.getName());

        if (sourceAttributes.isDirectory()) {
            Path sourceReal = sourcePath.toRealPath();
            Path targetPath = target.toPath().toAbsolutePath().normalize();
            if (targetPath.equals(sourceReal) || targetPath.startsWith(sourceReal))
                throw new IOException("Folder destination is inside the source tree");
        }

        File staging = safeChild(parent, ".toxifiles-copy-" + UUID.randomUUID());
        if (Files.exists(staging.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unable to reserve copy staging path");
        try {
            TreeTraversalBudget budget = new TreeTraversalBudget();
            copyInto(sourcePath, staging.toPath(), budget, 0);
            OperationCancellation.checkCurrent();
            File finalTarget = safeChild(parent, target.getName());
            if (Files.exists(finalTarget.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(finalTarget.getName());
            Files.move(staging.toPath(), finalTarget.toPath());
        } catch (IOException failure) {
            cleanupAfterFailure(staging, failure);
            throw failure;
        } catch (RuntimeException failure) {
            cleanupAfterFailure(staging, failure);
            throw failure;
        } catch (Error failure) {
            cleanupAfterFailure(staging, failure);
            throw failure;
        }
    }

    private static void copyInto(Path source, Path target, TreeTraversalBudget budget, int depth) throws IOException {
        OperationCancellation.checkCurrent();
        budget.enter(depth);
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink()) throw new IOException("Symbolic links are not copied");
        if (attributes.isDirectory()) {
            Files.createDirectory(target);
            try (DirectoryStream<Path> children = Files.newDirectoryStream(source)) {
                for (Path child : children) {
                    OperationCancellation.checkCurrent();
                    Path leaf = child.getFileName();
                    if (leaf == null) throw new IOException("Invalid source child path");
                    Path destination = target.resolve(leaf).normalize();
                    if (!target.equals(destination.getParent())) throw new IOException("Copy child escaped its staging folder");
                    copyInto(child, destination, budget, depth + 1);
                }
            }
            return;
        }
        if (!attributes.isRegularFile()) throw new IOException("Unsupported filesystem entry: " + source.getFileName());
        Files.createFile(target);
        try (InputStream input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
             FileOutputStream output = new FileOutputStream(target.toFile(), false)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                OperationCancellation.checkCurrent();
                if (count > 0) output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
        } catch (IOException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
        BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (after.isSymbolicLink() || after.size() != attributes.size()
                || after.lastModifiedTime().toMillis() != attributes.lastModifiedTime().toMillis())
            throw new IOException("Source changed during copy: " + source.getFileName());
    }

    private static void cleanupAfterFailure(File staging, Throwable failure) {
        try { deleteTreeOrThrow(staging.toPath(), false); }
        catch (IOException | RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
    }

    static boolean deleteTree(File file) {
        if (file == null) return false;
        try { deleteTreeOrThrow(file.toPath(), true); return true; }
        catch (IOException | RuntimeException failure) { return false; }
    }

    static void deleteTreeOrThrow(Path path, boolean cancellable) throws IOException {
        if (cancellable) OperationCancellation.checkCurrent();
        final BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (java.nio.file.NoSuchFileException missing) { return; }
        if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
            if (cancellable) OperationCancellation.checkCurrent();
            Files.deleteIfExists(path);
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
            for (Path child : children) {
                if (cancellable) OperationCancellation.checkCurrent();
                deleteTreeOrThrow(child, cancellable);
            }
        }
        if (cancellable) OperationCancellation.checkCurrent();
        Files.deleteIfExists(path);
    }

    static void treeSize(File file, long[] totals) {
        if (file == null || totals == null || totals.length < 2) throw new IllegalArgumentException("Tree size output requires two counters");
        try { treeSize(file.toPath(), totals); }
        catch (IOException | RuntimeException ignored) { /* Keep the successfully measured partial total. */ }
    }

    private static void treeSize(Path path, long[] totals) throws IOException {
        java.util.ArrayDeque<TraversalNode> pending = new java.util.ArrayDeque<>();
        TreeTraversalBudget budget = new TreeTraversalBudget();
        budget.enter(0);
        pending.push(new TraversalNode(path, 0));
        while (!pending.isEmpty()) {
            TraversalNode current = pending.pop();
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(current.path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (java.nio.file.NoSuchFileException missing) {
                continue;
            }
            if (attributes.isSymbolicLink()) continue;
            if (attributes.isDirectory()) {
                try (DirectoryStream<Path> children = Files.newDirectoryStream(current.path)) {
                    for (Path child : children) {
                        budget.enter(current.depth + 1);
                        pending.push(new TraversalNode(child, current.depth + 1));
                    }
                }
            } else if (attributes.isRegularFile()) {
                totals[0] = saturatedAdd(totals[0], attributes.size());
                totals[1] = totals[1] == Long.MAX_VALUE ? Long.MAX_VALUE : totals[1] + 1L;
            }
        }
    }

    private static final class TraversalNode {
        final Path path;
        final int depth;
        TraversalNode(Path path, int depth) {
            this.path = path;
            this.depth = depth;
        }
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static File safeChild(File directory, String name) throws IOException {
        return BatchRenameEngine.resolveChild(directory, name);
    }
}
