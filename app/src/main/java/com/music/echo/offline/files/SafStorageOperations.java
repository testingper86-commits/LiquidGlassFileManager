package com.music.echo.offline.files;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Provider-aware operations for user-granted Storage Access Framework document trees. */
final class SafStorageOperations {
    private SafStorageOperations() { }

    static DocumentFile copyLocalTree(ContentResolver resolver, File source, DocumentFile parent)
            throws IOException {
        OperationCancellation.checkCurrent();
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
                for (File child : children) copyLocalTree(resolver, child, target);
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
        OperationCancellation.checkCurrent();
        if (resolver == null || source == null || parent == null || !source.exists()) {
            throw new IOException("Source document or destination is unavailable");
        }
        if (!parent.exists() || !parent.isDirectory() || !parent.canWrite()) {
            throw new IOException("Destination document folder is not writable");
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
                DocumentFile[] children = source.listFiles();
                if (children == null) throw new IOException("Provider could not enumerate " + name);
                for (DocumentFile child : children) copyDocumentTree(resolver, child, target);
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
            while (cursor.moveToNext()) existing.add(cursor.getString(0));
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
    static Uri moveDocument(ContentResolver resolver, Uri source, Uri sourceParent, Uri targetParent)
            throws IOException {
        OperationCancellation.checkCurrent();
        if (context == null || resolver == null || source == null || sourceParent == null || targetParent == null) {
            throw new IOException("Source and parent document URIs are required");
        }
        try {
            Uri moved = DocumentsContract.moveDocument(resolver, source, sourceParent, targetParent);
            if (moved == null) throw new IOException("Storage provider declined the native move");
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
        if (resolver == null || source == null || sourceParent == null || targetParent == null
                || !source.exists()) {
            throw new IOException("Source document and parent folders are required");
        }
        if (!targetParent.exists() || !targetParent.isDirectory() || !targetParent.canWrite()) {
            throw new IOException("Destination document folder is not writable");
        }
        try {
            Uri moved = DocumentsContract.moveDocument(resolver, source.getUri(),
                    sourceParent.getUri(), targetParent.getUri());
            if (moved == null) throw new IOException("Provider declined the native move");
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
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            DocumentFile[] children = target.listFiles();
            if (children == null) throw new IOException("Provider could not enumerate partial output for cleanup");
            for (DocumentFile child : children) deleteTree(child);
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Provider refused to remove partial output: " + target.getName());
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
