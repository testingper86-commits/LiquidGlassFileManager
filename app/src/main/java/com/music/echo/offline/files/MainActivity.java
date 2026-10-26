package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

public class MainActivity extends Activity {
    final ExecutorService io = Executors.newFixedThreadPool(2);
    final ExecutorService ops = Executors.newSingleThreadExecutor();
    final Set<String> favs = new LinkedHashSet<>();
    File[] clip;
    boolean clipCut;

    FrameLayout root, content;
    BgView bg;
    ScrollView homeScroll, settingsScroll, aboutScroll, recycleScroll, vaultScroll, themeScroll;
    LinearLayout home, settings, about, recycle, vault;
    static final int REQ_SAF_TREE = 501, REQ_IMPORT_SETTINGS = 502;
    Uri safTree;
    ScrollView dupScroll;
    LinearLayout dup;
    boolean unlocked = false;
    BrowserView filesView, favView, listView;
    View[] screens;
    FrameLayout nav;
    View hl;
    ImageView[] navIc = new ImageView[3];
    TextView[] navTx = new TextView[3];
    TextView busyTv;
    int busyCount, tab = 0, pendingCat = -1;
    View overlayView;
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
        setTheme(lightPref ? R.style.AppTheme_Light : R.style.AppTheme);
        super.onCreate(b);
        IntentFilter screenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOffReceiver, screenOffFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOffReceiver, screenOffFilter);
        Ui.init(this);
        String savedSafTree = getSharedPreferences("lg_storage", MODE_PRIVATE).getString("saf_tree", null);
        if (savedSafTree != null) safTree = Uri.parse(savedSafTree);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        SharedPreferences fp = getSharedPreferences("lg_favs", MODE_PRIVATE);
        favs.addAll(fp.getStringSet("paths", new HashSet<String>()));

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.bg);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        bg = new BgView(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        homeScroll = new ScrollView(this);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeScroll.setClipToPadding(false);
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        homeScroll.addView(home, new FrameLayout.LayoutParams(-1, -2));
        settingsScroll = new ScrollView(this);
        settingsScroll.setVerticalScrollBarEnabled(false);
        settingsScroll.setClipToPadding(false);
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
        favView = new BrowserView(this, false, true);
        listView = new BrowserView(this, true, false);
        screens = new View[]{homeScroll, filesView, favView, settingsScroll};
        View[] all = {homeScroll, filesView, favView, settingsScroll, listView, aboutScroll, recycleScroll, vaultScroll, dupScroll, themeScroll};
        for (View v : all) { content.addView(v, new FrameLayout.LayoutParams(-1, -1)); v.setVisibility(View.GONE); }

        buildNav();
        busyTv = Ui.tv(this, "Working…", 13, Ui.WHITE, true);
        busyTv.setBackground(Ui.accentPill(20));
        busyTv.setPadding(Ui.dp(18), Ui.dp(10), Ui.dp(18), Ui.dp(10));
        busyTv.setVisibility(View.GONE);
        busyTv.setContentDescription("Operation progress. Tap to request cancellation.");
        busyTv.setOnClickListener(v -> { FileOps.requestCancel(); LanTransfer.cancel(); Ui.toast(this, "Cancellation requested"); });
        root.addView(busyTv, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Ui.top = in.top; Ui.bottom = in.bottom;
            } else { Ui.top = insets.getSystemWindowInsetTop(); Ui.bottom = insets.getSystemWindowInsetBottom(); }
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
        content.setPadding(0, Ui.top, 0, 0);
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) nav.getLayoutParams();
        np.bottomMargin = Ui.bottom + Ui.dp(12);
        nav.setLayoutParams(np);
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) busyTv.getLayoutParams();
        bp.topMargin = Ui.top + Ui.dp(8);
        busyTv.setLayoutParams(bp);
        int pad = Ui.dp(120) + Ui.bottom;
        homeScroll.setPadding(0, 0, 0, pad);
        settingsScroll.setPadding(0, 0, 0, pad);
        aboutScroll.setPadding(0, 0, 0, Ui.dp(24) + Ui.bottom);
        recycleScroll.setPadding(0, 0, 0, Ui.dp(24) + Ui.bottom);
        vaultScroll.setPadding(0, 0, 0, Ui.dp(24) + Ui.bottom);
        dupScroll.setPadding(0, 0, 0, Ui.dp(24) + Ui.bottom);
        filesView.applyInsets(); favView.applyInsets(); listView.applyInsets();
    }

    // ---------------- permissions ----------------
    boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23) return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
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
                        catch (Exception e2) { Ui.toast(this, "Open Settings > Apps > Special access > All files access"); }
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
        io.shutdown();
        ops.shutdown();
    }

    // ---------------- index ----------------
    void startIndex() {
        if (!hasAccess()) return;
        Indexer.I.start(this::onIndexDone);
        if (tab == 0 && overlayView == null) buildHome();
    }

    void onIndexDone() {
        String[] names = Indexer.CAT_NAMES;
        if (pendingCat >= 0 && pendingCat < 6) { listView.setList(names[pendingCat], Indexer.I.cats.get(pendingCat), false, false); pendingCat = -1; }
        else if (pendingCat == -2) { listView.setList("Search", Indexer.I.all, true, true); pendingCat = -1; }
        if (tab == 0 && overlayView == null) buildHome();
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
        GlowRing.attach(busyTv);
        GlowRing.setActive(busyTv, busyCount > 0);
        if (busyCount == 1 && on) busyTv.setText("Working…"); // reset text for ops that don't call progress()
    }

    /** Called from a background thread via runOnUiThread to show granular "N of M" progress. */
    void progress(String text) { busyTv.setText(text + " · tap to cancel"); GlowRing.attach(busyTv); GlowRing.setActive(busyTv, true); }

    // ---------------- opening things ----------------
    void openDir(File dir) {
        closeOverlay();
        selectTab(1);
        filesView.showDir(dir);
    }

    void openFile(Entry e, List<Entry> ctx) {
        if (e.dir) { openDir(e.f); return; }
        int k = e.kind;
        boolean inApp = Ui.bool("inapp", true);
        if (inApp && (k == FileKinds.IMAGE || k == FileKinds.VIDEO || k == FileKinds.AUDIO || k == FileKinds.TEXT || k == FileKinds.PDF)) {
            ArrayList<String> ps = new ArrayList<>();
            int idx = 0;
            if (k == FileKinds.IMAGE || k == FileKinds.VIDEO || k == FileKinds.AUDIO) {
                for (Entry x : ctx) if (x.kind == k) { if (x == e) idx = ps.size(); ps.add(x.f.getAbsolutePath()); }
                if (ps.size() > 400) {
                    int from = Math.max(0, Math.min(idx - 200, ps.size() - 400));
                    ps = new ArrayList<>(ps.subList(from, from + 400));
                    idx -= from;
                }
            } else ps.add(e.f.getAbsolutePath());
            Intent i = new Intent(this, ViewerActivity.class);
            i.putStringArrayListExtra("paths", ps);
            i.putExtra("index", idx);
            startActivity(i);
            return;
        }
        if (k == FileKinds.ARCHIVE && e.ext.equals("zip")) { showZip(e.f); return; }
        if (k == FileKinds.APK) { FileOps.install(this, e.f); return; }
        FileOps.external(this, e.f, false);
    }

    void showZip(final File z) {
        try {
            List<String> names = FileOps.listZip(z);
            android.widget.ListView lv = new android.widget.ListView(this);
            lv.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            new AlertDialog.Builder(this).setTitle(z.getName()).setView(lv).setNegativeButton("Close", null)
                    .setPositiveButton("Extract", (d, w) -> FileOps.unzip(this, z, msg -> { Ui.toast(this, msg); afterOps(); })).show();
        } catch (Exception ex) {
            Ui.toast(this, "Can't read this ZIP file");
        }
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
        nav.setVisibility(i == 3 ? View.GONE : View.VISIBLE);
        for (int k = 0; k < screens.length; k++) {
            screens[k].animate().cancel();
            screens[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
            screens[k].setAlpha(1f);
            screens[k].setTranslationY(0);
        }
        View s = screens[i];
        if (Ui.bool("reduce_motion", true)) { s.setAlpha(1f); s.setTranslationY(0f); }
        else { s.setAlpha(0f); s.setTranslationY(Ui.dp(8)); s.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start(); }
        if (i == 0) buildHome();
        else if (i == 1) {
            if (filesView.cur == null) {
                String def = Ui.sp.getString("default_folder", null);
                File start = (def != null && new File(def).isDirectory()) ? new File(def) : Environment.getExternalStorageDirectory();
                filesView.showDir(start);
            }
        }
        else if (i == 2) favView.refreshFavs();
        else buildSettings();
        moveHighlight(i);
    }

    void showOverlay(View v) {
        overlayView = v;
        if (v == vaultScroll) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 31 && !Ui.highContrast) {
            screens[tab].setRenderEffect(android.graphics.RenderEffect.createBlurEffect(Ui.dp(18), Ui.dp(18), android.graphics.Shader.TileMode.CLAMP));
        }
        screens[tab].setVisibility(View.VISIBLE);
        v.setVisibility(View.VISIBLE);
        if (Ui.bool("reduce_motion", true)) { v.setAlpha(1f); v.setTranslationY(0f); }
        else { v.setAlpha(0f); v.setTranslationY(Ui.dp(8)); v.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start(); }
        nav.setVisibility(v == listView ? View.GONE : View.VISIBLE);
    }

    void closeOverlay() {
        if (overlayView == null) return;
        boolean wasVault = overlayView == vaultScroll;
        if (overlayView == listView) { listView.closeSearch(); listView.selecting = false; listView.sel.clear(); pendingCat = -1; }
        overlayView.setVisibility(View.GONE);
        overlayView = null;
        if (wasVault) getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 31) screens[tab].setRenderEffect(null);
        screens[tab].setVisibility(View.VISIBLE);
        nav.setVisibility(View.VISIBLE);
        if (tab == 0) buildHome();
    }

    void navForSelection(boolean selecting) { nav.setVisibility(selecting || overlayView == listView ? View.GONE : View.VISIBLE); }

    @Override public void onBackPressed() {
        if (overlayView == listView) { listView.handleBack(); return; }
        if (overlayView != null) { closeOverlay(); return; }
        if (tab == 1 && filesView.handleBack()) return;
        if (tab == 2 && favView.handleBack()) return;
        if (tab != 0) { selectTab(0); return; }
        super.onBackPressed();
    }

    void buildNav() {
        nav = new FrameLayout(this);
        nav.setBackground(Ui.glass(30, true));
        nav.setElevation(Ui.dp(12));
        nav.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        hl = new View(this);
        hl.setBackground(Ui.accentPill(24));
        nav.addView(hl, new FrameLayout.LayoutParams(0, -1));
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(items, new FrameLayout.LayoutParams(-1, -1));
        int[] gl = {Glyphs.HOME, Glyphs.FOLDER};
        String[] lb = {"Home", "Files"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            LinearLayout it = new LinearLayout(this);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setGravity(Gravity.CENTER);
            navIc[i] = new ImageView(this);
            navTx[i] = Ui.tv(this, lb[i], 10.5f, Ui.SUB, false);
            navIc[i].setImageDrawable(Glyphs.get(gl[i], Ui.WHITE));
            Ui.ringIcon(navIc[i]);
            navIc[i].setContentDescription(lb[i]);
            it.addView(navIc[i], new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.topMargin = Ui.dp(3);
            it.addView(navTx[i], tp);
            it.setOnClickListener(v -> {
                Ui.tick(v);
                selectTab(idx);
            });
            Ui.press(it);
            items.addView(it, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, Ui.dp(66), Gravity.BOTTOM);
        lp.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(12));
        root.addView(nav, lp);
        nav.addOnLayoutChangeListener((v, l, t, r, b, l0, t0, r0, b0) -> {
            int iw = (r - l - Ui.dp(12)) / 2;
            FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) hl.getLayoutParams();
            if (hp.width != iw) { hp.width = iw; hl.setLayoutParams(hp); }
            hl.setTranslationX(Math.min(tab, 1) * iw);
        });
    }

    void moveHighlight(int i) {
        int iw = hl.getLayoutParams().width;
        int visualIndex = Math.min(i, 1);
        if (Ui.bool("reduce_motion", true)) hl.setTranslationX(visualIndex * iw);
        else hl.animate().translationX(visualIndex * iw).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        for (int k = 0; k < 2; k++) navTx[k].setTextColor(k == visualIndex ? Ui.WHITE : Ui.SUB);
    }

    void recolor() {
        root.setBackgroundColor(Ui.bg);
        bg.invalidate();
        hl.setBackground(Ui.accentPill(24));
        filesView.ad.notifyDataSetChanged();
        favView.ad.notifyDataSetChanged();
        listView.ad.notifyDataSetChanged();
        buildHome();
        buildSettings();
        if (vault.getChildCount() > 0) buildVault();
        if (recycle.getChildCount() > 0) buildRecycleBin();
    }

    // ---------------- HOME ----------------
    LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(Ui.dp(l), Ui.dp(t), Ui.dp(r), Ui.dp(b));
        return p;
    }

    TextView section(String t) {
        TextView v = Ui.tv(this, t.toUpperCase(java.util.Locale.ROOT), 11.5f, Ui.WHITE, true);
        v.setLetterSpacing(0.08f);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(14), Ui.dp(11));
        v.setBackground(Ui.glass(16, false));
        LinearLayout.LayoutParams p = lpm(-1, -2, 16, 18, 16, 8);
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
            v.setText((anyVisible ? "＋  " : "⌄  ") + t.toUpperCase(java.util.Locale.ROOT));
            Ui.tick(v);
        });
        return v;
    }

    void buildHome() {
        home.removeAllViews();
        Indexer ix = Indexer.I;
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(8), 0);
        hd.addView(Ui.tv(this, "Your files", 30, Ui.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView settingsBtn = Ui.iconBtn(this, Glyphs.SETTINGS, Ui.WHITE);
        settingsBtn.setContentDescription("Settings");
        settingsBtn.setOnClickListener(v -> selectTab(3));
        hd.addView(settingsBtn, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        home.addView(hd, new LinearLayout.LayoutParams(-1, Ui.dp(60)));
        TextView homeSub = Ui.tv(this, "Everything on your device, beautifully organized", 12.5f, Ui.SUB, false);
        homeSub.setPadding(Ui.dp(22), 0, Ui.dp(20), Ui.dp(10));
        home.addView(homeSub);
        if (ix.ready && ix.truncated) {
            TextView limitNotice = Ui.tv(this, "Large library: quick search and overview are limited to the first 150,000 indexed entries. Open folders directly to browse remaining files.", 12, Ui.SUB, false);
            limitNotice.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
            limitNotice.setBackground(Ui.glass(16, false));
            limitNotice.setContentDescription("Indexing limit notice. Some files may not appear in global search.");
            home.addView(limitNotice, lpm(-1, -2, 16, 4, 16, 8));
        }

        if (!hasAccess()) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setBackground(Ui.glass(24, true));
            c.setPadding(Ui.dp(20), Ui.dp(20), Ui.dp(20), Ui.dp(20));
            c.addView(Ui.tv(this, "Allow access to your files", 18, Ui.WHITE, true));
            TextView d = Ui.tv(this, "Grant \"All files access\" so LiquidFiles can browse, open and manage files on your phone.", 13, Ui.SUB, false);
            d.setPadding(0, Ui.dp(8), 0, Ui.dp(14));
            c.addView(d);
            TextView btn = Ui.tv(this, "Grant access", 14, Ui.WHITE, true);
            btn.setBackground(Ui.accentPill(22));
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, Ui.dp(13), 0, Ui.dp(13));
            btn.setOnClickListener(v -> showStorageAccess());
            Ui.press(btn);
            c.addView(btn, new LinearLayout.LayoutParams(-1, -2));
            home.addView(c, lpm(-1, -2, 16, 12, 16, 0));
            return;
        }

        // storage card
        File root = Environment.getExternalStorageDirectory();
        long total = root.getTotalSpace(), free = root.getFreeSpace(), used = Math.max(0, total - free);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.glass(26, true));
        card.setElevation(Ui.dp(5));
        card.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int[] cc = {Color.rgb(46, 196, 140), Color.rgb(236, 72, 130), Color.rgb(160, 90, 250), Color.rgb(64, 130, 245), Color.rgb(255, 176, 64)};
        long[] seg = new long[5];
        if (ix.ready) {
            seg[0] = ix.sizes[0]; seg[1] = ix.sizes[1]; seg[2] = ix.sizes[2]; seg[3] = ix.sizes[3];
            seg[4] = ix.sizes[4] + ix.sizes[5] + ix.sizes[6];
        }
        DonutView dv = new DonutView(this);
        float[] parts = new float[5];
        if (total > 0) for (int i = 0; i < 5; i++) parts[i] = (float) ((double) seg[i] / total);
        if (!ix.ready && total > 0) { parts = new float[]{(float) ((double) used / total)}; cc = new int[]{Ui.accent}; }
        dv.set(parts, cc, total > 0 ? (int) (used * 100 / total) + "%" : "—", "used");
        top.addView(dv, new LinearLayout.LayoutParams(Ui.dp(104), Ui.dp(104)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(16), 0, 0, 0);
        info.addView(Ui.tv(this, "Main storage", 17, Ui.WHITE, true));
        TextView u = Ui.tv(this, Ui.size(used) + " of " + Ui.size(total), 13, Ui.SUB, false);
        u.setPadding(0, Ui.dp(6), 0, 0);
        info.addView(u);
        TextView fr = Ui.tv(this, Ui.size(free) + " free", 14, Ui.accent, true);
        fr.setPadding(0, Ui.dp(6), 0, 0);
        info.addView(fr);
        if (ix.running) {
            TextView sc = Ui.tv(this, "Scanning files…", 12, Ui.SUB, false);
            sc.setPadding(0, Ui.dp(8), 0, 0);
            info.addView(sc);
        }
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));
        if (ix.ready) {
            LinearLayout lg = new LinearLayout(this);
            String[] ln = {"Images", "Videos", "Audio", "Docs", "Other"};
            for (int i = 0; i < 5; i++) {
                LinearLayout c1 = new LinearLayout(this);
                c1.setOrientation(LinearLayout.VERTICAL);
                c1.setGravity(Gravity.CENTER);
                TextView n = Ui.tv(this, "● " + ln[i], 10.5f, Ui.SUB, false);
                SpannableHelper.dot(n, cc[i]);
                TextView sz = Ui.tv(this, Ui.size(seg[i]), 11, Ui.WHITE, true);
                sz.setPadding(0, Ui.dp(3), 0, 0);
                c1.addView(n);
                c1.addView(sz);
                lg.addView(c1, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            card.addView(lg, lpm(-1, -2, 0, 14, 0, 0));
        }
        card.setContentDescription("Internal storage usage. " + Ui.size(used) + " used of " + Ui.size(total) + ". Tap to open storage analysis.");
        card.setFocusable(true);
        card.setOnClickListener(v -> openStorageAnalyzer());
        home.addView(card, lpm(-1, -2, 16, 6, 16, 0));

        // categories
        home.addView(section("Quick access"));
        // Keep every major media type directly accessible from Home, including audio.
        int[] categoryIndices = {0, 1, 2, 3, 4, 6};
        int[] categoryKinds = {FileKinds.IMAGE, FileKinds.VIDEO, FileKinds.AUDIO, FileKinds.DOC, FileKinds.ARCHIVE, FileKinds.OTHER};
        for (int r = 0; r < (categoryIndices.length + 2) / 3; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 3; c++) {
                final int position = r * 3 + c;
                if (position >= categoryIndices.length) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(1), 1f));
                    continue;
                }
                final int cat = categoryIndices[position];
                LinearLayout t = new LinearLayout(this);
                t.setOrientation(LinearLayout.VERTICAL);
                t.setBackground(Ui.glass(20, true));
                t.setElevation(Ui.dp(3));
                t.setPadding(Ui.dp(13), Ui.dp(14), Ui.dp(11), Ui.dp(14));
                ImageView ic = new ImageView(this);
                ic.setImageDrawable(cat == -1 ? Glyphs.get(Glyphs.SHIELD, Ui.accent) : Glyphs.tile(categoryKinds[position]));
                Ui.ringIcon(ic);
                t.addView(ic, new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(38)));
                String categoryName = Indexer.CAT_NAMES[cat];
                String categorySub = ix.ready ? ix.counts[cat] + " files" : "…";
                TextView nm = Ui.tv(this, categoryName, 14, Ui.WHITE, true);
                nm.setPadding(0, Ui.dp(12), 0, 0);
                t.addView(nm);
                TextView sub = Ui.tv(this, categorySub, 11.5f, Ui.SUB, false);
                sub.setPadding(0, Ui.dp(3), 0, 0);
                t.addView(sub);
                t.setContentDescription(categoryName + ", " + categorySub + ". Open category");
                t.setFocusable(true);
                t.setOnClickListener(v -> {
                    Ui.tick(v);
                    if (cat == 6) new AlertDialog.Builder(MainActivity.this).setTitle("Other")
                            .setItems(new String[]{"Other files and APKs", "System Apps", "Downloaded Apps"}, (dialog, which) -> { if (which == 0) openCategory(6); else showInstalledApps(which == 1); }).show();
                    else openCategory(cat);
                });
                Ui.press(t);
                row.addView(t, lpm(0, -2, c == 0 ? 16 : 4, 0, c == 2 ? 16 : 4, 8));
                ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
            }
            home.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        // Quick-file shortcuts removed at the user's request; browse through the organized categories or file browser.

        // Recent-files panel intentionally omitted for a cleaner, section-first home screen.
    }

    TextView appLaunchButton(String title, String subtitle, int glyph) {
        TextView v = Ui.tv(this, "", 14, Ui.WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Ui.glass(18, true));
        v.setText(title + "\n" + subtitle);
        v.setCompoundDrawablesWithIntrinsicBounds(Glyphs.get(glyph, Ui.accent), null, null, null);
        v.setCompoundDrawablePadding(Ui.dp(8));
        v.setOnTouchListener((view, event) -> { if (event.getAction() == android.view.MotionEvent.ACTION_UP) Ui.tick(view); return false; });
        return v;
    }

    void showInstalledApps(boolean system) {
        List<ApplicationInfo> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Set<String> hidden = Ui.sp.getStringSet("dev_hidden_apps", new HashSet<String>());
        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystem == system && !hidden.contains(ai.packageName) && (system || pm.getLaunchIntentForPackage(ai.packageName) != null)) apps.add(ai);
        }
        java.util.Collections.sort(apps, (a,b) -> String.valueOf(pm.getApplicationLabel(a)).compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b))));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        for (ApplicationInfo ai : apps) {
            LinearLayout item = new LinearLayout(this); item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(Ui.dp(10), Ui.dp(9), Ui.dp(10), Ui.dp(9)); item.setBackground(Ui.glass(16, false));
            ImageView icon = new ImageView(this); try { icon.setImageDrawable(pm.getApplicationIcon(ai)); } catch (Exception ignored) {}
            item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(38)));
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(Ui.dp(12), 0, 0, 0);
            labels.addView(Ui.tv(this, String.valueOf(pm.getApplicationLabel(ai)), 14, Ui.WHITE, true));
            labels.addView(Ui.tv(this, ai.packageName, 10, Ui.SUB, false));
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            item.setOnClickListener(v -> { try { Intent launch = pm.getLaunchIntentForPackage(ai.packageName); if (launch != null) startActivity(launch); else { Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ai.packageName)); startActivity(details); } } catch (Exception e) { Ui.toast(this, "This system component has no launchable screen"); } });
            Ui.press(item); list.addView(item, lpm(-1, -2, 0, 3, 0, 3));
        }
        if (apps.isEmpty()) list.addView(Ui.tv(this, "No launchable apps found in this section.", 13, Ui.SUB, false));
        scroll.addView(list);
        TextView heading = Ui.tv(this, system ? "System Apps" : "Downloaded Apps", 23, Ui.WHITE, true);
        heading.setPadding(Ui.dp(18), Ui.dp(18), Ui.dp(18), Ui.dp(8));
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.addView(heading); outer.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(430)));
        new AlertDialog.Builder(this).setView(outer).setNegativeButton("Close", null).show();
    }

    void showBuiltInTools() {
        String[] tools = {
                "Built-in image viewer and image grid",
                "Built-in video player",
                "Built-in music player",
                "Built-in text editor",
                "ZIP archive inspection and extraction",
                "PDF and document preview"
        };
        String[] details = {
                "Opens image files inside ToxiFiles and displays image collections in a grid.",
                "Plays supported local video files inside ToxiFiles.",
                "Plays supported local audio files inside ToxiFiles.",
                "Edits supported text files up to 1 MB and saves changes to the file.",
                "Inspects ZIP contents and extracts supported ZIP archives.",
                "Previews supported PDF and text documents inside the file manager."
        };
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        int[] icons = {Glyphs.IMAGE, Glyphs.VIDEO, Glyphs.AUDIO, Glyphs.DOC, Glyphs.ARCHIVE, Glyphs.FILE};
        for (int i = 0; i < tools.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(12));
            item.setBackground(Ui.glass(16, true));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(Glyphs.get(icons[i], Ui.accent));
            item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(30), Ui.dp(30)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(Ui.dp(12), 0, 0, 0);
            copy.addView(Ui.tv(this, tools[i], 14, Ui.WHITE, true));
            TextView sub = Ui.tv(this, details[i], 11.5f, Ui.SUB, false);
            sub.setPadding(0, Ui.dp(4), 0, 0);
            copy.addView(sub);
            item.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
            list.addView(item, lpm(-1, -2, 0, 3, 0, 3));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        TextView heading = Ui.tv(this, "ToxiFiles built-in tools", 22, Ui.WHITE, true);
        heading.setPadding(Ui.dp(18), Ui.dp(18), Ui.dp(18), Ui.dp(6));
        TextView note = Ui.tv(this, "These features run inside the file manager. This list does not launch or manage your phone's separate apps.", 12, Ui.SUB, false);
        note.setPadding(Ui.dp(18), 0, Ui.dp(18), Ui.dp(10));
        outer.addView(heading);
        outer.addView(note);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(430)));
        new AlertDialog.Builder(this).setView(outer).setNegativeButton("Close", null).show();
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
    View row(int glyph, String title, String sub, View right, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.glass(20, true));
        r.setElevation(Ui.dp(2));
        r.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(Ui.accentPill(12));
        ImageView g = new ImageView(this);
        g.setImageDrawable(Glyphs.get(glyph, Ui.WHITE));
        Ui.ringIcon(g);
        ic.addView(g, new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.dp(14), 0, Ui.dp(8), 0);
        TextView titleView = Ui.tv(this, title, 15, Ui.WHITE, false);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(titleView);
        if (sub != null) {
            TextView s = Ui.tv(this, sub, 12, Ui.SUB, false);
            s.setMaxLines(2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setPadding(0, Ui.dp(3), 0, 0);
            tx.addView(s);
        }
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        if (right != null) r.addView(right);
        if (l != null) {
            GlowRing.attach(r);
            GlowRing.setActive(r, false);
            r.setOnClickListener(v -> {
                GlowRing.setActive(r, true);
                l.onClick(v);
                r.postDelayed(() -> GlowRing.setActive(r, false), 420L);
            });
            Ui.press(r);
        }
        return r;
    }

    Switch sw(final String key, boolean def, final Runnable onChange) {
        Switch s = new Switch(this);
        s.setChecked(Ui.bool(key, def));
        s.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.accent, Color.rgb(200, 205, 215)}));
        s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Color.argb(120, Color.red(Ui.accent), Color.green(Ui.accent), Color.blue(Ui.accent)), Color.argb(90, 255, 255, 255)}));
        GlowRing.attach(s);
        GlowRing.setActive(s, false);
        s.setOnCheckedChangeListener((b, on) -> {
            GlowRing.setActive(s, true);
            Ui.set(key, on);
            if (onChange != null) onChange.run();
            s.postDelayed(() -> GlowRing.setActive(s, false), 420L);
        });
        return s;
    }

    View switchRow(int glyph, String title, String sub, String key, boolean def, Runnable onChange) {
        final Switch s = sw(key, def, onChange);
        return row(glyph, title, sub, s, v -> s.toggle());
    }

    void buildSettings() {
        settings.removeAllViews();
        TextView t = Ui.tv(this, "Settings", 32, Ui.WHITE, true);
        t.setPadding(Ui.dp(20), Ui.dp(18), Ui.dp(20), Ui.dp(2));
        settings.addView(t);
        t.setOnClickListener(v -> {
            int taps = Ui.sp.getInt("developer_unlock_taps", 0) + 1;
            if (taps >= 7) {
                Ui.sp.edit().putBoolean("developer_options_unlocked", true).putInt("developer_unlock_taps", 0).apply();
                Ui.toast(this, "Developer options unlocked");
                buildSettings();
            } else {
                Ui.sp.edit().putInt("developer_unlock_taps", taps).apply();
                if (taps >= 4) Ui.toast(this, (7 - taps) + " more taps to unlock developer options");
            }
        });
        TextView intro = Ui.tv(this, "Make LiquidFiles work your way", 13, Ui.SUB, false);
        intro.setPadding(Ui.dp(22), 0, Ui.dp(20), Ui.dp(8));
        settings.addView(intro);
        TextView hint = Ui.tv(this, "Choose a section to reveal its controls", 11.5f, Ui.accent, false);
        hint.setPadding(Ui.dp(22), 0, Ui.dp(20), Ui.dp(4));
        settings.addView(hint);

        settings.addView(section("Appearance"));
        settings.addView(row(Glyphs.PALETTE, "Theme", Ui.THEMES[Ui.theme], null, v -> openThemeScreen()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.INFO, "Font style", new String[]{"System Sans", "Rounded", "Serif", "Monospace"}[Ui.sp.getInt("font_family", 0)], null, v -> fontDialog()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(switchRow(Glyphs.SUN, "Light mode", "Switches panels, text, and system dialogs for a bright background", "light", false, () -> { Ui.setLight(Ui.bool("light", false), true); recreate(); }), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(switchRow(Glyphs.SPARKLE, "Background blur", "Soft frosted glass look (Android 12+)", "blur", true, () -> bg.applyBlur()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Files"));
        settings.addView(switchRow(Glyphs.EYE, "Show hidden files", "Files and folders starting with a dot", "hidden", false, () -> filesView.reload()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(switchRow(Glyphs.OPEN, "Open files inside the app", "Built-in viewer for photos, video, audio, PDF and text", "inapp", true, null), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(switchRow(Glyphs.TRASH, "Confirm before deleting", null, "confirm", true, null), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.SORT, "Default sort", null, null, v -> filesView.sortDialog()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Storage"));
        settings.addView(row(Glyphs.DATABASE, "All files access", hasAccess() ? "Allowed" : "Not allowed — tap to fix", null, v -> { if (!hasAccess()) showStorageAccess(); }), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.REFRESH, "Rescan storage", "Refresh categories, recents and search", null, v -> { Indexer.I.dirty = true; startIndex(); Ui.toast(this, "Scanning…"); }), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Recycle Bin"));
        settings.addView(switchRow(Glyphs.TRASH, "Use Recycle Bin", "Deleted items are kept here before being removed for good", "recyclebin", true, null), lpm(-1, -2, 16, 0, 16, 8));
        int binCount = RecycleBin.list(this).size();
        settings.addView(row(Glyphs.TRASH, "Recycle Bin", binCount == 0 ? "Empty" : binCount + " item" + (binCount == 1 ? "" : "s") + " • auto-removed after " + Ui.sp.getInt("recyclebin_days", 30) + " days", null, v -> showRecycleBin()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.TRASH, "Keep deleted items for", Ui.sp.getInt("recyclebin_days", 30) + " days", null, v -> retentionDialog()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("App Lock"));
        settings.addView(row(Glyphs.LOCK, AppLock.hasPin(this) ? "Change App Lock PIN" : "Set App Lock PIN", "Biometric unlock is offered first on supported devices", null, v -> setAppLockPin()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(switchRow(Glyphs.SHIELD, "Require unlock to open app", "Checked every time the app returns to the foreground", "applock_on", false, () -> {
            if (Ui.bool("applock_on", false) && !AppLock.hasPin(this)) { Ui.toast(this, "Set an App Lock PIN first"); Ui.set("applock_on", false); buildSettings(); }
        }), lpm(-1, -2, 16, 0, 16, 8));

        String defFolder = Ui.sp.getString("default_folder", null);
        settings.addView(row(Glyphs.FOLDER, "Default start folder", defFolder != null ? defFolder : "Internal storage root (use ⋮ in Files to set another)", null, v -> {
            if (defFolder == null) { Ui.toast(this, "Open a folder, then use ⋮ → Set as default start folder"); return; }
            new AlertDialog.Builder(this).setTitle("Reset default folder?").setMessage("Will start from Internal storage root again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset", (d, w) -> { Ui.sp.edit().remove("default_folder").apply(); buildSettings(); }).show();
        }), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Storage Analysis"));
        settings.addView(row(Glyphs.DATABASE, "Archive manager", "List archive files; inspect ZIP contents and extract ZIP files. Other formats can be listed but are not extracted by this build.", null, v -> openCategory(4)), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.ANALYTICS, "Find duplicate files", "Matches by size, then confirms with a checksum", null, v -> openDuplicates()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.DATABASE, "Find empty folders", "Folders with nothing inside them", null, v -> openCleanup()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.ANALYTICS, "Storage analyzer", "Count files and estimate space by category; shows largest files without deleting anything", null, v -> openStorageAnalyzer()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Offline & Privacy"));
        settings.addView(row(Glyphs.SHIELD, "Privacy and connectivity audit", "Review app permissions and what offline mode means", null, v -> showPrivacyAudit()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.ANALYTICS, "Crash logs & diagnostics", CrashLogger.hasReport(this) ? "A crash report is saved on this device" : "Save crash details automatically if the app closes unexpectedly", null, v -> showCrashLogOptions()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Vault"));
        settings.addView(row(Glyphs.LOCK, Vault.hasPin(this) ? "Change Vault PIN" : "Set Vault PIN", "AES-256 encrypted, key held in Android Keystore", null, v -> setVaultPin()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.FOLDER, "Open Vault", Vault.list(this).size() + " item" + (Vault.list(this).size() == 1 ? "" : "s"), null, v -> promptVaultPin()), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Wi-Fi Transfer"));
        settings.addView(row(Glyphs.WIFI, "Receive via Wi-Fi", "Both devices must be on the same Wi-Fi network", null, v -> receiveViaWifi()), lpm(-1, -2, 16, 0, 16, 8));
        settings.addView(row(Glyphs.WIFI, "Send via Wi-Fi", "Pick files first: select in Files, then ⋮ → Send via Wi-Fi…", null, v -> Ui.toast(this, "Select files in Files tab first, then use ⋮ → Send via Wi-Fi…")), lpm(-1, -2, 16, 0, 16, 8));

        settings.addView(section("Other storage"));
        settings.addView(row(Glyphs.CLOUD, "Browse other storage", "SD card, USB, or a cloud app's documents (read-only import)", null, v -> openOtherStorage()), lpm(-1, -2, 16, 0, 16, 8));


        settings.addView(section("About"));
        settings.addView(row(Glyphs.SPARKLE, "About ToxiFiles", versionName(), null, v -> showAbout()), lpm(-1, -2, 16, 0, 16, 8));

        if (Ui.bool("developer_options_unlocked", false)) {
            settings.addView(section("Developer options"));
            settings.addView(row(Glyphs.ANALYTICS, "Usage & diagnostics", "Local app version, Android details, storage permission and crash status", null, v -> showUsageDiagnostics()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(Glyphs.ANALYTICS, "Verbose diagnostics", "Keep additional local diagnostic details for troubleshooting", "dev_verbose", false, null), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(Glyphs.VIBRATE, "Haptic feedback", "Gentle vibration on selection", "haptic", true, null), lpm(-1, -2, 16, 0, 16, 8));
            TextView backupHeading = Ui.tv(this, "Backup & Restore", 14, Ui.accent, true);
            backupHeading.setPadding(Ui.dp(8), Ui.dp(12), 0, Ui.dp(2));
            settings.addView(backupHeading);
            settings.addView(row(Glyphs.DOWNLOAD, "Export settings", "Save theme and preferences to a file you can share or keep", null, v -> exportSettings()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(Glyphs.CLOUD, "Import settings", "Restore from a previously exported file", null, v -> importSettingsPick()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(Glyphs.VIBRATE, "Reduce motion", "Disable decorative transition animations", "reduce_motion", true, null), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(Glyphs.INFO, "Show debug toasts", "Show extra status feedback for developer troubleshooting", "dev_debug_toasts", false, null), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(switchRow(Glyphs.REFRESH, "Enable diagnostic logging", "Keep additional troubleshooting events locally", "dev_logging", false, null), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(Glyphs.ARCHIVE, "Built-in tools", "Inspect the file manager's integrated viewers, players, editor and extractor", null, v -> showBuiltInTools()), lpm(-1, -2, 16, 0, 16, 8));
            settings.addView(row(Glyphs.REFRESH, "Reset developer options", "Hide this section again", null, v -> { Ui.sp.edit().putBoolean("developer_options_unlocked", false).apply(); buildSettings(); }), lpm(-1, -2, 16, 0, 16, 8));
        }

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
            } else if (inSection) {
                child.setVisibility(appearanceSection ? View.VISIBLE : View.GONE);
            }
        }
    }

    // ---------------- STORAGE ANALYZER ----------------
    void openStorageAnalyzer() {
        if (!hasAccess()) { showStorageAccess(); return; }
        Ui.toast(this, "Analyzing storage…");
        io.execute(() -> {
            File rootDir = Environment.getExternalStorageDirectory();
            long[] totals = new long[6]; // images, video, audio, documents, archives, other
            long[] counts = new long[6];
            List<File> largest = new ArrayList<>();
            long[] totalBytes = {0};
            int[] totalFiles = {0};
            scanStorage(rootDir, totals, counts, largest, totalBytes, totalFiles, 0);
            largest.sort((a, b) -> Long.compare(b.length(), a.length()));
            String[] labels = {"Images", "Videos", "Audio", "Documents", "Archives", "Other files"};
            StringBuilder report = new StringBuilder();
            report.append("Scanned internal shared storage\n\nFiles: ").append(totalFiles[0])
                    .append("\nEstimated file size total: ").append(formatBytes(totalBytes[0])).append("\n\nBy category:\n");
            for (int i = 0; i < labels.length; i++) report.append(labels[i]).append(": ").append(counts[i]).append(" files • ").append(formatBytes(totals[i])).append("\n");
            report.append("\nLargest files found (up to 15):\n");
            int n = Math.min(15, largest.size());
            for (int i = 0; i < n; i++) report.append("\n").append(formatBytes(largest.get(i).length())).append(" — ").append(largest.get(i).getAbsolutePath());
            if (n == 0) report.append("None found.");
            final String result = report.toString();
            runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Storage analysis")
                    .setMessage(result).setPositiveButton("Done", null)
                    .setNeutralButton("Share report", (d, w) -> {
                        try {
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain"); share.putExtra(Intent.EXTRA_TEXT, result);
                            startActivity(Intent.createChooser(share, "Share storage report"));
                        } catch (Exception ex) { Ui.toast(this, "Unable to share report"); }
                    }).show());
        });
    }

    void scanStorage(File f, long[] totals, long[] counts, List<File> largest, long[] totalBytes, int[] totalFiles, int depth) {
        if (f == null || depth > 60) return;
        try {
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) for (File child : children) scanStorage(child, totals, counts, largest, totalBytes, totalFiles, depth + 1);
                return;
            }
            if (!f.isFile() || !f.canRead()) return;
            long size = f.length(); totalBytes[0] += size; totalFiles[0]++;
            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
            int k = name.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|heic|heif)$") ? 0
                    : name.matches(".*\\.(mp4|mkv|mov|webm|3gp|avi|m4v)$") ? 1
                    : name.matches(".*\\.(mp3|m4a|aac|wav|ogg|flac|opus|wma)$") ? 2
                    : name.matches(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|rtf|odt|ods|odp)$") ? 3
                    : name.matches(".*\\.(zip|7z|rar|tar|gz|bz2|xz)$") ? 4 : 5;
            totals[k] += size; counts[k]++;
            // Bound memory on devices with very large storage; this report is an estimate.
            if (largest.size() < 1000) largest.add(f);
            else {
                int smallest = 0;
                for (int i = 1; i < largest.size(); i++) if (largest.get(i).length() < largest.get(smallest).length()) smallest = i;
                if (size > largest.get(smallest).length()) largest.set(smallest, f);
            }
        } catch (SecurityException ignored) { }
    }

    String formatBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", b / (1024.0 * 1024));
        return String.format(java.util.Locale.getDefault(), "%.2f GB", b / (1024.0 * 1024 * 1024));
    }

    void showPrivacyAudit() {
        String report = "OFFLINE & PRIVACY REVIEW\n\n"
                + "• File browsing and built-in viewing operate on local files.\n"
                + "• No account or cloud-sync feature is provided by this app.\n"
                + "• Wi-Fi transfer is a local-network feature; it requires network permission and both devices on a reachable network. It is not internet cloud storage.\n"
                + "• All-files access gives broad access to shared storage. Disable it in Android settings when you no longer need it.\n"
                + "• App Lock protects opening this app; Vault behavior and its limits are described separately in the Vault screen.\n"
                + "• Settings export creates a readable JSON file. Do not share it if it contains preferences you consider private.\n\n"
                + "This is an in-app permission/feature summary, not an independent network-traffic audit. To verify network behavior, inspect the source and test traffic on the device.";
        new AlertDialog.Builder(this).setTitle("Privacy and connectivity audit")
                .setMessage(report).setPositiveButton("OK", null).show();
    }

    // ---------------- APP LOCK ----------------
    void setAppLockPin() {
        final EditText in = pinField();
        new AlertDialog.Builder(this).setTitle(AppLock.hasPin(this) ? "Change App Lock PIN" : "Set App Lock PIN")
                .setView(wrapField(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String p = in.getText().toString().trim();
                    if (p.length() < 4) { Ui.toast(this, "Use at least 4 digits"); return; }
                    AppLock.setPin(this, p);
                    Ui.toast(this, "App Lock PIN saved");
                    buildSettings();
                }).show();
    }

    /** Blocking, non-cancelable lock screen shown on launch/resume when App Lock is on. */
    void showAppLock() {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        final android.app.Dialog dlg = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.setCancelable(false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.bg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(Ui.dp(32), 0, Ui.dp(32), 0);
        col.addView(Ui.tv(this, "LiquidFiles is locked", 18, Ui.WHITE, true), lpm(-2, -2, 0, 0, 0, 16));
        final EditText in = pinField();
        col.addView(wrapField(in), new LinearLayout.LayoutParams(-1, -2));
        TextView unlockBtn = Ui.tv(this, "Unlock", 16, Ui.accent, true);
        unlockBtn.setGravity(Gravity.CENTER);
        unlockBtn.setBackground(Ui.glass(18, false));
        unlockBtn.setPadding(0, Ui.dp(14), 0, Ui.dp(14));
        Ui.press(unlockBtn);
        unlockBtn.setOnClickListener(v -> {
            if (AppLock.checkPin(this, in.getText().toString().trim())) {
                unlocked = true; getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); dlg.dismiss();
            } else {
                long remaining = AppLock.lockoutRemainingMs(this);
                Ui.toast(this, remaining > 0 ? "Too many attempts. Try again in " + Math.max(1, remaining / 1000) + " seconds" : "Wrong PIN");
            }
        });
        col.addView(unlockBtn, lpm(-1, -2, 0, 20, 0, 0));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        dlg.setContentView(root);
        dlg.setOnKeyListener((d, keyCode, event) -> keyCode == android.view.KeyEvent.KEYCODE_BACK);
        dlg.show();
        if (Build.VERSION.SDK_INT >= 28) tryBiometric(dlg);
    }

    void tryBiometric(final android.app.Dialog dlg) {
        try {
            android.hardware.biometrics.BiometricPrompt prompt = new android.hardware.biometrics.BiometricPrompt.Builder(this)
                    .setTitle("Unlock LiquidFiles")
                    .setNegativeButton("Use PIN", getMainExecutor(), (d, w) -> { })
                    .build();
            prompt.authenticate(new android.os.CancellationSignal(), getMainExecutor(),
                    new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                            unlocked = true;
                            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                            dlg.dismiss();
                        }
                    });
        } catch (Exception ignored) { } // no enrolled biometric / unsupported — PIN field stays usable
    }

    // ---------------- DUPLICATE FINDER ----------------
    void openDuplicates() {
        if (!Indexer.I.ready) { Ui.toast(this, "Still scanning storage — try again in a moment"); return; }
        Ui.toast(this, "Scanning for duplicates…");
        io.execute(() -> {
            final List<DupFinder.Group> groups = DupFinder.find(Indexer.I.all);
            runOnUiThread(() -> { buildDuplicates(groups); showOverlay(dupScroll); });
        });
    }

    void buildDuplicates(final List<DupFinder.Group> groups) {
        dup.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
        ImageView bk = Ui.iconBtn(this, Glyphs.BACK, Ui.WHITE);
        bk.setContentDescription("Back");
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        hd.addView(Ui.tv(this, "Duplicate Files", 22, Ui.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        dup.addView(hd, new LinearLayout.LayoutParams(-1, Ui.dp(56)));

        if (groups.isEmpty()) {
            TextView w = Ui.tv(this, "No exact duplicates found", 14, Ui.SUB, false);
            w.setPadding(Ui.dp(20), Ui.dp(20), 0, 0);
            dup.addView(w);
            return;
        }
        long reclaimable = 0;
        for (DupFinder.Group g : groups) reclaimable += g.size * (g.files.size() - 1);
        TextView sum = Ui.tv(this, groups.size() + " duplicate group" + (groups.size() == 1 ? "" : "s") + " — up to " + Ui.size(reclaimable) + " reclaimable", 12, Ui.SUB, false);
        sum.setPadding(Ui.dp(20), Ui.dp(2), Ui.dp(20), Ui.dp(6));
        dup.addView(sum);

        TextView bulk = Ui.tv(this, "Move all but the oldest copy to Recycle Bin", 14, Ui.accent, true);
        bulk.setBackground(Ui.glass(18, false));
        bulk.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        bulk.setGravity(Gravity.CENTER);
        Ui.press(bulk);
        bulk.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Move duplicates to Recycle Bin?")
                .setMessage("Keeps the oldest file in each group, moves the rest to Recycle Bin — they can still be restored from there.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move", (d, w) -> {
                    List<File> victims = new ArrayList<>();
                    for (DupFinder.Group g : groups) {
                        File keep = g.files.get(0);
                        for (File f : g.files) if (f.lastModified() < keep.lastModified()) keep = f;
                        for (File f : g.files) if (f != keep) victims.add(f);
                    }
                    RecycleBin.moveToBin(this, victims, msg -> { Ui.toast(this, msg); afterOps(); openDuplicates(); });
                }).show());
        dup.addView(bulk, lpm(-1, -2, 16, 4, 16, 14));

        for (final DupFinder.Group g : groups) {
            TextView gh = Ui.tv(this, g.files.size() + " copies • " + Ui.size(g.size) + " each", 12, Ui.SUB, true);
            gh.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), Ui.dp(2));
            dup.addView(gh);
            for (final File f : g.files) {
                Entry e = new Entry(f);
                View rv = Rows.build(this);
                Rows.H h = (Rows.H) rv.getTag();
                Rows.bind(h, e, false, false, f.getParentFile() == null ? "" : f.getParentFile().getAbsolutePath());
                View.OnClickListener act = v -> new AlertDialog.Builder(this).setTitle("Move to Recycle Bin?").setMessage(f.getAbsolutePath())
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Move", (d, w) -> RecycleBin.moveToBin(this, java.util.Collections.singletonList(f), msg -> { Ui.toast(this, msg); afterOps(); openDuplicates(); }))
                        .show();
                rv.setOnClickListener(act);
                h.more.setOnClickListener(act);
                dup.addView(rv, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    void openCleanup() {
        if (!Indexer.I.ready) { Ui.toast(this, "Still scanning storage — try again in a moment"); return; }
        Ui.toast(this, "Scanning for empty folders…");
        io.execute(() -> {
            final List<File> empties = new ArrayList<>();
            for (Entry e : Indexer.I.all) {
                if (e.dir) { File[] kids = e.f.listFiles(); if (kids == null || kids.length == 0) empties.add(e.f); }
            }
            runOnUiThread(() -> { buildEmptyFolders(empties); showOverlay(dupScroll); });
        });
    }

    void buildEmptyFolders(final List<File> empties) {
        dup.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
        ImageView bk = Ui.iconBtn(this, Glyphs.BACK, Ui.WHITE);
        bk.setContentDescription("Back");
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        hd.addView(Ui.tv(this, "Empty Folders", 22, Ui.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        dup.addView(hd, new LinearLayout.LayoutParams(-1, Ui.dp(56)));

        if (empties.isEmpty()) {
            TextView w = Ui.tv(this, "No empty folders found", 14, Ui.SUB, false);
            w.setPadding(Ui.dp(20), Ui.dp(20), 0, 0);
            dup.addView(w);
            return;
        }
        TextView sum = Ui.tv(this, empties.size() + " empty folder" + (empties.size() == 1 ? "" : "s"), 12, Ui.SUB, false);
        sum.setPadding(Ui.dp(20), Ui.dp(2), Ui.dp(20), Ui.dp(6));
        dup.addView(sum);

        TextView bulk = Ui.tv(this, "Delete all empty folders", 14, Ui.accent, true);
        bulk.setBackground(Ui.glass(18, false));
        bulk.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        bulk.setGravity(Gravity.CENTER);
        Ui.press(bulk);
        bulk.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete " + empties.size() + " empty folders?")
                .setMessage("They contain nothing, so this goes straight to Recycle Bin, same as any other delete.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> RecycleBin.moveToBin(this, empties, msg -> { Ui.toast(this, msg); afterOps(); openCleanup(); }))
                .show());
        dup.addView(bulk, lpm(-1, -2, 16, 4, 16, 14));

        for (final File f : empties) {
            Entry e = new Entry(f);
            View rv = Rows.build(this);
            Rows.H h = (Rows.H) rv.getTag();
            Rows.bind(h, e, false, false, f.getParentFile() == null ? "" : f.getParentFile().getAbsolutePath());
            View.OnClickListener act = v -> RecycleBin.moveToBin(this, java.util.Collections.singletonList(f), msg -> { Ui.toast(this, msg); afterOps(); openCleanup(); });
            rv.setOnClickListener(act);
            h.more.setOnClickListener(act);
            dup.addView(rv, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    // ---------------- VAULT ----------------
    EditText pinField() {
        EditText in = new EditText(this);
        GlowRing.watchFocus(in);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        in.setHint("4+ digit PIN");
        return in;
    }

    View wrapField(View v) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(Ui.dp(22), Ui.dp(8), Ui.dp(22), 0);
        f.addView(v, new FrameLayout.LayoutParams(-1, -2));
        return f;
    }

    void setVaultPin() {
        final EditText in = pinField();
        new AlertDialog.Builder(this).setTitle(Vault.hasPin(this) ? "Change Vault PIN" : "Set Vault PIN")
                .setView(wrapField(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String p = in.getText().toString().trim();
                    if (p.length() < 4) { Ui.toast(this, "Use at least 4 digits"); return; }
                    Vault.setPin(this, p);
                    Ui.toast(this, "Vault PIN saved");
                    buildSettings();
                }).show();
    }

    void promptVaultPin() {
        if (!Vault.hasPin(this)) { Ui.toast(this, "Set a Vault PIN first"); return; }
        final EditText in = pinField();
        new AlertDialog.Builder(this).setTitle("Enter Vault PIN")
                .setView(wrapField(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (Vault.checkPin(this, in.getText().toString().trim())) { buildVault(); showOverlay(vaultScroll); }
                    else {
                        long remaining = Vault.sp(this).getLong("blocked_until", 0L) - System.currentTimeMillis();
                        Ui.toast(this, remaining > 0 ? "Too many attempts. Try again in " + Math.max(1, remaining / 1000) + " seconds" : "Wrong PIN");
                    }
                }).show();
    }

    void buildVault() {
        vault.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
        ImageView bk = Ui.iconBtn(this, Glyphs.BACK, Ui.WHITE);
        bk.setContentDescription("Back");
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        hd.addView(Ui.tv(this, "Vault", 24, Ui.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        hd.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        vault.addView(hd, new LinearLayout.LayoutParams(-1, Ui.dp(56)));

        TextView note = Ui.tv(this, "Encrypted with AES-256 (key stored in Android's hardware Keystore, not derived from your PIN). The PIN gates this screen; the encryption protects the file bytes even if someone pulls them off the device directly.", 12, Ui.SUB, false);
        note.setPadding(Ui.dp(20), Ui.dp(2), Ui.dp(20), Ui.dp(10));
        vault.addView(note);

        List<Vault.Item> items = Vault.list(this);
        if (items.isEmpty()) {
            TextView w = Ui.tv(this, "Vault is empty", 14, Ui.SUB, false);
            w.setPadding(Ui.dp(20), Ui.dp(20), 0, 0);
            vault.addView(w);
        } else {
            for (final Vault.Item it : items) {
                Entry e = new Entry(it.file);
                View rv = Rows.build(this);
                Rows.H h = (Rows.H) rv.getTag();
                Rows.bind(h, e, false, false, "from " + it.originalParent.getName());
                rv.setOnClickListener(v -> vaultItemMenu(it));
                h.more.setOnClickListener(v -> vaultItemMenu(it));
                vault.addView(rv, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    void vaultItemMenu(final Vault.Item it) {
        String[] opts = {"Move out of Vault", "Delete forever"};
        new AlertDialog.Builder(this).setTitle(it.file.getName()).setItems(opts, (d, w) -> {
            if (w == 0) Vault.restoreOut(this, it, msg -> { Ui.toast(this, msg); afterOps(); buildVault(); });
            else new AlertDialog.Builder(this).setTitle("Delete forever?").setMessage("This can't be undone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d2, w2) -> Vault.deleteForever(this, it, msg -> { Ui.toast(this, msg); buildVault(); }))
                    .show();
        }).show();
    }

    // ---------------- WI-FI TRANSFER ----------------
    void receiveViaWifi() {
        final AlertDialog waitDlg = new AlertDialog.Builder(this).setTitle("Waiting for sender…")
                .setMessage("Starting…")
                .setNegativeButton("Cancel", (d, w) -> LanTransfer.cancel())
                .setCancelable(false).show();
        File destDir = new File(Environment.getExternalStorageDirectory(), "Download/LanReceived");
        LanTransfer.receive(destDir, new LanTransfer.Listener() {
            @Override public void onWaiting(String ip, int port, String pairingCode) {
                runOnUiThread(() -> waitDlg.setMessage("On the sending device, use ⋮ → Send via Wi-Fi… and enter:\n\n" + ip + ":" + port + "\nPairing code: " + pairingCode + "\n\nKeep this code private. Files are encrypted during transfer."));
            }
            @Override public void onIncoming(String fromIp, int count, long totalBytes, List<String> names, Runnable accept, Runnable reject) {
                runOnUiThread(() -> {
                    if (waitDlg.isShowing()) waitDlg.dismiss();
                    StringBuilder preview = new StringBuilder();
                    int shown = Math.min(8, names.size());
                    for (int i = 0; i < shown; i++) preview.append("\n• ").append(names.get(i));
                    if (names.size() > shown) preview.append("\n…and ").append(names.size() - shown).append(" more");
                    new AlertDialog.Builder(MainActivity.this).setTitle("Incoming transfer")
                            .setMessage("From " + fromIp + "\n" + count + " file" + (count == 1 ? "" : "s") + ", " + Ui.size(totalBytes) + "\n\nFiles:" + preview)
                            .setCancelable(false)
                            .setNegativeButton("Decline", (d, w) -> reject.run())
                            .setPositiveButton("Accept", (d, w) -> { accept.run(); busy(true); progress("Receiving…"); })
                            .show();
                });
            }
            @Override public void onFileStart(int idx, int total, String name) { runOnUiThread(() -> progress("Receiving " + idx + " of " + total + ": " + name)); }
            @Override public void onDone(String msg) { runOnUiThread(() -> { busy(false); Ui.toast(MainActivity.this, msg); afterOps(); }); }
            @Override public void onError(String msg) {
                runOnUiThread(() -> { busy(false); if (waitDlg.isShowing()) waitDlg.dismiss(); Ui.toast(MainActivity.this, msg); });
            }
        });
    }

    void sendViaWifi(final List<File> files) {
        if (files.isEmpty()) return;
        final EditText in = new EditText(this);
        GlowRing.watchFocus(in); in.setHint("e.g. 192.168.1.42:52017"); in.setSingleLine(true);
        final EditText code = new EditText(this);
        GlowRing.watchFocus(code); code.setHint("10-character pairing code"); code.setSingleLine(true);
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(wrapField(in)); fields.addView(wrapField(code));
        new AlertDialog.Builder(this).setTitle("Send via Wi-Fi")
                .setMessage("Enter the receiver address and one-time pairing code shown on the receiving device. Both devices must be on the same trusted Wi-Fi network.")
                .setView(fields).setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (d, w) -> {
                    String raw = in.getText().toString().trim();
                    String pairingCode = code.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
                    if (raw.isEmpty() || pairingCode.length() != 10) { Ui.toast(this, "Enter the address and 10-character pairing code"); return; }
                    String ip = raw.contains(":") ? raw.substring(0, raw.indexOf(':')) : raw;
                    busy(true);
                    progress("Connecting securely…");
                    LanTransfer.send(ip, pairingCode, files, new LanTransfer.Listener() {
                        @Override public void onWaiting(String ip2, int port, String pairingCode2) { }
                        @Override public void onIncoming(String fromIp, int count, long totalBytes, List<String> names, Runnable accept, Runnable reject) { }
                        @Override public void onFileStart(int idx, int total, String name) { runOnUiThread(() -> progress("Sending " + idx + " of " + total + ": " + name)); }
                        @Override public void onDone(String msg) { runOnUiThread(() -> { busy(false); Ui.toast(MainActivity.this, msg); }); }
                        @Override public void onError(String msg) { runOnUiThread(() -> { busy(false); Ui.toast(MainActivity.this, msg); }); }
                    });
                }).show();
    }

    // ---------------- OTHER STORAGE (SAF, read-only import) ----------------
    void openOtherStorage() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, REQ_SAF_TREE);
        } catch (Exception ex) { Ui.toast(this, "Can't open the storage picker on this device"); }
    }

    @Override protected void onActivityResult(int rq, int rc, Intent data) {
        super.onActivityResult(rq, rc, data);
        if (rc != RESULT_OK || data == null) return;
        if (rq == REQ_SAF_TREE) {
            Uri tree = data.getData();
            if (tree == null) return;
            int grantedFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(tree, grantedFlags); } catch (Exception ignored) { /* session grant may still work */ }
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
        if (safTree == null || files == null || files.isEmpty()) { Ui.toast(this, "Choose an SD/USB destination first"); return; }
        final DocumentFile rootDoc = DocumentFile.fromTreeUri(this, safTree);
        if (rootDoc == null || !rootDoc.canWrite()) { Ui.toast(this, "This storage location is not writable"); return; }
        busy(true);
        ops.execute(() -> {
            FileOps.cancelRequested = false;
            int ok = 0, failed = 0, idx = 0;
            String error = "";
            for (File file : files) {
                if (FileOps.cancelRequested) { failed++; error = "Operation cancelled"; break; }
                final int current = ++idx;
                runOnUiThread(() -> progress("Copying to SD/USB " + current + " of " + files.size() + ": " + file.getName()));
                try { copyLocalToSaf(file, rootDoc); ok++; }
                catch (Exception ex) { failed++; error = String.valueOf(ex.getMessage()); break; }
            }
            final int copied = ok, failures = failed; final String detail = error;
            runOnUiThread(() -> {
                busy(false);
                Ui.toast(this, failures == 0 ? "Copied " + copied + " item" + (copied == 1 ? "" : "s") + " to SD/USB" : "Copied " + copied + "; stopped: " + detail);
            });
        });
    }

    private void copyLocalToSaf(File source, DocumentFile parent) throws java.io.IOException {
        FileOps.checkCancelled();
        if (java.nio.file.Files.isSymbolicLink(source.toPath())) throw new java.io.IOException("Symbolic links cannot be copied to external storage");
        String base = source.getName(); String candidate = base; int suffix = 1;
        while (parent.findFile(candidate) != null) {
            int dot = source.isDirectory() ? -1 : base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            String ext = dot > 0 ? base.substring(dot) : "";
            candidate = stem + " (" + suffix++ + ")" + ext;
        }
        String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                android.webkit.MimeTypeMap.getFileExtensionFromUrl(candidate));
        DocumentFile target = source.isDirectory() ? parent.createDirectory(candidate) : parent.createFile(mime == null ? "application/octet-stream" : mime, candidate);
        if (target == null) throw new java.io.IOException("Could not create " + candidate + " on external storage");
        try {
            if (source.isDirectory()) {
                File[] children = source.listFiles();
                if (children == null) throw new java.io.IOException("Can't read folder " + source.getName());
                for (File child : children) copyLocalToSaf(child, target);
            } else {
                try (InputStream in = new java.io.FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(target.getUri(), "w")) {
                    if (out == null) throw new java.io.IOException("External storage returned no output stream");
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) { FileOps.checkCancelled(); out.write(buf, 0, n); }
                }
            }
        } catch (Exception copyError) {
            deleteSafTree(target);
            if (copyError instanceof java.io.IOException) throw (java.io.IOException) copyError;
            throw new java.io.IOException("Copy failed: " + copyError.getMessage(), copyError);
        }
    }

    private void deleteSafTree(DocumentFile target) {
        try { if (target.isDirectory()) { DocumentFile[] kids = target.listFiles(); if (kids != null) for (DocumentFile kid : kids) deleteSafTree(kid); } target.delete(); } catch (Exception ignored) { }
    }

    void showSafLevel(final Uri tree, final Uri docUri, final String title) {
        Ui.toast(this, "Loading…");
        io.execute(() -> {
            final List<String[]> rows = new ArrayList<>(); // [docId, name, "1" dir / "0" file]
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(docUri));
                Cursor c = getContentResolver().query(children, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) rows.add(new String[]{c.getString(0), c.getString(1), DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2)) ? "1" : "0"});
                    c.close();
                }
            } catch (Exception ignored) { }
            runOnUiThread(() -> {
                if (rows.isEmpty()) { Ui.toast(this, "Nothing here, or can't read this location"); return; }
                String[] names = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) names[i] = (rows.get(i)[2].equals("1") ? "📁 " : "") + rows.get(i)[1];
                new AlertDialog.Builder(this).setTitle(title).setItems(names, (d, w) -> {
                    String[] r = rows.get(w);
                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(tree, r[0]);
                    if (r[2].equals("1")) showSafLevel(tree, childUri, r[1]);
                    else importSafDoc(childUri, r[1]);
                }).setNegativeButton("Close", null).show();
            });
        });
    }

    void importSafDoc(final Uri doc, final String name) {
        busy(true);
        io.execute(() -> {
            String msg;
            try {
                File dest = FileOps.unique(new File(Environment.getExternalStorageDirectory(), "Download"), name);
                try (InputStream in = getContentResolver().openInputStream(doc); OutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                msg = "Imported to Download/" + dest.getName();
            } catch (Exception ex) { msg = "Import failed: " + ex.getMessage(); }
            final String m = msg;
            runOnUiThread(() -> { busy(false); Ui.toast(this, m); afterOps(); });
        });
    }

    // ---------------- SETTINGS BACKUP ----------------
    void exportSettings() {
        io.execute(() -> {
            String msg;
            try {
                JSONObject o = new JSONObject();
                for (String key : portableSettingKeys()) {
                    if (Ui.sp.contains(key)) o.put(key, Ui.sp.getAll().get(key));
                }
                File dest = FileOps.unique(new File(Environment.getExternalStorageDirectory(), "Download"), "liquidglass_settings.json");
                try (OutputStream out = new FileOutputStream(dest)) { out.write(o.toString(2).getBytes()); }
                msg = "Saved to Download/" + dest.getName();
                final File fd = dest;
                final String exportMessage = msg;
                runOnUiThread(() -> {
                    Ui.toast(this, exportMessage);
                    try {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("application/json");
                        i.putExtra(Intent.EXTRA_STREAM, FileOps.uri(this, fd));
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "Share settings file"));
                    } catch (Exception ignored) { }
                });
                return;
            } catch (Exception ex) { msg = "Export failed: " + ex.getMessage(); }
            final String m = msg;
            runOnUiThread(() -> Ui.toast(this, m));
        });
    }

    void importSettingsPick() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Choose settings file"), REQ_IMPORT_SETTINGS);
        } catch (Exception ex) { Ui.toast(this, "Can't open file picker"); }
    }

    private static java.util.Set<String> portableSettingKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList(
                "theme", "light", "haptic", "blur", "hidden", "sort", "grid",
                "font_family", "reduce_motion", "recyclebin_days", "dev_verbose",
                "dev_debug_toasts", "dev_logging"));
    }

    void applyImportedSettings(final Uri file) {
        io.execute(() -> {
            String msg;
            try {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(file)) {
                    if (in == null) throw new java.io.IOException("Unable to open settings file");
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (bytes.size() + n > 1024 * 1024) throw new java.io.IOException("Settings file exceeds the 1 MB limit");
                        bytes.write(buffer, 0, n);
                    }
                }
                JSONObject o = new JSONObject(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                java.util.Set<String> allowed = portableSettingKeys();
                SharedPreferences.Editor ed = Ui.sp.edit();
                java.util.Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (!allowed.contains(k)) continue;
                    Object v = o.get(k);
                    if ("theme".equals(k) && v instanceof Number) {
                        int theme = ((Number) v).intValue();
                        if (theme >= 0 && theme <= 14) ed.putInt(k, theme);
                    } else if ("font_family".equals(k) && v instanceof Number) {
                        int font = ((Number) v).intValue();
                        if (font >= 0 && font <= 3) ed.putInt(k, font);
                    } else if ("recyclebin_days".equals(k) && v instanceof Number) {
                        int days = ((Number) v).intValue();
                        if (days == 7 || days == 14 || days == 30 || days == 60 || days == 90) ed.putInt(k, days);
                    } else if (v instanceof Boolean) {
                        ed.putBoolean(k, (Boolean) v);
                    } else if (v instanceof String && "sort".equals(k)) {
                        String sort = (String) v;
                        if (sort.length() <= 32) ed.putString(k, sort);
                    }
                }
                ed.apply();
                msg = "Settings restored (security settings were not imported)";
            } catch (Exception ex) { msg = "Import failed: " + ex.getMessage(); }
            final String m = msg;
            runOnUiThread(() -> {
                Ui.toast(this, m);
                Ui.setTheme(Ui.sp.getInt("theme", 11), false);
                recolor();
                buildSettings();
            });
        });
    }

    // ---------------- LARGEST FILES ----------------
    void openLargest() {
        Indexer ix = Indexer.I;
        if (!ix.ready) { Ui.toast(this, "Still scanning storage — try again in a moment"); return; }
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
        new AlertDialog.Builder(this).setTitle("Keep deleted items for")
                .setItems(labels, (d, w) -> { Ui.sp.edit().putInt("recyclebin_days", days[w]).apply(); buildSettings(); })
                .show();
    }

    // ---------------- RECYCLE BIN ----------------
    void showRecycleBin() { buildRecycleBin(); showOverlay(recycleScroll); }

    void buildRecycleBin() {
        recycle.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
        ImageView bk = Ui.iconBtn(this, Glyphs.BACK, Ui.WHITE);
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        hd.addView(Ui.tv(this, "Recycle Bin", 24, Ui.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView emptyBtn = Ui.tv(this, "Empty", 14, Ui.accent, true);
        emptyBtn.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        emptyBtn.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Empty Recycle Bin?")
                .setMessage("Every item will be permanently deleted. This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Empty", (d, w) -> RecycleBin.empty(this, msg -> { Ui.toast(this, msg); buildRecycleBin(); }))
                .show());
        Ui.press(emptyBtn);
        hd.addView(emptyBtn, new LinearLayout.LayoutParams(-2, -2));
        recycle.addView(hd, new LinearLayout.LayoutParams(-1, Ui.dp(56)));

        TextView note = Ui.tv(this, "Items are kept for " + Ui.sp.getInt("recyclebin_days", 30) + " days, then removed automatically. Change this in Settings.", 12, Ui.SUB, false);
        note.setPadding(Ui.dp(20), Ui.dp(2), Ui.dp(20), Ui.dp(10));
        recycle.addView(note);

        List<RecycleBin.Item> items = RecycleBin.list(this);
        if (items.isEmpty()) {
            TextView w = Ui.tv(this, "Recycle Bin is empty", 14, Ui.SUB, false);
            w.setPadding(Ui.dp(20), Ui.dp(20), 0, 0);
            recycle.addView(w);
        } else {
            for (final RecycleBin.Item it : items) {
                Entry e = new Entry(it.file);
                View rv = Rows.build(this);
                Rows.H h = (Rows.H) rv.getTag();
                Rows.bind(h, e, false, false, "from " + it.originalParent.getName());
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
                RecycleBin.restore(this, it, msg -> { Ui.toast(this, msg); afterOps(); buildRecycleBin(); });
            } else {
                new AlertDialog.Builder(this).setTitle("Delete forever?").setMessage("This can't be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d2, w2) -> RecycleBin.permanentDelete(this, java.util.Collections.singletonList(it), msg -> { Ui.toast(this, msg); buildRecycleBin(); }))
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
        new AlertDialog.Builder(this).setTitle("Font style").setSingleChoiceItems(fonts, Ui.sp.getInt("font_family", 0), (d, which) -> {
            Ui.sp.edit().putInt("font_family", which).apply();
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
        panel.setPadding(Ui.dp(18), Ui.dp(24), Ui.dp(18), Ui.dp(112));

        TextView title = Ui.tv(this, "Liquid Glass Studio", 28, Ui.WHITE, true);
        title.setPadding(Ui.dp(2), Ui.dp(4), Ui.dp(2), Ui.dp(2));
        panel.addView(title);
        TextView subtitle = Ui.tv(this, "Choose a finish for your workspace", 13, Ui.SUB, false);
        subtitle.setPadding(Ui.dp(3), 0, Ui.dp(3), Ui.dp(18)); panel.addView(subtitle);

        LinearLayout current = new LinearLayout(this);
        current.setGravity(Gravity.CENTER_VERTICAL);
        current.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(16));
        current.setBackground(Ui.glass(24, true));
        FrameLayout currentIcon = new FrameLayout(this);
        currentIcon.setBackground(Ui.accentPill(16));
        TextView currentGlyph = Ui.tv(this, "◈", 25, Ui.WHITE, true); currentGlyph.setGravity(Gravity.CENTER);
        currentIcon.addView(currentGlyph, new FrameLayout.LayoutParams(-1, -1));
        current.addView(currentIcon, new LinearLayout.LayoutParams(Ui.dp(54), Ui.dp(54)));
        LinearLayout currentText = new LinearLayout(this); currentText.setOrientation(LinearLayout.VERTICAL);
        currentText.setPadding(Ui.dp(14), 0, Ui.dp(8), 0);
        currentText.addView(Ui.tv(this, "CURRENT FINISH", 10, Ui.accent, true));
        TextView activeName = Ui.tv(this, Ui.THEMES[Ui.theme], 17, Ui.WHITE, true);
        activeName.setPadding(0, Ui.dp(4), 0, 0); currentText.addView(activeName);
        current.addView(currentText, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView activeMark = Ui.tv(this, "✓", 22, Ui.accent, true); activeMark.setGravity(Gravity.CENTER);
        current.addView(activeMark, new LinearLayout.LayoutParams(Ui.dp(30), Ui.dp(42)));
        panel.addView(current, lpm(-1, -2, 0, 0, 0, 20));

        TextView allLabel = Ui.tv(this, "AVAILABLE FINISHES", 10.5f, Ui.SUB, true);
        allLabel.setLetterSpacing(0.12f); allLabel.setPadding(Ui.dp(3), 0, 0, Ui.dp(8)); panel.addView(allLabel);
        String[] marks = {"◈","◉","⬡","◇","✦","✧","◍","◐","◑","▣","⬢","◒","◓","✺","❖"};
        String[] finishNotes = {"Crystal cyan on pure black", "Warm amber molten glass", "Silver mirror with soft edges", "Emerald prism reflections", "Violet deep-space glass", "Rose quartz gloss", "Arctic teal reflections", "Solar copper glass", "Aqua neon crystal", "Cobalt spectral glass", "Deep jade obsidian", "Smoked titanium", "Ultramarine liquid crystal", "Magenta aurora glass", "Icy blue polished glass"};
        for (int index = 0; index < Ui.THEMES.length; index++) {
            final int themeIndex = index;
            boolean selected = index == Ui.theme;
            LinearLayout tile = new LinearLayout(this);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(Ui.dp(13), Ui.dp(12), Ui.dp(13), Ui.dp(12));
            tile.setBackground(selected ? Ui.accentPill(20) : Ui.glass(20, true));

            FrameLayout swatch = new FrameLayout(this);
            android.graphics.drawable.GradientDrawable swatchBg = new android.graphics.drawable.GradientDrawable();
            swatchBg.setColor(Ui.DOTS[index]); swatchBg.setCornerRadius(Ui.dp(14));
            swatchBg.setStroke(Ui.dp(1), Color.argb(130, 255, 255, 255));
            swatch.setBackground(swatchBg);
            TextView glyph = Ui.tv(this, marks[index], 20, Color.WHITE, true); glyph.setGravity(Gravity.CENTER);
            swatch.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
            tile.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));

            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(Ui.dp(13), 0, Ui.dp(8), 0);
            labels.addView(Ui.tv(this, Ui.THEMES[index], 14.5f, Ui.WHITE, selected));
            TextView desc = Ui.tv(this, selected ? "Currently applied · " + finishNotes[index] : finishNotes[index], 11, Ui.SUB, false);
            desc.setPadding(0, Ui.dp(3), 0, 0); labels.addView(desc);
            tile.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView status = Ui.tv(this, selected ? "✓" : "›", selected ? 20 : 25, selected ? Ui.accent : Ui.SUB, true);
            status.setGravity(Gravity.CENTER); tile.addView(status, new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(42)));

            tile.setOnClickListener(v -> {
                Ui.tick(v);
                Ui.setTheme(themeIndex, true);
                recolor();
                buildThemeScreen();
            });
            Ui.press(tile);
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
                .setMessage("LiquidFiles saved a local diagnostic report from the last unexpected crash. You can share it to help identify the cause. The report stays on this device unless you choose to share it.")
                .setPositiveButton("Share report", (d, w) -> shareCrashReport())
                .setNegativeButton("Later", null)
                .setNeutralButton("Delete report", (d, w) -> deleteCrashReport())
                .show();
    }

    void showCrashLogOptions() {
        if (!CrashLogger.hasReport(this)) {
            new AlertDialog.Builder(this).setTitle("Crash logs")
                    .setMessage("No crash report has been captured yet. If the app closes unexpectedly, LiquidFiles will save diagnostic details locally and offer them the next time you open the app.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Crash logs & diagnostics")
                .setMessage("A crash report is stored privately inside the app. It includes the exception stack trace, app version, Android version, device model, and crashing thread. Nothing is uploaded automatically.")
                .setPositiveButton("Share report", (d, w) -> shareCrashReport())
                .setNegativeButton("Close", null)
                .setNeutralButton("Delete report", (d, w) -> deleteCrashReport()).show();
    }

    void shareCrashReport() {
        File report = CrashLogger.latest(this);
        if (!report.isFile() || report.length() == 0) {
            Ui.toast(this, "No crash report available");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", report);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "LiquidFiles crash report");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(android.content.ClipData.newUri(getContentResolver(), "Crash report", uri));
            startActivity(Intent.createChooser(share, "Share crash report"));
        } catch (Exception e) {
            Ui.toast(this, "Could not share crash report");
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
        if (deleted) Ui.toast(this, "Crash reports deleted");
        else Ui.toast(this, "Could not delete crash report");
    }

    void showAbout() {
        about.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
        ImageView bk = Ui.iconBtn(this, Glyphs.BACK, Ui.WHITE);
        bk.setContentDescription("Back");
        bk.setOnClickListener(v -> closeOverlay());
        hd.addView(bk, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        hd.addView(Ui.tv(this, "About ToxiFiles", 22, Ui.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        about.addView(hd, new LinearLayout.LayoutParams(-1, Ui.dp(56)));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setBackground(Ui.glass(28, true));
        hero.setPadding(Ui.dp(20), Ui.dp(26), Ui.dp(20), Ui.dp(24));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_foreground);
        Ui.ringIcon(logo);
        logo.setBackground(Ui.glass(24, true));
        logo.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        logo.setContentDescription("ToxiFiles liquid glass app logo");
        hero.addView(logo, new LinearLayout.LayoutParams(Ui.dp(82), Ui.dp(82)));
        TextView nm = Ui.tv(this, "ToxiFiles", 25, Ui.WHITE, true);
        nm.setPadding(0, Ui.dp(14), 0, 0);
        hero.addView(nm);
        TextView subtitle = Ui.tv(this, "PREMIUM OFFLINE FILE MANAGER", 11, Ui.SUB, true);
        subtitle.setLetterSpacing(0.12f);
        subtitle.setPadding(0, Ui.dp(4), 0, 0);
        hero.addView(subtitle);
        TextView vs = Ui.tv(this, versionAndBuild(), 13, Ui.accent, true);
        vs.setPadding(0, Ui.dp(10), 0, 0);
        hero.addView(vs);
        TextView tg = Ui.tv(this, "Your files. Your device. Your control.", 14, Ui.SUB, false);
        tg.setGravity(Gravity.CENTER);
        tg.setPadding(0, Ui.dp(10), 0, 0);
        hero.addView(tg);
        about.addView(hero, lpm(-1, -2, 16, 8, 16, 12));

        about.addView(section("CREATOR"));
        about.addView(aboutCard(Glyphs.INFO, "Created by", "CHANDU❤️‍🔥", null), lpm(-1, -2, 16, 0, 16, 8));

        about.addView(section("ABOUT DEVELOPER"));
        about.addView(aboutCard(Glyphs.INFO, "Facebook", "facebook.com/chand.p.319", "https://www.facebook.com/chand.p.319/"), lpm(-1, -2, 16, 0, 16, 8));
        about.addView(aboutCard(Glyphs.INFO, "Instagram", "@_chandu_rp_1", "https://www.instagram.com/_chandu_rp_1?stkn=MWxsNzJhNGpwZzluaA=="), lpm(-1, -2, 16, 0, 16, 8));
        about.addView(aboutCard(Glyphs.INFO, "Telegram", "t.me/Toxiiii2k23", "https://t.me/Toxiiii2k23"), lpm(-1, -2, 16, 0, 16, 8));

        about.addView(section("PROJECT & COMMUNITY"));
        about.addView(aboutCard(Glyphs.SPARKLE, "Developer GitHub", "github.com/testingper86-commits", "https://github.com/testingper86-commits"), lpm(-1, -2, 16, 0, 16, 8));
        about.addView(aboutCard(Glyphs.INFO, "LiquidFiles source project", "View project updates, source and build workflow", "https://github.com/testingper86-commits/LiquidGlassFileManager"), lpm(-1, -2, 16, 0, 16, 8));
        LinearLayout social = new LinearLayout(this);
        social.setOrientation(LinearLayout.VERTICAL);
        social.setBackground(Ui.glass(20, true));
        social.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        social.addView(Ui.tv(this, "CONNECT & SHARE", 11, Ui.accent, true));
        TextView socialCopy = Ui.tv(this, "Follow the developer on Facebook, Instagram, Telegram, or GitHub. Links open in your browser or the relevant installed app.", 13, Ui.SUB, false);
        socialCopy.setPadding(0, Ui.dp(6), 0, Ui.dp(12));
        social.addView(socialCopy);
        LinearLayout socialActions = new LinearLayout(this);
        socialActions.setGravity(Gravity.CENTER_VERTICAL);
        TextView githubAction = Ui.tv(this, "OPEN GITHUB  ↗", 12, Ui.WHITE, true);
        githubAction.setGravity(Gravity.CENTER);
        githubAction.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        githubAction.setBackground(Ui.glass(14, false));
        githubAction.setOnClickListener(v -> openExternalUrl("https://github.com/testingper86-commits"));
        socialActions.addView(githubAction, new LinearLayout.LayoutParams(0, -2, 1f));
        View socialGap = new View(this);
        socialActions.addView(socialGap, new LinearLayout.LayoutParams(Ui.dp(10), 1));
        TextView shareAction = Ui.tv(this, "SHARE APP  ↗", 12, Ui.WHITE, true);
        shareAction.setGravity(Gravity.CENTER);
        shareAction.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        shareAction.setBackground(Ui.glass(14, false));
        shareAction.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "ToxiFiles");
            share.putExtra(Intent.EXTRA_TEXT, "ToxiFiles\nYour files. Your device. Your control.\nFacebook: https://www.facebook.com/chand.p.319/\nInstagram: https://www.instagram.com/_chandu_rp_1?stkn=MWxsNzJhNGpwZzluaA==\nTelegram: https://t.me/Toxiiii2k23\nProject: https://github.com/testingper86-commits/LiquidGlassFileManager");
            startActivity(Intent.createChooser(share, "Share LiquidFiles"));
        });
        socialActions.addView(shareAction, new LinearLayout.LayoutParams(0, -2, 1f));
        social.addView(socialActions);
        about.addView(social, lpm(-1, -2, 16, 0, 16, 12));

        about.addView(section("APP DETAILS"));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setBackground(Ui.glass(20, false));
        details.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        details.addView(Ui.tv(this, "BUILT FOR LOCAL FILE CONTROL", 11, Ui.accent, true));
        TextView detailText = Ui.tv(this, "Browse and organize files on your device, manage common file actions, and use the built-in viewing tools. LiquidFiles is designed around an offline-first workflow; optional features may require Android permissions or local connectivity. Availability depends on the installed build.", 13, Ui.SUB, false);
        detailText.setPadding(0, Ui.dp(7), 0, Ui.dp(12));
        details.addView(detailText);
        details.addView(aboutDetailRow("Product", "ToxiFiles"));
        details.addView(aboutDetailRow("Version / build", versionAndBuild()));
        details.addView(aboutDetailRow("Application ID", getPackageName()));
        details.addView(aboutDetailRow("Platform", "Android 8.0+ (API 26+)"));
        about.addView(details, lpm(-1, -2, 16, 0, 16, 12));

        about.addView(section("PRIVACY & OFFLINE USE"));
        LinearLayout privacy = aboutTextCard(
                "Files are managed on your device. Core browsing and file operations are designed for local use. The app requests Internet permission for optional local Wi-Fi transfer features; that permission alone does not establish that data is sent to a remote service. No claim of zero network traffic is made here."
        );
        about.addView(privacy, lpm(-1, -2, 16, 0, 16, 8));

        about.addView(section("OPEN-SOURCE ACKNOWLEDGEMENTS"));
        LinearLayout licenses = new LinearLayout(this);
        licenses.setOrientation(LinearLayout.VERTICAL);
        licenses.setBackground(Ui.glass(18, false));
        licenses.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        licenses.addView(Ui.tv(this, "Heroicons — MIT License", 14, Ui.WHITE, true));
        TextView heroicons = Ui.tv(this, "Bundled outline icon assets are stored locally in the app. Attribution and the license are included with the assets.", 12, Ui.SUB, false);
        heroicons.setPadding(0, Ui.dp(4), 0, Ui.dp(12));
        licenses.addView(heroicons);
        licenses.addView(Ui.tv(this, "AndroidX Core — Apache License 2.0", 14, Ui.WHITE, true));
        TextView androidx = Ui.tv(this, "The project declares AndroidX Core as a dependency. This summary is not a complete transitive-dependency license report; release packaging should include all required notices.", 12, Ui.SUB, false);
        androidx.setPadding(0, Ui.dp(4), 0, 0);
        licenses.addView(androidx);
        about.addView(licenses, lpm(-1, -2, 16, 0, 16, 8));

        TextView footer = Ui.tv(this, "ToxiFiles", 12, Ui.SUB, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Ui.dp(16), 0, Ui.dp(24));
        about.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        showOverlay(aboutScroll);
    }

    void openExternalUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, "Could not open link");
        }
    }

    View aboutDetailRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
        TextView left = Ui.tv(this, label, 12, Ui.SUB, false);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 0.9f));
        TextView right = Ui.tv(this, value, 12, Ui.WHITE, true);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1.1f));
        return row;
    }

    String versionAndBuild() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return "Version " + info.versionName + "  ·  Build " + code;
        } catch (Exception e) { return versionName(); }
    }

    LinearLayout aboutCard(int icon, String title, String detail, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.glass(18, false));
        card.setPadding(Ui.dp(14), Ui.dp(13), Ui.dp(14), Ui.dp(13));
        ImageView iv = Ui.iconBtn(this, icon, Ui.accent);
        iv.setContentDescription(title);
        card.addView(iv, new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(38)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(10), 0, Ui.dp(8), 0);
        texts.addView(Ui.tv(this, title, 14, Ui.WHITE, true));
        TextView d = Ui.tv(this, detail, 12, Ui.SUB, false);
        d.setPadding(0, Ui.dp(3), 0, 0);
        texts.addView(d);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (url != null) {
            ImageView ext = Ui.iconBtn(this, Glyphs.OPEN, Ui.SUB);
            ext.setContentDescription("Open " + title);
            card.addView(ext, new LinearLayout.LayoutParams(Ui.dp(32), Ui.dp(38)));
            View.OnClickListener open = v -> {
                try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
                catch (Exception e) { Ui.toast(this, "Could not open project link"); }
            };
            card.setOnClickListener(open);
            ext.setOnClickListener(open);
        }
        return card;
    }

    LinearLayout aboutTextCard(String text) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.glass(18, false));
        card.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        card.addView(Ui.tv(this, text, 13, Ui.SUB, false));
        return card;
    }

    // ---------------- background + donut ----------------
    static final class BgView extends View {
        BgView(android.content.Context c) { super(c); setLayerType(LAYER_TYPE_HARDWARE, null); applyBlur(); }

        void applyBlur() {
            if (Build.VERSION.SDK_INT >= 31) {
                boolean lowRam = false;
                try {
                    android.app.ActivityManager am = (android.app.ActivityManager) getContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);
                    lowRam = am != null && am.isLowRamDevice();
                } catch (Exception ignored) { }
                if (Ui.bool("blur", true) && !lowRam) setRenderEffect(RenderEffect.createBlurEffect(Ui.dp(24), Ui.dp(24), Shader.TileMode.CLAMP));
                else setRenderEffect(null);
            }
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawColor(Ui.bg);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            blob(c, p, w * 0.12f, h * 0.10f, w * 0.85f, Ui.blob1);
            blob(c, p, w * 0.98f, h * 0.52f, w * 0.95f, Ui.blob2);
            blob(c, p, w * 0.25f, h * 1.00f, w * 0.85f, Ui.blob3);
        }

        void blob(Canvas c, Paint p, float x, float y, float r, int col) {
            p.setShader(new RadialGradient(x, y, r, col, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
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
            t.setColor(Ui.WHITE);
        }

        void set(float[] pa, int[] co, String b, String s) { parts = pa; cols = co; big = b; small = s; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float sw = Ui.dp(11), pad = sw / 2f + Ui.dp(2);
            r.set(pad, pad, getWidth() - pad, getHeight() - pad);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(Ui.light ? Color.argb(45, 20, 22, 30) : Color.argb(45, 255, 255, 255));
            c.drawArc(r, 0, 360, false, p);
            float start = -90;
            for (int i = 0; i < parts.length && i < cols.length; i++) {
                float sweep = parts[i] * 360f;
                if (sweep < 0.5f) continue;
                p.setColor(cols[i]);
                c.drawArc(r, start, Math.max(sweep - 1.5f, 0.5f), false, p);
                start += sweep;
            }
            t.setColor(Ui.WHITE);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            t.setTextSize(Ui.dp(22));
            c.drawText(big, getWidth() / 2f, getHeight() / 2f + Ui.dp(5), t);
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            t.setTextSize(Ui.dp(11));
            t.setColor(Ui.SUB);
            c.drawText(small, getWidth() / 2f, getHeight() / 2f + Ui.dp(21), t);
        }
    }
}
