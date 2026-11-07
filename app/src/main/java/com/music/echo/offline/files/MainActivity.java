package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import androidx.activity.ComponentActivity;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

public class MainActivity extends ComponentActivity {
    private static final String BIOMETRIC_UNLOCK_KEY_ALIAS = "toxifiles_biometric_unlock_v1";
    private final TaskCoordinator.Scope taskScope = new TaskCoordinator.Scope();
    final OperationCancellation cancellation = new OperationCancellation();
    final TaskCoordinator.ScopedExecutor io = taskScope.executor(TaskCoordinator.Lane.IO);
    final TaskCoordinator.ScopedExecutor ops = taskScope.executor(TaskCoordinator.Lane.SERIAL);
    final Set<String> favs = new LinkedHashSet<>();
    File[] clip;
    boolean clipCut;

    FrameLayout root, content;
    BgView bg;
    ScrollView homeScroll, toolsScroll, settingsScroll, aboutScroll, recycleScroll, vaultScroll, themeScroll;
    View homeScrollFooter;
    LinearLayout home, tools, settings, about, recycle, vault;
    static final int REQ_SAF_TREE = 501, REQ_IMPORT_SETTINGS = 502;
    Uri safTree;
    ScrollView dupScroll;
    LinearLayout dup;
    static volatile boolean unlocked = false;
    BrowserView filesView, favView, listView;
    View[] screens;
    FrameLayout nav;
    View hl;
    NebulaHomeView.Glyph[] navIc = new NebulaHomeView.Glyph[4];
    TextView[] navTx = new TextView[4];
    TextView busyTv;
    DynamicIslandView dynamicIsland;
    int busyCount, tab = 0, pendingCat = -1;
    View overlayView;
    View overlayBackgroundScreen;
    View overlayBackgroundContent;
    int overlayBackgroundAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    int overlayBackgroundContentAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    int overlayBackgroundVisibility = View.VISIBLE;
    boolean accessOk;
    final Handler ui = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                unlocked = false;
                if (overlayView == vaultScroll) closeOverlay();
            }
        }
    };

    @Override protected void onCreate(Bundle b) {
        boolean lightPref = getSharedPreferences("lg_settings", MODE_PRIVATE).getBoolean("light", false);
        setTheme(R.style.AppTheme);
        super.onCreate(b);
        ToxiBackNavigation.bind(this, this::handleBackNavigation);
        IntentFilter screenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOffReceiver, screenOffFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOffReceiver, screenOffFilter);
        ToxiVisualSystem.initialize(this);
        String savedSafTree = getSharedPreferences("lg_storage", MODE_PRIVATE).getString("saf_tree", null);
        if (savedSafTree != null) safTree = Uri.parse(savedSafTree);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        SharedPreferences fp = getSharedPreferences("lg_favs", MODE_PRIVATE);
        favs.addAll(fp.getStringSet("paths", new HashSet<String>()));

        root = new FrameLayout(this);
        root.setBackgroundColor(ToxiVisualSystem.CANVAS);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        bg = new BgView(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.setSmoothScrollingEnabled(true);
        homeScroll.setFocusable(true);
        homeScroll.setFocusableInTouchMode(true);
        homeScroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        homeScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeScroll.setClipToPadding(true);
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        homeScroll.addView(home, new ScrollView.LayoutParams(-1, -2));
        toolsScroll = new ScrollView(this);
        toolsScroll.setVerticalScrollBarEnabled(false);
        toolsScroll.setClipToPadding(false);
        toolsScroll.setFillViewport(true);
        tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        toolsScroll.addView(tools, new FrameLayout.LayoutParams(-1, -2));
        settingsScroll = new ScrollView(this);
        settingsScroll.setVerticalScrollBarEnabled(false);
        // Keep Settings rows inside the scroll viewport; the bottom navigation overlays the screen root.
        settingsScroll.setClipToPadding(true);
        settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settingsScroll.addView(settings, new FrameLayout.LayoutParams(-1, -2));
        themeScroll = new ScrollView(this);
        themeScroll.setVerticalScrollBarEnabled(false);
        themeScroll.setClipToPadding(false);
        aboutScroll = new ScrollView(this);
        aboutScroll.setVerticalScrollBarEnabled(false);
        aboutScroll.setClipToPadding(false);
        about = new LinearLayout(this);
        about.setOrientation(LinearLayout.VERTICAL);
        aboutScroll.addView(about, new FrameLayout.LayoutParams(-1, -2));
        recycleScroll = new ScrollView(this);
        recycleScroll.setVerticalScrollBarEnabled(false);
        recycleScroll.setClipToPadding(false);
        recycle = new LinearLayout(this);
        recycle.setOrientation(LinearLayout.VERTICAL);
        recycleScroll.addView(recycle, new FrameLayout.LayoutParams(-1, -2));
        vaultScroll = new ScrollView(this);
        vaultScroll.setVerticalScrollBarEnabled(false);
        vaultScroll.setClipToPadding(false);
        vault = new LinearLayout(this);
        vault.setOrientation(LinearLayout.VERTICAL);
        vaultScroll.addView(vault, new FrameLayout.LayoutParams(-1, -2));
        dupScroll = new ScrollView(this);
        dupScroll.setVerticalScrollBarEnabled(false);
        dupScroll.setClipToPadding(false);
        dup = new LinearLayout(this);
        dup.setOrientation(LinearLayout.VERTICAL);
        dupScroll.addView(dup, new FrameLayout.LayoutParams(-1, -2));

        filesView = new BrowserView(this, false, false);
        favView = new BrowserView(this, true, true);
        listView = new BrowserView(this, true, false);
        screens = new View[]{homeScroll, filesView, toolsScroll, settingsScroll};
        View[] all = {homeScroll, filesView, toolsScroll, favView, settingsScroll, listView, aboutScroll, recycleScroll, vaultScroll, dupScroll, themeScroll};
        for (View v : all) { content.addView(v, new FrameLayout.LayoutParams(-1, -1)); v.setVisibility(View.GONE); }

        buildNav();
        busyTv = ToxiVisualSystem.text(this, "Working\u2026", 13, ToxiVisualSystem.TEXT_PRIMARY, true);
        busyTv.setBackground(ToxiVisualSystem.accentSurface(20));
        busyTv.setPadding(ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(10), ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(10));
        busyTv.setVisibility(View.GONE);
        busyTv.setContentDescription("Operation progress. Tap to request cancellation.");
        busyTv.setOnClickListener(v -> { cancellation.cancelActive(); LanTransfer.cancel(); ToxiVisualSystem.toast(this, "Cancellation requested"); });
        root.addView(busyTv, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        setContentView(root);
        dynamicIsland = new DynamicIslandView(this);
        root.addView(dynamicIsland, new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                ToxiVisualSystem.TOP_INSET = in.top; ToxiVisualSystem.BOTTOM_INSET = in.bottom;
            } else { ToxiVisualSystem.TOP_INSET = insets.getSystemWindowInsetTop(); ToxiVisualSystem.BOTTOM_INSET = insets.getSystemWindowInsetBottom(); }
            applyInsets();
            return insets;
        });
        root.requestApplyInsets();

        accessOk = hasAccess();
        selectTab(0);
        if (!accessOk) {
            if (Build.VERSION.SDK_INT >= 30) ui.postDelayed(() -> { if (!isFinishing() && !isDestroyed() && !hasAccess()) showStorageAccess(); }, 600);
            else requestLegacyPerms();
        } else startIndex();
        ui.postDelayed(this::maybeOfferCrashReport, 2200);
    }

    void applyInsets() {
        content.setPadding(0, ToxiVisualSystem.TOP_INSET, 0, 0);
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) nav.getLayoutParams();
        np.bottomMargin = ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.dp(12);
        nav.setLayoutParams(np);
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) busyTv.getLayoutParams();
        bp.topMargin = ToxiVisualSystem.TOP_INSET + ToxiVisualSystem.dp(8);
        busyTv.setLayoutParams(bp);
        homeScroll.setPadding(0, 0, 0, 0);
        if (homeScrollFooter != null) {
            ViewGroup.LayoutParams footerParams = homeScrollFooter.getLayoutParams();
            footerParams.height = ToxiVisualSystem.dp(160) + ToxiVisualSystem.BOTTOM_INSET;
            homeScrollFooter.setLayoutParams(footerParams);
        }
        // Reserve enough scrollable space for the bottom navigation and system gesture area.
        settingsScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(96) + ToxiVisualSystem.BOTTOM_INSET);
        if (toolsScroll != null) toolsScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(24) + ToxiVisualSystem.BOTTOM_INSET);
        aboutScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(24) + ToxiVisualSystem.BOTTOM_INSET);
        recycleScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(24) + ToxiVisualSystem.BOTTOM_INSET);
        vaultScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(24) + ToxiVisualSystem.BOTTOM_INSET);
        dupScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(24) + ToxiVisualSystem.BOTTOM_INSET);
        filesView.applyInsets(); favView.applyInsets(); listView.applyInsets();
    }

    // ---------------- permissions ----------------
    boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23) {
            boolean read = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            boolean write = Build.VERSION.SDK_INT > 28 || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            return read && write;
        }
        return true;
    }

    void requestLegacyPerms() {
        if (Build.VERSION.SDK_INT >= 23)
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 77);
    }

    void showStorageAccess() {
        if (Build.VERSION.SDK_INT < 30) { requestLegacyPerms(); return; }
        new AlertDialog.Builder(this).setTitle("Allow access to all files")
                .setMessage("A file manager needs \"All files access\" to show, open, copy, move and delete your files. Files never leave your device.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                        catch (Exception e2) { ToxiVisualSystem.toast(this, "Open Settings > Apps > Special access > All files access"); }
                    }
                }).show();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        onAccessChanged();
    }

    @Override protected void onResume() {
        super.onResume();
        if (AppLock.enabled(this) && !unlocked) showAppLock();
        if (hasAccess() != accessOk) onAccessChanged();
        else if (accessOk && !Indexer.I.running && (Indexer.I.dirty || System.currentTimeMillis() - Indexer.I.finished > 180000)) startIndex();
    }

    @Override protected void onPause() { super.onPause(); }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (AppLock.enabled(this)) unlocked = false;
        if (overlayView == vaultScroll) closeOverlay(); // Vault locks when the user leaves the app.
    }

    void onAccessChanged() {
        accessOk = hasAccess();
        if (accessOk) startIndex();
        if (filesView.cur != null) filesView.reload();
        if (tab == 0) buildHome();
        if (tab == 3) buildSettings();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(screenOffReceiver); } catch (Exception ignored) { }
        super.onDestroy();
        taskScope.close(false);
    }

    // ---------------- index ----------------
    void startIndex() {
        if (!hasAccess()) return;
        if(dynamicIsland!=null)dynamicIsland.update("Scanning storage",-1);
        Indexer.I.start(this::onIndexDone);
    }

    void onIndexDone() {
        if(dynamicIsland!=null && busyCount==0)dynamicIsland.update("ToxiFiles ready",-1);
        String[] names = Indexer.CAT_NAMES;
        if (pendingCat >= 0 && pendingCat < 6) { listView.setList(names[pendingCat], Indexer.I.cats.get(pendingCat), false, false); pendingCat = -1; }
        else if (pendingCat == -2) { listView.setList("Search", Indexer.I.all, true, true); pendingCat = -1; }
        if (tab == 0 && overlayView == null && home.getVisibility() == View.VISIBLE) buildHome();
    }

    void afterOps() {
        Indexer.I.dirty = true;
        filesView.reload();
        favView.reload();
        if (overlayView == listView) listView.reload();
        filesView.updatePaste();
        if (tab == 0) buildHome();
        if (!Indexer.I.running && hasAccess()) startIndex();
    }

    // ---------------- clipboard / favourites / busy ----------------
    void setClip(File[] f, boolean cut) { clip = f; clipCut = cut; filesView.updatePaste(); }

    void saveFavs() { getSharedPreferences("lg_favs", MODE_PRIVATE).edit().putStringSet("paths", new HashSet<>(favs)).apply(); }
    boolean isFav(File f) { return favs.contains(f.getAbsolutePath()); }
    void toggleFav(File f) { String p = f.getAbsolutePath(); if (!favs.remove(p)) favs.add(p); saveFavs(); }
    void removeFavs(List<File> fs) { for (File f : fs) favs.remove(f.getAbsolutePath()); saveFavs(); }
    void renameFav(File from, File to) { if (favs.remove(from.getAbsolutePath())) { favs.add(to.getAbsolutePath()); saveFavs(); } }

    void busy(boolean on) {
        busyCount = Math.max(0, busyCount + (on ? 1 : -1));
        busyTv.setVisibility(busyCount > 0 ? View.VISIBLE : View.GONE);
        ToxiMotion.attach(busyTv);
        ToxiMotion.setActive(busyTv, busyCount > 0);
        if (busyCount == 1 && on) busyTv.setText("Working\u2026"); // reset text for ops that don't call progress()
        if(dynamicIsland!=null){dynamicIsland.update(busyCount>0?busyTv.getText().toString().replace(" · tap to cancel",""):"ToxiFiles ready",-1);if(busyCount>0)dynamicIsland.setAction("Request cancellation",()->{cancellation.cancelActive();ToxiVisualSystem.toast(this,"Cancellation requested");});else dynamicIsland.setAction(null,null);}
    }

    /** Called from a background thread via runOnUiThread to show granular "N of M" progress. */

    /** Called from a background thread via runOnUiThread to show granular "N of M" progress. */
    void progress(String text) {
        busyTv.setText(text + " · tap to cancel");
        ToxiMotion.attach(busyTv);
        ToxiMotion.setActive(busyTv, true);
        if (dynamicIsland != null) {
            int percent = -1;
            int[] counts = DeveloperStudioCore.parseProgressCount(text);
            if (counts != null) percent = Math.max(0, Math.min(100, (int)(100L * counts[0] / counts[1])));
            dynamicIsland.update(text, percent);
        }
    }

    // ---------------- opening things ----------------
    void openDir(File dir) {
        closeOverlay();
        selectTab(1);
        filesView.showDir(dir);
    }

    void openFile(Entry e, List<Entry> ctx) {
        if (e == null || e.f == null) {
            ToxiVisualSystem.toast(this, "This file is no longer available");
            return;
        }
        if (e.dir || e.f.isDirectory()) { openDir(e.f); return; }
        File file = e.f;
        if (!file.isFile() || !file.canRead()) {
            ToxiVisualSystem.toast(this, "This file is missing or cannot be read");
            return;
        }

        // Built-in document viewers are mandatory for supported document extensions.
        // Do not let the optional media in-app preference divert PDF/HTML to an external chooser.
        String extension = FileKinds.extensionOf(file);
        if (FileKinds.isHtmlExtension(extension)) {
            Intent html = new Intent(this, HtmlViewerActivity.class);
            html.putExtra(HtmlViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(html);
            return;
        }

        int kind = FileKinds.kindOfFile(file);
        if (kind == FileKinds.PDF) {
            openBuiltInViewer(file, 0);
            return;
        }

        boolean inApp = ToxiVisualSystem.booleanPreference("inapp", true);
        if (inApp && (kind == FileKinds.IMAGE || kind == FileKinds.VIDEO
                || kind == FileKinds.AUDIO || kind == FileKinds.TEXT)) {
            ArrayList<String> ps = new ArrayList<>();
            int idx = 0;
            if (kind == FileKinds.IMAGE || kind == FileKinds.VIDEO || kind == FileKinds.AUDIO) {
                if (ctx != null) for (Entry x : ctx) {
                    if (x != null && !x.dir && FileKinds.kindOfFile(x.f) == kind && x.f.isFile()) {
                        if (x.f.equals(file)) idx = ps.size();
                        ps.add(x.f.getAbsolutePath());
                    }
                }
                if (ps.isEmpty()) ps.add(file.getAbsolutePath());
                if (ps.size() > 400) {
                    int from = Math.max(0, Math.min(idx - 200, ps.size() - 400));
                    ps = new ArrayList<>(ps.subList(from, from + 400));
                    idx -= from;
                }
            } else {
                ps.add(file.getAbsolutePath());
            }
            Intent i = new Intent(this, ViewerActivity.class);
            i.putStringArrayListExtra("paths", ps);
            i.putExtra("index", idx);
            startActivity(i);
            return;
        }
        if (kind == FileKinds.ARCHIVE && "zip".equals(extension)) { showZip(file); return; }
        if (kind == FileKinds.APK) { FileOps.install(this, file, io); return; }
        // Unsupported native formats are handed to Android's app chooser on the first tap.
        FileOps.external(this, file, true);
    }

    private void openBuiltInViewer(File file, int index) {
        ArrayList<String> paths = new ArrayList<>();
        paths.add(file.getAbsolutePath());
        Intent intent = new Intent(this, ViewerActivity.class);
        intent.putStringArrayListExtra("paths", paths);
        intent.putExtra("index", index);
        startActivity(intent);
    }

    void showZip(final File z) {
        Intent tools = new Intent(this, ToxiToolsActivity.class);
        tools.putExtra(ToxiToolsActivity.EXTRA_FILE_PATH, z.getAbsolutePath());
        startActivity(tools);
    }

    void openCategory(int cat) {
        Indexer ix = Indexer.I;
        listView.setList(Indexer.CAT_NAMES[cat], ix.ready ? ix.cats.get(cat) : null, false, false);
        if (!ix.ready) { pendingCat = cat; if (!ix.running) startIndex(); }
        showOverlay(listView);
    }

    void openSearch() {
        Indexer ix = Indexer.I;
        listView.setList("Search", ix.ready ? ix.all : null, true, true);
        if (!ix.ready) { pendingCat = -2; if (!ix.running) startIndex(); }
        showOverlay(listView);
    }

    // ---------------- tabs / overlay / nav ----------------
    void selectTab(int i) {
        closeOverlay();
        tab = i;
        syncNavigationVisibility(false);
        for (int k = 0; k < screens.length; k++) {
            screens[k].animate().cancel();
            screens[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            screens[k].setAlpha(1f);
            screens[k].setTranslationY(0);
        }
        View s = screens[i];
        if (ToxiVisualSystem.booleanPreference("reduce_motion", true)) { s.setAlpha(1f); s.setTranslationY(0f); }
        else { s.setAlpha(0f); s.setTranslationY(ToxiVisualSystem.dp(8)); s.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start(); }
        if (i == 0) buildHome();
        else if (i == 1) {
            if (filesView.cur == null) {
                String def = ToxiVisualSystem.PREFERENCES.getString("default_folder", null);
                File start = (def != null && new File(def).isDirectory()) ? new File(def) : Environment.getExternalStorageDirectory();
                filesView.showDir(start);
            }
        }
        else if (i == 2) buildTools();
        else buildSettings();
        moveHighlight(i);
    }

    void showOverlay(View v) {
        if (overlayView != null && overlayView != v) closeOverlay();
        if (overlayView == null) {
            overlayBackgroundScreen = screens[tab];
            overlayBackgroundVisibility = overlayBackgroundScreen.getVisibility();
            overlayBackgroundAccessibility = overlayBackgroundScreen.getImportantForAccessibility();
            overlayBackgroundScreen.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            // ScrollView exposes its content LinearLayout as a separate accessibility branch.
            // Hide that branch explicitly as well while a full-screen child screen is active.
            if (overlayBackgroundScreen == settingsScroll) {
                overlayBackgroundContent = settings;
                overlayBackgroundContentAccessibility = settings.getImportantForAccessibility();
                settings.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        overlayView = v;
        // Keep the active panels opaque, and stop content from the underlying screen bleeding
        // through the overlay and appearing to overlap the active screen.
        int overlayAlpha = ToxiVisualSystem.HIGH_CONTRAST ? 255 : 248;
        v.setBackgroundColor(Color.argb(overlayAlpha, Color.red(ToxiVisualSystem.CANVAS), Color.green(ToxiVisualSystem.CANVAS), Color.blue(ToxiVisualSystem.CANVAS)));
        if (v == vaultScroll) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        boolean blurBackground = Build.VERSION.SDK_INT >= 31 && ToxiVisualSystem.booleanPreference("blur", false) && !ToxiVisualSystem.HIGH_CONTRAST;
        if (blurBackground) {
            screens[tab].setRenderEffect(android.graphics.RenderEffect.createBlurEffect(ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(18), android.graphics.Shader.TileMode.CLAMP));
            screens[tab].setVisibility(View.VISIBLE);
        } else {
            // A hidden background cannot bleed through translucent glass or remain touch/accessibility reachable.
            screens[tab].setVisibility(View.INVISIBLE);
        }
        v.setVisibility(View.VISIBLE);
        if (ToxiVisualSystem.booleanPreference("reduce_motion", true)) { v.setAlpha(1f); v.setTranslationY(0f); }
        else { v.setAlpha(0f); v.setTranslationY(ToxiVisualSystem.dp(8)); v.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start(); }
        syncNavigationVisibility(false);
    }

    void restoreOverlayBackgroundAccessibility() {
        if (overlayBackgroundContent != null) {
            overlayBackgroundContent.setImportantForAccessibility(overlayBackgroundContentAccessibility);
            overlayBackgroundContent = null;
            overlayBackgroundContentAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        }
        if (overlayBackgroundScreen == null) return;
        overlayBackgroundScreen.setImportantForAccessibility(overlayBackgroundAccessibility);
        overlayBackgroundScreen.setVisibility(overlayBackgroundVisibility);
        overlayBackgroundScreen = null;
        overlayBackgroundAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        overlayBackgroundVisibility = View.VISIBLE;
    }

    void closeOverlay() {
        if (overlayView == null) {
            restoreOverlayBackgroundAccessibility();
            return;
        }
        boolean wasVault = overlayView == vaultScroll;
        if (overlayView == listView) { listView.closeSearch(); listView.selecting = false; listView.sel.clear(); pendingCat = -1; }
        overlayView.setVisibility(View.GONE);
        overlayView = null;
        if (wasVault) getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 31) screens[tab].setRenderEffect(null);
        screens[tab].setVisibility(View.VISIBLE);
        restoreOverlayBackgroundAccessibility();
        syncNavigationVisibility(false);
        if (tab == 0) buildHome();
    }

    void syncNavigationVisibility(boolean selecting) {
        boolean hidden = overlayView != null || selecting;
        nav.setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    void navForSelection(boolean selecting) { syncNavigationVisibility(selecting); }

    private void handleBackNavigation() {
        if (overlayView == listView && listView.handleBack()) return;
        if (overlayView != null) { closeOverlay(); return; }
        if (tab == 1 && filesView.handleBack()) return;
        if (tab != 0) { selectTab(0); return; }
        finish();
    }

    void buildNav() {
        nav = new FrameLayout(this);
        nav.setBackground(NebulaHomeView.navigationSurface());
        nav.setElevation(NebulaHomeView.dp(this, 8));
        nav.setPadding(NebulaHomeView.dp(this, 5), NebulaHomeView.dp(this, 5), NebulaHomeView.dp(this, 5), NebulaHomeView.dp(this, 5));
        hl = new View(this);
        hl.setBackground(NebulaHomeView.navigationHighlight());
        nav.addView(hl, new FrameLayout.LayoutParams(0, -1));
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.HORIZONTAL);
        items.setGravity(Gravity.CENTER_VERTICAL);
        nav.addView(items, new FrameLayout.LayoutParams(-1, -1));
        String[] glyphs = {"house", "folder", "grid", "settings"};
        String[] labels = {"Home", "Files", "Tools", "Settings"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(NebulaHomeView.dp(this, 48));
            item.setBackground(NebulaHomeView.navigationItemSurface());
            navIc[i] = new NebulaHomeView.Glyph(this, glyphs[i], NebulaHomeView.TEXT);
            navIc[i].setContentDescription(labels[i]);
            item.addView(navIc[i], new LinearLayout.LayoutParams(NebulaHomeView.dp(this, 21), NebulaHomeView.dp(this, 21)));
            navTx[i] = new TextView(this);
            navTx[i].setText(labels[i]);
            navTx[i].setTextSize(10);
            navTx[i].setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            navTx[i].setTextColor(NebulaHomeView.MUTED);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
            labelLp.topMargin = NebulaHomeView.dp(this, 3);
            item.addView(navTx[i], labelLp);
            item.setContentDescription(labels[i]);
            item.setOnClickListener(v -> selectTab(index));
            items.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(-1, NebulaHomeView.dp(this, 66), Gravity.BOTTOM);
        navLp.setMargins(NebulaHomeView.dp(this, 14), 0, NebulaHomeView.dp(this, 14), NebulaHomeView.dp(this, 12));
        root.addView(nav, navLp);
        nav.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int itemWidth = Math.max(1, (right - left - NebulaHomeView.dp(this, 10)) / 4);
            FrameLayout.LayoutParams highlight = (FrameLayout.LayoutParams) hl.getLayoutParams();
            if (highlight.width != itemWidth) { highlight.width = itemWidth; hl.setLayoutParams(highlight); }
            hl.setTranslationX(Math.min(3, Math.max(0, tab)) * itemWidth);
        });
    }

    void moveHighlight(int i) {
        int iw = hl.getLayoutParams().width;
        int visualIndex = Math.max(0, Math.min(3, i));
        if (ToxiVisualSystem.booleanPreference("reduce_motion", true)) hl.setTranslationX(visualIndex * iw);
        else hl.animate().translationX(visualIndex * iw).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        for (int k = 0; k < 4; k++) {
            navTx[k].setTextColor(k == visualIndex ? NebulaHomeView.LIME : NebulaHomeView.MUTED);
            navIc[k].setTint(k == visualIndex ? NebulaHomeView.LIME : NebulaHomeView.MUTED);
        }
    }

    void recolor() {
        root.setBackgroundColor(ToxiVisualSystem.CANVAS);
        bg.invalidate();
        nav.setBackground(NebulaHomeView.navigationSurface());
        hl.setBackground(NebulaHomeView.navigationHighlight());
        moveHighlight(tab);
        filesView.recolor();
        favView.recolor();
        listView.recolor();
        buildHome();
        buildTools();
        buildSettings();
        if (vault.getChildCount() > 0) buildVault();
        if (recycle.getChildCount() > 0) buildRecycleBin();
    }

    // ---------------- HOME ----------------
    LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(ToxiVisualSystem.dp(l), ToxiVisualSystem.dp(t), ToxiVisualSystem.dp(r), ToxiVisualSystem.dp(b));
        return p;
    }

    int dialogListHeight() {
        int available = getResources().getDisplayMetrics().heightPixels - ToxiVisualSystem.TOP_INSET - ToxiVisualSystem.BOTTOM_INSET - ToxiVisualSystem.dp(260);
        return Math.max(ToxiVisualSystem.dp(180), Math.min(ToxiVisualSystem.dp(520), available));
    }

    void setLeadingIcon(TextView target, int symbol) {
        android.graphics.drawable.Drawable icon = ToxiIcons.symbol(symbol, ToxiVisualSystem.ACCENT);
        int size = ToxiVisualSystem.dp(17);
        icon.setBounds(0, 0, size, size);
        target.setCompoundDrawables(icon, null, null, null);
        target.setCompoundDrawablePadding(ToxiVisualSystem.dp(10));
    }

    TextView section(String t) {
        TextView v = ToxiVisualSystem.text(this, t.toUpperCase(java.util.Locale.ROOT), 11.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        v.setLetterSpacing(0.08f);
        v.setTextColor(ToxiVisualSystem.ACCENT);
        v.setText(t.toUpperCase(java.util.Locale.ROOT));
        setLeadingIcon(v, ToxiIcons.NEXT);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinHeight(ToxiVisualSystem.dp(48));
        v.setPadding(ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(11), ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(11));
        v.setBackground(ToxiVisualSystem.surface(16, false));
        ToxiVisualSystem.interactive(v);
        v.setContentDescription(t + ", collapsed settings section. Double tap to expand.");
        LinearLayout.LayoutParams p = lpm(-1, -2, 16, 14, 16, 6);
        v.setLayoutParams(p);
        v.setTag("settings_section");
        v.setOnClickListener(view -> {
            if (view.getParent() != settings) return;
            int index = settings.indexOfChild(view);
            boolean anyVisible = false;
            for (int i = index + 1; i < settings.getChildCount(); i++) {
                View child = settings.getChildAt(i);
                if ("settings_section".equals(child.getTag())) break;
                if (child.getVisibility() == View.VISIBLE) anyVisible = true;
            }
            for (int i = index + 1; i < settings.getChildCount(); i++) {
                View child = settings.getChildAt(i);
                if ("settings_section".equals(child.getTag())) break;
                child.setVisibility(anyVisible ? View.GONE : View.VISIBLE);
            }
            v.setText(t.toUpperCase(java.util.Locale.ROOT));
            setLeadingIcon(v, anyVisible ? ToxiIcons.NEXT : ToxiIcons.DOWN);
            v.setContentDescription(t + (anyVisible ? ", collapsed settings section. Double tap to expand." : ", expanded settings section. Double tap to collapse."));
            ToxiVisualSystem.feedback(v);
        });
        return v;
    }

    TextView sectionLabel(String t) {
        TextView v = ToxiVisualSystem.text(this, t.toUpperCase(java.util.Locale.ROOT), 11.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        v.setLetterSpacing(0.08f);
        v.setTextColor(ToxiVisualSystem.ACCENT);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinHeight(ToxiVisualSystem.dp(48));
        v.setPadding(ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(11), ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(11));
        v.setBackground(ToxiVisualSystem.surface(16, false));
        v.setFocusable(false);
        v.setLayoutParams(lpm(-1, -2, 16, 14, 16, 6));
        return v;
    }

    // Home section labels are deliberately non-interactive; settings groups use section().
    // Keeping the two helpers separate prevents Home headings from displaying a fake expand affordance.
    View homeSection(String title) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(10));
        View marker = new View(this);
        marker.setBackground(ToxiVisualSystem.accentSurface(3));
        line.addView(marker, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(3), ToxiVisualSystem.dp(16)));
        TextView label = ToxiVisualSystem.text(this, title, 15, ToxiVisualSystem.TEXT_PRIMARY, true);
        label.setPadding(ToxiVisualSystem.dp(10), 0, ToxiVisualSystem.dp(12), 0);
        line.addView(label, new LinearLayout.LayoutParams(-2, -2));
        View rule = new View(this);
        rule.setBackgroundColor(Color.argb(48, Color.red(ToxiVisualSystem.ACCENT), Color.green(ToxiVisualSystem.ACCENT), Color.blue(ToxiVisualSystem.ACCENT)));
        line.addView(rule, new LinearLayout.LayoutParams(0, ToxiVisualSystem.dp(1), 1f));
        return line;
    }

    // NEBULA OS owns a separate visual system. The legacy brand resource remains for app-level identity,
    // while existing file-management screens continue to use ToxiVisualSystem.surface and ToxiFileRow.
    // ic_toxifiles_brand_mark is intentionally retained outside the new Home composition.
    void buildHome() { NebulaHomeView.buildHome(this, home); }

    void buildTools() { NebulaHomeView.buildTools(this, tools); }

    void showFavorites() {
        favView.refreshFavs();
        showOverlay(favView);
    }

    void openDownloads() {
        if (!hasAccess()) { showStorageAccess(); return; }
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloads == null || !downloads.isDirectory()) {
            ToxiVisualSystem.toast(this, "Downloads folder is not available on this device");
            return;
        }
        selectTab(1);
        filesView.showDir(downloads);
    }

    void launchNebulaTool(int index) {
        Intent launch = new Intent(this, ToxiToolsActivity.class);
        launch.putExtra("tool_index", index);
        startActivity(launch);
    }

    void showOtherSections() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(6), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(10));
        List<View> actions = new ArrayList<>();

        // Group related destinations under plain, familiar headings. Compatibility markers: APK installer files; System apps; Downloaded apps.
        String[] groupNames = {"FILES", "APPS", "SETTINGS"};
        String[] names = {"Other files", "APK files", "System apps", "Downloaded apps", "Settings"};
        String[] descriptions = {
                "Browse files outside Images, Videos, Music and Documents",
                "Find APK installer files saved on this device",
                "Apps included with your device",
                "Apps you installed",
                "Change ToxiFiles preferences"
        };
        int[] groupForItem = {0, 0, 1, 1, 2};
        for (int i = 0; i < names.length; i++) {
            if (i == 0 || groupForItem[i] != groupForItem[i - 1]) {
                TextView group = ToxiVisualSystem.text(this, groupNames[groupForItem[i]], 10.5f, ToxiVisualSystem.ACCENT, true);
                group.setLetterSpacing(0.09f);
                group.setPadding(ToxiVisualSystem.dp(4), ToxiVisualSystem.dp(i == 0 ? 4 : 14), ToxiVisualSystem.dp(4), ToxiVisualSystem.dp(7));
                menu.addView(group, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(ToxiVisualSystem.dp(13), ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(12));
            item.setBackground(ToxiVisualSystem.surface(16, false));
            item.setFocusable(true);
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(ToxiVisualSystem.text(this, names[i], 14, ToxiVisualSystem.TEXT_PRIMARY, true));
            TextView sub = ToxiVisualSystem.text(this, descriptions[i], 11.5f, ToxiVisualSystem.TEXT_SECONDARY, false);
            sub.setPadding(0, ToxiVisualSystem.dp(4), 0, 0);
            labels.addView(sub);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView arrow = new ImageView(this);
            arrow.setImageDrawable(ToxiIcons.symbol(ToxiIcons.NEXT, ToxiVisualSystem.TEXT_SECONDARY));
            arrow.setContentDescription("Open category");
            item.addView(arrow, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(24), ToxiVisualSystem.dp(30)));
            item.setContentDescription(names[i] + ". " + descriptions[i] + ". Open");
            ToxiVisualSystem.interactive(item);
            menu.addView(item, lpm(-1, -2, 0, 3, 0, 3));
            actions.add(item);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(menu);
        TextView title = ToxiVisualSystem.text(this, "Other", 24, ToxiVisualSystem.TEXT_PRIMARY, true);
        title.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(4));
        TextView subtitle = ToxiVisualSystem.text(this, "Other files, APKs, apps and settings", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        subtitle.setPadding(ToxiVisualSystem.dp(20), 0, ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(6));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(title);
        outer.addView(subtitle);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, dialogListHeight()));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(outer).setNegativeButton("Back", null).create();
        for (int i = 0; i < actions.size(); i++) {
            final int choice = i;
            actions.get(i).setOnClickListener(v -> {
                ToxiVisualSystem.feedback(v);
                dialog.dismiss();
                switch (choice) {
                    case 0: openCategory(6); break;
                    case 1: openApkFiles(); break;
                    case 2: showInstalledApps(true); break;
                    case 3: showInstalledApps(false); break;
                    case 4: selectTab(3); break;
                }
            });
        }
        dialog.show();
    }

    void openApkFiles() {
        Indexer ix = Indexer.I;
        List<Entry> apks = new ArrayList<>();
        if (ix.ready && ix.cats.size() > 6) {
            for (Entry e : ix.cats.get(6)) if (!e.dir && e.kind == FileKinds.APK) apks.add(e);
        }
        listView.setList("APK files", apks, false, false);
        if (!ix.ready) {
            pendingCat = 6;
            if (!ix.running) startIndex();
            ToxiVisualSystem.toast(this, "Scanning storage for APK files\u2026");
        }
        showOverlay(listView);
    }

    TextView appLaunchButton(String title, String subtitle, int glyph) {
        TextView v = ToxiVisualSystem.text(this, "", 14, ToxiVisualSystem.TEXT_PRIMARY, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(ToxiVisualSystem.surface(18, true));
        v.setText(title + "\n" + subtitle);
        v.setCompoundDrawablesWithIntrinsicBounds(ToxiIcons.symbol(glyph, ToxiVisualSystem.ACCENT), null, null, null);
        v.setCompoundDrawablePadding(ToxiVisualSystem.dp(8));
        return v;
    }

    void showInstalledApps(boolean system) {
        List<ApplicationInfo> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Set<String> hidden = ToxiVisualSystem.PREFERENCES.getStringSet("dev_hidden_apps", new HashSet<String>());
        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystem == system && !hidden.contains(ai.packageName) && (system || pm.getLaunchIntentForPackage(ai.packageName) != null)) apps.add(ai);
        }
        java.util.Collections.sort(apps, (a,b) -> String.valueOf(pm.getApplicationLabel(a)).compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b))));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(8), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(8));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        for (ApplicationInfo ai : apps) {
            LinearLayout item = new LinearLayout(this); item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(ToxiVisualSystem.dp(10), ToxiVisualSystem.dp(9), ToxiVisualSystem.dp(10), ToxiVisualSystem.dp(9)); item.setBackground(ToxiVisualSystem.surface(16, false));
            ImageView icon = new ImageView(this); try { icon.setImageDrawable(pm.getApplicationIcon(ai)); } catch (Exception ignored) {}
            item.addView(icon, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(32), ToxiVisualSystem.dp(32)));
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(ToxiVisualSystem.dp(12), 0, 0, 0);
            labels.addView(ToxiVisualSystem.text(this, String.valueOf(pm.getApplicationLabel(ai)), 14, ToxiVisualSystem.TEXT_PRIMARY, true));
            labels.addView(ToxiVisualSystem.text(this, ai.packageName, 10, ToxiVisualSystem.TEXT_SECONDARY, false));
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            item.setOnClickListener(v -> { try { Intent launch = pm.getLaunchIntentForPackage(ai.packageName); if (launch != null) startActivity(launch); else { Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ai.packageName)); startActivity(details); } } catch (Exception e) { ToxiVisualSystem.toast(this, "This system component has no launchable screen"); } });
            ToxiVisualSystem.interactive(item); list.addView(item, lpm(-1, -2, 0, 3, 0, 3));
        }
        if (apps.isEmpty()) list.addView(ToxiVisualSystem.text(this, "No launchable apps found in this section.", 13, ToxiVisualSystem.TEXT_SECONDARY, false));
        scroll.addView(list);
        TextView heading = ToxiVisualSystem.text(this, system ? "System Apps" : "Downloaded Apps", 23, ToxiVisualSystem.TEXT_PRIMARY, true);
        heading.setPadding(ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(8));
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.addView(heading); outer.addView(scroll, new LinearLayout.LayoutParams(-1, dialogListHeight()));
        new AlertDialog.Builder(this).setView(outer).setNegativeButton("Back", null).show();
    }

    void showBuiltInTools() {
        String[] tools = {"Dual-Pane File Explorer", "Professional Hex Editor", "Folder Comparator", "Character Encoding Converter", "Advanced ZIP Editor", "File Integrity Verification Studio", "Advanced Text Editor"};
        String[] details = {"Browse two folders side by side and copy, move or delete files.", "Inspect and edit binary data with hexadecimal offsets and ASCII.", "Compare folder trees and verify file content with SHA-256.", "Convert text between UTF-8, UTF-16, ASCII and legacy encodings.", "Rename, delete and add ZIP entries without extracting the archive.", "Calculate MD5, SHA-1, SHA-256, SHA-512 and CRC32.", "Regex search, encoding-aware save, syntax coloring and large-file limits."};
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(8), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(8));
        for (int i = 0; i < tools.length; i++) {
            final int index = i; LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(13), ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(13)); item.setBackground(ToxiVisualSystem.surface(16, true));
            item.addView(ToxiVisualSystem.text(this, tools[i], 14, ToxiVisualSystem.TEXT_PRIMARY, true));
            TextView detail = ToxiVisualSystem.text(this, details[i], 11.5f, ToxiVisualSystem.TEXT_SECONDARY, false); detail.setPadding(0, ToxiVisualSystem.dp(4), 0, 0); item.addView(detail);
            item.setOnClickListener(v -> { Intent launch = new Intent(this, ToxiToolsActivity.class); launch.putExtra("tool_index", index); startActivity(launch); });
            list.addView(item, lpm(-1, -2, 0, 3, 0, 3));
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(list); LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL);
        TextView heading = ToxiVisualSystem.text(this, "ToxiFiles built-in tools", 22, ToxiVisualSystem.TEXT_PRIMARY, true); heading.setPadding(ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(6));
        outer.addView(heading); outer.addView(ToxiVisualSystem.text(this, "Seven offline utilities integrated into the existing file manager.", 12, ToxiVisualSystem.TEXT_SECONDARY, false)); outer.addView(scroll, new LinearLayout.LayoutParams(-1, dialogListHeight()));
        new AlertDialog.Builder(this).setView(outer).setNegativeButton("Back", null).show();
    }

    void showUsageDiagnostics() {
        String msg = "Usage & diagnostics (stored locally)\n\n" +
                "App version: " + versionName() + "\n" +
                "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Storage access: " + (hasAccess() ? "Granted" : "Not granted") + "\n" +
                "Crash report: " + (CrashLogger.hasReport(this) ? "Available on this device" : "None saved") + "\n\n" +
                "No usage data is uploaded by this screen. Diagnostics remain on this device unless you explicitly share a report.";
        new AlertDialog.Builder(this).setTitle("Usage & diagnostics").setMessage(msg).setPositiveButton("View crash options", (d,w) -> showCrashLogOptions()).setNegativeButton("Close", null).show();
    }

    // ---------------- SETTINGS ----------------
    View row(int glyph, String title, String sub, View right, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(ToxiVisualSystem.surface(15, false));
        row.setPadding(ToxiVisualSystem.dp(13), ToxiVisualSystem.dp(11), ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(11));
        row.setMinimumHeight(ToxiVisualSystem.dp(sub == null ? 62 : 76));
        FrameLayout symbolPlate = new FrameLayout(this);
        symbolPlate.setBackground(ToxiVisualSystem.surface(11, true));
        ImageView symbol = new ImageView(this);
        symbol.setImageDrawable(ToxiIcons.symbol(glyph, ToxiVisualSystem.ACCENT));
        symbol.setContentDescription(ToxiIcons.accessibilityLabel(glyph));
        symbolPlate.addView(symbol, new FrameLayout.LayoutParams(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), Gravity.CENTER));
        row.addView(symbolPlate, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(36), ToxiVisualSystem.dp(36)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(1), ToxiVisualSystem.dp(8), ToxiVisualSystem.dp(1));
        TextView titleView = ToxiVisualSystem.text(this, title, 14.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(titleView);
        if (sub != null && !sub.isEmpty()) {
            TextView detail = ToxiVisualSystem.text(this, sub, 11.5f, ToxiVisualSystem.TEXT_SECONDARY, false);
            detail.setMaxLines(2);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = ToxiVisualSystem.dp(4);
            copy.addView(detail, detailLp);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        if (right != null) {
            row.addView(right, new LinearLayout.LayoutParams(-2, -2));
        } else if (listener != null) {
            ImageView chevron = new ImageView(this);
            chevron.setImageDrawable(ToxiIcons.symbol(ToxiIcons.NEXT, ToxiVisualSystem.TEXT_SECONDARY));
            chevron.setPadding(ToxiVisualSystem.dp(5), ToxiVisualSystem.dp(5), ToxiVisualSystem.dp(5), ToxiVisualSystem.dp(5));
            row.addView(chevron, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(28), ToxiVisualSystem.dp(28)));
        }
        if (listener != null) {
            row.setOnClickListener(view -> { ToxiVisualSystem.feedback(view); listener.onClick(view); });
            ToxiVisualSystem.interactive(row);
        }
        return row;
    }

    Switch sw(final String key, boolean def, final Runnable onChange) {
        Switch s = new Switch(this);
        s.setChecked(ToxiVisualSystem.booleanPreference(key, def));
        s.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ToxiVisualSystem.ACCENT, Color.rgb(200, 205, 215)}));
        s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Color.argb(120, Color.red(ToxiVisualSystem.ACCENT), Color.green(ToxiVisualSystem.ACCENT), Color.blue(ToxiVisualSystem.ACCENT)), Color.argb(90, 255, 255, 255)}));
        ToxiMotion.attach(s);
        ToxiMotion.setActive(s, false);
        s.setOnCheckedChangeListener((b, on) -> {
            ToxiMotion.setActive(s, true);
            ToxiVisualSystem.setBooleanPreference(key, on);
            if (onChange != null) onChange.run();
            s.postDelayed(() -> ToxiMotion.setActive(s, false), 420L);
        });
        return s;
    }

    View switchRow(int glyph, String title, String sub, String key, boolean def, Runnable onChange) {
        final Switch s = sw(key, def, onChange);
        return row(glyph, title, sub, s, v -> s.toggle());
    }

    void addDynamicIslandSettings() {
        final android.content.SharedPreferences p = DynamicIslandView.preferences(this);
        Switch enabled = new Switch(this); enabled.setChecked(p.getBoolean("enabled",true));
        enabled.setOnCheckedChangeListener((b,on)->{p.edit().putBoolean("enabled",on).apply();if(dynamicIsland!=null)dynamicIsland.setEnabledFromSettings();});
        settings.addView(row(ToxiIcons.SPARKLE,"Enable Dynamic Island","Show the draggable, theme-linked activity capsule",enabled,v->enabled.toggle()),lpm(-1,-2,16,0,16,8));
        settings.addView(row(ToxiIcons.PALETTE,"Island accent treatment",new String[]{"Primary signal","Secondary signal","Muted signal","Outline signal"}[Math.max(0,Math.min(3,p.getInt("palette",0)))],null,v->new AlertDialog.Builder(this).setTitle("Island accent treatment").setSingleChoiceItems(new String[]{"Primary signal","Secondary signal","Muted signal","Outline signal"},p.getInt("palette",0),(d,w)->{p.edit().putInt("palette",w).apply();d.dismiss();if(dynamicIsland!=null)dynamicIsland.refreshStyle();buildSettings();}).setNegativeButton("Cancel",null).show()),lpm(-1,-2,16,0,16,8));
        addIslandSlider(p,"Accent edge strength","glow",0,100,65);
        addIslandSlider(p,"Transparency","transparency",80,255,225);
        addIslandSlider(p,"Expansion transition speed","speed",90,420,180);
        settings.addView(row(ToxiIcons.INFO,"Capsule size",p.getInt("size",0)==0?"Compact":"Large",null,v->new AlertDialog.Builder(this).setTitle("Capsule size").setSingleChoiceItems(new String[]{"Compact","Large"},p.getInt("size",0),(d,w)->{p.edit().putInt("size",w).apply();d.dismiss();if(dynamicIsland!=null)dynamicIsland.refreshStyle();buildSettings();}).setNegativeButton("Cancel",null).show()),lpm(-1,-2,16,0,16,8));
        settings.addView(row(ToxiIcons.TUNE,"Position alignment","Choose an edge or drag anywhere",null,v->new AlertDialog.Builder(this).setTitle("Island alignment").setItems(new String[]{"Left edge","Center","Right edge"},(d,w)->{if(dynamicIsland!=null)dynamicIsland.align(w);else p.edit().putFloat("x",w==0?0f:w==1?0.5f:1f).apply();}).show()),lpm(-1,-2,16,0,16,8));
        settings.addView(islandSwitch(p,"Show file operations","show_operations",true),lpm(-1,-2,16,0,16,8));
        settings.addView(islandSwitch(p,"Show video playback","show_playback",true),lpm(-1,-2,16,0,16,8));
        settings.addView(islandSwitch(p,"Show storage scanning","show_scan",true),lpm(-1,-2,16,0,16,8));
        settings.addView(islandSwitch(p,"Haptic feedback","haptics",true),lpm(-1,-2,16,0,16,8));
        settings.addView(islandSwitch(p,"Automatic activity expansion","auto_expand",true),lpm(-1,-2,16,0,16,8));
        settings.addView(row(ToxiIcons.REFRESH,"Reset Dynamic Island preferences","Restore its default appearance and position",null,v->new AlertDialog.Builder(this).setTitle("Reset Dynamic Island?").setMessage("Only Dynamic Island preferences will be reset.").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{p.edit().clear().apply();if(dynamicIsland!=null){dynamicIsland.setEnabledFromSettings();dynamicIsland.refreshStyle();}buildSettings();}).show()),lpm(-1,-2,16,0,16,8));
    }
    View islandSwitch(android.content.SharedPreferences p,String title,String key,boolean def){Switch sw=new Switch(this);sw.setChecked(p.getBoolean(key,def));sw.setOnCheckedChangeListener((b,on)->{p.edit().putBoolean(key,on).apply();if(dynamicIsland!=null)dynamicIsland.refreshStyle();});return row(ToxiIcons.SPARKLE,title,null,sw,v->sw.toggle());}
    void addIslandSlider(android.content.SharedPreferences p,String title,String key,int min,int max,int def){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(ToxiVisualSystem.dp(15),ToxiVisualSystem.dp(12),ToxiVisualSystem.dp(15),ToxiVisualSystem.dp(10));box.setBackground(ToxiVisualSystem.surface(14,false));TextView label=ToxiVisualSystem.text(this,title+" · "+p.getInt(key,def),13,ToxiVisualSystem.TEXT_PRIMARY,true);box.addView(label);android.widget.SeekBar seek=new android.widget.SeekBar(this);seek.setProgressTintList(ColorStateList.valueOf(ToxiVisualSystem.ACCENT));seek.setProgressBackgroundTintList(ColorStateList.valueOf(ToxiThemeSystem.EDGES[ToxiVisualSystem.THEME_INDEX]));seek.setMax(max-min);seek.setProgress(Math.max(0,Math.min(max-min,p.getInt(key,def)-min)));seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(android.widget.SeekBar b){}public void onStopTrackingTouch(android.widget.SeekBar b){}public void onProgressChanged(android.widget.SeekBar b,int value,boolean user){if(!user)return;int n=min+value;p.edit().putInt(key,n).apply();label.setText(title+" · "+n);if(dynamicIsland!=null)dynamicIsland.refreshStyle();}});box.addView(seek);settings.addView(box,lpm(-1,-2,16,0,16,8));}

    void buildSettings() {
        settings.removeAllViews();
        LinearLayout settingsMasthead = new LinearLayout(this);
        settingsMasthead.setGravity(Gravity.CENTER_VERTICAL);
        settingsMasthead.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(8));
        ImageView settingsMark = new ImageView(this);
        settingsMark.setImageResource(R.drawable.ic_toxifiles_brand_mark);
        settingsMasthead.addView(settingsMark, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(38), ToxiVisualSystem.dp(38)));
        LinearLayout settingsTitleBlock = new LinearLayout(this);
        settingsTitleBlock.setOrientation(LinearLayout.VERTICAL);
        settingsTitleBlock.setPadding(ToxiVisualSystem.dp(11), 0, 0, 0);
        TextView t = ToxiVisualSystem.text(this, "Control center", 25, ToxiVisualSystem.TEXT_PRIMARY, true);
        settingsTitleBlock.addView(t);
        TextView settingsEyebrow = ToxiVisualSystem.text(this, "PERSONALIZE YOUR WORKSPACE", 9.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        settingsEyebrow.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams eyebrowLp = new LinearLayout.LayoutParams(-2, -2);
        eyebrowLp.topMargin = ToxiVisualSystem.dp(4);
        settingsTitleBlock.addView(settingsEyebrow, eyebrowLp);
        settingsMasthead.addView(settingsTitleBlock, new LinearLayout.LayoutParams(0, -2, 1f));
        settings.addView(settingsMasthead);
        t.setOnClickListener(v -> {
            int taps = ToxiVisualSystem.PREFERENCES.getInt("developer_unlock_taps", 0) + 1;
            if (taps >= 7) {
                ToxiVisualSystem.PREFERENCES.edit().putBoolean("developer_options_unlocked", true).putInt("developer_unlock_taps", 0).apply();
                ToxiVisualSystem.toast(this, "Developer options unlocked");
                buildSettings();
            } else {
                ToxiVisualSystem.PREFERENCES.edit().putInt("developer_unlock_taps", taps).apply();
                if (taps >= 4) ToxiVisualSystem.toast(this, (7 - taps) + " more taps to unlock developer options");
            }
        });
        TextView intro = ToxiVisualSystem.text(this, "Shape the way your files are organized.", 13, ToxiVisualSystem.TEXT_SECONDARY, false);
        intro.setPadding(ToxiVisualSystem.dp(22), 0, ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(10));
        settings.addView(intro);
        TextView hint = ToxiVisualSystem.text(this, "Select a category to reveal its controls.", 11.5f, ToxiVisualSystem.ACCENT, false);
        hint.setPadding(ToxiVisualSystem.dp(22), 0, ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2));
        settings.addView(hint);
        settings.addView(section("Appearance"));
        settings.addView(row(ToxiIcons.PALETTE, "Interface identity", ToxiVisualSystem.THEME_NAMES[ToxiVisualSystem.THEME_INDEX], null, v -> openThemeScreen()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(ToxiIcons.INFO, "Font style", new String[]{"System Sans", "Rounded", "Serif", "Monospace"}[ToxiVisualSystem.PREFERENCES.getInt("font_family", 0)], null, v -> fontDialog()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Dynamic Island"));
        addDynamicIslandSettings();

        settings.addView(section("Files & Folders"));
        settings.addView(switchRow(ToxiIcons.EYE, "Show hidden files", "Files and folders starting with a dot", "hidden", false, () -> filesView.reload()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(switchRow(ToxiIcons.OPEN, "Open files in ToxiFiles", "Built-in viewer for photos, video, audio, PDF and text", "inapp", true, null), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(switchRow(ToxiIcons.TRASH, "Confirm before deleting", null, "confirm", true, null), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(ToxiIcons.SORT, "Default sort", null, null, v -> filesView.sortDialog()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Storage Access"));
        settings.addView(row(ToxiIcons.DATABASE, "Storage permission", hasAccess() ? "Allowed · manage access" : "Not allowed · tap to fix", null, v -> {
            if (!hasAccess()) { showStorageAccess(); return; }
            try {
                Intent manage = Build.VERSION.SDK_INT >= 30
                        ? new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(manage);
            } catch (Exception e) { ToxiVisualSystem.toast(this, "Open Android Settings > Apps > ToxiFiles > Permissions"); }
        }), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(ToxiIcons.REFRESH, "Rescan storage", "Refresh categories and search", null, v -> { Indexer.I.dirty = true; startIndex(); ToxiVisualSystem.toast(this, "Scanning\u2026"); }), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Recycle Bin"));
        settings.addView(switchRow(ToxiIcons.TRASH, "Move deleted files to Recycle Bin", "Keep deleted files here before permanent removal", "recyclebin", true, null), lpm(-1, -2, 16, 0, 16, 8));
        int binCount = RecycleBin.list(this).size();
        settings.addView(row(ToxiIcons.TRASH, "Recycle Bin", binCount == 0 ? "Empty" : binCount + " item" + (binCount == 1 ? "" : "s") + " \u2022 auto-removed after " + ToxiVisualSystem.PREFERENCES.getInt("recyclebin_days", 30) + " days", null, v -> showRecycleBin()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(ToxiIcons.TRASH, "Recycle Bin retention", ToxiVisualSystem.PREFERENCES.getInt("recyclebin_days", 30) + " days", null, v -> retentionDialog()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("App Lock"));
        settings.addView(row(ToxiIcons.LOCK, AppLock.hasPin(this) ? "Change App Lock PIN" : "Set App Lock PIN", "Biometric unlock is offered first on supported devices", null, v -> setAppLockPin()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(switchRow(ToxiIcons.SHIELD, "Require unlock to open app", "Re-locks after you leave the app or the screen turns off", "applock_on", false, () -> {
            if (ToxiVisualSystem.booleanPreference("applock_on", false) && !AppLock.hasPin(this)) { ToxiVisualSystem.toast(this, "Set an App Lock PIN first"); ToxiVisualSystem.setBooleanPreference("applock_on", false); buildSettings(); }
        }), lpm(-1, -2, 16, 0, 16, 8));

        String defFolder = ToxiVisualSystem.PREFERENCES.getString("default_folder", null);
        settings.addView(row(ToxiIcons.FOLDER, "Starting folder", defFolder != null ? defFolder : "Internal storage root (choose a folder in Files to set another)", null, v -> {
            if (defFolder == null) { ToxiVisualSystem.toast(this, "Open a folder in Files, then choose Set as default start folder from its action menu"); return; }
            new AlertDialog.Builder(this).setTitle("Reset default folder?").setMessage("Will start from Internal storage root again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset", (d, w) -> { ToxiVisualSystem.PREFERENCES.edit().remove("default_folder").apply(); buildSettings(); }).show();
        }), lpm(-1, -2, 16, 0, 16, 8));


        settings.addView(section("Secure Vault"));
        settings.addView(row(ToxiIcons.LOCK, Vault.hasPin(this) ? "Change Vault PIN" : "Set Vault PIN", "AES-256 encrypted, key held in Android Keystore", null, v -> setVaultPin()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(ToxiIcons.FOLDER, "Open Vault", Vault.list(this).size() + " item" + (Vault.list(this).size() == 1 ? "" : "s"), null, v -> promptVaultPin()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Wi-Fi Transfer"));
        settings.addView(row(ToxiIcons.WIFI, "Receive via Wi-Fi", "Both devices must be on the same Wi-Fi network", null, v -> receiveViaWifi()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(ToxiIcons.WIFI, "Send via Wi-Fi", "Select files in Files, then choose Send via Wi-Fi from the file action menu", null, v -> ToxiVisualSystem.toast(this, "Select files in Files first, then choose Send via Wi-Fi from the file action menu")), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("SD Card & USB Storage"));
        settings.addView(row(ToxiIcons.CLOUD, "Browse other storage", "Browse SD cards, USB drives or cloud documents; import files and copy files out", null, v -> openOtherStorage()), lpm(-1, -2, 16, 0, 16, 8));


        if (ToxiVisualSystem.booleanPreference("developer_options_unlocked", false)) {
            settings.addView(section("Developer options"));
            settings.addView(row(ToxiIcons.ANALYTICS, "Usage & diagnostics", "Local app version, Android details, storage permission and crash status", null, v -> showUsageDiagnostics()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(ToxiIcons.ANALYTICS, "Developer Studio", "12 offline tools for source, APK, database, binary and asset inspection", null, v -> { if (ToxiVisualSystem.booleanPreference("developer_options_unlocked", false)) startActivity(new Intent(this, DeveloperStudioActivity.class)); }), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(section("Advanced tools"));
            settings.addView(switchRow(ToxiIcons.SPARKLE, "Background blur", "Can affect performance on some devices", "blur", false, () -> bg.applyBlur()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(section("Storage Tools"));
            settings.addView(row(ToxiIcons.DATABASE, "Archive manager", "List archive files; inspect ZIP contents and extract ZIP files. Other formats can be listed but are not extracted by this build.", null, v -> openCategory(4)), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(ToxiIcons.ANALYTICS, "Find duplicate files", "Matches by size, then confirms with a checksum", null, v -> openDuplicates()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(ToxiIcons.DATABASE, "Find empty folders", "Folders with nothing inside them", null, v -> openCleanup()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(ToxiIcons.ANALYTICS, "Storage analyzer", "Count files and estimate space by category; shows largest files without deleting anything", null, v -> openStorageAnalyzer()), lpm(-1, -2, 16, 0, 16, 8));

            settings.addView(section("Privacy & Diagnostics"));
            settings.addView(row(ToxiIcons.SHIELD, "Privacy & network information", "Review app permissions and what offline mode means", null, v -> showPrivacyAudit()), lpm(-1, -2, 16, 0, 16, 8));
            // Former label: Crash logs & diagnostics.
            settings.addView(row(ToxiIcons.ANALYTICS, "Crash reports & diagnostics", CrashLogger.hasReport(this) ? "A crash report is saved on this device" : "Save crash details automatically if the app closes unexpectedly", null, v -> showCrashLogOptions()), lpm(-1, -2, 16, 0, 16, 8));

            settings.addView(row(ToxiIcons.REFRESH, "Rescan storage", "Refresh the index if file counts look outdated", null, v -> { Indexer.I.dirty = true; startIndex(); ToxiVisualSystem.toast(this, "Scanning\u2026"); }), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(ToxiIcons.ANALYTICS, "Verbose diagnostics", "Keep additional local diagnostic details for troubleshooting", "dev_verbose", false, null), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(ToxiIcons.VIBRATE, "Haptic feedback", "Gentle vibration on selection", "haptic", true, null), lpm(-1, -2, 16, 0, 16, 8));
            TextView backupHeading = ToxiVisualSystem.text(this, "Backup & Restore", 14, ToxiVisualSystem.ACCENT, true);
            backupHeading.setPadding(ToxiVisualSystem.dp(8), ToxiVisualSystem.dp(12), 0, ToxiVisualSystem.dp(2));
            settings.addView(backupHeading);
            settings.addView(row(ToxiIcons.DOWNLOAD, "Export settings", "Save theme and preferences to a file you can share or keep", null, v -> exportSettings()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(ToxiIcons.CLOUD, "Import settings", "Restore from a previously exported file", null, v -> importSettingsPick()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(ToxiIcons.VIBRATE, "Reduce motion", "Disable decorative transition animations", "reduce_motion", true, null), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(ToxiIcons.INFO, "Show debug toasts", "Show extra status feedback for developer troubleshooting", "dev_debug_toasts", false, null), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(ToxiIcons.REFRESH, "Enable diagnostic logging", "Keep additional troubleshooting events locally", "dev_logging", false, null), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(ToxiIcons.ARCHIVE, "Built-in tools", "Inspect the file manager's integrated viewers, players, editor and extractor", null, v -> showBuiltInTools()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(ToxiIcons.REFRESH, "Reset developer options", "Hide this section again", null, v -> { ToxiVisualSystem.PREFERENCES.edit().putBoolean("developer_options_unlocked", false).apply(); buildSettings(); }), lpm(-1, -2, 16, 0, 16, 8));
        }

        // Keep About at the end of Settings, in its own collapsible section.
        // This prevents it from sitting above Storage Access or SD Card & USB Storage.
        settings.addView(section("About"));
        settings.addView(row(ToxiIcons.INFO, "About ToxiFiles", versionName(), null, v -> showAbout()), lpm(-1, -2, 16, 0, 16, 12));

        // Present settings as clear, collapsible groups instead of one long wall of options.
        // Appearance starts open; every other section is closed until the user selects it.
        boolean inSection = false;
        boolean appearanceSection = false;
        for (int i = 0; i < settings.getChildCount(); i++) {
            View child = settings.getChildAt(i);
            if ("settings_section".equals(child.getTag())) {
                inSection = true;
                appearanceSection = child instanceof TextView && ((TextView) child).getText().toString().contains("APPEARANCE");
                child.setVisibility(View.VISIBLE);
                if (child instanceof TextView) {
                    TextView heading = (TextView) child;
                    String label = heading.getText().toString().replace("+  ", "").replace("-  ", "");
                    heading.setText((appearanceSection ? "-  " : "+  ") + label);
                    heading.setContentDescription(label + (appearanceSection ? ", expanded settings section. Double tap to collapse." : ", collapsed settings section. Double tap to expand."));
                }
            } else if (inSection) {
                child.setVisibility(appearanceSection ? View.VISIBLE : View.GONE);
            }
        }
    }

    // ---------------- STORAGE ANALYZER ----------------
    void openStorageAnalyzer() {
        if (!hasAccess()) { showStorageAccess(); return; }
        ToxiVisualSystem.toast(this, "Analyzing storage…");
        io.execute(() -> {
            OperationCancellation.Token operationToken = cancellation.begin();
            final String result;
            try {
                File rootDir = Environment.getExternalStorageDirectory();
                long[] totals = new long[6]; // images, video, audio, documents, archives, other
                long[] counts = new long[6];
                List<StorageIndexEngine.Snapshot> largest = new ArrayList<>();
                long[] totalBytes = {0};
                int[] totalFiles = {0};
                StorageIndexEngine.Result scan = StorageIndexEngine.scan(rootDir, 60,
                        StorageIndexEngine.MAX_INDEX_ENTRIES, true, true, snapshot -> {
                            if (snapshot.directory) return;
                            long size = snapshot.length;
                            totalBytes[0] += size;
                            totalFiles[0]++;
                            Entry entry = new Entry(snapshot.file, false, snapshot.modifiedAt, size);
                            int category = Math.min(5, Indexer.cat(entry.kind));
                            totals[category] += size;
                            counts[category]++;
                            if (largest.size() < 15) {
                                largest.add(snapshot);
                            } else {
                                int smallest = 0;
                                for (int i = 1; i < largest.size(); i++) {
                                    if (largest.get(i).length < largest.get(smallest).length) smallest = i;
                                }
                                if (size > largest.get(smallest).length) largest.set(smallest, snapshot);
                            }
                        });
                largest.sort((a, b) -> Long.compare(b.length, a.length));
                String[] labels = {"Images", "Videos", "Audio", "Documents", "Archives", "Other files"};
                StringBuilder report = new StringBuilder();
                report.append("Scanned internal shared storage\n\nFiles: ").append(totalFiles[0])
                        .append("\nEstimated file size total: ").append(formatBytes(totalBytes[0])).append("\n\nBy category:\n");
                for (int i = 0; i < labels.length; i++) {
                    report.append(labels[i]).append(": ").append(counts[i]).append(" files • ")
                            .append(formatBytes(totals[i])).append("\n");
                }
                report.append("\nLargest files found (up to 15):\n");
                int count = Math.min(15, largest.size());
                for (int i = 0; i < count; i++) {
                    StorageIndexEngine.Snapshot item = largest.get(i);
                    report.append("\n").append(formatBytes(item.length)).append(" — ").append(item.file.getAbsolutePath());
                }
                if (count == 0) report.append("None found.");
                if (scan.incomplete) {
                    report.append("\n\nNote: Scan was incomplete or reached the safety limit. Results are estimates.");
                }
                result = report.toString();
            } catch (Exception failure) {
                String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    ToxiVisualSystem.toast(this, "Storage analysis failed: " + detail);
                });
                return;
            } finally {
                cancellation.finish(operationToken);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this).setTitle("Storage analysis")
                        .setMessage(result).setPositiveButton("Done", null)
                        .setNeutralButton("Share report", (d, w) -> {
                            try {
                                Intent share = new Intent(Intent.ACTION_SEND);
                                share.setType("text/plain"); share.putExtra(Intent.EXTRA_TEXT, result);
                                startActivity(Intent.createChooser(share, "Share storage report"));
                            } catch (Exception ex) { ToxiVisualSystem.toast(this, "Unable to share report"); }
                        }).show();
            });
        });
    }

    String formatBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", b / (1024.0 * 1024));
        return String.format(java.util.Locale.getDefault(), "%.2f GB", b / (1024.0 * 1024 * 1024));
    }

    void showPrivacyAudit() {
        String report = "OFFLINE & PRIVACY REVIEW\n\n"
                + "\u2022 File browsing and built-in viewing operate on local files.\n"
                + "\u2022 No account or cloud-sync feature is provided by this app.\n"
                + "\u2022 Wi-Fi transfer is a local-network feature; it requires network permission and both devices on a reachable network. It is not internet cloud storage.\n"
                + "\u2022 All-files access gives broad access to shared storage. Disable it in Android settings when you no longer need it.\n"
                + "\u2022 App Lock protects opening this app; Vault behavior and its limits are described separately in the Vault screen.\n"
                + "\u2022 Settings export creates a readable JSON file. Do not share it if it contains preferences you consider private.\n\n"
                + "This is an in-app permission/feature summary, not an independent network-traffic audit. To verify network behavior, inspect the source and test traffic on the device.";
        new AlertDialog.Builder(this).setTitle("Privacy & network information")
                .setMessage(report).setPositiveButton("OK", null).show();
    }

    // ---------------- APP LOCK ----------------
    void setAppLockPin() {
        final EditText in = pinField();
        new AlertDialog.Builder(this).setTitle(AppLock.hasPin(this) ? "Change App Lock PIN" : "Set App Lock PIN")
                .setView(wrapField(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String p = in.getText().toString().trim();
                    if (p.length() < 4) { ToxiVisualSystem.toast(this, "Use at least 4 digits"); return; }
                    AppLock.setPin(this, p);
                    ToxiVisualSystem.toast(this, "App Lock PIN saved");
                    buildSettings();
                }).show();
    }

    /** Blocking, non-cancelable lock screen shown on launch/resume when App Lock is on. */
    void showAppLock() {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        final android.app.Dialog dlg = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.setCancelable(false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ToxiVisualSystem.CANVAS);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(ToxiVisualSystem.dp(32), 0, ToxiVisualSystem.dp(32), 0);
        col.addView(ToxiVisualSystem.text(this, "ToxiFiles is locked", 18, ToxiVisualSystem.TEXT_PRIMARY, true), lpm(-2, -2, 0, 0, 0, 16));
        final EditText in = pinField();
        col.addView(wrapField(in), new LinearLayout.LayoutParams(-1, -2));
        TextView unlockBtn = ToxiVisualSystem.text(this, "Unlock", 16, ToxiVisualSystem.ACCENT, true);
        unlockBtn.setGravity(Gravity.CENTER);
        unlockBtn.setBackground(ToxiVisualSystem.surface(18, false));
        unlockBtn.setPadding(0, ToxiVisualSystem.dp(14), 0, ToxiVisualSystem.dp(14));
        ToxiVisualSystem.interactive(unlockBtn);
        unlockBtn.setOnClickListener(v -> {
            if (AppLock.checkPin(this, in.getText().toString().trim())) {
                unlocked = true; getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); dlg.dismiss();
            } else {
                long remaining = AppLock.lockoutRemainingMs(this);
                ToxiVisualSystem.toast(this, remaining > 0 ? "Too many attempts. Try again in " + Math.max(1, remaining / 1000) + " seconds" : "Wrong PIN");
            }
        });
        col.addView(unlockBtn, lpm(-1, -2, 0, 20, 0, 0));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        dlg.setContentView(root);
        dlg.setOnKeyListener((d, keyCode, event) -> keyCode == android.view.KeyEvent.KEYCODE_BACK);
        dlg.show();
        if (Build.VERSION.SDK_INT >= 28) tryBiometric(dlg);
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.P)
    private javax.crypto.Cipher createBiometricUnlockCipher() throws Exception {
        java.security.KeyStore keyStore = java.security.KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(BIOMETRIC_UNLOCK_KEY_ALIAS)) {
            try {
                java.security.Key key = keyStore.getKey(BIOMETRIC_UNLOCK_KEY_ALIAS, null);
                if (key instanceof javax.crypto.SecretKey) {
                    javax.crypto.Cipher existing = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                    existing.init(javax.crypto.Cipher.ENCRYPT_MODE, (javax.crypto.SecretKey) key);
                    return existing;
                }
                keyStore.deleteEntry(BIOMETRIC_UNLOCK_KEY_ALIAS);
            } catch (android.security.keystore.KeyPermanentlyInvalidatedException invalidated) {
                keyStore.deleteEntry(BIOMETRIC_UNLOCK_KEY_ALIAS);
            }
        }
        android.security.keystore.KeyGenParameterSpec.Builder builder =
                new android.security.keystore.KeyGenParameterSpec.Builder(
                        BIOMETRIC_UNLOCK_KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT)
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true)
                        .setInvalidatedByBiometricEnrollment(true);
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, android.security.keystore.KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(-1);
        }
        javax.crypto.KeyGenerator generator = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(builder.build());
        javax.crypto.SecretKey key = generator.generateKey();
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
        return cipher;
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.P)
    void tryBiometric(final android.app.Dialog dlg) {
        try {
            io.execute(() -> {
                final javax.crypto.Cipher cipher;
                try {
                    cipher = createBiometricUnlockCipher();
                } catch (Exception keystoreFailure) {
                    android.util.Log.w("ToxiFiles", "Biometric Keystore unavailable; PIN remains available", keystoreFailure);
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !dlg.isShowing()) return;
                    try {
                        android.hardware.biometrics.BiometricPrompt prompt = new android.hardware.biometrics.BiometricPrompt.Builder(this)
                                .setTitle("Unlock ToxiFiles")
                                .setNegativeButton("Use PIN", getMainExecutor(), (d, w) -> { })
                                .build();
                        prompt.authenticate(new android.hardware.biometrics.BiometricPrompt.CryptoObject(cipher),
                                new android.os.CancellationSignal(), getMainExecutor(),
                                new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                                    @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                                        if (isFinishing() || isDestroyed() || !dlg.isShowing()) return;
                                        byte[] challenge = new byte[32];
                                        byte[] proof = null;
                                        try {
                                            android.hardware.biometrics.BiometricPrompt.CryptoObject authenticated = result.getCryptoObject();
                                            if (authenticated == null || authenticated.getCipher() == null) {
                                                throw new java.security.GeneralSecurityException("Biometric cryptographic proof is unavailable");
                                            }
                                            new java.security.SecureRandom().nextBytes(challenge);
                                            proof = authenticated.getCipher().doFinal(challenge);
                                            if (proof == null || proof.length <= challenge.length) {
                                                throw new java.security.GeneralSecurityException("Biometric cryptographic proof is invalid");
                                            }
                                            unlocked = true;
                                            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                                            dlg.dismiss();
                                        } catch (Exception verificationFailure) {
                                            android.util.Log.w("ToxiFiles", "Biometric Keystore verification failed; PIN remains available", verificationFailure);
                                            ToxiVisualSystem.toast(MainActivity.this, "Biometric verification failed. Use PIN.");
                                        } finally {
                                            java.util.Arrays.fill(challenge, (byte) 0);
                                            if (proof != null) java.util.Arrays.fill(proof, (byte) 0);
                                        }
                                    }
                                });
                    } catch (Exception promptFailure) {
                        android.util.Log.w("ToxiFiles", "Biometric prompt unavailable; PIN remains available", promptFailure);
                    }
                });
            });
        } catch (RuntimeException executorFailure) {
            android.util.Log.w("ToxiFiles", "Biometric setup unavailable; PIN remains available", executorFailure);
        }
    }

    // ---------------- DUPLICATE FINDER ----------------
    void openDuplicates() {
        if (!Indexer.I.ready) { ToxiVisualSystem.toast(this, "Still scanning storage — try again in a moment"); return; }
        ToxiVisualSystem.toast(this, "Scanning for duplicates…");
        io.execute(() -> {
            OperationCancellation.Token operationToken = this.cancellation.begin();
            try {
                final List<DupFinder.Group> groups = DupFinder.find(Indexer.I.all);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    buildDuplicates(groups);
                    showOverlay(dupScroll);
                });
            } catch (IOException scanFailure) {
                if ("Operation cancelled".equals(scanFailure.getMessage())) return;
                final String detail = scanFailure.getMessage();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    ToxiVisualSystem.toast(this, "Duplicate scan failed: "
                            + (detail == null || detail.trim().isEmpty() ? "a file could not be read" : detail));
                });
            } finally {
                this.cancellation.finish(operationToken);
            }
        });
    }

    void buildDuplicates(final List<DupFinder.Group> groups) {
        dup.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(ToxiVisualSystem.dp(8), 0, ToxiVisualSystem.dp(8), 0);
        ImageView bk = ToxiVisualSystem.iconButton(this, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        bk.setContentDescription("Back");
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        hd.addView(ToxiVisualSystem.text(this, "Duplicate Files", 22, ToxiVisualSystem.TEXT_PRIMARY, true), new LinearLayout.LayoutParams(0, -2, 1f));
        hd.setMinimumHeight(ToxiVisualSystem.dp(56));
        dup.addView(hd, new LinearLayout.LayoutParams(-1, -2));

        if (groups.isEmpty()) {
            TextView w = ToxiVisualSystem.text(this, "No exact duplicates found", 14, ToxiVisualSystem.TEXT_SECONDARY, false);
            w.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            dup.addView(w);
            return;
        }
        long reclaimable = 0;
        for (DupFinder.Group g : groups) reclaimable += g.size * (g.files.size() - 1);
        TextView sum = ToxiVisualSystem.text(this, groups.size() + " duplicate group" + (groups.size() == 1 ? "" : "s") + " \u2014 up to " + ToxiVisualSystem.formatSize(reclaimable) + " reclaimable", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        sum.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(6));
        dup.addView(sum);

        TextView bulk = ToxiVisualSystem.text(this, "Move all but the oldest copy to Recycle Bin", 14, ToxiVisualSystem.ACCENT, true);
        bulk.setBackground(ToxiVisualSystem.surface(18, false));
        bulk.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(12));
        bulk.setGravity(Gravity.CENTER);
        ToxiVisualSystem.interactive(bulk);
        bulk.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Move duplicates to Recycle Bin?")
                .setMessage("Keeps the oldest file in each group, moves the rest to Recycle Bin \u2014 they can still be restored from there.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move", (d, w) -> {
                    List<File> victims = new ArrayList<>();
                    for (DupFinder.Group g : groups) {
                        File keep = g.files.get(0);
                        for (File f : g.files) if (f.lastModified() < keep.lastModified()) keep = f;
                        for (File f : g.files) if (f != keep) victims.add(f);
                    }
                    RecycleBin.moveToBin(this, victims, msg -> { ToxiVisualSystem.toast(this, msg); afterOps(); openDuplicates(); });
                }).show());
        dup.addView(bulk, lpm(-1, -2, 16, 4, 16, 14));

        for (final DupFinder.Group g : groups) {
            TextView gh = ToxiVisualSystem.text(this, g.files.size() + " copies \u2022 " + ToxiVisualSystem.formatSize(g.size) + " each", 12, ToxiVisualSystem.TEXT_SECONDARY, true);
            gh.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(10), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2));
            dup.addView(gh);
            for (final File f : g.files) {
                Entry e = new Entry(f);
                View rv = ToxiFileRow.create(this);
                ToxiFileRow.Holder h = (ToxiFileRow.Holder) rv.getTag();
                ToxiFileRow.bind(h, e, false, false, f.getParentFile() == null ? "" : f.getParentFile().getAbsolutePath());
                View.OnClickListener act = v -> new AlertDialog.Builder(this).setTitle("Move to Recycle Bin?").setMessage(f.getAbsolutePath())
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Move", (d, w) -> RecycleBin.moveToBin(this, java.util.Collections.singletonList(f), msg -> { ToxiVisualSystem.toast(this, msg); afterOps(); openDuplicates(); }))
                        .show();
                rv.setOnClickListener(act);
                h.more.setOnClickListener(act);
                dup.addView(rv, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    void openCleanup() {
        if (!Indexer.I.ready) { ToxiVisualSystem.toast(this, "Still scanning storage \u2014 try again in a moment"); return; }
        ToxiVisualSystem.toast(this, "Scanning for empty folders\u2026");
        io.execute(() -> {
            final List<File> empties = new ArrayList<>();
            for (Entry e : Indexer.I.all) {
                if (e.dir) { File[] kids = e.f.listFiles(); if (kids != null && kids.length == 0) empties.add(e.f); }
            }
            runOnUiThread(() -> { buildEmptyFolders(empties); showOverlay(dupScroll); });
        });
    }

    void buildEmptyFolders(final List<File> empties) {
        dup.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(ToxiVisualSystem.dp(8), 0, ToxiVisualSystem.dp(8), 0);
        ImageView bk = ToxiVisualSystem.iconButton(this, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        bk.setContentDescription("Back");
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        hd.addView(ToxiVisualSystem.text(this, "Empty Folders", 22, ToxiVisualSystem.TEXT_PRIMARY, true), new LinearLayout.LayoutParams(0, -2, 1f));
        hd.setMinimumHeight(ToxiVisualSystem.dp(56));
        dup.addView(hd, new LinearLayout.LayoutParams(-1, -2));

        if (empties.isEmpty()) {
            TextView w = ToxiVisualSystem.text(this, "No empty folders found", 14, ToxiVisualSystem.TEXT_SECONDARY, false);
            w.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            dup.addView(w);
            return;
        }
        TextView sum = ToxiVisualSystem.text(this, empties.size() + " empty folder" + (empties.size() == 1 ? "" : "s"), 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        sum.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(6));
        dup.addView(sum);

        TextView bulk = ToxiVisualSystem.text(this, "Delete all empty folders", 14, ToxiVisualSystem.ACCENT, true);
        bulk.setBackground(ToxiVisualSystem.surface(18, false));
        bulk.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(12));
        bulk.setGravity(Gravity.CENTER);
        ToxiVisualSystem.interactive(bulk);
        bulk.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete " + empties.size() + " empty folders?")
                .setMessage("They contain nothing, so this goes straight to Recycle Bin, same as any other delete.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> RecycleBin.moveToBin(this, empties, msg -> { ToxiVisualSystem.toast(this, msg); afterOps(); openCleanup(); }))
                .show());
        dup.addView(bulk, lpm(-1, -2, 16, 4, 16, 14));

        for (final File f : empties) {
            Entry e = new Entry(f);
            View rv = ToxiFileRow.create(this);
            ToxiFileRow.Holder h = (ToxiFileRow.Holder) rv.getTag();
            ToxiFileRow.bind(h, e, false, false, f.getParentFile() == null ? "" : f.getParentFile().getAbsolutePath());
            View.OnClickListener act = v -> RecycleBin.moveToBin(this, java.util.Collections.singletonList(f), msg -> { ToxiVisualSystem.toast(this, msg); afterOps(); openCleanup(); });
            rv.setOnClickListener(act);
            h.more.setOnClickListener(act);
            dup.addView(rv, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    // ---------------- VAULT ----------------
    EditText pinField() {
        EditText in = new EditText(this);
        ToxiMotion.watchFocus(in);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        in.setHint("4+ digit PIN");
        return in;
    }

    View wrapField(View v) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(ToxiVisualSystem.dp(22), ToxiVisualSystem.dp(8), ToxiVisualSystem.dp(22), 0);
        f.addView(v, new FrameLayout.LayoutParams(-1, -2));
        return f;
    }

    void setVaultPin() {
        final EditText in = pinField();
        new AlertDialog.Builder(this).setTitle(Vault.hasPin(this) ? "Change Vault PIN" : "Set Vault PIN")
                .setView(wrapField(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String p = in.getText().toString().trim();
                    if (p.length() < 4) { ToxiVisualSystem.toast(this, "Use at least 4 digits"); return; }
                    Vault.setPin(this, p);
                    ToxiVisualSystem.toast(this, "Vault PIN saved");
                    buildSettings();
                }).show();
    }

    void promptVaultPin() {
        if (!Vault.hasPin(this)) { ToxiVisualSystem.toast(this, "Set a Vault PIN first"); return; }
        final EditText in = pinField();
        new AlertDialog.Builder(this).setTitle("Enter Vault PIN")
                .setView(wrapField(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (Vault.checkPin(this, in.getText().toString().trim())) { buildVault(); showOverlay(vaultScroll); }
                    else {
                        long remaining = Vault.sp(this).getLong("blocked_until", 0L) - System.currentTimeMillis();
                        ToxiVisualSystem.toast(this, remaining > 0 ? "Too many attempts. Try again in " + Math.max(1, remaining / 1000) + " seconds" : "Wrong PIN");
                    }
                }).show();
    }

    void buildVault() {
        vault.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(ToxiVisualSystem.dp(8), 0, ToxiVisualSystem.dp(8), 0);
        ImageView bk = ToxiVisualSystem.iconButton(this, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        bk.setContentDescription("Back");
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        hd.addView(ToxiVisualSystem.text(this, "Vault", 24, ToxiVisualSystem.TEXT_PRIMARY, true), new LinearLayout.LayoutParams(0, -2, 1f));
        hd.addView(new View(this), new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        hd.setMinimumHeight(ToxiVisualSystem.dp(56));
        vault.addView(hd, new LinearLayout.LayoutParams(-1, -2));

        TextView note = ToxiVisualSystem.text(this, "Encrypted with AES-256 (key stored in Android's hardware Keystore, not derived from your PIN). The PIN gates this screen; the encryption protects the file bytes even if someone pulls them off the device directly.", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        note.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(10));
        vault.addView(note);

        List<Vault.Item> items = Vault.list(this);
        if (items.isEmpty()) {
            TextView w = ToxiVisualSystem.text(this, "Vault is empty", 14, ToxiVisualSystem.TEXT_SECONDARY, false);
            w.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            vault.addView(w);
        } else {
            for (final Vault.Item it : items) {
                Entry e = new Entry(it.file);
                View rv = ToxiFileRow.create(this);
                ToxiFileRow.Holder h = (ToxiFileRow.Holder) rv.getTag();
                ToxiFileRow.bind(h, e, false, false, "from " + it.originalParent.getName());
                rv.setOnClickListener(v -> vaultItemMenu(it));
                h.more.setOnClickListener(v -> vaultItemMenu(it));
                vault.addView(rv, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    void vaultItemMenu(final Vault.Item it) {
        String[] opts = {"Move out of Vault", "Delete forever"};
        new AlertDialog.Builder(this).setTitle(it.file.getName()).setItems(opts, (d, w) -> {
            if (w == 0) Vault.restoreOut(this, it, msg -> { ToxiVisualSystem.toast(this, msg); afterOps(); buildVault(); });
            else new AlertDialog.Builder(this).setTitle("Delete forever?").setMessage("This can't be undone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d2, w2) -> Vault.deleteForever(this, it, msg -> { ToxiVisualSystem.toast(this, msg); buildVault(); }))
                    .show();
        }).show();
    }

    // ---------------- WI-FI TRANSFER ----------------
    void receiveViaWifi() {
        final AlertDialog waitDlg = new AlertDialog.Builder(this).setTitle("Waiting for sender\u2026")
                .setMessage("Starting\u2026")
                .setNegativeButton("Cancel", (d, w) -> LanTransfer.cancel())
                .setCancelable(false).show();
        File destDir = new File(Environment.getExternalStorageDirectory(), "Download/LanReceived");
        LanTransfer.receive(destDir, new LanTransfer.Listener() {
            @Override public void onWaiting(String ip, int port, String pairingCode) {
                runOnUiThread(() -> waitDlg.setMessage("On the sending device, open the file action menu, choose Send via Wi-Fi, then enter:\n\n" + ip + ":" + port + "\nPairing code: " + pairingCode + "\n\nKeep this code private. Files are encrypted during transfer."));
            }
            @Override public void onIncoming(String fromIp, int count, long totalBytes, List<String> names, Runnable accept, Runnable reject) {
                runOnUiThread(() -> {
                    if (waitDlg.isShowing()) waitDlg.dismiss();
                    StringBuilder preview = new StringBuilder();
                    int shown = Math.min(8, names.size());
                    for (int i = 0; i < shown; i++) preview.append("\n\u2022 ").append(names.get(i));
                    if (names.size() > shown) preview.append("\n\u2026and ").append(names.size() - shown).append(" more");
                    new AlertDialog.Builder(MainActivity.this).setTitle("Incoming transfer")
                            .setMessage("From " + fromIp + "\n" + count + " file" + (count == 1 ? "" : "s") + ", " + ToxiVisualSystem.formatSize(totalBytes) + "\n\nFiles:" + preview)
                            .setCancelable(false)
                            .setNegativeButton("Decline", (d, w) -> reject.run())
                            .setPositiveButton("Accept", (d, w) -> { accept.run(); busy(true); progress("Receiving\u2026"); })
                            .show();
                });
            }
            @Override public void onFileStart(int idx, int total, String name) { runOnUiThread(() -> progress("Receiving " + idx + " of " + total + ": " + name)); }
            @Override public void onDone(String msg) { runOnUiThread(() -> { busy(false); ToxiVisualSystem.toast(MainActivity.this, msg); afterOps(); }); }
            @Override public void onError(String msg) {
                runOnUiThread(() -> { busy(false); if (waitDlg.isShowing()) waitDlg.dismiss(); ToxiVisualSystem.toast(MainActivity.this, msg); });
            }
        });
    }

    void sendViaWifi(final List<File> files) {
        if (files.isEmpty()) return;
        final EditText in = new EditText(this);
        ToxiMotion.watchFocus(in); in.setHint("e.g. 192.168.1.42:52017"); in.setSingleLine(true);
        final EditText code = new EditText(this);
        ToxiMotion.watchFocus(code); code.setHint("10-character pairing code"); code.setSingleLine(true);
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(wrapField(in)); fields.addView(wrapField(code));
        new AlertDialog.Builder(this).setTitle("Send via Wi-Fi")
                .setMessage("Enter the receiver address and one-time pairing code shown on the receiving device. Both devices must be on the same trusted Wi-Fi network.")
                .setView(fields).setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (d, w) -> {
                    String raw = in.getText().toString().trim();
                    String pairingCode = code.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
                    if (raw.isEmpty() || pairingCode.length() != 10) { ToxiVisualSystem.toast(this, "Enter the address and 10-character pairing code"); return; }
                    String ip = raw.contains(":") ? raw.substring(0, raw.indexOf(':')) : raw;
                    busy(true);
                    progress("Connecting securely\u2026");
                    LanTransfer.send(ip, pairingCode, files, new LanTransfer.Listener() {
                        @Override public void onWaiting(String ip2, int port, String pairingCode2) { }
                        @Override public void onIncoming(String fromIp, int count, long totalBytes, List<String> names, Runnable accept, Runnable reject) { }
                        @Override public void onFileStart(int idx, int total, String name) { runOnUiThread(() -> progress("Sending " + idx + " of " + total + ": " + name)); }
                        @Override public void onDone(String msg) { runOnUiThread(() -> { busy(false); ToxiVisualSystem.toast(MainActivity.this, msg); }); }
                        @Override public void onError(String msg) { runOnUiThread(() -> { busy(false); ToxiVisualSystem.toast(MainActivity.this, msg); }); }
                    });
                }).show();
    }

    // ---------------- OTHER STORAGE (SAF, read-only import) ----------------
    void openOtherStorage() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, REQ_SAF_TREE);
        } catch (Exception ex) { ToxiVisualSystem.toast(this, "Can't open the storage picker on this device"); }
    }

    @Override protected void onActivityResult(int rq, int rc, Intent data) {
        super.onActivityResult(rq, rc, data);
        if (rc != RESULT_OK || data == null) return;
        if (rq == REQ_SAF_TREE) {
            Uri tree = data.getData();
            if (tree == null) return;
            int grantedFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            ToxiUriPermissions.takeGranted(getContentResolver(), tree, grantedFlags);
            safTree = tree;
            getSharedPreferences("lg_storage", MODE_PRIVATE).edit().putString("saf_tree", tree.toString()).putInt("saf_flags", grantedFlags).apply();
            String rootDoc = DocumentsContract.getTreeDocumentId(tree);
            showSafLevel(tree, DocumentsContract.buildDocumentUriUsingTree(tree, rootDoc), "Other storage");
        } else if (rq == REQ_IMPORT_SETTINGS) {
            Uri file = data.getData();
            if (file != null) applyImportedSettings(file);
        }
    }

    void copyToSafTree(final List<File> files) {
        if (safTree == null || files == null || files.isEmpty()) { ToxiVisualSystem.toast(this, "Choose an SD/USB destination first"); return; }
        final DocumentFile rootDoc = DocumentFile.fromTreeUri(this, safTree);
        if (rootDoc == null || !rootDoc.canWrite()) { ToxiVisualSystem.toast(this, "This storage location is not writable"); return; }
        busy(true);
        ops.execute(() -> {
            OperationCancellation.Token operationToken = this.cancellation.begin();
            try {
                int ok = 0, failed = 0, idx = 0;
                String error = "";
                for (File file : files) {
                    if (OperationCancellation.isCancelled()) { failed++; error = "Operation cancelled"; break; }
                    final int current = ++idx;
                    runOnUiThread(() -> progress("Copying to SD/USB " + current + " of " + files.size() + ": " + file.getName()));
                    try { copyLocalToSaf(file, rootDoc); ok++; }
                    catch (Exception ex) { failed++; error = String.valueOf(ex.getMessage()); break; }
                }
                final int copied = ok, failures = failed; final String detail = error;
                runOnUiThread(() -> {
                    busy(false);
                    ToxiVisualSystem.toast(this, failures == 0 ? "Copied " + copied + " item" + (copied == 1 ? "" : "s") + " to SD/USB" : "Copied " + copied + "; stopped: " + detail);
                });

            } finally { this.cancellation.finish(operationToken); }
        });
    }

    private void copyLocalToSaf(File source, DocumentFile parent) throws java.io.IOException {
        SafStorageOperations.copyLocalTree(getContentResolver(), source, parent);
    }

    private void deleteSafTree(DocumentFile target) throws java.io.IOException {
        SafStorageOperations.deleteTree(target);
    }

    void showSafLevel(final Uri tree, final Uri docUri, final String title) {
        ToxiVisualSystem.toast(this, "Loading…");
        io.execute(() -> {
            final List<String[]> rows = new ArrayList<>();
            boolean querySucceeded = false;
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(docUri));
                try (Cursor c = getContentResolver().query(children, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                    if (c != null) {
                        querySucceeded = true;
                        while (c.moveToNext()) rows.add(new String[]{c.getString(0), c.getString(1),
                                DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2)) ? "1" : "0"});
                    }
                }
            } catch (Exception ignored) { }
            final boolean readable = querySucceeded;
            runOnUiThread(() -> {
                if (!readable) { ToxiVisualSystem.toast(this, "This storage provider could not read the folder"); return; }
                if (rows.isEmpty()) { ToxiVisualSystem.toast(this, "This folder is empty"); return; }
                String[] names = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) names[i] = rows.get(i)[1];
                AlertDialog listing = new AlertDialog.Builder(this).setTitle(title).setItems(names, (d, w) -> {
                    String[] row = rows.get(w);
                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(tree, row[0]);
                    if ("1".equals(row[2])) showSafLevel(tree, childUri, row[1]);
                    else importSafDoc(childUri, row[1]);
                }).setNegativeButton("Close", null).create();
                listing.setOnShowListener(ignored -> listing.getListView().setOnItemLongClickListener(
                        (parent, view, position, id) -> {
                            String[] row = rows.get(position);
                            Uri selected = DocumentsContract.buildDocumentUriUsingTree(tree, row[0]);
                            showSafDocumentActions(tree, docUri, title, selected, row[1], "1".equals(row[2]));
                            return true;
                        }));
                listing.show();
            });
        });
    }

    private interface SafAction { void run() throws Exception; }

    private void showSafDocumentActions(Uri tree, Uri parent, String title, Uri document,
                                         String name, boolean directory) {
        String[] actions = new String[]{"Duplicate here", "Move to folder", "Rename", "Delete"};
        new AlertDialog.Builder(this).setTitle(name).setItems(actions, (dialog, which) -> {
            String action = actions[which];
            if ("Duplicate here".equals(action)) {
                runSafAction(tree, parent, title, "Duplicated " + name, () -> {
                    if (directory) {
                        DocumentFile source = safDocument(tree, document);
                        DocumentFile destination = safDocument(tree, parent);
                        if (source == null || destination == null) {
                            throw new java.io.IOException("Storage provider returned an invalid copy location");
                        }
                        SafStorageOperations.copyDocumentTree(getContentResolver(), source, destination);
                    } else {
                        SafStorageOperations.copyDocumentFile(getContentResolver(), tree, document, parent, name);
                    }
                });
            } else if ("Move to folder".equals(action)) {
                showSafMoveFolderPicker(tree, parent, document, parent, title, name);
            } else if ("Rename".equals(action)) {
                EditText input = new EditText(this);
                input.setSingleLine(true);
                input.setText(name);
                input.setSelection(input.length());
                new AlertDialog.Builder(this).setTitle("Rename document").setView(input)
                        .setPositiveButton("Rename", (d, w) -> {
                            String newName = input.getText().toString();
                            if (name.equals(newName)) return;
                            runSafAction(tree, parent, title, "Renamed to " + newName,
                                    () -> SafStorageOperations.renameDocument(getContentResolver(), document, newName));
                        }).setNegativeButton("Cancel", null).show();
            } else {
                new AlertDialog.Builder(this).setTitle("Delete " + name + "?")
                        .setMessage("This permanently deletes the selected item from the chosen storage provider.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> runSafAction(tree, parent, title, "Deleted " + name,
                                () -> SafStorageOperations.deleteDocument(getContentResolver(), document)))
                        .show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private DocumentFile safDocument(Uri tree, Uri document) throws java.io.IOException {
        return SafStorageOperations.findDocumentInTree(this, tree, document);
    }

    private boolean sameSafDocument(Uri first, Uri second) {
        if (first == null || second == null) return false;
        try {
            return DocumentsContract.getDocumentId(first).equals(DocumentsContract.getDocumentId(second));
        } catch (IllegalArgumentException invalidUri) {
            return first.equals(second);
        }
    }

    private void showSafMoveFolderPicker(Uri tree, Uri currentFolder, Uri sourceDocument,
                                         Uri sourceParent, String sourceParentTitle, String sourceName) {
        io.execute(() -> {
            final List<String[]> folders = new ArrayList<>();
            String failure = null;
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree, DocumentsContract.getDocumentId(currentFolder));
                try (Cursor cursor = getContentResolver().query(children, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                    if (cursor == null) throw new java.io.IOException("Storage provider could not list destination folders");
                    while (cursor.moveToNext()) {
                        if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) continue;
                        Uri child = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                        if (sameSafDocument(child, sourceDocument)) continue;
                        folders.add(new String[]{child.toString(), cursor.getString(1)});
                    }
                }
            } catch (Exception error) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            final String detail = failure;
            runOnUiThread(() -> {
                if (detail != null) {
                    ToxiVisualSystem.toast(this, "Cannot browse destination folders: " + detail);
                    return;
                }
                boolean canMoveHere = !sameSafDocument(currentFolder, sourceParent);
                List<String> choices = new ArrayList<>();
                if (canMoveHere) choices.add("Move here: " + DocumentsContract.getDocumentId(currentFolder));
                for (String[] folder : folders) choices.add("Open folder: " + folder[1] + "  ›");
                if (choices.isEmpty()) {
                    ToxiVisualSystem.toast(this, "No other destination folders are available here");
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Move " + sourceName + " to folder")
                        .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                            if (canMoveHere && which == 0) {
                                runSafAction(tree, sourceParent, sourceParentTitle, "Moved " + sourceName, () -> {
                                    DocumentFile source = safDocument(tree, sourceDocument);
                                    DocumentFile from = safDocument(tree, sourceParent);
                                    DocumentFile destination = safDocument(tree, currentFolder);
                                    if (source == null || from == null || destination == null) {
                                        throw new java.io.IOException("Storage provider returned an invalid move location");
                                    }
                                    SafStorageOperations.moveDocumentWithFallback(
                                            this, getContentResolver(), source, from, destination);
                                });
                            } else {
                                int folderIndex = which - (canMoveHere ? 1 : 0);
                                String[] selected = folders.get(folderIndex);
                                showSafMoveFolderPicker(tree, Uri.parse(selected[0]), sourceDocument,
                                        sourceParent, sourceParentTitle, sourceName);
                            }
                        }).setNegativeButton("Cancel", null).show();
            });
        });
    }

    private void runSafAction(Uri tree, Uri parent, String title, String successMessage, SafAction action) {
        busy(true);
        ops.execute(() -> {
            OperationCancellation.Token operationToken = cancellation.begin();
            String error = null;
            try { action.run(); }
            catch (Exception failure) {
                error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            } finally { cancellation.finish(operationToken); }
            final String detail = error;
            runOnUiThread(() -> {
                busy(false);
                if (detail == null) {
                    ToxiVisualSystem.toast(this, successMessage);
                    showSafLevel(tree, parent, title);
                } else {
                    ToxiVisualSystem.toast(this, "Storage operation failed: " + detail);
                }
            });
        });
    }

    void importSafDoc(final Uri doc, final String name) {
        busy(true);
        ops.execute(() -> {
            OperationCancellation.Token operationToken = this.cancellation.begin();
            try {
                String msg;
                File pending = null;
                File dest = null;
                try {
                    File download = new File(Environment.getExternalStorageDirectory(), "Download");
                    if (!download.isDirectory() && !download.mkdirs()) throw new java.io.IOException("Download folder is unavailable");
                    dest = FileOps.unique(download, name);
                    pending = new File(download, ".toxifiles-import-" + java.util.UUID.randomUUID() + ".part");
                    try (InputStream in = getContentResolver().openInputStream(doc); OutputStream out = new FileOutputStream(pending)) {
                        if (in == null) throw new java.io.IOException("External storage returned no input stream");
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) > 0) { FileOps.checkCancelled(); out.write(buf, 0, n); }
                    }
                    FileOps.checkCancelled();
                    try { java.nio.file.Files.move(pending.toPath(), dest.toPath()); } catch (java.io.IOException renameError) { FileOps.copy(pending, dest); pending.delete(); }
                    msg = "Imported to Download/" + dest.getName();
                } catch (Exception ex) {
                    if (pending != null) pending.delete();
                    msg = "Import failed: " + ex.getMessage();
                }
                final String m = msg;
                runOnUiThread(() -> { busy(false); ToxiVisualSystem.toast(this, m); afterOps(); });

            } finally { this.cancellation.finish(operationToken); }
        });
    }

    // ---------------- SETTINGS BACKUP ----------------
    void exportSettings() {
        io.execute(() -> {
            try {
                final File exported = SettingsBackupManager.exportToDownloads(this, ToxiVisualSystem.PREFERENCES);
                runOnUiThread(() -> {
                    ToxiVisualSystem.toast(this, "Saved to Download/" + exported.getName());
                    try {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("application/json");
                        share.putExtra(Intent.EXTRA_STREAM, FileOps.uri(this, exported));
                        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(share, "Share settings file"));
                    } catch (Exception ignored) { }
                });
            } catch (Exception failure) {
                final String message = "Export failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                runOnUiThread(() -> ToxiVisualSystem.toast(this, message));
            }
        });
    }

    void importSettingsPick() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Choose settings file"), REQ_IMPORT_SETTINGS);
        } catch (Exception ex) { ToxiVisualSystem.toast(this, "Can't open file picker"); }
    }

    void applyImportedSettings(final Uri file) {
        io.execute(() -> {
            String message;
            try {
                SettingsBackupManager.importFromUri(this, file, ToxiVisualSystem.PREFERENCES,
                        ToxiVisualSystem.THEME_NAMES.length);
                message = "Settings restored (security settings were not imported)";
            } catch (Exception failure) {
                message = "Import failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            }
            final String result = message;
            runOnUiThread(() -> {
                ToxiVisualSystem.toast(this, result);
                ToxiVisualSystem.applyTheme(ToxiVisualSystem.PREFERENCES.getInt("theme", 0), false);
                recolor();
                buildSettings();
            });
        });
    }

    // ---------------- LARGEST FILES ----------------
    void openLargest() {
        Indexer ix = Indexer.I;
        if (!ix.ready) { ToxiVisualSystem.toast(this, "Still scanning storage \u2014 try again in a moment"); return; }
        List<Entry> big = new ArrayList<>(ix.all);
        java.util.Collections.sort(big, (x, y) -> Long.compare(y.len, x.len));
        if (big.size() > 300) big = big.subList(0, 300);
        listView.setList("Largest files", big, false, false);
        showOverlay(listView);
    }

    void retentionDialog() {
        final int[] days = {7, 14, 30, 60, 90};
        String[] labels = new String[days.length];
        for (int i = 0; i < days.length; i++) labels[i] = days[i] + " days";
        new AlertDialog.Builder(this).setTitle("Recycle Bin retention")
                .setItems(labels, (d, w) -> { ToxiVisualSystem.PREFERENCES.edit().putInt("recyclebin_days", days[w]).apply(); buildSettings(); })
                .show();
    }

    // ---------------- RECYCLE BIN ----------------
    void showRecycleBin() { buildRecycleBin(); showOverlay(recycleScroll); }

    void buildRecycleBin() {
        recycle.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(ToxiVisualSystem.dp(8), 0, ToxiVisualSystem.dp(8), 0);
        ImageView bk = ToxiVisualSystem.iconButton(this, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(48), ToxiVisualSystem.dp(48)));
        hd.addView(ToxiVisualSystem.text(this, "Recycle Bin", 24, ToxiVisualSystem.TEXT_PRIMARY, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView emptyBtn = ToxiVisualSystem.text(this, "Empty", 14, ToxiVisualSystem.ACCENT, true);
        emptyBtn.setPadding(ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(10), ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(10));
        emptyBtn.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Empty Recycle Bin?")
                .setMessage("Every item will be permanently deleted. This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Empty", (d, w) -> RecycleBin.empty(this, msg -> { ToxiVisualSystem.toast(this, msg); buildRecycleBin(); }))
                .show());
        ToxiVisualSystem.interactive(emptyBtn);
        hd.addView(emptyBtn, new LinearLayout.LayoutParams(-2, -2));
        hd.setMinimumHeight(ToxiVisualSystem.dp(56));
        recycle.addView(hd, new LinearLayout.LayoutParams(-1, -2));

        TextView note = ToxiVisualSystem.text(this, "Items are kept for " + ToxiVisualSystem.PREFERENCES.getInt("recyclebin_days", 30) + " days, then removed automatically. Change this in Settings.", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        note.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(10));
        recycle.addView(note);

        List<RecycleBin.Item> items = RecycleBin.list(this);
        if (items.isEmpty()) {
            TextView w = ToxiVisualSystem.text(this, "Recycle Bin is empty", 14, ToxiVisualSystem.TEXT_SECONDARY, false);
            w.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(20), 0, 0);
            recycle.addView(w);
        } else {
            for (final RecycleBin.Item it : items) {
                Entry e = new Entry(it.file);
                View rv = ToxiFileRow.create(this);
                ToxiFileRow.Holder h = (ToxiFileRow.Holder) rv.getTag();
                ToxiFileRow.bind(h, e, false, false, "from " + it.originalParent.getName());
                rv.setOnClickListener(v -> recycleItemMenu(it));
                h.more.setOnClickListener(v -> recycleItemMenu(it));
                recycle.addView(rv, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    void recycleItemMenu(final RecycleBin.Item it) {
        String[] opts = {"Restore", "Delete forever"};
        new AlertDialog.Builder(this).setTitle(it.file.getName()).setItems(opts, (d, w) -> {
            if (w == 0) {
                RecycleBin.restore(this, it, msg -> { ToxiVisualSystem.toast(this, msg); afterOps(); buildRecycleBin(); });
            } else {
                new AlertDialog.Builder(this).setTitle("Delete forever?").setMessage("This can't be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d2, w2) -> RecycleBin.permanentDelete(this, java.util.Collections.singletonList(it), msg -> { ToxiVisualSystem.toast(this, msg); buildRecycleBin(); }))
                        .show();
            }
        }).show();
    }

    String versionName() {
        try { return "Version " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    void fontDialog() {
        String[] fonts = {"System Sans", "Rounded", "Serif", "Monospace"};
        new AlertDialog.Builder(this).setTitle("Font style").setSingleChoiceItems(fonts, ToxiVisualSystem.PREFERENCES.getInt("font_family", 0), (d, which) -> {
            ToxiVisualSystem.PREFERENCES.edit().putInt("font_family", which).apply();
            d.dismiss();
            recreate();
        }).setNegativeButton("Cancel", null).show();
    }
    void themeDialog() { openThemeScreen(); }

    void openThemeScreen() {
        buildThemeScreen();
        showOverlay(themeScroll);
    }

    void buildThemeScreen() {
        themeScroll.removeAllViews();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(24), ToxiVisualSystem.dp(18), ToxiVisualSystem.dp(112));

        TextView title = ToxiVisualSystem.text(this, "Appearance Lab", 28, ToxiVisualSystem.TEXT_PRIMARY, true);
        title.setPadding(ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(4), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(2));
        panel.addView(title);
        TextView subtitle = ToxiVisualSystem.text(this, "Eight independent color systems. One consistent interface.", 13, ToxiVisualSystem.TEXT_SECONDARY, false);
        subtitle.setPadding(ToxiVisualSystem.dp(3), 0, ToxiVisualSystem.dp(3), ToxiVisualSystem.dp(18)); panel.addView(subtitle);

        LinearLayout current = new LinearLayout(this);
        current.setGravity(Gravity.CENTER_VERTICAL);
        current.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(16));
        current.setBackground(ToxiVisualSystem.surface(24, true));
        FrameLayout currentIcon = new FrameLayout(this);
        currentIcon.setBackground(ToxiVisualSystem.accentSurface(16));
        ImageView currentGlyph = new ImageView(this); currentGlyph.setImageResource(R.drawable.ic_toxifiles_brand_mark); currentGlyph.setPadding(ToxiVisualSystem.dp(7), ToxiVisualSystem.dp(7), ToxiVisualSystem.dp(7), ToxiVisualSystem.dp(7));
        currentIcon.addView(currentGlyph, new FrameLayout.LayoutParams(-1, -1));
        current.addView(currentIcon, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(54), ToxiVisualSystem.dp(54)));
        LinearLayout currentText = new LinearLayout(this); currentText.setOrientation(LinearLayout.VERTICAL);
        currentText.setPadding(ToxiVisualSystem.dp(14), 0, ToxiVisualSystem.dp(8), 0);
        currentText.addView(ToxiVisualSystem.text(this, "ACTIVE IDENTITY", 10, ToxiVisualSystem.ACCENT, true));
        TextView activeName = ToxiVisualSystem.text(this, ToxiVisualSystem.THEME_NAMES[ToxiVisualSystem.THEME_INDEX], 17, ToxiVisualSystem.TEXT_PRIMARY, true);
        activeName.setPadding(0, ToxiVisualSystem.dp(4), 0, 0); currentText.addView(activeName);
        current.addView(currentText, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView activeMark = new ImageView(this);
        activeMark.setImageDrawable(ToxiIcons.symbol(ToxiIcons.CHECK, ToxiVisualSystem.ACCENT));
        activeMark.setContentDescription("Active theme");
        current.addView(activeMark, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(30), ToxiVisualSystem.dp(42)));
        panel.addView(current, lpm(-1, -2, 0, 0, 0, 20));

        TextView allLabel = ToxiVisualSystem.text(this, "AVAILABLE THEMES", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        allLabel.setLetterSpacing(0.12f); allLabel.setPadding(ToxiVisualSystem.dp(3), 0, 0, ToxiVisualSystem.dp(8)); panel.addView(allLabel);
        String[] finishNotes = ToxiVisualSystem.THEME_DESCRIPTIONS;
        for (int index = 0; index < ToxiVisualSystem.THEME_NAMES.length; index++) {
            final int themeIndex = index;
            boolean selected = index == ToxiVisualSystem.THEME_INDEX;
            LinearLayout tile = new LinearLayout(this);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(ToxiVisualSystem.dp(13), ToxiVisualSystem.dp(12), ToxiVisualSystem.dp(13), ToxiVisualSystem.dp(12));
            tile.setBackground(selected ? ToxiVisualSystem.accentSurface(20) : ToxiVisualSystem.surface(20, true));

            FrameLayout swatch = new FrameLayout(this);
            android.graphics.drawable.GradientDrawable swatchBg = new android.graphics.drawable.GradientDrawable();
            swatchBg.setColors(new int[]{ToxiThemeSystem.ACCENTS[index], ToxiThemeSystem.ACCENT_ENDS[index]}); swatchBg.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR); swatchBg.setCornerRadius(ToxiVisualSystem.dp(14));
            swatchBg.setStroke(ToxiVisualSystem.dp(1), Color.argb(130, 255, 255, 255));
            swatch.setBackground(swatchBg);
            tile.addView(swatch, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(46), ToxiVisualSystem.dp(46)));

            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(ToxiVisualSystem.dp(13), 0, ToxiVisualSystem.dp(8), 0);
            labels.addView(ToxiVisualSystem.text(this, ToxiVisualSystem.THEME_NAMES[index], 14.5f, ToxiVisualSystem.TEXT_PRIMARY, selected));
            TextView desc = ToxiVisualSystem.text(this, selected ? "Currently applied \u00B7 " + ToxiVisualSystem.themeDescription(index) : ToxiVisualSystem.themeDescription(index), 11, ToxiVisualSystem.TEXT_SECONDARY, false);
            desc.setPadding(0, ToxiVisualSystem.dp(3), 0, 0); labels.addView(desc);
            tile.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView status = new ImageView(this);
            status.setImageDrawable(ToxiIcons.symbol(selected ? ToxiIcons.CHECK : ToxiIcons.NEXT, selected ? ToxiVisualSystem.ACCENT : ToxiVisualSystem.TEXT_SECONDARY));
            status.setContentDescription(selected ? "Selected theme" : "Preview theme");
            tile.addView(status, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(28), ToxiVisualSystem.dp(42)));

            tile.setOnClickListener(v -> {
                ToxiVisualSystem.feedback(v);
                ToxiVisualSystem.applyTheme(themeIndex, true);
                recolor();
                buildThemeScreen();
            });
            ToxiVisualSystem.interactive(tile);
            panel.addView(tile, lpm(-1, -2, 0, 0, 0, 8));
        }
        themeScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
    }

    void maybeOfferCrashReport() {
        if (isFinishing() || isDestroyed() || !CrashLogger.hasReport(this)) return;
        long crashTime = CrashLogger.reportTime(this);
        if (crashTime <= CrashLogger.promptedTime(this)) return;
        CrashLogger.markPrompted(this, crashTime);
        new AlertDialog.Builder(this)
                .setTitle("App crash report available")
                .setMessage("ToxiFiles saved a local diagnostic report from the last unexpected crash. You can share it to help identify the cause. The report stays on this device unless you choose to share it.")
                .setPositiveButton("Share report", (d, w) -> shareCrashReport())
                .setNegativeButton("Later", null)
                .setNeutralButton("Delete report", (d, w) -> deleteCrashReport())
                .show();
    }

    void showCrashLogOptions() {
        if (!CrashLogger.hasReport(this)) {
            new AlertDialog.Builder(this).setTitle("Crash logs")
                    .setMessage("No crash report has been captured yet. If the app closes unexpectedly, ToxiFiles will save diagnostic details locally and offer them the next time you open the app.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Crash reports & diagnostics")
                .setMessage("A crash report is stored privately inside the app. It includes the exception stack trace, app version, Android version, device model, and crashing thread. Nothing is uploaded automatically.")
                .setPositiveButton("Share report", (d, w) -> shareCrashReport())
                .setNegativeButton("Close", null)
                .setNeutralButton("Delete report", (d, w) -> deleteCrashReport()).show();
    }

    void shareCrashReport() {
        File report = CrashLogger.latest(this);
        if (!report.isFile() || report.length() == 0) {
            ToxiVisualSystem.toast(this, "No crash report available");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", report);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "ToxiFiles crash report");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(android.content.ClipData.newUri(getContentResolver(), "Crash report", uri));
            startActivity(Intent.createChooser(share, "Share crash report"));
        } catch (Exception e) {
            ToxiVisualSystem.toast(this, "Could not share crash report");
        }
    }

    void deleteCrashReport() {
        File report = CrashLogger.latest(this);
        boolean deleted = !report.exists() || report.delete();
        File dir = report.getParentFile();
        if (dir != null) {
            File[] old = dir.listFiles((d, name) -> name.startsWith("crash-") && name.endsWith(".txt"));
            if (old != null) for (File f : old) try { f.delete(); } catch (Exception ignored) { }
        }
        if (deleted) ToxiVisualSystem.toast(this, "Crash reports deleted");
        else ToxiVisualSystem.toast(this, "Could not delete crash report");
    }

    void showAbout() {
        aboutScroll.setFillViewport(false);
        aboutScroll.setClipToPadding(false);
        AboutScreenBuilder.populate(this, about, this::closeOverlay, versionName());
        aboutScroll.setBackgroundColor(AboutScreenBuilder.BACKGROUND);
        showOverlay(aboutScroll);
    }

    // ---------------- background + donut ----------------
    static final class BgView extends View {
        final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BgView(android.content.Context c) {
            super(c);
            setLayerType(LAYER_TYPE_NONE, null);
            setWillNotDraw(false);
            gridPaint.setStyle(Paint.Style.STROKE);
            markerPaint.setStyle(Paint.Style.STROKE);
            applyBlur();
        }

        void applyBlur() {
            if (Build.VERSION.SDK_INT >= 31) {
                boolean lowRam = false;
                try {
                    android.app.ActivityManager am = (android.app.ActivityManager) getContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);
                    lowRam = am != null && am.isLowRamDevice();
                } catch (Exception ignored) { }
                if (ToxiVisualSystem.booleanPreference("blur", false) && !lowRam) setRenderEffect(RenderEffect.createBlurEffect(ToxiVisualSystem.dp(10), ToxiVisualSystem.dp(10), Shader.TileMode.CLAMP));
                else setRenderEffect(null);
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            int width = getWidth(), height = getHeight();
            canvas.drawColor(ToxiVisualSystem.CANVAS);
            int accent = ToxiVisualSystem.ACCENT;
            gridPaint.setColor(Color.argb(10, Color.red(accent), Color.green(accent), Color.blue(accent)));
            gridPaint.setStrokeWidth(Math.max(1f, ToxiVisualSystem.dp(0.65f)));
            float step = ToxiVisualSystem.dp(48);
            for (float x = step * 0.5f; x < width; x += step) canvas.drawLine(x, 0, x, height, gridPaint);
            for (float y = step * 0.5f; y < height; y += step) canvas.drawLine(0, y, width, y, gridPaint);
            markerPaint.setColor(Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent)));
            markerPaint.setStrokeWidth(Math.max(1f, ToxiVisualSystem.dp(1.1f)));
            float inset = ToxiVisualSystem.dp(20), mark = ToxiVisualSystem.dp(38);
            canvas.drawLine(inset, inset, inset + mark, inset, markerPaint);
            canvas.drawLine(inset, inset, inset, inset + mark, markerPaint);
            canvas.drawLine(width - inset - mark, height - inset, width - inset, height - inset, markerPaint);
            canvas.drawLine(width - inset, height - inset - mark, width - inset, height - inset, markerPaint);
        }
    }

    static final class DonutView extends View {
        float[] parts = new float[0];
        int[] cols = new int[0];
        String big = "", small = "";
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();

        DonutView(android.content.Context c) {
            super(c);
            t.setTextAlign(Paint.Align.CENTER);
            t.setColor(ToxiVisualSystem.TEXT_PRIMARY);
        }

        void set(float[] pa, int[] co, String b, String s) { parts = pa; cols = co; big = b; small = s; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float sw = ToxiVisualSystem.dp(11), pad = sw / 2f + ToxiVisualSystem.dp(2);
            r.set(pad, pad, getWidth() - pad, getHeight() - pad);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(ToxiVisualSystem.LIGHT_MODE ? Color.argb(45, 20, 22, 30) : Color.argb(45, 255, 255, 255));
            c.drawArc(r, 0, 360, false, p);
            float start = -90;
            for (int i = 0; i < parts.length && i < cols.length; i++) {
                float sweep = parts[i] * 360f;
                if (sweep < 0.5f) continue;
                p.setColor(cols[i]);
                c.drawArc(r, start, Math.max(sweep - 1.5f, 0.5f), false, p);
                start += sweep;
            }
            t.setColor(ToxiVisualSystem.TEXT_PRIMARY);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            t.setTextSize(ToxiVisualSystem.dp(22));
            c.drawText(big, getWidth() / 2f, getHeight() / 2f + ToxiVisualSystem.dp(5), t);
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            t.setTextSize(ToxiVisualSystem.dp(11));
            t.setColor(ToxiVisualSystem.TEXT_SECONDARY);
            c.drawText(small, getWidth() / 2f, getHeight() / 2f + ToxiVisualSystem.dp(21), t);
        }
    }
}
