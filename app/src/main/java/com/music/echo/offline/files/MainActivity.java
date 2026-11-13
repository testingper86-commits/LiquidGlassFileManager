package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import org.json.JSONObject;
import androidx.activity.ComponentActivity;

public class MainActivity extends ComponentActivity {
    private CrashReportController crashReports;
    private StoragePermissionController storagePermissions;
    private AppLockLifecycleController appLockLifecycle;
    private AppLockScreenController appLockScreen;
    private StorageAnalyzerController storageAnalyzerController;
    private RecycleBinScreenController recycleBinScreen;
    private SettingsScreenController settingsScreenController;
    private StorageMaintenanceScreenController storageMaintenanceScreen;
    private VaultScreenController vaultScreenController;
    private LanTransferScreenController lanTransferScreen;
    private ThemeScreenController themeScreenController;
    private SafBrowserController safBrowserController;
    private final ActivityTaskRuntime taskRuntime = new ActivityTaskRuntime();
    private final Runnable indexDoneCallback = this::onIndexDone;
    final OperationCancellation cancellation = taskRuntime.cancellation;
    final TaskCoordinator.ScopedExecutor io = taskRuntime.io;
    final TaskCoordinator.ScopedExecutor ops = taskRuntime.ops;
    private final FavoritesRepository favoritesRepository = new FavoritesRepository();
    final Set<String> favs = favoritesRepository.paths();
    final ClipboardController clipboard = new ClipboardController();

    FrameLayout root, content;
    BgView bg;
    ScrollView homeScroll, toolsScroll, settingsScroll, aboutScroll, recycleScroll, vaultScroll, themeScroll;
    View homeScrollFooter;
    LinearLayout home, tools, settings, about, recycle, vault;
    static final int REQ_SAF_TREE = 501, REQ_IMPORT_SETTINGS = 502;
    ScrollView dupScroll;
    LinearLayout dup;
    static volatile boolean unlocked = false;
    BrowserView filesView, favView, listView;
    View[] screens;
    private MainNavigationController navigationController;
    TextView busyTv;
    int busyCount, tab = 0, pendingCat = -1;
    private MainOverlayController overlayController;
    private FileOpenCoordinator fileOpenCoordinator;
    boolean accessOk;
    final Handler ui = new Handler(Looper.getMainLooper());
    @Override protected void onCreate(Bundle b) {
        boolean lightPref = getSharedPreferences("lg_settings", MODE_PRIVATE).getBoolean("light", false);
        setTheme(lightPref ? R.style.AppTheme_Light : R.style.AppTheme);
        super.onCreate(b);
        navigationController = new MainNavigationController(this);
        crashReports = new CrashReportController(this);
        storagePermissions = new StoragePermissionController(this);
        appLockScreen = new AppLockScreenController(this);
        storageAnalyzerController = new StorageAnalyzerController(this);
        recycleBinScreen = new RecycleBinScreenController(this);
        settingsScreenController = new SettingsScreenController(this);
        storageMaintenanceScreen = new StorageMaintenanceScreenController(this);
        vaultScreenController = new VaultScreenController(this);
        lanTransferScreen = new LanTransferScreenController(this);
        themeScreenController = new ThemeScreenController(this);
        safBrowserController = new SafBrowserController(this);
        appLockLifecycle = new AppLockLifecycleController(this,
                () -> {
                    unlocked = false;
                    if (vaultScroll != null && overlayController != null && overlayController.current() == vaultScroll) closeOverlay();
                },
                () -> { if (AppLock.enabled(this) && !unlocked) showAppLock(); },
                () -> {
                    if (AppLock.enabled(this)) unlocked = false;
                    if (vaultScroll != null && overlayController != null && overlayController.current() == vaultScroll) closeOverlay();
                });
        ToxiBackNavigation.bind(this, this::handleBackNavigation);
        appLockLifecycle.register();
        ToxiVisualSystem.initialize(this);
        safBrowserController.restorePersistedTree();
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        favoritesRepository.load(getSharedPreferences("lg_favs", MODE_PRIVATE));

        root = new FrameLayout(this);
        root.setBackgroundColor(ToxiVisualSystem.CANVAS);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams windowParams = getWindow().getAttributes();
            windowParams.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(windowParams);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }

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
        overlayController = new MainOverlayController(this);
        fileOpenCoordinator = new FileOpenCoordinator(this);
        View[] all = {homeScroll, filesView, toolsScroll, favView, settingsScroll, listView, aboutScroll, recycleScroll, vaultScroll, dupScroll, themeScroll};
        for (View v : all) { content.addView(v, new FrameLayout.LayoutParams(-1, -1)); v.setVisibility(View.GONE); }

