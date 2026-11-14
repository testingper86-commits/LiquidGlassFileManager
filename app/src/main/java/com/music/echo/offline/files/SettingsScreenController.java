package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class SettingsScreenController {
    private final MainActivity activity;

    SettingsScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void build() {
        activity.settings.removeAllViews();
        LinearLayout settingsMasthead = new LinearLayout(activity);
        settingsMasthead.setGravity(Gravity.CENTER_VERTICAL);
        settingsMasthead.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        ImageView settingsMark = new ImageView(activity);
        settingsMark.setImageResource(R.drawable.toxifiles_master_art);
        settingsMasthead.addView(settingsMark, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(38)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(38))));
        LinearLayout settingsTitleBlock = new LinearLayout(activity);
        settingsTitleBlock.setOrientation(LinearLayout.VERTICAL);
        settingsTitleBlock.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)), 0, 0, 0);
        TextView t = ToxiVisualSystem.text(activity, "Prism console", 25, ToxiVisualSystem.TEXT_PRIMARY, true);
        settingsTitleBlock.addView(t);
        TextView settingsEyebrow = ToxiVisualSystem.text(activity, "PRISM / SYSTEM CONFIGURATION", 9.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        settingsEyebrow.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams eyebrowLp = new LinearLayout.LayoutParams(-2, -2);
        eyebrowLp.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
        settingsTitleBlock.addView(settingsEyebrow, eyebrowLp);
        settingsMasthead.addView(settingsTitleBlock, new LinearLayout.LayoutParams(0, -2, 1f));
        activity.settings.addView(settingsMasthead);
        t.setOnClickListener(v -> {
            int taps = ToxiVisualSystem.PREFERENCES.getInt("developer_unlock_taps", 0) + 1;
            if (taps >= 7) {
                ToxiVisualSystem.PREFERENCES.edit().putBoolean("developer_options_unlocked", true).putInt("developer_unlock_taps", 0).apply();
                ToxiVisualSystem.toast(activity, "Developer options unlocked");
                activity.buildSettings();
            } else {
                ToxiVisualSystem.PREFERENCES.edit().putInt("developer_unlock_taps", taps).apply();
                if (taps >= 4) ToxiVisualSystem.toast(activity, (7 - taps) + " more taps to unlock developer options");
            }
        });
        TextView intro = ToxiVisualSystem.text(activity, "Tune the materials, behavior and privacy of your filespace.", 13, ToxiVisualSystem.TEXT_SECONDARY, false);
        intro.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        activity.settings.addView(intro);
        TextView hint = ToxiVisualSystem.text(activity, "Each module opens its own live controls.", 11.5f, ToxiVisualSystem.ACCENT, false);
        hint.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)));
        activity.settings.addView(hint);
        activity.settings.addView(activity.section("Appearance"));
        activity.settings.addView(activity.row(ToxiIcons.PALETTE, "Interface identity", ToxiVisualSystem.THEME_NAMES[ToxiVisualSystem.THEME_INDEX], null, v -> activity.openThemeScreen()), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.switchRow(ToxiIcons.PALETTE, "Light appearance", "Use a bright, high-clarity surface palette", "light", false, () -> {
            ToxiVisualSystem.refresh(activity);
            activity.setTheme(ToxiVisualSystem.LIGHT_MODE ? R.style.AppTheme_Light : R.style.AppTheme);
            activity.recolor();
        }), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.row(ToxiIcons.INFO, "Font style", new String[]{"System Sans", "Rounded", "Serif", "Monospace"}[ToxiVisualSystem.PREFERENCES.getInt("font_family", 0)], null, v -> activity.fontDialog()), activity.lpm(-1, -2, 16, 0, 16, 8));


        activity.settings.addView(activity.section("Files & Folders"));
        activity.settings.addView(activity.switchRow(ToxiIcons.EYE, "Show hidden files", "Files and folders starting with a dot", "hidden", false, () -> activity.filesView.reload()), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.switchRow(ToxiIcons.OPEN, "Open files in ToxiFiles", "Built-in viewer for photos, video, audio, PDF and text", "inapp", true, null), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.switchRow(ToxiIcons.TRASH, "Confirm before deleting", null, "confirm", true, null), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.row(ToxiIcons.SORT, "Default sort", null, null, v -> activity.filesView.sortDialog()), activity.lpm(-1, -2, 16, 0, 16, 8));

        activity.settings.addView(activity.section("Storage Access"));
        activity.settings.addView(activity.row(ToxiIcons.DATABASE, "Storage permission", activity.hasAccess() ? "Allowed Â· manage access" : "Not allowed Â· tap to fix", null, v -> {
            if (!activity.hasAccess()) { activity.showStorageAccess(); return; }
            try {
                Intent manage = Build.VERSION.SDK_INT >= 30
                        ? new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + activity.getPackageName()))
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(manage);
            } catch (Exception e) { ToxiVisualSystem.toast(activity, "Open Android Settings > Apps > ToxiFiles > Permissions"); }
        }), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.row(ToxiIcons.REFRESH, "Rescan storage", "Refresh categories and search", null, v -> { Indexer.I.dirty = true; activity.startIndex(); ToxiVisualSystem.toast(activity, "Scanning\u2026"); }), activity.lpm(-1, -2, 16, 0, 16, 8));

        activity.settings.addView(activity.section("Recycle Bin"));
        activity.settings.addView(activity.switchRow(ToxiIcons.TRASH, "Move deleted files to Recycle Bin", "Keep deleted files here before permanent removal", "recyclebin", true, null), activity.lpm(-1, -2, 16, 0, 16, 8));
        int binCount = RecycleBin.count(activity);
        activity.settings.addView(activity.row(ToxiIcons.TRASH, "Recycle Bin", binCount == 0 ? "Empty" : binCount + " item" + (binCount == 1 ? "" : "s") + " \u2022 auto-removed after " + ToxiVisualSystem.PREFERENCES.getInt("recyclebin_days", 30) + " days", null, v -> activity.showRecycleBin()), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.row(ToxiIcons.TRASH, "Recycle Bin retention", ToxiVisualSystem.PREFERENCES.getInt("recyclebin_days", 30) + " days", null, v -> activity.retentionDialog()), activity.lpm(-1, -2, 16, 0, 16, 8));

        activity.settings.addView(activity.section("App Lock"));
        activity.settings.addView(activity.row(ToxiIcons.LOCK, AppLock.hasPin(activity) ? "Change App Lock PIN" : "Set App Lock PIN", "Biometric unlock is offered first on supported devices", null, v -> activity.setAppLockPin()), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.switchRow(ToxiIcons.SHIELD, "Require unlock to open app", "Re-locks after you leave the app or the screen turns off", "applock_on", false, () -> {
            if (ToxiVisualSystem.booleanPreference("applock_on", false) && !AppLock.hasPin(activity)) { ToxiVisualSystem.toast(activity, "Set an App Lock PIN first"); ToxiVisualSystem.setBooleanPreference("applock_on", false); activity.buildSettings(); }
        }), activity.lpm(-1, -2, 16, 0, 16, 8));

        String defFolder = ToxiVisualSystem.PREFERENCES.getString("default_folder", null);
        activity.settings.addView(activity.row(ToxiIcons.FOLDER, "Starting folder", defFolder != null ? defFolder : "Internal storage root (choose a folder in Files to set another)", null, v -> {
            if (defFolder == null) { ToxiVisualSystem.toast(activity, "Open a folder in Files, then choose Set as default start folder from its action menu"); return; }
            ToxiVisualSystem.dialogBuilder(activity).setTitle("Reset default folder?").setMessage("Will start from Internal storage root again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset", (d, w) -> { ToxiVisualSystem.PREFERENCES.edit().remove("default_folder").apply(); activity.buildSettings(); }).show();
        }), activity.lpm(-1, -2, 16, 0, 16, 8));


        activity.settings.addView(activity.section("Secure Vault"));
        activity.settings.addView(activity.row(ToxiIcons.LOCK, Vault.hasPin(activity) ? "Change Vault PIN" : "Set Vault PIN", "AES-256 encrypted, key held in Android Keystore", null, v -> activity.setVaultPin()), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.row(ToxiIcons.FOLDER, "Open Vault", Vault.list(activity).size() + " item" + (Vault.list(activity).size() == 1 ? "" : "s"), null, v -> activity.promptVaultPin()), activity.lpm(-1, -2, 16, 0, 16, 8));

        activity.settings.addView(activity.section("Wi-Fi Transfer"));
        activity.settings.addView(activity.row(ToxiIcons.WIFI, "Receive via Wi-Fi", "Both devices must be on the same Wi-Fi network", null, v -> activity.receiveViaWifi()), activity.lpm(-1, -2, 16, 0, 16, 8));
        activity.settings.addView(activity.row(ToxiIcons.WIFI, "Send via Wi-Fi", "Select files in Files, then choose Send via Wi-Fi from the file action menu", null, v -> ToxiVisualSystem.toast(activity, "Select files in Files first, then choose Send via Wi-Fi from the file action menu")), activity.lpm(-1, -2, 16, 0, 16, 8));

        activity.settings.addView(activity.section("SD Card & USB Storage"));
        activity.settings.addView(activity.row(ToxiIcons.CLOUD, "Browse other storage", "Browse SD cards, USB drives or cloud documents; import files and copy files out", null, v -> activity.openOtherStorage()), activity.lpm(-1, -2, 16, 0, 16, 8));


        if (ToxiVisualSystem.booleanPreference("developer_options_unlocked", false)) {
            activity.settings.addView(activity.section("Developer options"));
            activity.settings.addView(activity.row(ToxiIcons.ANALYTICS, "Usage & diagnostics", "Local app version, Android details, storage permission and crash status", null, v -> activity.showUsageDiagnostics()), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.row(ToxiIcons.ANALYTICS, "Developer Studio", "12 offline tools for source, APK, database, binary and asset inspection", null, v -> { if (ToxiVisualSystem.booleanPreference("developer_options_unlocked", false)) activity.startActivity(new Intent(activity, DeveloperStudioActivity.class)); }), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.section("Advanced tools"));
            activity.settings.addView(activity.switchRow(ToxiIcons.SPARKLE, "Background blur", "Can affect performance on some devices", "blur", false, () -> activity.bg.applyBlur()), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.section("Storage Tools"));
            activity.settings.addView(activity.row(ToxiIcons.DATABASE, "Archive manager", "List archive files; inspect ZIP contents and extract ZIP files. Other formats can be listed but are not extracted by this build.", null, v -> activity.openCategory(4)), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.row(ToxiIcons.ANALYTICS, "Find duplicate files", "Matches by size, then confirms with a checksum", null, v -> activity.openDuplicates()), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.row(ToxiIcons.DATABASE, "Find empty folders", "Folders with nothing inside them", null, v -> activity.openCleanup()), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.row(ToxiIcons.ANALYTICS, "Storage analyzer", "Count files and estimate space by category; shows largest files without deleting anything", null, v -> activity.openStorageAnalyzer()), activity.lpm(-1, -2, 16, 0, 16, 8));

            activity.settings.addView(activity.section("Privacy & Diagnostics"));
            activity.settings.addView(activity.row(ToxiIcons.SHIELD, "Privacy & network information", "Review app permissions and what offline mode means", null, v -> activity.showPrivacyAudit()), activity.lpm(-1, -2, 16, 0, 16, 8));
            // Former label: Crash logs & diagnostics.
            activity.settings.addView(activity.row(ToxiIcons.ANALYTICS, "Crash reports & diagnostics", CrashLogger.hasReport(activity) ? "A crash report is saved on this device" : "Save crash details automatically if the app closes unexpectedly", null, v -> activity.showCrashLogOptions()), activity.lpm(-1, -2, 16, 0, 16, 8));

            activity.settings.addView(activity.row(ToxiIcons.REFRESH, "Rescan storage", "Refresh the index if file counts look outdated", null, v -> { Indexer.I.dirty = true; activity.startIndex(); ToxiVisualSystem.toast(activity, "Scanning\u2026"); }), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.switchRow(ToxiIcons.ANALYTICS, "Verbose diagnostics", "Keep additional local diagnostic details for troubleshooting", "dev_verbose", false, null), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.switchRow(ToxiIcons.VIBRATE, "Haptic feedback", "Gentle vibration on selection", "haptic", true, null), activity.lpm(-1, -2, 16, 0, 16, 8));
            TextView backupHeading = ToxiVisualSystem.text(activity, "Backup & Restore", 14, ToxiVisualSystem.ACCENT, true);
            backupHeading.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)));
            activity.settings.addView(backupHeading);
            activity.settings.addView(activity.row(ToxiIcons.DOWNLOAD, "Export settings", "Save theme and preferences to a file you can share or keep", null, v -> activity.exportSettings()), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.row(ToxiIcons.CLOUD, "Import settings", "Restore from a previously exported file", null, v -> activity.importSettingsPick()), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.switchRow(ToxiIcons.VIBRATE, "Reduce motion", "Disable decorative transition animations", "reduce_motion", false, null), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.switchRow(ToxiIcons.INFO, "Show debug toasts", "Show extra status feedback for developer troubleshooting", "dev_debug_toasts", false, null), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.switchRow(ToxiIcons.REFRESH, "Enable diagnostic logging", "Keep additional troubleshooting events locally", "dev_logging", false, null), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.row(ToxiIcons.ARCHIVE, "Built-in tools", "Inspect the file manager's integrated viewers, players, editor and extractor", null, v -> activity.showBuiltInTools()), activity.lpm(-1, -2, 16, 0, 16, 8));
            activity.settings.addView(activity.row(ToxiIcons.REFRESH, "Reset developer options", "Hide this section again", null, v -> { ToxiVisualSystem.PREFERENCES.edit().putBoolean("developer_options_unlocked", false).apply(); activity.buildSettings(); }), activity.lpm(-1, -2, 16, 0, 16, 8));
        }

        // Keep About at the end of Settings, in its own collapsible section.
        // This prevents it from sitting above Storage Access or SD Card & USB Storage.
        activity.settings.addView(activity.section("About"));
        activity.settings.addView(activity.row(ToxiIcons.INFO, "About ToxiFiles", activity.versionName(), null, v -> activity.showAbout()), activity.lpm(-1, -2, 16, 0, 16, 12));

        // Present settings as clear, collapsible groups instead of one long wall of options.
        // Appearance starts open; every other section is closed until the user selects it.
        boolean inSection = false;
        boolean appearanceSection = false;
        for (int i = 0; i < activity.settings.getChildCount(); i++) {
            View child = activity.settings.getChildAt(i);
            if ("settings_section".equals(child.getTag())) {
                inSection = true;
                appearanceSection = child instanceof TextView && ((TextView) child).getText().toString().contains("APPEARANCE");
                child.setVisibility(View.VISIBLE);
                if (child instanceof TextView) {
                    TextView heading = (TextView) child;
                    String label = heading.getText().toString();
                    heading.setText(label);
                    activity.setLeadingIcon(heading, appearanceSection ? ToxiIcons.DOWN : ToxiIcons.NEXT);
                    heading.setContentDescription(label + (appearanceSection ? ", expanded settings section. Double tap to collapse." : ", collapsed settings section. Double tap to expand."));
                }
            } else if (inSection) {
                child.setVisibility(appearanceSection ? View.VISIBLE : View.GONE);
            }
        }
        ToxiVisualSystem.revealChildren(activity.settings);
    }


}
