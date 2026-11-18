package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.EditText;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Owns persisted SAF tree grants, SD/USB browsing and document operations. */
final class SafBrowserController {
    private final MainActivity activity;
    private Uri safTree;

    SafBrowserController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void restorePersistedTree() {
        android.content.SharedPreferences preferences = activity.getSharedPreferences("lg_storage", android.content.Context.MODE_PRIVATE);
        String savedTree = preferences.getString("saf_tree", null);
        if (savedTree == null) return;
        try {
            Uri restoredTree = Uri.parse(savedTree);
            int flags = ToxiUriPermissions.persistedFlags(activity.getContentResolver(), restoredTree);
            if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                safTree = restoredTree;
            } else {
                preferences.edit().remove("saf_tree").remove("saf_flags").apply();
            }
        } catch (RuntimeException invalidSavedTree) {
            preferences.edit().remove("saf_tree").remove("saf_flags").apply();
        }
    }

    boolean hasTree() { return safTree != null; }

    void open() {
        try {
            activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), MainActivity.REQ_SAF_TREE);
        } catch (Exception unavailable) {
            ToxiVisualSystem.toast(activity, "Can't open the storage picker on this device");
        }
    }

    void onTreeResult(Intent data) {
        if (data == null) return;
        Uri tree = data.getData();
        if (tree == null) return;
        int grantedFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        int persistedFlags = ToxiUriPermissions.takeGranted(activity.getContentResolver(), tree, grantedFlags);
        safTree = tree;
        android.content.SharedPreferences preferences = activity.getSharedPreferences("lg_storage", android.content.Context.MODE_PRIVATE);
        if ((persistedFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            preferences.edit().putString("saf_tree", tree.toString()).putInt("saf_flags", persistedFlags).apply();
        } else {
            preferences.edit().remove("saf_tree").remove("saf_flags").apply();
            ToxiVisualSystem.toast(activity, "This provider grants temporary access only; select it again after restarting ToxiFiles");
        }
        showSafLevel(tree, SafStorageOperations.treeRootDocumentUri(tree), "Other storage");
    }

    void copyToSafTree(final List<File> files) {
        if (safTree == null || files == null || files.isEmpty()) { ToxiVisualSystem.toast(activity, "Choose an SD/USB destination first"); return; }
        activity.busy(true);
        activity.executeOperation(() -> {
            OperationCancellation.Token operationToken = activity.cancellation.begin();
            try {
                SafStorageOperations.BatchCopyResult result = SafStorageOperations.copyLocalFilesToTree(
                        activity.getApplicationContext(), activity.getContentResolver(), files, safTree,
                        (current, total, source) ->
                                activity.runUiIfAlive(() -> activity.progress("Copying to SD/USB " + current + " of " + total + ": " + source.getName())));
                activity.runUiIfAlive(() -> {
                    activity.busy(false);
                    ToxiVisualSystem.toast(activity, result.failed == 0
                            ? "Copied " + result.copied + " item" + (result.copied == 1 ? "" : "s") + " to SD/USB"
                            : "Copied " + result.copied + "; stopped: " + result.error);
                });
            } finally { activity.cancellation.finish(operationToken); }
        });
    }

    void showSafLevel(final Uri tree, final Uri docUri, final String title) {
        ToxiVisualSystem.toast(activity, "Loadingâ¦");
        activity.executeIo(() -> {
            final List<SafStorageOperations.DirectoryEntry> rows;
            boolean querySucceeded = false;
            List<SafStorageOperations.DirectoryEntry> loaded = new ArrayList<>();
            try {
                loaded = SafStorageOperations.listChildren(activity.getContentResolver(), tree, docUri);
                querySucceeded = true;
            } catch (Exception ignored) { }
            rows = loaded;
            final boolean readable = querySucceeded;
            activity.runUiIfAlive(() -> {
                if (!readable) { ToxiVisualSystem.toast(activity, "This storage provider could not read the folder"); return; }
                if (rows.isEmpty()) { ToxiVisualSystem.toast(activity, "This folder is empty"); return; }
                String[] names = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) names[i] = rows.get(i).name;
                AlertDialog listing = ToxiVisualSystem.dialogBuilder(activity).setTitle(title).setItems(names, (d, w) -> {
                    SafStorageOperations.DirectoryEntry row = rows.get(w);
                    Uri childUri = SafStorageOperations.documentUriInTree(tree, row.documentId);
                    if (row.directory) showSafLevel(tree, childUri, row.name);
                    else importSafDoc(childUri, row.name);
                }).setNegativeButton("Close", null).create();
                listing.setOnShowListener(ignored -> {
                    ToxiVisualSystem.styleDialog(listing);
                    listing.getListView().setOnItemLongClickListener(
                        (parent, view, position, id) -> {
                            SafStorageOperations.DirectoryEntry row = rows.get(position);
                            Uri selected = SafStorageOperations.documentUriInTree(tree, row.documentId);
                            showSafDocumentActions(tree, docUri, title, selected, row.name, row.directory);
                            return true;
                        });
                });
                listing.show();
            });
        });
    }

    private interface SafAction { void run() throws Exception; }

    private void showSafDocumentActions(Uri tree, Uri parent, String title, Uri document,
                                         String name, boolean directory) {
        String[] actions = new String[]{"Duplicate here", "Move to folder", "Rename", "Delete"};
        ToxiVisualSystem.dialogBuilder(activity).setTitle(name).setItems(actions, (dialog, which) -> {
            String action = actions[which];
            if ("Duplicate here".equals(action)) {
                runSafAction(tree, parent, title, "Duplicated " + name,
                        () -> SafStorageOperations.duplicateDocumentInTree(activity, activity.getContentResolver(),
                                tree, document, parent, name, directory));
            } else if ("Move to folder".equals(action)) {
                showSafMoveFolderPicker(tree, parent, document, parent, title, name);
            } else if ("Rename".equals(action)) {
                EditText input = new EditText(activity);
                input.setSingleLine(true);
                input.setText(name);
                input.setSelection(input.length());
                ToxiVisualSystem.dialogBuilder(activity).setTitle("Rename document").setView(input)
                        .setPositiveButton("Rename", (d, w) -> {
                            String newName = input.getText().toString();
                            if (name.equals(newName)) return;
                            runSafAction(tree, parent, title, "Renamed to " + newName,
                                    () -> SafStorageOperations.renameDocument(activity.getContentResolver(), document, newName));
                        }).setNegativeButton("Cancel", null).show();
            } else {
                ToxiVisualSystem.dialogBuilder(activity).setTitle("Delete " + name + "?")
                        .setMessage("This permanently deletes the selected item from the chosen storage provider.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> runSafAction(tree, parent, title, "Deleted " + name,
                                () -> SafStorageOperations.deleteDocument(activity.getContentResolver(), document)))
                        .show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void showSafMoveFolderPicker(Uri tree, Uri currentFolder, Uri sourceDocument,
                                         Uri sourceParent, String sourceParentTitle, String sourceName) {
        activity.executeIo(() -> {
            final List<SafStorageOperations.DirectoryEntry> folders = new ArrayList<>();
            String failure = null;
            try {
                folders.addAll(SafStorageOperations.listMoveDestinations(
                        activity, activity.getContentResolver(), tree, currentFolder, sourceDocument));
            } catch (Exception error) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            final String detail = failure;
            activity.runUiIfAlive(() -> {
                if (detail != null) {
                    ToxiVisualSystem.toast(activity, "Cannot browse destination folders: " + detail);
                    return;
                }
                boolean canMoveHere = !SafStorageOperations.sameDocument(currentFolder, sourceParent);
                List<String> choices = new ArrayList<>();
                if (canMoveHere) choices.add("Move here: " + SafStorageOperations.documentId(currentFolder));
                for (SafStorageOperations.DirectoryEntry folder : folders) choices.add("Open folder: " + folder.name + "  âº");
                if (choices.isEmpty()) {
                    ToxiVisualSystem.toast(activity, "No other destination folders are available here");
                    return;
                }
                ToxiVisualSystem.dialogBuilder(activity).setTitle("Move " + sourceName + " to folder")
                        .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                            if (canMoveHere && which == 0) {
                                runSafAction(tree, sourceParent, sourceParentTitle, "Moved " + sourceName,
                                        () -> SafStorageOperations.moveDocumentInTree(activity, activity.getContentResolver(),
                                                tree, sourceDocument, sourceParent, currentFolder));
                            } else {
                                int folderIndex = which - (canMoveHere ? 1 : 0);
                                SafStorageOperations.DirectoryEntry selected = folders.get(folderIndex);
                                Uri selectedFolder = SafStorageOperations.documentUriInTree(tree, selected.documentId);
                                showSafMoveFolderPicker(tree, selectedFolder, sourceDocument,
                                        sourceParent, sourceParentTitle, sourceName);
                            }
                        }).setNegativeButton("Cancel", null).show();
            });
        });
    }

    private void runSafAction(Uri tree, Uri parent, String title, String successMessage, SafAction action) {
        activity.busy(true);
        activity.executeOperation(() -> {
            OperationCancellation.Token operationToken = activity.cancellation.begin();
            String error = null;
            try { action.run(); }
            catch (Exception failure) {
                error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            } finally { activity.cancellation.finish(operationToken); }
            final String detail = error;
            activity.runUiIfAlive(() -> {
                activity.busy(false);
                if (detail == null) {
                    ToxiVisualSystem.toast(activity, successMessage);
                    showSafLevel(tree, parent, title);
                } else {
                    ToxiVisualSystem.toast(activity, "Storage operation failed: " + detail);
                }
            });
        });
    }

    void importSafDoc(final Uri doc, final String name) {
        activity.busy(true);
        activity.executeOperation(() -> {
            OperationCancellation.Token operationToken = activity.cancellation.begin();
            try {
                String msg;
                try {
                    File download = new File(Environment.getExternalStorageDirectory(), "Download");
                    File destination = SafStorageOperations.importDocument(
                            activity.getApplicationContext(), doc, download, name);
                    msg = "Imported to Download/" + destination.getName();
                } catch (Exception ex) {
                    msg = "Import failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                }
                final String m = msg;
                activity.runUiIfAlive(() -> { activity.busy(false); ToxiVisualSystem.toast(activity, m); activity.afterOps(); });

            } finally { activity.cancellation.finish(operationToken); }
        });
    }


}