        buildNav();
        busyTv = ToxiVisualSystem.text(this, "Working\u2026", 13, ToxiVisualSystem.TEXT_PRIMARY, true);
        busyTv.setBackground(ToxiVisualSystem.accentSurface(20));
        busyTv.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        busyTv.setVisibility(View.GONE);
        busyTv.setContentDescription("Operation progress. Tap to request cancellation.");
        busyTv.setOnClickListener(v -> { cancellation.cancelActive(); LanTransfer.cancel(); ToxiVisualSystem.toast(this, "Cancellation requested"); });
        root.addView(busyTv, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        setContentView(root);
        applySystemBarAppearance();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets navigation = insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                ToxiVisualSystem.TOP_INSET = in.top;
                ToxiVisualSystem.BOTTOM_INSET = Math.max(navigation.bottom, 0);
                ToxiVisualSystem.IME_INSET = ime.bottom;
            } else {
                ToxiVisualSystem.TOP_INSET = insets.getSystemWindowInsetTop();
                ToxiVisualSystem.BOTTOM_INSET = insets.getSystemWindowInsetBottom();
                ToxiVisualSystem.IME_INSET = 0;
            }
            navigationController.setImeVisible(Build.VERSION.SDK_INT >= 30 && insets.isVisible(WindowInsets.Type.ime()));
            applyInsets();
            return insets;
        });
        // Some first frames do not receive dispatched insets until after the initial dock pass.
        // Apply the conservative gesture-area baseline immediately, then let the callback refine it.
        applyInsets();
        root.requestApplyInsets();

        accessOk = hasAccess();
        selectTab(0);
        if (!accessOk) {
            if (Build.VERSION.SDK_INT >= 30) ui.postDelayed(() -> { if (!isFinishing() && !isDestroyed() && !hasAccess()) showStorageAccess(); }, 600);
            else requestLegacyPerms();
        } else startIndex();
        ui.postDelayed(() -> crashReports.maybeOfferCrashReport(), 2200);
    }

    void applyInsets() {
        // Apply each system inset exactly once: content owns top/cutout; the dock owns bottom bars.
        content.setPadding(0, ToxiVisualSystem.TOP_INSET, 0, 0);
        navigationController.applyBottomInset();
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) busyTv.getLayoutParams();
        bp.topMargin = ToxiVisualSystem.TOP_INSET + ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));
        busyTv.setLayoutParams(bp);
        homeScroll.setPadding(0, 0, 0, 0);
        if (homeScrollFooter != null) {
            ViewGroup.LayoutParams footerParams = homeScrollFooter.getLayoutParams();
            footerParams.height = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(110)) + ToxiVisualSystem.BOTTOM_INSET;
            homeScrollFooter.setLayoutParams(footerParams);
        }
        // Reserve enough scrollable space for the bottom navigation and system gesture area.
        settingsScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(96)) + ToxiVisualSystem.BOTTOM_INSET
                + ToxiVisualSystem.IME_INSET);
        if (toolsScroll != null) toolsScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24))
                + ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.IME_INSET);
        aboutScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)) + ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.IME_INSET);
        recycleScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)) + ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.IME_INSET);
        vaultScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)) + ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.IME_INSET);
        dupScroll.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)) + ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.IME_INSET);
        filesView.applyInsets(); favView.applyInsets(); listView.applyInsets();
    }

    // ---------------- permissions ----------------
    boolean hasAccess() { return storagePermissions.hasAccess(); }

    void requestLegacyPerms() { storagePermissions.requestLegacyPermissions(); }

    void showStorageAccess() { storagePermissions.showAccessPrompt(); }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        onAccessChanged();
    }

    @Override protected void onResume() {
        super.onResume();
        appLockLifecycle.onResume();
        if (hasAccess() != accessOk) onAccessChanged();
        else if (accessOk && !Indexer.I.running && (Indexer.I.dirty || System.currentTimeMillis() - Indexer.I.finished > 180000)) startIndex();
    }

    @Override protected void onPause() { super.onPause(); }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        appLockLifecycle.onUserLeaveHint();
    }

    void onAccessChanged() {
        accessOk = hasAccess();
        if (accessOk) startIndex();
        if (filesView.cur != null) filesView.reload();
        if (tab == 0) buildHome();
        if (tab == 3) buildSettings();
    }

    @Override protected void onDestroy() {
        appLockLifecycle.unregister();
        taskRuntime.shutdown();
        super.onDestroy();
    }

    // ---------------- index ----------------
    void startIndex() {
        if (!hasAccess()) return;
        Indexer.I.start(indexDoneCallback);
    }

    void runUiIfAlive(Runnable action) {
        if (action == null || isFinishing() || isDestroyed()) return;
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) action.run();
        });
    }

    boolean executeIo(Runnable task) {
        return executeIo(task, null);
    }

    boolean executeIo(Runnable task, Runnable onRejected) {
        try {
            io.execute(task);
            return true;
        } catch (RejectedExecutionException rejected) {
            runUiIfAlive(() -> {
                ToxiVisualSystem.toast(this, "Background task queue is busy. Try again shortly.");
                if (onRejected != null) onRejected.run();
            });
            return false;
        }
    }

    void executeOperation(Runnable task) {
        try {
            ops.execute(task);
        } catch (RejectedExecutionException rejected) {
            runUiIfAlive(() -> {
                busy(false);
                ToxiVisualSystem.toast(this, "Operation could not be started. Try again shortly.");
            });
        }
    }

    void onIndexDone() {
        if (isFinishing() || isDestroyed()) return;
        Indexer ix = Indexer.I;
        Indexer.PublishedSnapshot index = ix.snapshot();
        if (!ix.lastError.isEmpty()) {
            ToxiVisualSystem.toast(this, index.ready
                    ? "Storage refresh failed. Showing the last successful index."
                    : "Storage scan failed. Check storage access and retry.");
        } else if (ix.incomplete) {
            ToxiVisualSystem.toast(this, "Storage scan incomplete. Some files may be missing.");
        }
        String[] names = Indexer.CAT_NAMES;
        if (pendingCat >= 0 && pendingCat < 6) { listView.setList(names[pendingCat], index.cats.get(pendingCat), false, false); pendingCat = -1; }
        else if (pendingCat == -2) { listView.setList("Search", index.all, true, true); pendingCat = -1; }
        if (tab == 0 && overlayController.current() == null && home.getVisibility() == View.VISIBLE) buildHome();
    }

    void afterOps() {
        Indexer.I.dirty = true;
        filesView.reload();
        favView.reload();
        if (overlayController.current() == listView) listView.reload();
        filesView.updatePaste();
        if (tab == 0) buildHome();
        if (!Indexer.I.running && hasAccess()) startIndex();
    }

    // ---------------- clipboard / favourites / busy ----------------
    void setClip(File[] files, boolean cut) { clipboard.set(files, cut); filesView.updatePaste(); }

    void saveFavs() { favoritesRepository.save(getSharedPreferences("lg_favs", MODE_PRIVATE)); }
    boolean isFav(File f) { return favoritesRepository.contains(f); }
    void toggleFav(File f) { favoritesRepository.toggle(f); saveFavs(); }
    void removeFavs(List<File> fs) { favoritesRepository.remove(fs); saveFavs(); }
    void renameFav(File from, File to) { favoritesRepository.rename(from, to); saveFavs(); }

    void busy(boolean on) {
        busyCount = Math.max(0, busyCount + (on ? 1 : -1));
        busyTv.setVisibility(busyCount > 0 ? View.VISIBLE : View.GONE);
        ToxiMotion.attach(busyTv);
        ToxiMotion.setActive(busyTv, busyCount > 0);
        if (busyCount == 1 && on) busyTv.setText("Working\u2026"); // reset text for ops that don't call progress()
    }

    /** Called from a background thread via runOnUiThread to show granular "N of M" progress. */

    /** Called from a background thread via runOnUiThread to show granular "N of M" progress. */
    void progress(String text) {
        busyTv.setText(text + " Â· tap to cancel");
        ToxiMotion.attach(busyTv);
        ToxiMotion.setActive(busyTv, true);
    }

    // ---------------- opening things ----------------
    void openDir(File dir) {
        closeOverlay();
        selectTab(1);
        filesView.showDir(dir);
    }

    void openFile(Entry e, List<Entry> ctx) { fileOpenCoordinator.open(e, ctx); }

    void showZip(File file) { fileOpenCoordinator.showZip(file); }

    void openCategory(int cat) {
        Indexer ix = Indexer.I;
        Indexer.PublishedSnapshot index = ix.snapshot();
        listView.setList(Indexer.CAT_NAMES[cat], index.ready ? index.cats.get(cat) : null, false, false);
        if (!index.ready) { pendingCat = cat; if (!ix.running) startIndex(); }
        showOverlay(listView);
    }

    void openSearch() {
        Indexer ix = Indexer.I;
        Indexer.PublishedSnapshot index = ix.snapshot();
        listView.setList("Search", index.ready ? index.all : null, true, true);
        if (!index.ready) { pendingCat = -2; if (!ix.running) startIndex(); }
        showOverlay(listView);
    }

    // ---------------- tabs / overlay / nav ----------------
    void selectTab(int i) { navigationController.selectTab(i); }

    View currentOverlay() { return overlayController == null ? null : overlayController.current(); }

    void showOverlay(View v) { overlayController.show(v); }

    void closeOverlay() { overlayController.close(); }

    void syncNavigationVisibility(boolean selecting) { navigationController.syncVisibility(selecting); }

    void navForSelection(boolean selecting) { syncNavigationVisibility(selecting); }

    private void handleBackNavigation() { navigationController.handleBackNavigation(); }

    void buildNav() { navigationController.build(); }

    void moveHighlight(int i) { navigationController.moveHighlight(i); }

    void applySystemBarAppearance() {
        if (Build.VERSION.SDK_INT < 23) return;
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) lightFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(ToxiVisualSystem.LIGHT_MODE ? (flags | lightFlags) : (flags & ~lightFlags));
    }

    void recolor() {
        root.setBackgroundColor(ToxiVisualSystem.CANVAS);
        applySystemBarAppearance();
        bg.invalidate();
        navigationController.recolor(tab);
        filesView.recolor();
        favView.recolor();
        listView.recolor();
        buildHome();
        buildTools();
        buildSettings();
        View activeOverlay = currentOverlay();
        if (vaultScroll != null && (vault.getChildCount() > 0 || activeOverlay == vaultScroll)) buildVault();
        if (recycleScroll != null && (recycle.getChildCount() > 0 || activeOverlay == recycleScroll)) buildRecycleBin();
        if (activeOverlay == themeScroll) buildThemeScreen();
        else if (activeOverlay == aboutScroll) {
            AboutScreenBuilder.populate(this, about, this::closeOverlay, versionName());
            aboutScroll.setBackgroundColor(AboutScreenBuilder.BACKGROUND);
        } else if (activeOverlay == favView) favView.refreshFavs();
        if (activeOverlay == listView) listView.recolor();
        if (activeOverlay != null) activeOverlay.invalidate();
    }

    // ---------------- HOME ----------------
    LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(l)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(t)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(r)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(b)));
        return p;
    }

    int dialogListHeight() {
        int available = getResources().getDisplayMetrics().heightPixels - ToxiVisualSystem.TOP_INSET
                - ToxiVisualSystem.BOTTOM_INSET - ToxiVisualSystem.IME_INSET - ToxiVisualSystem.dp(ToxiThemeEngine.spacing(260));
        return Math.max(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(180)), Math.min(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(520)), available));
    }

    void setLeadingIcon(TextView target, int symbol) {
        android.graphics.drawable.Drawable icon = ToxiIcons.symbol(symbol, ToxiVisualSystem.ACCENT);
        int size = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(17));
        icon.setBounds(0, 0, size, size);
        target.setCompoundDrawables(icon, null, null, null);
        target.setCompoundDrawablePadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
    }

    TextView section(String t) {
        TextView v = ToxiVisualSystem.text(this, t.toUpperCase(java.util.Locale.ROOT), 11.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        v.setLetterSpacing(0.08f);
        v.setTextColor(ToxiVisualSystem.signalTextColor());
        v.setText(t.toUpperCase(java.util.Locale.ROOT));
        setLeadingIcon(v, ToxiIcons.NEXT);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinHeight(ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48)));
        v.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)));
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
        v.setTextColor(ToxiVisualSystem.signalTextColor());
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinHeight(ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48)));
        v.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)));
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
        line.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        View marker = new View(this);
        marker.setBackground(ToxiVisualSystem.accentSurface(3));
        line.addView(marker, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16))));
        TextView label = ToxiVisualSystem.text(this, title, 15, ToxiVisualSystem.TEXT_PRIMARY, true);
        label.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0);
        line.addView(label, new LinearLayout.LayoutParams(-2, -2));
        View rule = new View(this);
        rule.setBackgroundColor(Color.argb(48, Color.red(ToxiVisualSystem.ACCENT), Color.green(ToxiVisualSystem.ACCENT), Color.blue(ToxiVisualSystem.ACCENT)));
        line.addView(rule, new LinearLayout.LayoutParams(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1)), 1f));
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
        menu.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
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
                group.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(i == 0 ? 4 : 14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)));
                menu.addView(group, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
            item.setBackground(ToxiVisualSystem.surface(16, false));
            item.setFocusable(true);
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(ToxiVisualSystem.text(this, names[i], 14, ToxiVisualSystem.TEXT_PRIMARY, true));
            TextView sub = ToxiVisualSystem.text(this, descriptions[i], 11.5f, ToxiVisualSystem.TEXT_SECONDARY, false);
            sub.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, 0);
            labels.addView(sub);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView arrow = new ImageView(this);
            arrow.setImageDrawable(ToxiIcons.symbol(ToxiIcons.NEXT, ToxiVisualSystem.TEXT_SECONDARY));
            arrow.setContentDescription("Open category");
            item.addView(arrow, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(30))));
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
        title.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)));
        TextView subtitle = ToxiVisualSystem.text(this, "Other files, APKs, apps and settings", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        subtitle.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(title);
        outer.addView(subtitle);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, dialogListHeight()));
        AlertDialog dialog = ToxiVisualSystem.dialogBuilder(this).setView(outer).setNegativeButton("Back", null).create();
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
        Indexer.PublishedSnapshot index = ix.snapshot();
        if (index.ready && index.cats.size() > 6) {
            for (Entry e : index.cats.get(6)) if (!e.dir && e.kind == FileKinds.APK) apks.add(e);
        }
        listView.setList("APK files", apks, false, false);
        if (!index.ready) {
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
        v.setCompoundDrawablePadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
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
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        for (ApplicationInfo ai : apps) {
            LinearLayout item = new LinearLayout(this); item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9))); item.setBackground(ToxiVisualSystem.surface(16, false));
            ImageView icon = new ImageView(this); try { icon.setImageDrawable(pm.getApplicationIcon(ai)); } catch (Exception ignored) {}
            item.addView(icon, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(32)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(32))));
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0, 0, 0);
            labels.addView(ToxiVisualSystem.text(this, String.valueOf(pm.getApplicationLabel(ai)), 14, ToxiVisualSystem.TEXT_PRIMARY, true));
            labels.addView(ToxiVisualSystem.text(this, ai.packageName, 10, ToxiVisualSystem.TEXT_SECONDARY, false));
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            item.setOnClickListener(v -> { try { Intent launch = pm.getLaunchIntentForPackage(ai.packageName); if (launch != null) startActivity(launch); else { Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ai.packageName)); startActivity(details); } } catch (Exception e) { ToxiVisualSystem.toast(this, "This system component has no launchable screen"); } });
            ToxiVisualSystem.interactive(item); list.addView(item, lpm(-1, -2, 0, 3, 0, 3));
        }
        if (apps.isEmpty()) list.addView(ToxiVisualSystem.text(this, "No launchable apps found in this section.", 13, ToxiVisualSystem.TEXT_SECONDARY, false));
        scroll.addView(list);
        TextView heading = ToxiVisualSystem.text(this, system ? "System Apps" : "Downloaded Apps", 23, ToxiVisualSystem.TEXT_PRIMARY, true);
        heading.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.addView(heading); outer.addView(scroll, new LinearLayout.LayoutParams(-1, dialogListHeight()));
        ToxiVisualSystem.dialogBuilder(this).setView(outer).setNegativeButton("Back", null).show();
    }

    void showBuiltInTools() {
        String[] tools = {"Dual-Pane File Explorer", "Professional Hex Editor", "Folder Comparator", "Character Encoding Converter", "Advanced ZIP Editor", "File Integrity Verification Studio", "Advanced Text Editor"};
        String[] details = {"Browse two folders side by side and copy, move or delete files.", "Inspect and edit binary data with hexadecimal offsets and ASCII.", "Compare folder trees and verify file content with SHA-256.", "Convert text between UTF-8, UTF-16, ASCII and legacy encodings.", "Rename, delete and add ZIP entries without extracting the archive.", "Calculate MD5, SHA-1, SHA-256, SHA-512 and CRC32.", "Regex search, encoding-aware save, syntax coloring and large-file limits."};
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        for (int i = 0; i < tools.length; i++) {
            final int index = i; LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13))); item.setBackground(ToxiVisualSystem.surface(16, true));
            item.addView(ToxiVisualSystem.text(this, tools[i], 14, ToxiVisualSystem.TEXT_PRIMARY, true));
            TextView detail = ToxiVisualSystem.text(this, details[i], 11.5f, ToxiVisualSystem.TEXT_SECONDARY, false); detail.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, 0); item.addView(detail);
            item.setOnClickListener(v -> { Intent launch = new Intent(this, ToxiToolsActivity.class); launch.putExtra("tool_index", index); startActivity(launch); });
            list.addView(item, lpm(-1, -2, 0, 3, 0, 3));
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(list); LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL);
        TextView heading = ToxiVisualSystem.text(this, "ToxiFiles built-in tools", 22, ToxiVisualSystem.TEXT_PRIMARY, true); heading.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)));
        outer.addView(heading); outer.addView(ToxiVisualSystem.text(this, "Seven offline utilities integrated into the existing file manager.", 12, ToxiVisualSystem.TEXT_SECONDARY, false)); outer.addView(scroll, new LinearLayout.LayoutParams(-1, dialogListHeight()));
        ToxiVisualSystem.dialogBuilder(this).setView(outer).setNegativeButton("Back", null).show();
    }

    void showCrashLogOptions() { crashReports.showCrashLogOptions(); }

    void showUsageDiagnostics() {
        String msg = "Usage & diagnostics (stored locally)\n\n" +
                "App version: " + versionName() + "\n" +
                "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Storage access: " + (hasAccess() ? "Granted" : "Not granted") + "\n" +
                "Crash report: " + (CrashLogger.hasReport(this) ? "Available on this device" : "None saved") + "\n\n" +
                "No usage data is uploaded by this screen. Diagnostics remain on this device unless you explicitly share a report.";
        ToxiVisualSystem.dialogBuilder(this).setTitle("Usage & diagnostics").setMessage(msg).setPositiveButton("View crash options", (d,w) -> crashReports.showCrashLogOptions()).setNegativeButton("Close", null).show();
    }

    // ---------------- SETTINGS ----------------
    View row(int glyph, String title, String sub, View right, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(ToxiVisualSystem.surface(18, false));
        row.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
        row.setMinimumHeight(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(sub == null ? 64 : 78)));
        FrameLayout symbolPlate = new FrameLayout(this);
        symbolPlate.setBackground(ToxiVisualSystem.surface(13, true));
        ImageView symbol = new ImageView(this);
        symbol.setImageDrawable(ToxiIcons.symbol(glyph, ToxiVisualSystem.ACCENT));
        symbol.setContentDescription(ToxiIcons.accessibilityLabel(glyph));
        symbolPlate.addView(symbol, new FrameLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), Gravity.CENTER));
        row.addView(symbolPlate, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40))));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1)));
        TextView titleView = ToxiVisualSystem.text(this, title, 14.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(titleView);
        if (sub != null && !sub.isEmpty()) {
            TextView detail = ToxiVisualSystem.text(this, sub, 11.5f, ToxiVisualSystem.TEXT_SECONDARY, false);
            detail.setMaxLines(2);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
            copy.addView(detail, detailLp);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        if (right != null) {
            row.addView(right, new LinearLayout.LayoutParams(-2, -2));
        } else if (listener != null) {
            ImageView chevron = new ImageView(this);
            chevron.setImageDrawable(ToxiIcons.symbol(ToxiIcons.NEXT, ToxiVisualSystem.TEXT_SECONDARY));
            chevron.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)));
            row.addView(chevron, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28))));
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
        s.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ToxiVisualSystem.signalTextColor(), ToxiVisualSystem.EDGE_STROKE}));
        s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Color.argb(120, Color.red(ToxiVisualSystem.ACCENT), Color.green(ToxiVisualSystem.ACCENT), Color.blue(ToxiVisualSystem.ACCENT)), ToxiVisualSystem.LIGHT_MODE ? Color.argb(180, Color.red(ToxiVisualSystem.EDGE_STROKE), Color.green(ToxiVisualSystem.EDGE_STROKE), Color.blue(ToxiVisualSystem.EDGE_STROKE)) : Color.argb(90, 255, 255, 255)}));
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

    void buildSettings() { settingsScreenController.build(); }

    // ---------------- STORAGE ANALYZER ----------------
    void openStorageAnalyzer() { storageAnalyzerController.open(); }

    void showPrivacyAudit() {
        String report = "OFFLINE & PRIVACY REVIEW\n\n"
                + "\u2022 File browsing and built-in viewing operate on local files.\n"
                + "\u2022 No account or cloud-sync feature is provided by this app.\n"
                + "\u2022 Wi-Fi transfer is a local-network feature; it requires network permission and both devices on a reachable network. It is not internet cloud storage.\n"
                + "\u2022 All-files access gives broad access to shared storage. Disable it in Android settings when you no longer need it.\n"
                + "\u2022 App Lock protects opening this app; Vault behavior and its limits are described separately in the Vault screen.\n"
                + "\u2022 Settings export creates a readable JSON file. Do not share it if it contains preferences you consider private.\n\n"
                + "This is an in-app permission/feature summary, not an independent network-traffic audit. To verify network behavior, inspect the source and test traffic on the device.";
        ToxiVisualSystem.dialogBuilder(this).setTitle("Privacy & network information")
                .setMessage(report).setPositiveButton("OK", null).show();
    }

    // ---------------- APP LOCK ----------------
    void setAppLockPin() { appLockScreen.showSetPinDialog(); }

    void showAppLock() { appLockScreen.showLockScreen(); }

    // ---------------- STORAGE MAINTENANCE ----------------
    void openDuplicates() { storageMaintenanceScreen.openDuplicates(); }

    void buildDuplicates(final List<DupFinder.Group> groups) { storageMaintenanceScreen.buildDuplicates(groups); }

    void openCleanup() { storageMaintenanceScreen.openCleanup(); }

    void buildEmptyFolders(final List<File> empties) { storageMaintenanceScreen.buildEmptyFolders(empties); }

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
        f.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), 0);
        f.addView(v, new FrameLayout.LayoutParams(-1, -2));
        return f;
    }

    void setVaultPin() { vaultScreenController.showSetPinDialog(); }

    void promptVaultPin() { vaultScreenController.promptUnlock(); }

    void buildVault() { vaultScreenController.build(); }

    void vaultItemMenu(final Vault.Item item) { vaultScreenController.showItemMenu(item); }

    // ---------------- WI-FI TRANSFER ----------------
    void receiveViaWifi() { lanTransferScreen.receive(); }

    void sendViaWifi(final List<File> files) { lanTransferScreen.send(files); }

    // ---------------- OTHER STORAGE (SAF, read-only import) ----------------
    void openOtherStorage() { safBrowserController.open(); }

    @Override protected void onActivityResult(int rq, int rc, Intent data) {
        super.onActivityResult(rq, rc, data);
        if (rc != RESULT_OK || data == null) return;
        if (rq == REQ_SAF_TREE) {
            safBrowserController.onTreeResult(data);
        } else if (rq == REQ_IMPORT_SETTINGS) {
            Uri file = data.getData();
            if (file != null) applyImportedSettings(file);
        }
    }

    boolean hasSafTree() { return safBrowserController != null && safBrowserController.hasTree(); }

    void copyToSafTree(final List<File> files) { safBrowserController.copyToSafTree(files); }

    void showSafLevel(final Uri tree, final Uri docUri, final String title) { safBrowserController.showSafLevel(tree, docUri, title); }

    void importSafDoc(final Uri doc, final String name) { safBrowserController.importSafDoc(doc, name); }

    // ---------------- SETTINGS BACKUP ----------------
    void exportSettings() {
        executeIo(() -> {
            try {
                final File exported = SettingsBackupManager.exportToDownloads(this, ToxiVisualSystem.PREFERENCES);
                runUiIfAlive(() -> {
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
                runUiIfAlive(() -> ToxiVisualSystem.toast(this, message));
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
        executeIo(() -> {
            String message;
            try {
                SettingsBackupManager.importFromUri(this, file, ToxiVisualSystem.PREFERENCES,
                        ToxiVisualSystem.THEME_NAMES.length);
                message = "Settings restored (security settings were not imported)";
            } catch (Exception failure) {
                message = "Import failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            }
            final String result = message;
            runUiIfAlive(() -> {
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
        Indexer.PublishedSnapshot index = ix.snapshot();
        if (!index.ready) { ToxiVisualSystem.toast(this, "Still scanning storage \u2014 try again in a moment"); return; }
        List<Entry> big = new ArrayList<>(index.all);
        java.util.Collections.sort(big, (x, y) -> Long.compare(y.len, x.len));
        if (big.size() > 300) big = big.subList(0, 300);
        listView.setList("Largest files", big, false, false);
        showOverlay(listView);
    }

    void retentionDialog() { recycleBinScreen.showRetentionDialog(); }

    // ---------------- RECYCLE BIN ----------------
    void showRecycleBin() { recycleBinScreen.show(); }

    void buildRecycleBin() { recycleBinScreen.reload(); }

    void recycleItemMenu(final RecycleBin.Item item) { recycleBinScreen.showItemMenu(item); }

    String versionName() {
        try { return "Version " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    void fontDialog() {
        String[] fonts = {"System Sans", "Rounded", "Serif", "Monospace"};
        ToxiVisualSystem.dialogBuilder(this).setTitle("Font style").setSingleChoiceItems(fonts, ToxiVisualSystem.PREFERENCES.getInt("font_family", 0), (d, which) -> {
            ToxiVisualSystem.PREFERENCES.edit().putInt("font_family", which).apply();
            d.dismiss();
            recreate();
        }).setNegativeButton("Cancel", null).show();
    }
    void themeDialog() { openThemeScreen(); }

    void openThemeScreen() { themeScreenController.open(); }

    void buildThemeScreen() { themeScreenController.build(); }

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
        final Paint auroraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int auroraWidth = -1, auroraHeight = -1, auroraAccent, auroraEnd;
        private boolean auroraLightMode;
        private Shader auroraPrimary, auroraSecondary, auroraTertiary;
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
                if (ToxiVisualSystem.booleanPreference("blur", false) && !lowRam) setRenderEffect(RenderEffect.createBlurEffect(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), Shader.TileMode.CLAMP));
                else setRenderEffect(null);
            }
        }

        private void ensureAurora(int width, int height) {
            int accent = ToxiVisualSystem.ACCENT;
            int accentEnd = ToxiThemeSystem.ACCENT_ENDS[ToxiVisualSystem.THEME_INDEX];
            if (width == auroraWidth && height == auroraHeight
                    && accent == auroraAccent && accentEnd == auroraEnd
                    && auroraLightMode == ToxiVisualSystem.LIGHT_MODE) return;
            auroraWidth = width;
            auroraHeight = height;
            auroraAccent = accent;
            auroraEnd = accentEnd;
            auroraLightMode = ToxiVisualSystem.LIGHT_MODE;
            float radius = Math.max(width, height) * 0.76f;
            int primaryAlpha = ToxiVisualSystem.LIGHT_MODE ? 24 : 42;
            int secondaryAlpha = ToxiVisualSystem.LIGHT_MODE ? 17 : 30;
            auroraPrimary = new RadialGradient(width * 0.18f, height * 0.22f, radius,
                    new int[]{Color.argb(primaryAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)),
                            Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
            auroraSecondary = new RadialGradient(width * 0.86f, height * 0.58f, radius * 0.82f,
                    new int[]{Color.argb(secondaryAlpha, Color.red(accentEnd), Color.green(accentEnd), Color.blue(accentEnd)),
                            Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
            int blended = ToxiIcons.mix(accent, accentEnd, 0.5f);
            auroraTertiary = new RadialGradient(width * 0.52f, height * 0.94f, radius * 0.58f,
                    new int[]{Color.argb(ToxiVisualSystem.LIGHT_MODE ? 10 : 18,
                            Color.red(blended), Color.green(blended), Color.blue(blended)),
                            Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        }

        private void drawAurora(Canvas canvas, int width, int height) {
            ensureAurora(width, height);
            auroraPaint.setShader(auroraPrimary);
            canvas.drawRect(0, 0, width, height, auroraPaint);
            auroraPaint.setShader(auroraSecondary);
            canvas.drawRect(0, 0, width, height, auroraPaint);
            auroraPaint.setShader(auroraTertiary);
            canvas.drawRect(0, 0, width, height, auroraPaint);
            auroraPaint.setShader(null);
        }

        private void drawThemePattern(Canvas canvas, int width, int height, float step) {
            switch (ToxiVisualSystem.THEME_INDEX) {
                case 0: // Grand Voyage: layered rolling ocean contours and distant horizon.
                    android.graphics.Path ocean = new android.graphics.Path();
                    float horizon = height * .43f;
                    canvas.drawLine(0, horizon, width, horizon, gridPaint);
                    for (int row = 0; row < 7; row++) {
                        float base = horizon + step * (.35f + row * .52f);
                        ocean.reset();
                        ocean.moveTo(0, base);
                        for (float x = 0; x <= width; x += step * .4f)
                            ocean.lineTo(x, base + (float) Math.sin(x / step * .85f + row * .72f) * step * .17f);
                        canvas.drawPath(ocean, gridPaint);
                    }
                    break;
                case 1: // Shinobi Spirit: chakra-ring arcs and controlled diagonal wind paths.
                    for (float radius = step * .65f; radius < Math.max(width, height); radius += step * 1.65f)
                        canvas.drawCircle(width * .72f, height * .28f, radius, gridPaint);
                    for (float offset = -height; offset < width; offset += step * 2.5f)
                        canvas.drawLine(offset, 0, offset + height * .58f, height, gridPaint);
                    break;
                case 2: // Breathing Legends: flowing blade arcs and flame-like angled lines.
                    for (float offset = -height; offset < width; offset += step * 1.8f) {
                        android.graphics.Path slash = new android.graphics.Path();
                        slash.moveTo(offset, height);
                        slash.cubicTo(offset + step, height * .68f, offset + height * .25f, height * .22f,
                                offset + height * .48f, 0);
                        canvas.drawPath(slash, gridPaint);
                    }
                    break;
                case 3: // Ultra Instinct: radial energy field with concentric kinetic rings.
                    for (float radius = step * .7f; radius < Math.max(width, height); radius += step * 1.55f)
                        canvas.drawCircle(width * .5f, height * .47f, radius, gridPaint);
                    for (float angle = 0; angle < 360; angle += 24) {
                        double radians = Math.toRadians(angle);
                        canvas.drawLine(width * .5f, height * .47f,
                                width * .5f + (float) Math.cos(radians) * width,
                                height * .47f + (float) Math.sin(radians) * height, gridPaint);
                    }
                    break;
                default: // Titan Frontier: fortress wall bands, vertical battlements and horizon.
                    float wall = height * .62f;
                    canvas.drawLine(0, wall, width, wall, gridPaint);
                    canvas.drawLine(0, wall + step * .10f, width, wall + step * .10f, gridPaint);
                    for (float x = step * .35f; x < width; x += step * 1.65f) {
                        canvas.drawLine(x, wall - step * .48f, x, height, gridPaint);
                        canvas.drawLine(x - step * .18f, wall - step * .48f, x + step * .18f, wall - step * .48f, gridPaint);
                    }
                    for (float y = wall + step * .65f; y < height; y += step * 1.2f)
                        canvas.drawLine(0, y, width, y, gridPaint);
                    break;
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            int width = getWidth(), height = getHeight();
            if (width <= 0 || height <= 0) return;
            canvas.drawColor(ToxiVisualSystem.CANVAS);
            drawAurora(canvas, width, height);
            int accent = ToxiVisualSystem.ACCENT;
            gridPaint.setColor(Color.argb(10, Color.red(accent), Color.green(accent), Color.blue(accent)));
            gridPaint.setStrokeWidth(Math.max(1f, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(0.65f))));
            float step = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48));
            drawThemePattern(canvas, width, height, step);
            markerPaint.setColor(Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent)));
            markerPaint.setStrokeWidth(Math.max(1f, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1.1f))));
            float inset = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), mark = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(38));
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
            float sw = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)), pad = sw / 2f + ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2));
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
            t.setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), Typeface.BOLD));
            t.setTextSize(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)));
            c.drawText(big, getWidth() / 2f, getHeight() / 2f + ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), t);
            t.setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), Typeface.NORMAL));
            t.setTextSize(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)));
            t.setColor(ToxiVisualSystem.TEXT_SECONDARY);
            c.drawText(small, getWidth() / 2f, getHeight() / 2f + ToxiVisualSystem.dp(ToxiThemeEngine.spacing(21)), t);
        }
    }
}
