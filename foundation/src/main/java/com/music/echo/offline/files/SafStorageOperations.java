package com.music.echo.offline.files;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Provider-aware operations for user-granted Storage Access Framework document trees. */
final class SafStorageOperations {
    private SafStorageOperations() { }

    static final class DirectoryEntry {
        final String documentId;
        final String name;
        final boolean directory;

        DirectoryEntry(String documentId, String name, boolean directory) {
            this.documentId = documentId;
            this.name = name;
            this.directory = directory;
        }
    }

    /** Lists immediate children through the selected tree, with bounded provider traversal. */
    static ArrayList<DirectoryEntry> listChildren(ContentResolver resolver, Uri tree, Uri parent)
            throws IOException {
        OperationCancellation.checkCurrent();
        if (resolver == null || tree == null || parent == null) {
            throw new IOException("Storage tree and parent document are required");
        }
        Uri children;
        try {
            children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree, DocumentsContract.getDocumentId(parent));
        } catch (IllegalArgumentException invalidUri) {
            throw new IOException("Invalid storage folder URI", invalidUri);
        }
        ArrayList<DirectoryEntry> entries = new ArrayList<>();
        try (Cursor cursor = resolver.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null) throw new IOException("Storage provider could not list this folder");
            while (cursor.moveToNext()) {
                OperationCancellation.checkCurrent();
                if (entries.size() >= TreeTraversalBudget.DEFAULT_MAX_ENTRIES) {
                    throw new IOException("Storage folder contains too many entries to browse safely");
                }
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                if (id == null || name == null) {
                    throw new IOException("Storage provider returned an incomplete document entry");
                }
                entries.add(new DirectoryEntry(id, name,
                        DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))));
            }
        } catch (SecurityException denied) {
            throw new IOException("Storage provider denied access to this folder", denied);
        } catch (RuntimeException providerFailure) {
            throw new IOException("Storage provider failed while listing this folder", providerFailure);
        }
        return entries;
    }

    /** Returns true when target is the directory itself or appears anywhere below it. */
    static boolean containsDocument(ContentResolver resolver, Uri tree, Uri directory, Uri target)
            throws IOException {
        OperationCancellation.checkCurrent();
        if (resolver == null || tree == null || directory == null || target == null) {
            throw new IOException("Storage tree and document URIs are required");
        }
        final String targetId;
        try {
            targetId = DocumentsContract.getDocumentId(target);
        } catch (IllegalArgumentException invalidUri) {
            throw new IOException("Invalid target document URI", invalidUri);
        }
        ArrayList<Uri> pending = new ArrayList<>();
        HashSet<String> visited = new HashSet<>();
        pending.add(directory);
        int cursor = 0;
        int scanned = 0;
        while (cursor < pending.size()) {
            OperationCancellation.checkCurrent();
            if (++scanned > TreeTraversalBudget.DEFAULT_MAX_ENTRIES
                    || pending.size() > TreeTraversalBudget.DEFAULT_MAX_ENTRIES) {
                throw new IOException("Destination subtree is too large to validate safely");
            }
            Uri current = pending.get(cursor++);
            String currentId;
            try { currentId = DocumentsContract.getDocumentId(current); }
            catch (IllegalArgumentException invalidUri) {
                throw new IOException("Storage provider returned an invalid folder URI", invalidUri);
            }
            if (targetId.equals(currentId)) return true;
            if (!visited.add(currentId)) continue;
            for (DirectoryEntry entry : listChildren(resolver, tree, current)) {
                Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId);
                if (targetId.equals(entry.documentId)) return true;
                if (entry.directory && !visited.contains(entry.documentId)) pending.add(child);
            }
        }
        return false;
    }

    /** Returns only safe child-folder destinations for moving the selected document. */
    static ArrayList<DirectoryEntry> listMoveDestinations(ContentResolver resolver, Uri tree,
                                                           Uri currentFolder, Uri sourceDocument,
                                                           boolean sourceIsDirectory) throws IOException {
        OperationCancellation.checkCurrent();
        if (resolver == null || tree == null || currentFolder == null || sourceDocument == null) {
            throw new IOException("Storage tree and move document URIs are required");
        }
        ArrayList<DirectoryEntry> destinations = new ArrayList<>();
        for (DirectoryEntry entry : listChildren(resolver, tree, currentFolder)) {
            OperationCancellation.checkCurrent();
            if (!entry.directory) continue;
            Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId);
            if (sameDocument(child, sourceDocument)) continue;
            if (sourceIsDirectory && containsDocument(resolver, tree, child, sourceDocument)) continue;
            destinations.add(entry);
        }
        return destinations;
    }

    static boolean sameDocument(Uri first, Uri second) {
        if (first == null || second == null) return false;
        try {
            return DocumentsContract.getDocumentId(first).equals(DocumentsContract.getDocumentId(second));
        } catch (IllegalArgumentException invalidUri) {
            return first.equals(second);
        }
    }

    /** Imports one provider document into local storage using a private staging file. */
    static File importDocument(Context context, Uri document, File destinationDirectory,
                               String requestedName) throws IOException {
        OperationCancellation.checkCurrent();
        if (context == null || document == null || destinationDirectory == null
                || requestedName == null || requestedName.trim().isEmpty()) {
            throw new IOException("Document, destination folder and filename are required");
        }
        if (!destinationDirectory.isDirectory() && !destinationDirectory.mkdirs()) {
            throw new IOException("Download folder is unavailable");
        }
        if (!destinationDirectory.isDirectory() || !destinationDirectory.canWrite()) {
            throw new IOException("Download folder is not writable");
        }
        final File destination;
        try { destination = FileOperationEngine.unique(destinationDirectory, requestedName); }
        catch (IllegalArgumentException invalidName) {
            throw new IOException("Invalid imported filename", invalidName);
        }
        File pending = new File(destinationDirectory, ".toxifiles-import-" + UUID.randomUUID() + ".part");
        boolean reserved = false;
        try {
            reserved = pending.createNewFile();
            if (!reserved) throw new IOException("Unable to reserve an import staging file");
            try (InputStream input = ToxiUriPermissions.openInputStream(context, document);
                 FileOutputStream output = new FileOutputStream(pending, false)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    OperationCancellation.checkCurrent();
                    if (count > 0) output.write(buffer, 0, count);
                }
                output.flush();
                output.getFD().sync();
            } catch (SecurityException denied) {
                throw new IOException("Storage provider denied access to this document", denied);
            }
            OperationCancellation.checkCurrent();
            try {
                Files.move(pending.toPath(), destination.toPath());
            } catch (IOException moveFailure) {
                FileOperationEngine.copy(pending, destination);
                try { Files.deleteIfExists(pending.toPath()); }
                catch (IOException cleanupFailure) { /* The published import is complete; leave cleanup for later. */ }
            }
            return destination;
        } catch (IOException | RuntimeException failure) {
            if (reserved) {
                try { Files.deleteIfExists(pending.toPath()); }
                catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            throw failure;
        }
    }

    interface CopyProgressListener {
        void onProgress(int current, int total, File source);
    }

    static final class BatchCopyResult {
        final int copied;
        final int failed;
        final String error;

        BatchCopyResult(int copied, int failed, String error) {
            this.copied = copied;
            this.failed = failed;
            this.error = error;
        }
    }

    /** Copies a batch to one SAF folder, stopping at the first failure or cancellation. */
    static BatchCopyResult copyLocalFiles(ContentResolver resolver, List<File> sources,
                                          DocumentFile parent, CopyProgressListener progress) {
        if (resolver == null || sources == null || parent == null) {
            return new BatchCopyResult(0, 1, "Source files and destination folder are required");
        }
        if (!parent.exists() || !parent.isDirectory() || !parent.canWrite()) {
            return new BatchCopyResult(0, 1, "Destination storage folder is not writable");
        }
        int copied = 0;
        int failed = 0;
        String error = "";
        for (int index = 0; index < sources.size(); index++) {
            File source = sources.get(index);
            try {
                OperationCancellation.checkCurrent();
                if (source == null) throw new IOException("Source file is unavailable");
                if (progress != null) progress.onProgress(index + 1, sources.size(), source);
                copyLocalTree(resolver, source, parent);
                copied++;
            } catch (Exception failure) {
                failed = 1;
                error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                break;
            }
        }
        return new BatchCopyResult(copied, failed, error);
    }

    static DocumentFile copyLocalTree(ContentResolver resolver, File source, DocumentFile parent)
            throws IOException {
        return copyLocalTree(resolver, source, parent, new TreeTraversalBudget(), 0);
    }

    private static DocumentFile copyLocalTree(ContentResolver resolver, File source, DocumentFile parent,
                                              TreeTraversalBudget budget, int depth) throws IOException {
        OperationCancellation.checkCurrent();
        budget.enter(depth);
        if (resolver == null || source == null || parent == null) {
            throw new IOException("Source, destination and content resolver are required");
        }
        if (!source.exists()) throw new IOException("Source no longer exists: " + source.getName());
        if (!parent.exists() || !parent.isDirectory() || !parent.canWrite()) {
            throw new IOException("External storage destination is unavailable or read-only");
        }
        if (java.nio.file.Files.isSymbolicLink(source.toPath())) {
            throw new IOException("Symbolic links cannot be copied to external storage");
        }
        String name = source.getName();
        String invalid = BatchRenameEngine.validateName(name);
        if (invalid != null) throw new IOException(invalid + ": " + name);
        String candidate = uniqueName(parent, name, source.isDirectory());
        DocumentFile target = source.isDirectory()
                ? parent.createDirectory(candidate)
                : parent.createFile(mimeType(candidate), candidate);
        if (target == null) throw new IOException("Provider could not create " + candidate);
        try {
            if (source.isDirectory()) {
                File[] children = source.listFiles();
                if (children == null) throw new IOException("Cannot enumerate source folder " + name);
                for (File child : children) copyLocalTree(resolver, child, target, budget, depth + 1);
            } else {
                try (InputStream in = new FileInputStream(source);
                     OutputStream out = resolver.openOutputStream(target.getUri(), "w")) {
                    if (out == null) throw new IOException("Provider returned no output stream for " + candidate);
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        OperationCancellation.checkCurrent();
                        if (count > 0) out.write(buffer, 0, count);
                    }
                    out.flush();
                }
            }
            OperationCancellation.checkCurrent();
            return target;
        } catch (Exception failure) {
            try { deleteTree(target); }
            catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Copy to external storage failed: " + failure.getMessage(), failure);
        }
    }

    static DocumentFile copyDocumentTree(ContentResolver resolver, DocumentFile source,
                                         DocumentFile parent) throws IOException {
        return copyDocumentTree(resolver, source, parent, new TreeTraversalBudget(), new HashSet<>(), 0);
    }

    private static DocumentFile copyDocumentTree(ContentResolver resolver, DocumentFile source,
                                                  DocumentFile parent, TreeTraversalBudget budget,
                                                  HashSet<String> visitedDirectories, int depth) throws IOException {
        OperationCancellation.checkCurrent();
        budget.enter(depth);
        if (resolver == null || source == null || parent == null || !source.exists()) {
            throw new IOException("Source document or destination is unavailable");
        }
        if (!parent.exists() || !parent.isDirectory() || !parent.canWrite()) {
            throw new IOException("Destination document folder is not writable");
        }
        boolean directory = source.isDirectory();
        if (directory && !visitedDirectories.add(documentIdentity(source.getUri()))) {
            throw new IOException("Storage provider returned a repeated or cyclic directory");
        }
        String name = source.getName();
        String invalid = BatchRenameEngine.validateName(name);
        if (invalid != null) throw new IOException(invalid + ": " + name);
        String candidate = uniqueName(parent, name, directory);
        DocumentFile target = directory
                ? parent.createDirectory(candidate)
                : parent.createFile(mimeType(candidate), candidate);
        if (target == null) throw new IOException("Provider could not create " + candidate);
        try {
            if (directory) {
                DocumentFile[] children = source.listFiles();
                if (children == null) throw new IOException("Provider could not enumerate " + name);
                for (DocumentFile child : children) {
                    if (child == null) throw new IOException("Provider returned an invalid child document");
                    copyDocumentTree(resolver, child, target, budget, visitedDirectories, depth + 1);
                }
            } else {
                try (InputStream in = resolver.openInputStream(source.getUri());
                     OutputStream out = resolver.openOutputStream(target.getUri(), "w")) {
                    if (in == null || out == null) throw new IOException("Provider did not open the document streams");
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        OperationCancellation.checkCurrent();
                        if (count > 0) out.write(buffer, 0, count);
                    }
                    out.flush();
                }
            }
            OperationCancellation.checkCurrent();
            return target;
        } catch (Exception failure) {
            try { deleteTree(target); }
            catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Document copy failed: " + failure.getMessage(), failure);
        }
    }

    static Uri copyDocumentFile(ContentResolver resolver, Uri tree, Uri source,
                                 Uri parent, String requestedName) throws IOException {
        OperationCancellation.checkCurrent();
        if (resolver == null || tree == null || source == null || parent == null) {
            throw new IOException("Source and destination document URIs are required");
        }
        String invalid = BatchRenameEngine.validateName(requestedName);
        if (invalid != null) throw new IOException(invalid);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getDocumentId(parent));
        java.util.Set<String> existing = new java.util.HashSet<>();
        try (Cursor cursor = resolver.query(children,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor == null) throw new IOException("Provider could not inspect destination names");
            int scanned = 0;
            while (cursor.moveToNext()) {
                OperationCancellation.checkCurrent();
                if (++scanned > TreeTraversalBudget.DEFAULT_MAX_ENTRIES) {
                    throw new IOException("Destination folder contains too many entries to safely duplicate into");
                }
                existing.add(cursor.getString(0));
            }
        }
        String base = requestedName, extension = "";
        int dot = requestedName.lastIndexOf('.');
        if (dot > 0) { base = requestedName.substring(0, dot); extension = requestedName.substring(dot); }
        String candidate = requestedName;
        for (int suffix = 1; existing.contains(candidate); suffix++) {
            candidate = base + " (" + suffix + ")" + extension;
            if (BatchRenameEngine.validateName(candidate) != null) {
                throw new IOException("Unable to create a safe duplicate filename");
            }
        }
        String mime = resolver.getType(source);
        if (mime == null) mime = mimeType(requestedName);
        Uri target = DocumentsContract.createDocument(resolver, parent, mime, candidate);
        if (target == null) throw new IOException("Provider could not create " + candidate);
        try (InputStream in = resolver.openInputStream(source);
             OutputStream out = resolver.openOutputStream(target, "w")) {
            if (in == null || out == null) throw new IOException("Provider did not open the document streams");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) {
                OperationCancellation.checkCurrent();
                if (count > 0) out.write(buffer, 0, count);
            }
            out.flush();
            OperationCancellation.checkCurrent();
            return target;
        } catch (Exception failure) {
            try {
                if (!DocumentsContract.deleteDocument(resolver, target)) {
                    throw new IOException("Provider refused to remove partial duplicate output");
                }
            } catch (Exception cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Document duplicate failed: " + failure.getMessage(), failure);
        }
    }

    /** Uses the provider's native move; unsupported providers must use copy-then-delete. */
    /**
     * Resolve a provider document through the selected tree so recursive operations retain
     * tree-aware DocumentFile instances. Single-document wrappers cannot reliably enumerate
     * descendants on every SAF provider.
     */
    static DocumentFile findDocumentInTree(Context context, Uri tree, Uri target) throws IOException {
        OperationCancellation.checkCurrent();
        if (context == null || tree == null || target == null) {
            throw new IOException("Storage tree and target document are required");
        }
        final String targetId;
        try {
            targetId = DocumentsContract.getDocumentId(target);
        } catch (IllegalArgumentException invalidUri) {
            throw new IOException("Invalid target document URI", invalidUri);
        }
        DocumentFile root = DocumentFile.fromTreeUri(context, tree);
        if (root == null) throw new IOException("Selected storage tree is no longer available");
        ArrayList<DocumentFile> pending = new ArrayList<>();
        HashSet<String> visitedDirectories = new HashSet<>();
        pending.add(root);
        int cursor = 0;
        int scannedDirectories = 0;
        while (cursor < pending.size()) {
            OperationCancellation.checkCurrent();
            if (++scannedDirectories > TreeTraversalBudget.DEFAULT_MAX_ENTRIES
                    || pending.size() > TreeTraversalBudget.DEFAULT_MAX_ENTRIES) {
                throw new IOException("Storage tree is too large to resolve this document safely");
            }
            DocumentFile current = pending.get(cursor++);
            String currentId;
            try {
                currentId = DocumentsContract.getDocumentId(current.getUri());
            } catch (IllegalArgumentException treeRootUri) {
                currentId = DocumentsContract.getTreeDocumentId(tree);
            }
            if (targetId.equals(currentId)) return current;
            if (!current.isDirectory()) continue;
            DocumentFile[] children;
            try {
                children = current.listFiles();
            } catch (RuntimeException providerFailure) {
                throw new IOException("Storage provider could not enumerate a folder", providerFailure);
            }
            if (children == null) continue;
            for (DocumentFile child : children) {
                if (child == null) continue;
                String childId;
                try {
                    childId = DocumentsContract.getDocumentId(child.getUri());
                } catch (IllegalArgumentException invalidChild) {
                    continue;
                }
                if (targetId.equals(childId)) return child;
                if (child.isDirectory() && visitedDirectories.add(childId)) pending.add(child);
            }
        }
        throw new IOException("Document is no longer available in the selected storage tree");
    }

    static Uri moveDocument(ContentResolver resolver, Uri source, Uri sourceParent, Uri targetParent)
            throws IOException {
        OperationCancellation.checkCurrent();
        if (resolver == null || source == null || sourceParent == null || targetParent == null) {
            throw new IOException("Source and parent document URIs are required");
        }
        try {
            Uri moved = DocumentsContract.moveDocument(resolver, source, sourceParent, targetParent);
            if (moved == null) throw new UnsupportedOperationException("Storage provider declined the native move");
            return moved;
        } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
            throw new IOException("Storage provider does not support native moves", unsupported);
        }
    }

    /**
     * Move a SAF entry with a provider-native operation where supported.
     * If native move is unsupported, copy fully before deleting the source.
     * If deletion fails, both copies remain and the exception names the destination.
     */
    static DocumentFile moveDocumentWithFallback(Context context, ContentResolver resolver, DocumentFile source,
                                                 DocumentFile sourceParent, DocumentFile targetParent)
            throws IOException {
        OperationCancellation.checkCurrent();
        if (context == null || resolver == null || source == null || sourceParent == null || targetParent == null
                || !source.exists()) {
            throw new IOException("Source document and parent folders are required");
        }
        if (!targetParent.exists() || !targetParent.isDirectory() || !targetParent.canWrite()) {
            throw new IOException("Destination document folder is not writable");
        }
        try {
            Uri moved = DocumentsContract.moveDocument(resolver, source.getUri(),
                    sourceParent.getUri(), targetParent.getUri());
            if (moved == null) throw new UnsupportedOperationException("Provider declined the native move");
            DocumentFile result = DocumentFile.fromSingleUri(context, moved);
            if (result == null || !result.exists()) {
                throw new IOException("Provider returned an invalid moved document");
            }
            return result;
        } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
            OperationCancellation.checkCurrent();
            DocumentFile copied = copyDocumentTree(resolver, source, targetParent);
            try {
                deleteDocument(resolver, source.getUri());
            } catch (IOException deleteFailure) {
                throw new IOException("Copied to " + copied.getName()
                        + " but the original remains because the provider refused deletion; both copies were preserved", deleteFailure);
            }
            return copied;
        }
    }

    static Uri renameDocument(ContentResolver resolver, Uri document, String newName)
            throws IOException {
        OperationCancellation.checkCurrent();
        String invalid = BatchRenameEngine.validateName(newName);
        if (invalid != null) throw new IOException(invalid);
        try {
            Uri renamed = DocumentsContract.renameDocument(resolver, document, newName);
            if (renamed == null) throw new IOException("Storage provider declined the rename");
            return renamed;
        } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
            throw new IOException("Storage provider does not support document rename", unsupported);
        }
    }

    static void deleteDocument(ContentResolver resolver, Uri document) throws IOException {
        OperationCancellation.checkCurrent();
        if (resolver == null || document == null) throw new IOException("Document URI is required");
        try {
            if (!DocumentsContract.deleteDocument(resolver, document)) {
                throw new IOException("Storage provider refused to delete the document");
            }
        } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
            throw new IOException("Storage provider does not support document deletion", unsupported);
        }
    }

    static void deleteTree(DocumentFile target) throws IOException {
        deleteTree(target, new TreeTraversalBudget(), new HashSet<>(), 0);
    }

    private static void deleteTree(DocumentFile target, TreeTraversalBudget budget,
                                   HashSet<String> visitedDirectories, int depth) throws IOException {
        if (target == null || !target.exists()) return;
        budget.enter(depth);
        if (target.isDirectory()) {
            if (!visitedDirectories.add(documentIdentity(target.getUri()))) {
                throw new IOException("Storage provider returned a repeated or cyclic directory during cleanup");
            }
            DocumentFile[] children = target.listFiles();
            if (children == null) throw new IOException("Provider could not enumerate partial output for cleanup");
            for (DocumentFile child : children) {
                if (child == null) throw new IOException("Provider returned an invalid child during cleanup");
                deleteTree(child, budget, visitedDirectories, depth + 1);
            }
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Provider refused to remove partial output: " + target.getName());
        }
    }

    private static String documentIdentity(Uri uri) {
        try {
            return DocumentsContract.getDocumentId(uri);
        } catch (IllegalArgumentException invalidDocumentUri) {
            return uri.toString();
        }
    }

    private static String uniqueName(DocumentFile parent, String name, boolean directory)
            throws IOException {
        if (parent.findFile(name) == null) return name;
        String base = name, extension = "";
        if (!directory) {
            int dot = name.lastIndexOf('.');
            if (dot > 0) { base = name.substring(0, dot); extension = name.substring(dot); }
        }
        for (int suffix = 1; suffix <= 9999; suffix++) {
            String candidate = base + " (" + suffix + ")" + extension;
            if (BatchRenameEngine.validateName(candidate) == null && parent.findFile(candidate) == null) {
                return candidate;
            }
        }
        throw new IOException("Unable to reserve a unique filename for " + name);
    }

    private static String mimeType(String name) {
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name);
        String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? "application/octet-stream" : mime;
    }
}
