package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

/** Device-first file browser used for Files, Favorites, categories and global search. */
final class BrowserView extends FrameLayout {
    static final int DIR = 0, LIST = 1;
    final MainActivity a;
    final boolean overlay, isFavs;
    int mode;
    File cur;
    String listTitle = "";
    boolean limit, loading;
    List<Entry> all = new ArrayList<>(), shown = new ArrayList<>();
    String query = "";
    int searchTypeFilter = 0, searchSizeFilter = 0, searchDateFilter = 0;
    final LinkedHashSet<Entry> sel = new LinkedHashSet<>();
    boolean selecting;
    String rangeAnchor;
    volatile long selectionSummaryToken;
    long loadGeneration, filterGeneration;
    Future<?> filterTask;
    int columns = 1;

    final ListView lv;
    final BaseAdapter ad;
    final TextView title, empty, pathTv, pasteBtn;
    final ImageView back, searchBtn, moreBtn;
    final EditText search;
    final ProgressBar prog;
    final LinearLayout selBar, header;
    final View.OnClickListener moreL = v -> itemMenu((Entry) v.getTag());

    BrowserView(MainActivity act, boolean overlay, boolean favs) {
        super(act);
        a = act;
        this.overlay = overlay;
        this.isFavs = favs;
        columns = responsiveColumns(act.getResources().getConfiguration().screenWidthDp,
                ToxiVisualSystem.booleanPreference("grid", false));
        mode = favs ? LIST : DIR;
        if (favs) listTitle = "Favorites";

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -1));

        header = new LinearLayout(a);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)));
        back = ToxiVisualSystem.iconButton(a, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setOnClickListener(v -> { if (selecting) endSelect(); else if (!handleBack()) a.closeOverlay(); });
        title = ToxiVisualSystem.text(a, "", 24, ToxiVisualSystem.TEXT_PRIMARY, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        searchBtn = ToxiVisualSystem.iconButton(a, ToxiIcons.SEARCH, ToxiVisualSystem.TEXT_PRIMARY);
        searchBtn.setOnClickListener(v -> toggleSearch());
        moreBtn = ToxiVisualSystem.iconButton(a, ToxiIcons.MORE, ToxiVisualSystem.TEXT_PRIMARY);
        moreBtn.setOnClickListener(v -> headerMenu());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48))));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
        header.addView(title, tl);
        header.addView(searchBtn, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48))));
        header.addView(moreBtn, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48))));
        header.setBackground(ToxiVisualSystem.surface(24, true));
        header.setElevation(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)));
        header.setMinimumHeight(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(60)));
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        col.addView(header, headerLp);

        pathTv = ToxiVisualSystem.text(a, "", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        pathTv.setSingleLine(true);
        pathTv.setEllipsize(TextUtils.TruncateAt.START);
        pathTv.setBackground(ToxiVisualSystem.surface(16, false));
        pathTv.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        col.addView(pathTv, pl);

        search = new EditText(a);
        search.setSingleLine(true);
        search.setHint("Search files, or .pdf for a type");
        search.setHintTextColor(ToxiVisualSystem.TEXT_SECONDARY);
        search.setTextColor(ToxiVisualSystem.TEXT_PRIMARY);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ToxiVisualSystem.textSizePx(a, 15));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(ToxiVisualSystem.surface(18, true));
        search.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        search.setVisibility(GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        col.addView(search, sl);

        lv = new ListView(a);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setClipToPadding(false);
        lv.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(120)));
        lv.setVerticalScrollBarEnabled(false);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        ToxiVisualSystem.revealChildren(col);

        ad = new BaseAdapter() {
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int i) { return columns <= 1 ? 0 : 1; }
            @Override public int getCount() {
                if (columns <= 1) return shown.size();
                return (shown.size() + columns - 1) / columns;
            }
            @Override public Object getItem(int i) { return columns <= 1 ? shown.get(i) : null; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                if (columns <= 1) {
                    ToxiFileRow.Holder h = ToxiFileRow.holder(cv);
                    if (h == null) {
                        cv = ToxiFileRow.create(a);
                        h = ToxiFileRow.holder(cv);
                    }
                    Entry e = shown.get(i);
                    String extra = null;
                    if (mode == LIST) { File par = e.f.getParentFile(); extra = par == null ? "" : par.getName(); }
                    ToxiFileRow.bind(h, e, selecting, sel.contains(e), extra);
                    ToxiFileRow.applyLabel(h, Labels.get(a, e.f));
                    // The whole row opens on one tap; the overflow control remains independent.
                    View.OnClickListener openClick = v -> { if (selecting) toggle(e); else open(e); };
                    View.OnLongClickListener selectClick = v -> { if (selecting) selectRange(e); else startSelect(e); return true; };
                    h.root.setOnClickListener(openClick);
                    h.row.setOnClickListener(openClick);
                    h.root.setOnLongClickListener(selectClick);
                    h.row.setOnLongClickListener(selectClick);
                    h.root.setContentDescription((e.dir ? "Folder " : "File ") + e.name + ", tap to open");
                    h.more.setTag(e);
                    h.more.setOnClickListener(moreL);
                    return cv;
                }
                LinearLayout row = (cv instanceof LinearLayout) ? (LinearLayout) cv : buildGridRow();
                for (int col = 0; col < columns; col++) {
                    int idx = i * columns + col;
                    FrameLayout tile = (FrameLayout) row.getChildAt(col);
                    ImageView thumb = (ImageView) tile.getChildAt(0);
                    TextView nameTv = (TextView) tile.getChildAt(1);
                    View check = tile.getChildAt(2);
                    if (idx >= shown.size()) { tile.setVisibility(View.INVISIBLE); tile.setOnClickListener(null); tile.setOnLongClickListener(null); continue; }
                    tile.setVisibility(View.VISIBLE);
                    final Entry e = shown.get(idx);
                    Thumbs.bind(thumb, e, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(96)));
                    nameTv.setText(e.name);
                    check.setVisibility(selecting && sel.contains(e) ? View.VISIBLE : View.GONE);
                    tile.setOnClickListener(v -> { if (selecting) toggle(e); else open(e); });
                    tile.setOnLongClickListener(v -> { if (selecting) selectRange(e); else startSelect(e); return true; });
                }
                return row;
            }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            if (columns > 1 || pos >= shown.size()) return;
            Entry e = shown.get(pos);
            if (selecting) toggle(e); else open(e);
        });
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            if (columns <= 1 && pos < shown.size()) { if (selecting) selectRange(shown.get(pos)); else startSelect(shown.get(pos)); }
            return true;
        });

        empty = ToxiVisualSystem.text(a, "", 15, ToxiVisualSystem.TEXT_SECONDARY, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        addView(empty, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        empty.setOnClickListener(v -> { if (!a.hasAccess()) a.showStorageAccess(); });

        prog = new ProgressBar(a);
        prog.setIndeterminate(true);
        prog.setIndeterminateTintList(ColorStateList.valueOf(ToxiVisualSystem.signalTextColor()));
        prog.setVisibility(GONE);
        addView(prog, new FrameLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)), Gravity.CENTER));

        selBar = new LinearLayout(a);
        selBar.setOrientation(LinearLayout.HORIZONTAL);
        selBar.setGravity(Gravity.CENTER);
        selBar.setBackground(ToxiVisualSystem.surface(28, true));
        selBar.setVisibility(GONE);
        selBar.addView(selBtn(ToxiIcons.COPY, "Copy", () -> copySel(false)));
        selBar.addView(selBtn(ToxiIcons.CUT, "Cut", () -> copySel(true)));
        selBar.addView(selBtn(ToxiIcons.TRASH, "Delete", () -> confirmSelectionDelete(new ArrayList<>(sel))));
        selBar.addView(selBtn(ToxiIcons.SHARE, "Share", () -> FileOps.share(a, files(sel))));
        selBar.addView(selBtn(ToxiIcons.MORE, "More", this::selMore));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(68)), Gravity.BOTTOM);
        bl.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)) + ToxiVisualSystem.BOTTOM_INSET);
        addView(selBar, bl);

        pasteBtn = ToxiVisualSystem.text(a, "Paste here", 14, ToxiVisualSystem.TEXT_PRIMARY, true);
        pasteBtn.setBackground(ToxiVisualSystem.accentSurface(24));
        pasteBtn.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)));
        pasteBtn.setVisibility(GONE);
        ToxiVisualSystem.interactive(pasteBtn);
        pasteBtn.setOnClickListener(v -> paste());
        FrameLayout.LayoutParams pb = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        pb.setMargins(0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(100)) + ToxiVisualSystem.BOTTOM_INSET);
        addView(pasteBtn, pb);

        updateHeader();
    }

    View selBtn(int glyph, String label, final Runnable action) {
        LinearLayout button = new LinearLayout(a);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), 0);
        ImageView symbol = new ImageView(a);
        symbol.setImageDrawable(ToxiIcons.symbol(glyph, ToxiVisualSystem.TEXT_PRIMARY));
        button.addView(symbol, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(19)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(19))));
        TextView caption = ToxiVisualSystem.text(a, label, 10.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(-2, -2);
        captionLp.leftMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5));
        button.addView(caption, captionLp);
        button.setOnClickListener(v -> { ToxiVisualSystem.feedback(v); action.run(); });
        ToxiVisualSystem.interactive(button);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        return button;
    }

    void applyInsets() {
        lv.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(112)) + ToxiVisualSystem.BOTTOM_INSET);
        FrameLayout.LayoutParams selectionParams = (FrameLayout.LayoutParams) selBar.getLayoutParams();
        selectionParams.bottomMargin = ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12));
        selBar.setLayoutParams(selectionParams);
        FrameLayout.LayoutParams pasteParams = (FrameLayout.LayoutParams) pasteBtn.getLayoutParams();
        pasteParams.bottomMargin = ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.dp(ToxiThemeEngine.spacing(100));
        pasteBtn.setLayoutParams(pasteParams);
    }

    void recolor() {
        setBackgroundColor(ToxiVisualSystem.CANVAS);
        header.setBackground(ToxiVisualSystem.surface(22, false));
        back.setImageDrawable(ToxiIcons.symbol(ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY));
        searchBtn.setImageDrawable(ToxiIcons.symbol(ToxiIcons.SEARCH, ToxiVisualSystem.TEXT_PRIMARY));
        moreBtn.setImageDrawable(ToxiIcons.symbol(ToxiIcons.MORE, ToxiVisualSystem.TEXT_PRIMARY));
        pathTv.setBackground(ToxiVisualSystem.surface(16, false));
        search.setBackground(ToxiVisualSystem.surface(18, true));
        selBar.setBackground(ToxiVisualSystem.surface(18, true));
        pasteBtn.setBackground(ToxiVisualSystem.accentSurface(18));
        prog.setIndeterminateTintList(ColorStateList.valueOf(ToxiVisualSystem.signalTextColor()));
        empty.setTextColor(ToxiVisualSystem.TEXT_SECONDARY);
        int firstVisible = lv.getFirstVisiblePosition();
        View firstChild = lv.getChildAt(0);
        int firstTop = firstChild == null ? 0 : firstChild.getTop();
        lv.setAdapter(null);
        lv.setAdapter(ad);
        if (ad.getCount() > 0) {
            lv.setSelectionFromTop(Math.min(firstVisible, ad.getCount() - 1), firstTop);
        }
    }

    // ---------- loading ----------
    boolean atRoot() { return cur == null || cur.equals(Environment.getExternalStorageDirectory()); }

    void showDir(File dir) {
        mode = DIR;
        cur = dir;
        closeSearch();
        selecting = false;
        sel.clear();
        rangeAnchor = null;
        selBar.setVisibility(GONE);
        a.navForSelection(false);
        updateHeader();
        loadDir();
        updatePaste();
    }

    void reload() {
        if (isFavs) refreshFavs();
        else if (mode == DIR && cur != null) loadDir();
        else pruneMissing();
    }

    Comparator<Entry> comparator() {
        final int k = ToxiVisualSystem.PREFERENCES.getInt("sort", 0);
        final boolean asc = ToxiVisualSystem.PREFERENCES.getBoolean("asc", true);
        return (x, y) -> {
            if (x.dir != y.dir) return x.dir ? -1 : 1;
            int r;
            switch (k) {
                case 1: r = Long.compare(x.mod, y.mod); break;
                case 2: r = Long.compare(x.len, y.len); break;
                case 3: r = x.ext.compareTo(y.ext); if (r == 0) r = x.lname.compareTo(y.lname); break;
                default: r = x.lname.compareTo(y.lname);
            }
            return asc ? r : -r;
        };
    }

    void loadDir() {
        final long token = ++loadGeneration;
        final File d = cur;
        if (!a.hasAccess()) { loading = false; all = new ArrayList<>(); sel.clear(); selecting = false; rangeAnchor = null; updateSel(); applyFilter(); return; }
        loading = true;
        updateEmpty();
        final Comparator<Entry> cmp = comparator();
        a.executeIo(() -> {
            final List<Entry> res = new ArrayList<>();
            File[] fs = d.listFiles();
            boolean hid = ToxiVisualSystem.booleanPreference("hidden", false);
            if (fs != null) for (File f : fs) { if (!hid && f.getName().startsWith(".")) continue; res.add(new Entry(f)); }
            Collections.sort(res, cmp);
            post(() -> {
                if (token != loadGeneration || d != cur || mode != DIR) return;
                loading = false;
                all = res;
                reconcileSelection();
                applyFilter();
                lv.setSelection(0);
            });
        }, () -> {
            if (token == loadGeneration && d == cur && mode == DIR) {
                loading = false;
                updateEmpty();
            }
        });
    }

    void refreshFavs() {
        final long token = ++loadGeneration;
        final List<String> ps = new ArrayList<>(a.favs);
        a.executeIo(() -> {
            final List<Entry> l = new ArrayList<>();
            for (String p : ps) { File f = new File(p); if (f.exists()) l.add(new Entry(f)); }
            post(() -> { if (token != loadGeneration || mode != LIST || !isFavs) return; loading = false; all = l; reconcileSelection(); applyFilter(); });
        });
    }

    /** list == null means "still loading". */
    void setList(String t, List<Entry> list, boolean limited, boolean focusSearch) {
        ++loadGeneration;
        mode = LIST;
        listTitle = t;
        String normalizedTitle = t == null ? "" : t.toLowerCase(Locale.ROOT);
        boolean mediaGrid = normalizedTitle.contains("image") || normalizedTitle.contains("photo")
                || normalizedTitle.contains("video") || normalizedTitle.contains("audio")
                || normalizedTitle.contains("music");
        columns = responsiveColumns(getResources().getConfiguration().screenWidthDp,
                mediaGrid || ToxiVisualSystem.booleanPreference("grid", false));
        limit = limited;
        selecting = false;
        sel.clear();
        rangeAnchor = null;
        selBar.setVisibility(GONE);
        a.navForSelection(false);
        loading = list == null;
        all = list == null ? new ArrayList<Entry>() : new ArrayList<>(list);
        if (focusSearch && search.getVisibility() != VISIBLE) showSearch();
        else if (!focusSearch && list != null && search.getVisibility() == VISIBLE && !limited) { /* keep */ }
        updateHeader();
        updatePaste();
        applyFilter();
    }

    void pruneMissing() {
        final long token = ++loadGeneration;
        final List<Entry> snap = new ArrayList<>(all);
        a.executeIo(() -> {
            final List<Entry> keep = new ArrayList<>();
            for (Entry e : snap) if (e.f.exists()) keep.add(e);
            post(() -> { if (token != loadGeneration) return; all = keep; reconcileSelection(); applyFilter(); });
        });
    }

    void applyFilter() {
        final long token = ++filterGeneration;
        if (filterTask != null) filterTask.cancel(true);
        final List<Entry> snapshot = new ArrayList<>(all);
        final String filter = query;
        final int currentMode = mode;
        final String currentTitle = listTitle;
        final boolean capped = limit;
        final int typeFilter = searchTypeFilter;
        final int sizeFilter = searchSizeFilter;
        final int dateFilter = searchDateFilter;
        final long now = System.currentTimeMillis();
        filterTask = a.io.submit(() -> {
            List<Entry> out = new ArrayList<>();
            int cap = capped ? 500 : Integer.MAX_VALUE;
            boolean extOnly = filter.length() > 1 && filter.charAt(0) == '.';
            String extQuery = extOnly ? filter.substring(1) : null;
            for (Entry e : snapshot) {
                if (Thread.currentThread().isInterrupted()) return;
                boolean match = filter.isEmpty() || (extOnly ? e.ext.equals(extQuery) : e.lname.contains(filter));
                if (match && currentMode == LIST && "Search".equals(currentTitle))
                    match = matchesAdvancedFilter(e, typeFilter, sizeFilter, dateFilter, now);
                if (match) { out.add(e); if (out.size() >= cap) break; }
            }
            post(() -> {
                if (token != filterGeneration) return;
                shown = out;
                ad.notifyDataSetChanged();
                updateEmpty();
            });
        });
    }

    void updateEmpty() {
        ToxiMotion.setActive(prog, loading);
        if (loading) { empty.setVisibility(GONE); prog.setVisibility(VISIBLE); return; }
        prog.setVisibility(GONE);
        if (mode == DIR && !a.hasAccess()) {
            empty.setText("Allow access to all files\nTap here to open settings");
            empty.setVisibility(VISIBLE);
        } else if (shown.isEmpty()) {
            empty.setText((!query.isEmpty() || (mode == LIST && "Search".equals(listTitle) && hasAdvancedFilters())) ? "No results for the current search and filters" : mode == DIR ? "This folder is empty" : isFavs ? "No favorites yet\nUse the file action menu to add one" : "Nothing here yet");
            empty.setVisibility(VISIBLE);
        } else empty.setVisibility(GONE);
    }

    void updateHeader() {
        if (selecting) {
            title.setText(sel.size() + " selected");
            back.setImageDrawable(ToxiIcons.symbol(ToxiIcons.CLOSE, ToxiVisualSystem.TEXT_PRIMARY));
            back.setVisibility(VISIBLE);
            searchBtn.setVisibility(GONE);
            pathTv.setVisibility(GONE);
            return;
        }
        searchBtn.setVisibility(VISIBLE);
        back.setImageDrawable(ToxiIcons.symbol(ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY));
        if (mode == DIR) {
            title.setText(atRoot() ? "Internal Storage" : (cur == null ? "" : cur.getName()));
            back.setVisibility(atRoot() ? GONE : VISIBLE);
            pathTv.setVisibility(VISIBLE);
            if (cur != null) {
                String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                pathTv.setText(cur.getAbsolutePath().replace(root, "Internal storage"));
            }
        } else {
            title.setText(listTitle + ("Search".equals(listTitle) && hasAdvancedFilters() ? " • Filtered" : ""));
            back.setVisibility(overlay ? VISIBLE : GONE);
            pathTv.setVisibility(GONE);
        }
        title.setPadding(back.getVisibility() == VISIBLE ? 0 : ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0, 0, 0);
    }

    // ---------- search ----------
    void toggleSearch() { if (search.getVisibility() == VISIBLE) closeSearch(); else showSearch(); }

    void showSearch() {
        search.setVisibility(VISIBLE);
        search.requestFocus();
        InputMethodManager im = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.showSoftInput(search, 0);
    }

    void closeSearch() {
        if (search.getVisibility() != VISIBLE && query.isEmpty()) return;
        search.setText("");
        search.setVisibility(GONE);
        InputMethodManager im = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    // ---------- navigation / open ----------
    boolean handleBack() {
        if (selecting) { endSelect(); return true; }
        if (search.getVisibility() == VISIBLE) { closeSearch(); return true; }
        if (overlay) { a.closeOverlay(); return true; }
        if (mode == DIR && !atRoot()) {
            File p = cur.getParentFile();
            if (p != null) { showDir(p); return true; }
        }
        return false;
    }

    void open(Entry e) {
        if (e.dir) { if (mode == DIR) showDir(e.f); else a.openDir(e.f); }
        else a.openFile(e, shown);
    }

    // ---------- selection ----------
    private List<String> selectedPaths() {
        List<String> paths = new ArrayList<>();
        for (Entry e : sel) paths.add(e.f.getAbsolutePath());
        return paths;
    }

    private void reconcileSelection() {
        if (sel.isEmpty()) return;
        java.util.Set<String> paths = new java.util.HashSet<>(selectedPaths());
        sel.clear();
        for (Entry e : all) if (paths.contains(e.f.getAbsolutePath())) sel.add(e);
        if (sel.isEmpty()) selecting = false;
        updateSel();
    }

    void startSelect(Entry e) { selecting = true; sel.clear(); sel.add(e); rangeAnchor = e.f.getAbsolutePath(); ToxiVisualSystem.feedback(this); updateSel(); }
    void toggle(Entry e) { if (!sel.remove(e)) { sel.add(e); if (rangeAnchor == null) rangeAnchor = e.f.getAbsolutePath(); } if (sel.isEmpty()) endSelect(); else updateSel(); }
    void selectRange(Entry end) {
        if (!selecting) { startSelect(end); return; }
        int first = -1, last = -1;
        for (int i=0;i<shown.size();i++) {
            String path = shown.get(i).f.getAbsolutePath();
            if (path.equals(rangeAnchor)) first = i;
            if (path.equals(end.f.getAbsolutePath())) last = i;
        }
        if (first < 0 || last < 0) { toggle(end); return; }
        for (int i=Math.min(first,last);i<=Math.max(first,last);i++) sel.add(shown.get(i));
        updateSel();
    }
    void endSelect() { selecting = false; sel.clear(); rangeAnchor = null; updateSel(); }
    void selectAll() { selecting = true; if (rangeAnchor == null && !shown.isEmpty()) rangeAnchor = shown.get(0).f.getAbsolutePath(); sel.addAll(shown); updateSel(); }
    void deselectAll() { endSelect(); }

    private void updateSelectionSummary() {
        final long token = ++selectionSummaryToken;
        final List<File> snapshot = files(sel);
        final int count = snapshot.size();
        if (count == 0) return;
        title.setText(count + " selected · calculating size…");
        a.executeIo(() -> {
            if (token != selectionSummaryToken) return;
            long[] totals = new long[2];
            for (File f : snapshot) {
                if (token != selectionSummaryToken) return;
                if (f.exists() && !FileOps.treeSize(f, totals, () -> token != selectionSummaryToken)) {
                    if (token != selectionSummaryToken || Thread.currentThread().isInterrupted()) return;
                    post(() -> { if (token == selectionSummaryToken && selecting && sel.size() == count) title.setText(count + " selected · size unavailable"); });
                    return;
                }
            }
            final long bytes = totals[0];
            post(() -> { if (token == selectionSummaryToken && selecting && sel.size() == count) title.setText(count + " selected · " + ToxiVisualSystem.formatSize(bytes)); });
        }, () -> {
            if (token == selectionSummaryToken && selecting && sel.size() == count) title.setText(count + " selected · size unavailable");
        });
    }

    void selectionStudio() {
        if (sel.isEmpty()) return;
        final TextView details = ToxiVisualSystem.text(a, sel.size() + " selected items\nLong-press another item to select a range.\nCalculating combined size…", 15, ToxiVisualSystem.TEXT_PRIMARY, true);
        details.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)));
        AlertDialog dialog = ToxiVisualSystem.dialogBuilder(a).setTitle("Selection Studio").setView(details)
                .setNeutralButton("Deselect all", (d,w) -> deselectAll())
                .setNegativeButton("Close", null)
                .setPositiveButton("Select all", (d,w) -> selectAll()).create();
        final List<File> snapshot = files(sel);
        java.util.concurrent.atomic.AtomicBoolean dismissed = new java.util.concurrent.atomic.AtomicBoolean(false);
        dialog.setOnDismissListener(d -> dismissed.set(true));
        dialog.setOnShowListener(d -> {
            ToxiVisualSystem.styleDialog(dialog);
            a.executeIo(() -> {
                long[] total = new long[2];
                boolean complete = true;
                for (File f : snapshot) {
                    if (dismissed.get()) return;
                    if (f.exists() && !FileOps.treeSize(f, total, dismissed::get)) {
                        if (dismissed.get() || Thread.currentThread().isInterrupted()) return;
                        complete = false;
                        break;
                    }
                }
                final String value = complete
                        ? snapshot.size() + " selected items\n" + ToxiVisualSystem.formatSize(total[0]) + " combined file size\n" + total[1] + " files"
                        : snapshot.size() + " selected items\nCombined size unavailable";
                post(() -> { if (dialog.isShowing() && !dismissed.get()) details.setText(value); });
            }, () -> { if (dialog.isShowing() && !dismissed.get()) details.setText(snapshot.size() + " selected items\nCombined size unavailable"); });
        });
        dialog.show();
    }

    void updateSel() {
        selBar.setVisibility(selecting ? VISIBLE : GONE);
        a.navForSelection(selecting);
        updateHeader();
        updateSelectionSummary();
        updatePaste();
        ad.notifyDataSetChanged();
    }

    static List<File> files(java.util.Collection<Entry> c) {
        List<File> l = new ArrayList<>();
        for (Entry e : c) l.add(e.f);
        return l;
    }

    // ---------- clipboard ----------
    void copySel(boolean cut) {
        List<File> l = files(sel);
        a.setClip(l.toArray(new File[0]), cut);
        ToxiVisualSystem.toast(a, (cut ? "Cut " : "Copied ") + l.size() + " — open a folder and tap Paste");
        endSelect();
    }

    void updatePaste() {
        boolean show = mode == DIR && !overlay && a.clipboard.hasItems() && !selecting;
        pasteBtn.setVisibility(show ? VISIBLE : GONE);
        if (show) pasteBtn.setText("Paste " + a.clipboard.size() + " here");
    }

    void paste() {
        if (!a.clipboard.hasItems() || cur == null) return;
        final File[] src = a.clipboard.snapshot();
        final boolean cut = a.clipboard.isCut();
        FileOps.Done done = msg -> {
            if (cut) a.setClip(null, false);
            ToxiVisualSystem.toast(a, msg);
            a.afterOps();
        };
        if (cut) ToxiVisualSystem.dialogBuilder(a).setTitle("Move " + src.length + " items?")
                .setMessage("The selected items will be moved out of their current folder. Continue?")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", (d,w) -> pasteWithConflictPrompt(src,cur,true,done)).show();
        else pasteWithConflictPrompt(src, cur, false, done);
    }

    void pasteWithConflictPrompt(final File[] src, final File destination, final boolean move, final FileOps.Done done) {
        int conflicts = 0;
        for (File file : src) if (!(move && destination.equals(file.getParentFile())) && new File(destination, file.getName()).exists()) conflicts++;
        if (conflicts == 0) { FileOps.paste(a, src, destination, move, false, done); return; }
        ToxiVisualSystem.dialogBuilder(a).setTitle("Name conflicts")
                .setMessage(conflicts + " item" + (conflicts == 1 ? " already exists" : "s already exist") + " in the destination. Choose how to continue.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Skip existing", (dialog, which) -> FileOps.paste(a, src, destination, move, true, done))
                .setPositiveButton("Keep both", (dialog, which) -> FileOps.paste(a, src, destination, move, false, done))
                .show();
    }

    // ---------- menus ----------
    static void add(List<String> l, List<Runnable> r, String s, Runnable x) { l.add(s); r.add(x); }

    static void showMenu(Context c, String title, List<String> l, final List<Runnable> r) {
        ToxiVisualSystem.dialogBuilder(c).setTitle(title).setItems(l.toArray(new String[0]), (d, w) -> r.get(w).run()).show();
    }

    LinearLayout buildGridRow() {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < columns; i++) {
            FrameLayout tile = new FrameLayout(a);
            tile.setBackground(ToxiVisualSystem.surface(15, false));
            tile.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
            ImageView thumb = new ImageView(a);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(ToxiVisualSystem.surface(12, true));
            ToxiVisualSystem.clipRound(thumb, 12);
            FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(columns <= 2 ? 76 : 68)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(columns <= 2 ? 76 : 68)), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            tile.addView(thumb, thumbLp);
            TextView nameTv = ToxiVisualSystem.text(a, "", 11.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
            nameTv.setMaxLines(2);
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            nameTv.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            nameLp.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)));
            tile.addView(nameTv, nameLp);
            View check = new View(a);
            check.setBackground(ToxiVisualSystem.accentSurface(9));
            check.setVisibility(GONE);
            FrameLayout.LayoutParams checkLp = new FrameLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(17)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(17)), Gravity.TOP | Gravity.END);
            checkLp.setMargins(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), 0);
            tile.addView(check, checkLp);
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0,
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(columns <= 2 ? 138 : 126)), 1f);
            tileLp.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)));
            row.addView(tile, tileLp);
        }
        return row;
    }

    private static int responsiveColumns(int widthDp, boolean gridEnabled) {
        if (!gridEnabled) return 1;
        if (widthDp >= 900) return 5;
        if (widthDp >= 700) return 4;
        if (widthDp >= 460) return 3;
        return 2;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (ad == null || width <= 0) return;
        int widthDp = Math.round(width / Math.max(1f, getResources().getDisplayMetrics().density));
        boolean mediaGrid = mode == LIST && (listTitle.toLowerCase(Locale.ROOT).contains("image")
                || listTitle.toLowerCase(Locale.ROOT).contains("photo")
                || listTitle.toLowerCase(Locale.ROOT).contains("video")
                || listTitle.toLowerCase(Locale.ROOT).contains("audio")
                || listTitle.toLowerCase(Locale.ROOT).contains("music"));
        int nextColumns = responsiveColumns(widthDp, mediaGrid || ToxiVisualSystem.booleanPreference("grid", false));
        if (nextColumns != columns) {
            columns = nextColumns;
            ad.notifyDataSetChanged();
        }
    }

    void toggleView() {
        boolean enabled = columns <= 1;
        ToxiVisualSystem.setBooleanPreference("grid", enabled);
        columns = responsiveColumns(a.getResources().getConfiguration().screenWidthDp, enabled);
        ad.notifyDataSetChanged();
    }

    void headerMenu() {
        List<String> l = new ArrayList<>();
        List<Runnable> r = new ArrayList<>();
        if (mode == DIR && !selecting && !isFavs) add(l, r, "Favorites", a::showFavorites);
        if (mode == DIR && !selecting) add(l, r, "New folder", this::newFolder);
        if (mode == DIR && !selecting) add(l, r, "Choose SD/USB storage…", a::openOtherStorage);
        if (mode == DIR && !selecting && cur != null) add(l, r, "Set as default start folder", () -> { ToxiVisualSystem.PREFERENCES.edit().putString("default_folder", cur.getAbsolutePath()).apply(); ToxiVisualSystem.toast(a, "Set as default: " + cur.getAbsolutePath()); });
        add(l, r, columns > 1 ? "List view" : "Grid view", this::toggleView);
        add(l, r, "Sort by…", this::sortDialog);
        if (mode == LIST && "Search".equals(listTitle)) add(l, r, "Advanced search filters…", this::advancedSearchDialog);
        if (selecting) {
            add(l, r, "Selection Studio · count and size", this::selectionStudio);
            add(l, r, "Deselect all", this::deselectAll);
        } else if (!shown.isEmpty()) add(l, r, "Select all", this::selectAll);
        if (mode == DIR) {
            boolean on = ToxiVisualSystem.booleanPreference("hidden", false);
            add(l, r, on ? "Hide hidden files" : "Show hidden files", () -> { ToxiVisualSystem.setBooleanPreference("hidden", !on); reload(); });
            if (a.clipboard.hasItems()) add(l, r, "Paste here", this::paste);
            add(l, r, "Refresh", this::reload);
        }
        showMenu(a, "Options", l, r);
    }

    private Spinner filterSpinner(String[] options, int selected) {
        Spinner spinner = new Spinner(a);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(a, android.R.layout.simple_spinner_item, options) {
            private TextView styleRow(View row, String value) {
                TextView text = (TextView) row;
                text.setText(value);
                text.setTextColor(ToxiVisualSystem.TEXT_PRIMARY);
                text.setTypeface(android.graphics.Typeface.create(ToxiVisualSystem.fontFamily(), android.graphics.Typeface.NORMAL));
                text.setTextSize(13);
                text.setSingleLine(true);
                text.setEllipsize(TextUtils.TruncateAt.END);
                text.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),
                        ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
                return text;
            }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return styleRow(super.getView(position, convertView, parent), getItem(position));
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return styleRow(super.getDropDownView(position, convertView, parent), getItem(position));
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selected);
        return spinner;
    }

    void advancedSearchDialog() {
        LinearLayout panel = new LinearLayout(a); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        String[] types = {"Any type", "Images", "Videos", "Audio", "Documents", "Archives", "APK packages", "Folders", "Other files"};
        String[] sizes = {"Any size", "At least 1 MB", "At least 10 MB", "At least 100 MB"};
        String[] dates = {"Any date", "Modified in last 7 days", "Modified in last 30 days", "Modified in last 365 days"};
        panel.addView(ToxiVisualSystem.text(a, "File type", 12, ToxiVisualSystem.TEXT_SECONDARY, true));
        Spinner typeSpinner = filterSpinner(types, searchTypeFilter); panel.addView(typeSpinner);
        panel.addView(ToxiVisualSystem.text(a, "Minimum size", 12, ToxiVisualSystem.TEXT_SECONDARY, true));
        Spinner sizeSpinner = filterSpinner(sizes, searchSizeFilter); panel.addView(sizeSpinner);
        panel.addView(ToxiVisualSystem.text(a, "Modified date", 12, ToxiVisualSystem.TEXT_SECONDARY, true));
        Spinner dateSpinner = filterSpinner(dates, searchDateFilter); panel.addView(dateSpinner);
        ToxiVisualSystem.dialogBuilder(a).setTitle("Advanced search")
                .setMessage("Filters apply to the indexed search results. Open a folder directly to browse files outside the global index limit.")
                .setView(panel).setNegativeButton("Cancel", null)
                .setNeutralButton("Reset", (d, w) -> { searchTypeFilter = searchSizeFilter = searchDateFilter = 0; applyFilter(); updateHeader(); })
                .setPositiveButton("Apply", (d, w) -> {
                    searchTypeFilter = typeSpinner.getSelectedItemPosition();
                    searchSizeFilter = sizeSpinner.getSelectedItemPosition();
                    searchDateFilter = dateSpinner.getSelectedItemPosition();
                    applyFilter(); updateHeader();
                }).show();
    }

    private TextView styleSpinnerRow(View row, String value) {
        TextView text = (TextView) row;
        text.setText(value);
        text.setTextColor(ToxiVisualSystem.TEXT_PRIMARY);
        text.setTypeface(android.graphics.Typeface.create(ToxiVisualSystem.fontFamily(), android.graphics.Typeface.NORMAL));
        text.setTextSize(13);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setBackground(ToxiVisualSystem.surface(10, false));
        text.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        return text;
    }

    private static boolean matchesAdvancedFilter(Entry e, int typeFilter, int sizeFilter, int dateFilter, long now) {
        if (typeFilter != 0) {
            boolean typeMatch;
            switch (typeFilter) {
                case 1: typeMatch = e.kind == FileKinds.IMAGE; break;
                case 2: typeMatch = e.kind == FileKinds.VIDEO; break;
                case 3: typeMatch = e.kind == FileKinds.AUDIO; break;
                case 4: typeMatch = e.kind == FileKinds.PDF || e.kind == FileKinds.TEXT; break;
                case 5: typeMatch = e.kind == FileKinds.ARCHIVE; break;
                case 6: typeMatch = e.kind == FileKinds.APK; break;
                case 7: typeMatch = e.dir; break;
                default: typeMatch = e.kind == FileKinds.OTHER; break;
            }
            if (!typeMatch) return false;
        }
        long minBytes = sizeFilter == 1 ? 1L * 1024 * 1024 : sizeFilter == 2 ? 10L * 1024 * 1024 : sizeFilter == 3 ? 100L * 1024 * 1024 : 0L;
        if (minBytes > 0 && (e.dir || e.len < minBytes)) return false;
        int days = dateFilter == 1 ? 7 : dateFilter == 2 ? 30 : dateFilter == 3 ? 365 : 0;
        return days == 0 || e.mod >= now - days * 86_400_000L;
    }

    private boolean hasAdvancedFilters() { return searchTypeFilter != 0 || searchSizeFilter != 0 || searchDateFilter != 0; }

    void sortDialog() {
        final String[] names = {"Name A to Z", "Name Z to A", "Newest first", "Oldest first", "Largest first", "Smallest first", "Type"};
        ToxiVisualSystem.dialogBuilder(a).setTitle("Sort by").setItems(names, (d, w) -> {
            int k; boolean asc;
            switch (w) {
                case 0: k = 0; asc = true; break;
                case 1: k = 0; asc = false; break;
                case 2: k = 1; asc = false; break;
                case 3: k = 1; asc = true; break;
                case 4: k = 2; asc = false; break;
                case 5: k = 2; asc = true; break;
                default: k = 3; asc = true;
            }
            ToxiVisualSystem.PREFERENCES.edit().putInt("sort", k).putBoolean("asc", asc).apply();
            final Comparator<Entry> cmp = comparator();
            Collections.sort(all, cmp);
            applyFilter();
        }).show();
    }

    void selMore() {
        List<String> l = new ArrayList<>();
        List<Runnable> r = new ArrayList<>();
        final List<Entry> s = new ArrayList<>(sel);
        add(l, r, "Selection Studio · count and size", this::selectionStudio);
        add(l, r, "Select all", this::selectAll);
        add(l, r, "Deselect all", this::deselectAll);
        add(l, r, "Compress to ZIP", () -> zip(s));
        add(l, r, "Move to…", () -> confirmSelectionMove(s));
        add(l, r, "Send via Wi-Fi…", () -> a.sendViaWifi(files(s)));
        if (a.hasSafTree()) add(l, r, "Copy to SD/USB", () -> a.copyToSafTree(files(s)));
        add(l, r, "Label", () -> labelDialog(s));
        add(l, r, "Move to Vault", () -> vaultMove(s));
        if (s.size() == 1) {
            final Entry e = s.get(0);
            add(l, r, "Rename", () -> rename(e));
            add(l, r, a.isFav(e.f) ? "Remove from favorites" : "Add to favorites", () -> { a.toggleFav(e.f); endSelect(); a.afterOps(); });
            add(l, r, "Properties", () -> properties(e));
        }
        add(l, r, "Batch Rename Studio", () -> batchRename(s));
        showMenu(a, "More", l, r);
    }

    /** Native preview-first batch rename studio. All filesystem work runs on the existing ops executor. */
    void batchRename(final List<Entry> list) {
        if (list == null || list.isEmpty()) return;
        final EditText prefix = input("");
        final EditText suffix = input("");
        final EditText find = input("");
        final EditText replacement = input("");
        final EditText start = input("1");
        final EditText increment = input("1");
        final EditText padding = input("1");
        final CheckBox numbering = new CheckBox(a); numbering.setText("Append sequential number"); numbering.setTextColor(ToxiVisualSystem.TEXT_PRIMARY); numbering.setButtonTintList(ColorStateList.valueOf(ToxiVisualSystem.signalTextColor()));
        final CheckBox preserve = new CheckBox(a); preserve.setText("Preserve file extensions (default)"); preserve.setTextColor(ToxiVisualSystem.TEXT_PRIMARY); preserve.setButtonTintList(ColorStateList.valueOf(ToxiVisualSystem.signalTextColor())); preserve.setChecked(true);
        final Spinner capitalization = new Spinner(a);
        ArrayAdapter<String> cases = new ArrayAdapter<String>(a, android.R.layout.simple_spinner_item,
                new String[]{"Keep capitalization", "lowercase", "UPPERCASE", "Title Case"}) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return styleSpinnerRow(super.getView(position, convertView, parent), getItem(position));
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return styleSpinnerRow(super.getDropDownView(position, convertView, parent), getItem(position));
            }
        };
        cases.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); capitalization.setAdapter(cases);
        final TextView preview = ToxiVisualSystem.text(a, "Enter rename rules to preview the result.", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        preview.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        LinearLayout panel = new LinearLayout(a); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
        addRenameField(panel,"Prefix",prefix); addRenameField(panel,"Suffix",suffix); addRenameField(panel,"Find text (blank = none)",find); addRenameField(panel,"Replace with (blank removes matches)",replacement);
        addRenameField(panel,"First number",start); addRenameField(panel,"Number increment",increment); addRenameField(panel,"Number padding (digits)",padding);
        addRenameField(panel,"Capitalization",capitalization); panel.addView(numbering); panel.addView(preserve);
        TextView previewTitle = ToxiVisualSystem.text(a,"LIVE BEFORE / AFTER PREVIEW",11,ToxiVisualSystem.ACCENT,true); previewTitle.setPadding(0,ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)),0,ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4))); panel.addView(previewTitle); panel.addView(preview);
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(false); scroll.addView(panel);
        final long[] previewGeneration = {0};
        final Runnable[] pendingPreview = {null};
        final Runnable refresh = () -> {
            final long token = ++previewGeneration[0];
            if (pendingPreview[0] != null) preview.removeCallbacks(pendingPreview[0]);
            pendingPreview[0] = () -> {
                final BatchRenameEngine.Options o = renameOptions(prefix,suffix,find,replacement,start,increment,padding,capitalization,numbering,preserve);
                final List<File> sources = files(list);
                a.executeIo(() -> {
                    List<BatchRenameEngine.Item> plan = BatchRenameEngine.plan(sources,o);
                    StringBuilder text = new StringBuilder(); int shownCount = Math.min(12,plan.size());
                    for(int i=0;i<shownCount;i++){ BatchRenameEngine.Item item=plan.get(i); text.append(item.source == null ? "(missing)" : item.source.getName()).append("\n  to ").append(item.target == null ? "(invalid)" : item.target.getName()); if(!item.valid()) text.append("\n  ! ").append(item.problem); text.append("\n\n"); }
                    if(plan.size()>shownCount) text.append("… and ").append(plan.size()-shownCount).append(" more\n");
                    long invalid=plan.stream().filter(item -> !item.valid()).count();
                    text.append("\n").append(plan.size()).append(" items · ").append(invalid).append(" conflict(s)");
                    final String rendered=text.toString();
                    post(() -> { if(token == previewGeneration[0]) preview.setText(rendered); });
                });
            };
            preview.postDelayed(pendingPreview[0],160);
        };
        android.text.TextWatcher watcher = new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){refresh.run();} public void afterTextChanged(android.text.Editable e){} };
        for(EditText edit : new EditText[]{prefix,suffix,find,replacement,start,increment,padding}) edit.addTextChangedListener(watcher);
        capitalization.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){ public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){refresh.run();} });
        numbering.setOnCheckedChangeListener((b,c)->refresh.run()); preserve.setOnCheckedChangeListener((b,c)->refresh.run()); refresh.run();
        AlertDialog editor = ToxiVisualSystem.dialogBuilder(a).setTitle("Batch Rename Studio · " + list.size() + " items").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Review rename",null).create();
        editor.setOnShowListener(d -> {
            ToxiVisualSystem.styleDialog(editor);
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final long token = ++previewGeneration[0];
            if (pendingPreview[0] != null) preview.removeCallbacks(pendingPreview[0]);
            final android.widget.Button review = editor.getButton(AlertDialog.BUTTON_POSITIVE);
            review.setEnabled(false); preview.setText("Validating destination names…");
            final BatchRenameEngine.Options o = renameOptions(prefix,suffix,find,replacement,start,increment,padding,capitalization,numbering,preserve);
            final List<File> sources = files(list);
            a.executeIo(() -> {
                List<BatchRenameEngine.Item> plan = BatchRenameEngine.plan(sources,o);
                List<String> problems = new ArrayList<>(); for(BatchRenameEngine.Item item:plan) if(!item.valid()) problems.add((item.source==null?"Missing file":item.source.getName())+": "+item.problem);
                StringBuilder summary=new StringBuilder(); for(int i=0;i<Math.min(20,plan.size());i++) { BatchRenameEngine.Item item=plan.get(i); summary.append(item.source==null?"(missing)":item.source.getName()).append(" to ").append(item.target==null?"(invalid)":item.target.getName()).append("\n"); }
                if(plan.size()>20) summary.append("… and ").append(plan.size()-20).append(" more");
                final String reviewText=summary.toString();
                post(() -> {
                    if (!editor.isShowing()) return;
                    review.setEnabled(true);
                    if (token != previewGeneration[0]) return;
                    if(!problems.isEmpty()) { refresh.run(); ToxiVisualSystem.dialogBuilder(a).setTitle("Resolve rename conflicts").setMessage(joinLines(problems,10)).setPositiveButton("Back to editor",null).show(); return; }
                    preview.setText(reviewText + "\n" + plan.size() + " destinations validated · no overwrites");
                    ToxiVisualSystem.dialogBuilder(a).setTitle("Confirm batch rename").setMessage("Rename "+plan.size()+" items? Existing files will never be overwritten.\n\n"+reviewText)
                            .setNegativeButton("Cancel",null).setPositiveButton("Rename",(dd,ww)->{editor.dismiss();executeBatchRename(plan);}).show();
                });
            });
            });
        });
        editor.show();
    }

    private void addRenameField(LinearLayout panel,String label,View field) {
        TextView title=ToxiVisualSystem.text(a,label,12,ToxiVisualSystem.TEXT_SECONDARY,true); title.setPadding(0,ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)),0,ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3))); panel.addView(title);
        if(field instanceof EditText){ EditText edit=(EditText)field; edit.setTextColor(ToxiVisualSystem.TEXT_PRIMARY); edit.setHintTextColor(ToxiVisualSystem.TEXT_SECONDARY); edit.setBackground(ToxiVisualSystem.surface(12,true)); edit.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9))); }
        panel.addView(field,new LinearLayout.LayoutParams(-1,field instanceof Spinner ? ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48)) : -2));
    }

    private BatchRenameEngine.Options renameOptions(EditText prefix,EditText suffix,EditText find,EditText replacement,EditText start,EditText increment,EditText padding,Spinner capitalization,CheckBox numbering,CheckBox preserve) {
        BatchRenameEngine.Options o=new BatchRenameEngine.Options(); o.prefix=prefix.getText().toString(); o.suffix=suffix.getText().toString(); o.find=find.getText().toString(); o.replacement=replacement.getText().toString();
        o.start=parseInt(start.getText().toString(),-1); o.increment=parseInt(increment.getText().toString(),0); o.padding=parseInt(padding.getText().toString(),0);
        o.capitalization=capitalization.getSelectedItemPosition(); o.numbering=numbering.isChecked(); o.preserveExtension=preserve.isChecked(); return o;
    }
    private int parseInt(String value,int fallback){try{return Integer.parseInt(value.trim());}catch(Exception e){return fallback;}}
    private String joinLines(List<String> lines,int max){StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(max,lines.size());i++)b.append("• ").append(lines.get(i)).append("\n");if(lines.size()>max)b.append("… and ").append(lines.size()-max).append(" more");return b.toString();}

    private void executeBatchRename(final List<BatchRenameEngine.Item> plan) {
        a.busy(true);
        a.executeOperation(() -> {
            BatchRenameEngine.Result result=BatchRenameEngine.execute(plan);
            a.runUiIfAlive(() -> {
                a.busy(false);
                if(!result.completed.isEmpty()) for(BatchRenameEngine.Item item:result.completed){Labels.moved(a,item.source,item.target);a.renameFav(item.source,item.target);}
                if(result.success()) { endSelect(); a.afterOps(); showRenameResult(result,"Renamed "+result.completed.size()+" items."); }
                else { a.afterOps(); showRenameResult(result,result.errors.isEmpty()?"Rename did not complete.":joinLines(result.errors,8)); }
            });
        });
    }

    private void showRenameResult(BatchRenameEngine.Result result,String message) {
        AlertDialog.Builder b=ToxiVisualSystem.dialogBuilder(a).setTitle(result.success()?"Batch rename complete":"Batch rename result").setMessage(message).setPositiveButton("Done",null);
        if(!result.completed.isEmpty()) b.setNeutralButton("Undo",(d,w)->undoBatchRename(result));
        b.show();
    }

    private void undoBatchRename(BatchRenameEngine.Result prior) {
        a.busy(true); a.executeOperation(() -> { BatchRenameEngine.Result undone=BatchRenameEngine.undo(prior); a.runUiIfAlive(() -> {
            a.busy(false); for(BatchRenameEngine.Item item:undone.completed){Labels.moved(a,item.source,item.target);a.renameFav(item.source,item.target);} a.afterOps();
            String msg=undone.errors.isEmpty()?"Restored "+undone.completed.size()+" original names.":joinLines(undone.errors,8);
            ToxiVisualSystem.dialogBuilder(a).setTitle(undone.errors.isEmpty()?"Undo complete":"Undo incomplete").setMessage(msg).setPositiveButton("OK",null).show();
        }); });
    }

    void labelDialog(final List<Entry> list) {
        String[] names = new String[Labels.NAMES.length + 1];
        names[0] = "Remove label";
        System.arraycopy(Labels.NAMES, 0, names, 1, Labels.NAMES.length);
        ToxiVisualSystem.dialogBuilder(a).setTitle("Label").setItems(names, (d, w) -> {
            int idx = w - 1; // -1 means "remove"
            for (Entry e : list) Labels.set(a, e.f, idx);
            endSelect(); a.afterOps();
        }).show();
    }

    /** One-tap move to a common folder; no need to cut, navigate, then paste. */
    void confirmSelectionMove(final List<Entry> list) {
        if (list == null || list.isEmpty()) return;
        ToxiVisualSystem.dialogBuilder(a).setTitle("Move " + list.size() + " selected items?")
                .setMessage("The selected items will leave their current location. Choose a destination only after confirming.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", (d,w) -> moveToQuick(list)).show();
    }

    void moveToQuick(final List<Entry> list) {
        final File root = android.os.Environment.getExternalStorageDirectory();
        final String[] names = {"Download", "Documents", "DCIM", "Pictures", "Movies", "Music", "Internal storage (root)"};
        final File[] dirs = new File[names.length];
        for (int i = 0; i < names.length - 1; i++) dirs[i] = new File(root, names[i]);
        dirs[dirs.length - 1] = root;
        ToxiVisualSystem.dialogBuilder(a).setTitle("Move to").setItems(names, (d, w) -> {
            File dest = dirs[w];
            if (!dest.isDirectory()) { ToxiVisualSystem.toast(a, "That folder doesn't exist on this device"); return; }
            pasteWithConflictPrompt(files(list).toArray(new File[0]), dest, true, msg -> { endSelect(); ToxiVisualSystem.toast(a, msg); a.afterOps(); });
        }).show();
    }

    void vaultMove(final List<Entry> list) {
        if (!Vault.hasPin(a)) { ToxiVisualSystem.toast(a, "Set a Vault PIN first in Settings, under Vault"); return; }
        ToxiVisualSystem.dialogBuilder(a).setTitle("Move " + list.size() + " item" + (list.size() > 1 ? "s" : "") + " to Vault?")
                .setMessage("Encrypted at rest with AES-GCM using Android Keystore. Keep your Vault PIN private; loss of device Keystore keys can make vault data unrecoverable.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move", (d, w) -> Vault.moveIn(a, files(list), msg -> {
                    a.removeFavs(files(list)); endSelect(); a.afterOps(); ToxiVisualSystem.toast(a, msg);
                })).show();
    }

    void itemMenu(final Entry e) {
        List<String> l = new ArrayList<>();
        List<Runnable> r = new ArrayList<>();
        add(l, r, "Open", () -> open(e));
        if (!e.dir) add(l, r, "Open with…", () -> FileOps.external(a, e.f, true));
        add(l, r, "Rename", () -> rename(e));
        add(l, r, "Copy", () -> { a.setClip(new File[]{e.f}, false); ToxiVisualSystem.toast(a, "Copied — open a folder and tap Paste"); });
        add(l, r, "Cut", () -> { a.setClip(new File[]{e.f}, true); ToxiVisualSystem.toast(a, "Cut — open a folder and tap Paste"); });
        add(l, r, "Move to…", () -> { List<Entry> one = new ArrayList<>(); one.add(e); moveToQuick(one); });
        add(l, r, "Send via Wi-Fi…", () -> { List<Entry> one = new ArrayList<>(); one.add(e); a.sendViaWifi(files(one)); });
        if (a.hasSafTree()) add(l, r, "Copy to SD/USB", () -> { List<File> one = new ArrayList<>(); one.add(e.f); a.copyToSafTree(one); });
        add(l, r, "Compress to ZIP", () -> { List<Entry> one = new ArrayList<>(); one.add(e); zip(one); });
        if (e.ext.equals("zip")) add(l, r, "Extract here", () -> unzip(e.f));
        add(l, r, "Delete", () -> { List<Entry> one = new ArrayList<>(); one.add(e); confirmDelete(one); });
        if (!e.dir) add(l, r, "Share", () -> { List<File> one = new ArrayList<>(); one.add(e.f); FileOps.share(a, one); });
        add(l, r, a.isFav(e.f) ? "Remove from favorites" : "Add to favorites", () -> { a.toggleFav(e.f); a.afterOps(); });
        add(l, r, "Label", () -> { List<Entry> one = new ArrayList<>(); one.add(e); labelDialog(one); });
        add(l, r, "Move to Vault", () -> { List<Entry> one = new ArrayList<>(); one.add(e); vaultMove(one); });
        add(l, r, "Properties", () -> properties(e));
        if (!e.dir && isExifImage(e.ext)) add(l, r, "Strip GPS metadata (save copy)…", () -> stripGpsMetadata(e.f));
        showMenu(a, e.name, l, r);
    }

    // ---------- operations ----------
    void newFolder() {
        final EditText in = input("");
        ToxiVisualSystem.dialogBuilder(a).setTitle("New folder").setView(wrap(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (BatchRenameEngine.validateName(n) != null) { ToxiVisualSystem.toast(a, "Invalid name"); return; }
                    try {
                        File f = BatchRenameEngine.resolveChild(cur, n);
                        if (f.exists()) ToxiVisualSystem.toast(a, "Already exists");
                        else if (!f.mkdir()) ToxiVisualSystem.toast(a, "Couldn't create folder");
                    } catch (IOException invalidPath) {
                        ToxiVisualSystem.toast(a, "Invalid folder destination");
                    }
                    a.afterOps();
                }).show();
    }

    void rename(final Entry e) {
        final EditText in = input(e.name);
        ToxiVisualSystem.dialogBuilder(a).setTitle("Rename").setView(wrap(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (BatchRenameEngine.validateName(n) != null || n.equals(e.name)) { ToxiVisualSystem.toast(a, "Invalid name"); return; }
                    try {
                        File t = BatchRenameEngine.resolveChild(e.f.getParentFile(), n);
                        if (t.exists()) { ToxiVisualSystem.toast(a, "A file with that name exists"); return; }
                        BatchRenameEngine.moveChild(e.f, t);
                        a.renameFav(e.f, t);
                        endSelect();
                        a.afterOps();
                    } catch (java.nio.file.FileAlreadyExistsException occupied) {
                        ToxiVisualSystem.toast(a, "A file with that name exists");
                    } catch (IOException renameFailure) {
                        ToxiVisualSystem.toast(a, "Rename failed");
                    }
                }).show();
        in.post(() -> {
            int dot = e.name.lastIndexOf('.');
            in.setSelection(0, dot > 0 && !e.dir ? dot : e.name.length());
        });
    }

    EditText input(String text) {
        EditText in = new EditText(a);
        ToxiMotion.watchFocus(in);
        in.setSingleLine(true);
        in.setText(text);
        return in;
    }

    View wrap(View v) {
        FrameLayout f = new FrameLayout(a);
        f.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), 0);
        f.addView(v, new FrameLayout.LayoutParams(-1, -2));
        return f;
    }

    private void deleteEntries(final List<Entry> list, final boolean useBin) {
        final List<File> fs = files(list);
        FileOps.Done after = msg -> { a.removeFavs(fs); endSelect(); a.afterOps(); ToxiVisualSystem.toast(a, msg); };
        if (useBin) RecycleBin.moveToBin(a, fs, after); else FileOps.delete(a, fs, after);
    }

    void confirmDelete(final List<Entry> list) {
        if (list.isEmpty()) return;
        final boolean useBin = ToxiVisualSystem.booleanPreference("recyclebin", true);
        if (!ToxiVisualSystem.booleanPreference("confirm", true)) { deleteEntries(list,useBin); return; }
        ToxiVisualSystem.dialogBuilder(a).setTitle("Delete " + list.size() + " item" + (list.size() > 1 ? "s" : "") + "?")
                .setMessage(useBin ? "Moved to Recycle Bin — you can restore it later." : "This can't be undone.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d,w) -> deleteEntries(list,useBin)).show();
    }

    void confirmSelectionDelete(final List<Entry> list) {
        if (list == null || list.isEmpty()) return;
        final boolean useBin = ToxiVisualSystem.booleanPreference("recyclebin", true);
        ToxiVisualSystem.dialogBuilder(a).setTitle("Delete " + list.size() + " selected item" + (list.size() > 1 ? "s" : "") + "?")
                .setMessage(useBin ? "The selected items will be moved to Recycle Bin and can be restored." : "This permanently deletes the selected items and cannot be undone.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d,w) -> deleteEntries(list,useBin)).show();
    }

    void zip(List<Entry> list) {
        List<File> fs = files(list);
        File dest = (mode == DIR && cur != null) ? cur : fs.get(0).getParentFile();
        FileOps.zip(a, fs, dest, msg -> { endSelect(); ToxiVisualSystem.toast(a, msg); a.afterOps(); });
    }

    void unzip(File z) { FileOps.unzip(a, z, msg -> { ToxiVisualSystem.toast(a, msg); a.afterOps(); }); }

    private boolean isExifImage(String ext) {
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext)
                || "heic".equals(ext) || "heif".equals(ext) || "tif".equals(ext) || "tiff".equals(ext);
    }

    void stripGpsMetadata(final File source) {
        ToxiVisualSystem.dialogBuilder(a).setTitle("Remove GPS metadata?")
                .setMessage("Creates a separate copy without GPS coordinates and related location tags. The original stays unchanged; camera and date metadata may remain.")
                .setNegativeButton("Cancel", null).setPositiveButton("Save copy", (dialog, which) -> {
                    a.busy(true);
                    a.executeOperation(() -> {
            OperationCancellation.Token operationToken = a.cancellation.begin();
            try {
                        File copy = null; String result;
                        try {
                            android.media.ExifInterface original = new android.media.ExifInterface(source.getAbsolutePath());
                            String[] gpsTags = {"GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef", "GPSTimeStamp", "GPSDateStamp", "GPSProcessingMethod", "GPSDestBearing", "GPSDestBearingRef", "GPSDestDistance", "GPSDestDistanceRef", "GPSDestLatitude", "GPSDestLatitudeRef", "GPSDestLongitude", "GPSDestLongitudeRef", "GPSSpeed", "GPSSpeedRef", "GPSImgDirection", "GPSImgDirectionRef", "GPSMapDatum", "GPSVersionID", "GPSAreaInformation", "GPSDifferential", "GPSHPositioningError"};
                            boolean hasGps = false;
                            for (String tag : gpsTags) if (original.getAttribute(tag) != null) { hasGps = true; break; }
                            if (!hasGps) throw new java.io.IOException("No GPS/location metadata was found");
                            String name = source.getName(); int dot = name.lastIndexOf('.');
                            String base = dot > 0 ? name.substring(0, dot) : name; String ext = dot > 0 ? name.substring(dot) : "";
                            copy = FileOps.unique(source.getParentFile(), base + "_no_location" + ext);
                            FileOps.copy(source, copy);
                            android.media.ExifInterface cleaned = new android.media.ExifInterface(copy.getAbsolutePath());
                            for (String tag : gpsTags) cleaned.setAttribute(tag, null);
                            FileOps.checkCancelled();
                            cleaned.saveAttributes();
                            result = "Saved copy without GPS metadata: " + copy.getName();
                        } catch (Exception ex) {
                            if (copy != null) FileOps.deleteTree(copy);
                            result = "GPS metadata removal failed: " + ex.getMessage();
                        }
                        final String message = result;
                        a.runUiIfAlive(() -> { a.busy(false); ToxiVisualSystem.toast(a, message); a.afterOps(); });
                    
            } finally { a.cancellation.finish(operationToken); }
        });
                }).show();
    }

    String propText(Entry e, String size, String hash) {
        return "Path: " + e.f.getAbsolutePath() + "\nType: " + (e.dir ? "Folder" : e.ext.isEmpty() ? "File" : e.ext.toUpperCase(Locale.ROOT) + " file")
                + "\nSize: " + size + "\nModified: " + ToxiVisualSystem.formatDate(e.mod)
                + (e.dir ? "" : "\nSHA-256: " + hash);
    }

    private String sha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder out = new StringBuilder();
            for (byte b : md.digest()) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (Exception ex) { return "unavailable"; }
    }

    void properties(final Entry e) {
        final String[] digest = {null};
        final AlertDialog dlg = ToxiVisualSystem.dialogBuilder(a).setTitle(e.name)
                .setMessage(propText(e, e.dir ? "calculating…" : ToxiVisualSystem.formatSize(e.len), e.dir ? "" : "calculating…"))
                .setNeutralButton(e.dir ? "" : "Copy SHA-256", null).setPositiveButton("Close", null).show();
        final android.widget.Button copyHash = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (e.dir) copyHash.setVisibility(View.GONE); else {
            copyHash.setEnabled(false);
            copyHash.setOnClickListener(v -> {
                if (digest[0] == null || "unavailable".equals(digest[0])) return;
                android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) { cm.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", digest[0])); ToxiVisualSystem.toast(a, "SHA-256 copied"); }
            });
        }
        if (e.dir) a.executeIo(() -> {
            final long[] t = new long[2];
            if (!FileOps.treeSize(e.f, t)) {
                if (Thread.currentThread().isInterrupted()) return;
                a.runUiIfAlive(() -> { if (dlg.isShowing()) dlg.setMessage(propText(e, "Size unavailable", "")); });
                return;
            }
            a.runUiIfAlive(() -> { if (dlg.isShowing()) dlg.setMessage(propText(e, ToxiVisualSystem.formatSize(t[0]) + " • " + t[1] + " files", "")); });
        }, () -> { if (dlg.isShowing()) dlg.setMessage(propText(e, "Size unavailable", "")); }); else a.executeIo(() -> {
            digest[0] = sha256(e.f);
            a.runUiIfAlive(() -> {
                if (!dlg.isShowing()) return;
                dlg.setMessage(propText(e, ToxiVisualSystem.formatSize(e.f.length()), digest[0]));
                copyHash.setEnabled(!"unavailable".equals(digest[0]));
            });
        });
    }
}
