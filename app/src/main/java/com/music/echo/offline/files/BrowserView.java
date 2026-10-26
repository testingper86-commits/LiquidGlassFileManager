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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** A glass file list: used for the Files tab, Favorites, categories and global search. */
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
    final LinkedHashSet<Entry> sel = new LinkedHashSet<>();
    boolean selecting;
    int columns = Ui.bool("grid", false) ? 3 : 1;

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
        mode = favs ? LIST : DIR;
        if (favs) listTitle = "Favorites";

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -1));

        header = new LinearLayout(a);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(12), Ui.dp(4), Ui.dp(12), Ui.dp(4));
        back = Ui.iconBtn(a, Glyphs.BACK, Ui.WHITE);
        back.setOnClickListener(v -> { if (selecting) endSelect(); else handleBack(); });
        title = Ui.tv(a, "", 24, Ui.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        searchBtn = Ui.iconBtn(a, Glyphs.SEARCH, Ui.WHITE);
        searchBtn.setOnClickListener(v -> toggleSearch());
        moreBtn = Ui.iconBtn(a, Glyphs.MORE, Ui.WHITE);
        moreBtn.setOnClickListener(v -> headerMenu());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(4);
        header.addView(title, tl);
        header.addView(searchBtn, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        header.addView(moreBtn, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        header.setBackground(Ui.glass(22, false));
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, Ui.dp(60));
        headerLp.setMargins(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(8));
        col.addView(header, headerLp);

        pathTv = Ui.tv(a, "", 12, Ui.SUB, false);
        pathTv.setSingleLine(true);
        pathTv.setEllipsize(TextUtils.TruncateAt.START);
        pathTv.setBackground(Ui.glass(16, false));
        pathTv.setPadding(Ui.dp(14), Ui.dp(9), Ui.dp(14), Ui.dp(9));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(8));
        col.addView(pathTv, pl);

        search = new EditText(a);
        search.setSingleLine(true);
        search.setHint("Search files, or .pdf for a type");
        search.setHintTextColor(Ui.SUB);
        search.setTextColor(Ui.WHITE);
        search.setTextSize(15);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(Ui.glass(18, true));
        search.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
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
        sl.setMargins(Ui.dp(16), Ui.dp(2), Ui.dp(16), Ui.dp(8));
        col.addView(search, sl);
        GlowRing.watchFocus(search);

        lv = new ListView(a);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setClipToPadding(false);
        lv.setPadding(0, Ui.dp(4), 0, Ui.dp(120));
        lv.setVerticalScrollBarEnabled(false);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));

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
                    if (cv == null) cv = Rows.build(a);
                    Rows.H h = (Rows.H) cv.getTag();
                    Entry e = shown.get(i);
                    String extra = null;
                    if (mode == LIST) { File par = e.f.getParentFile(); extra = par == null ? "" : par.getName(); }
                    Rows.bind(h, e, selecting, sel.contains(e), extra);
                    Rows.setLabel(h, Labels.get(a, e.f));
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
                    Thumbs.bind(thumb, e, Ui.dp(96));
                    nameTv.setText(e.name);
                    check.setVisibility(selecting && sel.contains(e) ? View.VISIBLE : View.GONE);
                    tile.setOnClickListener(v -> { if (selecting) toggle(e); else open(e); });
                    tile.setOnLongClickListener(v -> { if (!selecting) startSelect(e); return true; });
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
            if (columns <= 1 && pos < shown.size() && !selecting) startSelect(shown.get(pos));
            return true;
        });

        empty = Ui.tv(a, "", 15, Ui.SUB, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        addView(empty, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        empty.setOnClickListener(v -> { if (!a.hasAccess()) a.showStorageAccess(); });

        prog = new ProgressBar(a);
        prog.setIndeterminate(true);
        prog.setIndeterminateTintList(ColorStateList.valueOf(Ui.accent));
        prog.setVisibility(GONE);
        addView(prog, new FrameLayout.LayoutParams(Ui.dp(40), Ui.dp(40), Gravity.CENTER));
        GlowRing.attach(prog);
        GlowRing.setActive(prog, false);

        selBar = new LinearLayout(a);
        selBar.setOrientation(LinearLayout.HORIZONTAL);
        selBar.setGravity(Gravity.CENTER);
        selBar.setBackground(Ui.glass(28, true));
        selBar.setVisibility(GONE);
        selBar.addView(selBtn(Glyphs.COPY, "Copy", () -> copySel(false)));
        selBar.addView(selBtn(Glyphs.CUT, "Cut", () -> copySel(true)));
        selBar.addView(selBtn(Glyphs.TRASH, "Delete", () -> confirmDelete(new ArrayList<>(sel))));
        selBar.addView(selBtn(Glyphs.SHARE, "Share", () -> FileOps.share(a, files(sel))));
        selBar.addView(selBtn(Glyphs.MORE, "More", this::selMore));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, Ui.dp(68), Gravity.BOTTOM);
        bl.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(12));
        addView(selBar, bl);

        pasteBtn = Ui.tv(a, "Paste here", 14, Ui.WHITE, true);
        pasteBtn.setBackground(Ui.accentPill(24));
        pasteBtn.setPadding(Ui.dp(20), Ui.dp(13), Ui.dp(20), Ui.dp(13));
        pasteBtn.setVisibility(GONE);
        Ui.press(pasteBtn);
        pasteBtn.setOnClickListener(v -> paste());
        FrameLayout.LayoutParams pb = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        pb.setMargins(0, 0, Ui.dp(20), Ui.dp(100));
        addView(pasteBtn, pb);

        updateHeader();
    }

    View selBtn(int glyph, String label, final Runnable r) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(Glyphs.get(glyph, Ui.WHITE));
        b.addView(ic, new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
        TextView t = Ui.tv(a, label, 10.5f, Ui.SUB, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = Ui.dp(4);
        b.addView(t, tp);
        b.setOnClickListener(v -> r.run());
        Ui.press(b);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        return b;
    }

    void applyInsets() {
        lv.setPadding(0, Ui.dp(4), 0, Ui.dp(112) + Ui.bottom);
        ((FrameLayout.LayoutParams) selBar.getLayoutParams()).bottomMargin = Ui.bottom + Ui.dp(12);
        ((FrameLayout.LayoutParams) pasteBtn.getLayoutParams()).bottomMargin = Ui.bottom + Ui.dp(100);
        selBar.requestLayout();
        pasteBtn.requestLayout();
    }

    // ---------- loading ----------
    boolean atRoot() { return cur == null || cur.equals(Environment.getExternalStorageDirectory()); }

    void showDir(File dir) {
        mode = DIR;
        cur = dir;
        closeSearch();
        selecting = false;
        sel.clear();
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
        final int k = Ui.sp.getInt("sort", 0);
        final boolean asc = Ui.sp.getBoolean("asc", true);
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
        final File d = cur;
        if (!a.hasAccess()) { loading = false; all = new ArrayList<>(); applyFilter(); return; }
        loading = true;
        updateEmpty();
        final Comparator<Entry> cmp = comparator();
        a.io.execute(() -> {
            final List<Entry> res = new ArrayList<>();
            File[] fs = d.listFiles();
            boolean hid = Ui.bool("hidden", false);
            if (fs != null) for (File f : fs) { if (!hid && f.getName().startsWith(".")) continue; res.add(new Entry(f)); }
            Collections.sort(res, cmp);
            post(() -> {
                if (d != cur || mode != DIR) return;
                loading = false;
                all = res;
                applyFilter();
                lv.setSelection(0);
            });
        });
    }

    void refreshFavs() {
        final List<String> ps = new ArrayList<>(a.favs);
        a.io.execute(() -> {
            final List<Entry> l = new ArrayList<>();
            for (String p : ps) { File f = new File(p); if (f.exists()) l.add(new Entry(f)); }
            post(() -> { loading = false; all = l; applyFilter(); });
        });
    }

    /** list == null means "still loading". */
    void setList(String t, List<Entry> list, boolean limited, boolean focusSearch) {
        mode = LIST;
        listTitle = t;
        String normalizedTitle = t == null ? "" : t.toLowerCase(Locale.ROOT);
        if (normalizedTitle.contains("image") || normalizedTitle.contains("photo") || normalizedTitle.contains("video") || normalizedTitle.contains("audio") || normalizedTitle.contains("music")) columns = 3;
        else columns = Ui.bool("grid", false) ? 3 : 1;
        limit = limited;
        selecting = false;
        sel.clear();
        selBar.setVisibility(GONE);
        a.navForSelection(false);
        loading = list == null;
        all = list == null ? new ArrayList<Entry>() : new ArrayList<>(list);
        if (focusSearch && search.getVisibility() != VISIBLE) showSearch();
        else if (!focusSearch && list != null && search.getVisibility() == VISIBLE && !limited) { /* keep */ }
        updateHeader();
        applyFilter();
    }

    void pruneMissing() {
        final List<Entry> snap = new ArrayList<>(all);
        a.io.execute(() -> {
            final List<Entry> keep = new ArrayList<>();
            for (Entry e : snap) if (e.f.exists()) keep.add(e);
            post(() -> { all = keep; applyFilter(); });
        });
    }

    void applyFilter() {
        List<Entry> out = new ArrayList<>();
        int cap = limit ? 500 : Integer.MAX_VALUE;
        boolean extOnly = query.length() > 1 && query.charAt(0) == '.';
        String extQuery = extOnly ? query.substring(1) : null;
        for (Entry e : all) {
            boolean match = query.isEmpty() || (extOnly ? e.ext.equals(extQuery) : e.lname.contains(query));
            if (match) { out.add(e); if (out.size() >= cap) break; }
        }
        shown = out;
        ad.notifyDataSetChanged();
        updateEmpty();
    }

    void updateEmpty() {
        GlowRing.setActive(prog, loading);
        if (loading) { empty.setVisibility(GONE); prog.setVisibility(VISIBLE); return; }
        prog.setVisibility(GONE);
        if (mode == DIR && !a.hasAccess()) {
            empty.setText("Allow access to all files\nTap here to open settings");
            empty.setVisibility(VISIBLE);
        } else if (shown.isEmpty()) {
            empty.setText(!query.isEmpty() ? "No results" : mode == DIR ? "This folder is empty" : isFavs ? "No favorites yet\nUse ⋮ on a file to add one" : "Nothing here yet");
            empty.setVisibility(VISIBLE);
        } else empty.setVisibility(GONE);
    }

    void updateHeader() {
        if (selecting) {
            title.setText(sel.size() + " selected");
            back.setImageDrawable(Glyphs.get(Glyphs.CLOSE, Ui.WHITE));
            back.setVisibility(VISIBLE);
            searchBtn.setVisibility(GONE);
            pathTv.setVisibility(GONE);
            return;
        }
        searchBtn.setVisibility(VISIBLE);
        back.setImageDrawable(Glyphs.get(Glyphs.BACK, Ui.WHITE));
        if (mode == DIR) {
            title.setText(atRoot() ? "Internal Storage" : (cur == null ? "" : cur.getName()));
            back.setVisibility(atRoot() ? GONE : VISIBLE);
            pathTv.setVisibility(VISIBLE);
            if (cur != null) {
                String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                pathTv.setText(cur.getAbsolutePath().replace(root, "Internal storage"));
            }
        } else {
            title.setText(listTitle);
            back.setVisibility(overlay ? VISIBLE : GONE);
            pathTv.setVisibility(GONE);
        }
        title.setPadding(back.getVisibility() == VISIBLE ? 0 : Ui.dp(10), 0, 0, 0);
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
    void startSelect(Entry e) { selecting = true; sel.clear(); sel.add(e); Ui.tick(this); updateSel(); }
    void toggle(Entry e) { if (!sel.remove(e)) sel.add(e); if (sel.isEmpty()) endSelect(); else updateSel(); }
    void endSelect() { selecting = false; sel.clear(); updateSel(); }
    void selectAll() { selecting = true; sel.addAll(shown); updateSel(); }

    void updateSel() {
        selBar.setVisibility(selecting ? VISIBLE : GONE);
        a.navForSelection(selecting);
        updateHeader();
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
        Ui.toast(a, (cut ? "Cut " : "Copied ") + l.size() + " — open a folder and tap Paste");
        endSelect();
    }

    void updatePaste() {
        boolean show = mode == DIR && !overlay && a.clip != null && !selecting;
        pasteBtn.setVisibility(show ? VISIBLE : GONE);
        if (show) pasteBtn.setText("Paste " + a.clip.length + " here");
    }

    void paste() {
        if (a.clip == null || cur == null) return;
        final File[] src = a.clip;
        final boolean cut = a.clipCut;
        pasteWithConflictPrompt(src, cur, cut, msg -> {
            if (cut) a.setClip(null, false);
            Ui.toast(a, msg);
            a.afterOps();
        });
    }

    void pasteWithConflictPrompt(final File[] src, final File destination, final boolean move, final FileOps.Done done) {
        int conflicts = 0;
        for (File file : src) if (!(move && destination.equals(file.getParentFile())) && new File(destination, file.getName()).exists()) conflicts++;
        if (conflicts == 0) { FileOps.paste(a, src, destination, move, false, done); return; }
        new AlertDialog.Builder(a).setTitle("Name conflicts")
                .setMessage(conflicts + " item" + (conflicts == 1 ? " already exists" : "s already exist") + " in the destination. Choose how to continue.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Skip existing", (dialog, which) -> FileOps.paste(a, src, destination, move, true, done))
                .setPositiveButton("Keep both", (dialog, which) -> FileOps.paste(a, src, destination, move, false, done))
                .show();
    }

    // ---------- menus ----------
    static void add(List<String> l, List<Runnable> r, String s, Runnable x) { l.add(s); r.add(x); }

    static void showMenu(Context c, String title, List<String> l, final List<Runnable> r) {
        new AlertDialog.Builder(c).setTitle(title).setItems(l.toArray(new String[0]), (d, w) -> r.get(w).run()).show();
    }

    LinearLayout buildGridRow() {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            FrameLayout tile = new FrameLayout(a);
            ImageView thumb = new ImageView(a);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(Ui.glass(16, false));
            Ui.round(thumb, 16);
            tile.addView(thumb, new FrameLayout.LayoutParams(-1, Ui.dp(96)));
            TextView nameTv = Ui.tv(a, "", 11, Ui.WHITE, false);
            nameTv.setMaxLines(1);
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            nameTv.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            np.setMargins(Ui.dp(4), 0, Ui.dp(4), Ui.dp(4));
            tile.addView(nameTv, np);
            View check = new View(a);
            check.setBackground(Ui.accentPill(10));
            check.setVisibility(GONE);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.dp(16), Ui.dp(16), Gravity.TOP | Gravity.END);
            cp.setMargins(0, Ui.dp(6), Ui.dp(6), 0);
            tile.addView(check, cp);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.setMargins(Ui.dp(5), Ui.dp(5), Ui.dp(5), Ui.dp(5));
            row.addView(tile, tp);
        }
        return row;
    }

    void toggleView() {
        columns = columns > 1 ? 1 : 3;
        Ui.set("grid", columns > 1);
        ad.notifyDataSetChanged();
    }

    void headerMenu() {
        List<String> l = new ArrayList<>();
        List<Runnable> r = new ArrayList<>();
        if (mode == DIR && !selecting) add(l, r, "New folder", this::newFolder);
        if (mode == DIR && !selecting) add(l, r, "Choose SD/USB storage…", a::openOtherStorage);
        if (mode == DIR && !selecting && cur != null) add(l, r, "Set as default start folder", () -> { Ui.sp.edit().putString("default_folder", cur.getAbsolutePath()).apply(); Ui.toast(a, "Set as default: " + cur.getAbsolutePath()); });
        add(l, r, columns > 1 ? "List view" : "Grid view", this::toggleView);
        add(l, r, "Sort by…", this::sortDialog);
        add(l, r, "Select all", this::selectAll);
        if (mode == DIR) {
            boolean on = Ui.bool("hidden", false);
            add(l, r, on ? "Hide hidden files" : "Show hidden files", () -> { Ui.set("hidden", !on); reload(); });
            if (a.clip != null) add(l, r, "Paste here", this::paste);
            add(l, r, "Refresh", this::reload);
        }
        showMenu(a, "Options", l, r);
    }

    void sortDialog() {
        final String[] names = {"Name (A → Z)", "Name (Z → A)", "Newest first", "Oldest first", "Largest first", "Smallest first", "Type"};
        new AlertDialog.Builder(a).setTitle("Sort by").setItems(names, (d, w) -> {
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
            Ui.sp.edit().putInt("sort", k).putBoolean("asc", asc).apply();
            final Comparator<Entry> cmp = comparator();
            Collections.sort(all, cmp);
            applyFilter();
        }).show();
    }

    void selMore() {
        List<String> l = new ArrayList<>();
        List<Runnable> r = new ArrayList<>();
        final List<Entry> s = new ArrayList<>(sel);
        add(l, r, "Select all", this::selectAll);
        add(l, r, "Compress to ZIP", () -> zip(s));
        add(l, r, "Move to…", () -> moveToQuick(s));
        add(l, r, "Send via Wi-Fi…", () -> a.sendViaWifi(files(s)));
        if (a.safTree != null) add(l, r, "Copy to SD/USB", () -> a.copyToSafTree(files(s)));
        add(l, r, "Label", () -> labelDialog(s));
        add(l, r, "Move to Vault", () -> vaultMove(s));
        if (s.size() == 1) {
            final Entry e = s.get(0);
            add(l, r, "Rename", () -> rename(e));
            add(l, r, a.isFav(e.f) ? "Remove from favorites" : "Add to favorites", () -> { a.toggleFav(e.f); endSelect(); a.afterOps(); });
            add(l, r, "Properties", () -> properties(e));
        } else {
            add(l, r, "Rename (batch)", () -> batchRename(s));
        }
        showMenu(a, "More", l, r);
    }

    /** Applies "Pattern {n}" to every selected item; {n} becomes a running number, extension is kept. */
    void batchRename(final List<Entry> list) {
        final EditText in = input("Photo {n}");
        new AlertDialog.Builder(a).setTitle("Rename " + list.size() + " items")
                .setMessage("Use {n} for a running number, e.g. \"Vacation {n}\"")
                .setView(wrap(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (d, w) -> {
                    String pattern = in.getText().toString().trim();
                    if (pattern.isEmpty()) return;
                    int n = 1, ok = 0;
                    for (Entry e : list) {
                        String base = pattern.contains("{n}") ? pattern.replace("{n}", String.valueOf(n)) : pattern + " " + n;
                        n++;
                        String ext = e.dir ? "" : e.ext.isEmpty() ? "" : "." + e.ext;
                        File t = FileOps.unique(e.f.getParentFile(), base + ext);
                        if (e.f.renameTo(t)) { Labels.moved(a, e.f, t); a.renameFav(e.f, t); ok++; }
                    }
                    Ui.toast(a, "Renamed " + ok + " item" + (ok == 1 ? "" : "s"));
                    endSelect(); a.afterOps();
                }).show();
    }

    void labelDialog(final List<Entry> list) {
        String[] names = new String[Labels.NAMES.length + 1];
        names[0] = "Remove label";
        System.arraycopy(Labels.NAMES, 0, names, 1, Labels.NAMES.length);
        new AlertDialog.Builder(a).setTitle("Label").setItems(names, (d, w) -> {
            int idx = w - 1; // -1 means "remove"
            for (Entry e : list) Labels.set(a, e.f, idx);
            endSelect(); a.afterOps();
        }).show();
    }

    /** One-tap move to a common folder, no need to cut → navigate → paste. */
    void moveToQuick(final List<Entry> list) {
        final File root = android.os.Environment.getExternalStorageDirectory();
        final String[] names = {"Download", "Documents", "DCIM", "Pictures", "Movies", "Music", "Internal storage (root)"};
        final File[] dirs = new File[names.length];
        for (int i = 0; i < names.length - 1; i++) dirs[i] = new File(root, names[i]);
        dirs[dirs.length - 1] = root;
        new AlertDialog.Builder(a).setTitle("Move to").setItems(names, (d, w) -> {
            File dest = dirs[w];
            if (!dest.isDirectory()) { Ui.toast(a, "That folder doesn't exist on this device"); return; }
            pasteWithConflictPrompt(files(list).toArray(new File[0]), dest, true, msg -> { endSelect(); Ui.toast(a, msg); a.afterOps(); });
        }).show();
    }

    void vaultMove(final List<Entry> list) {
        if (!Vault.hasPin(a)) { Ui.toast(a, "Set a Vault PIN first — Settings → Vault"); return; }
        new AlertDialog.Builder(a).setTitle("Move " + list.size() + " item" + (list.size() > 1 ? "s" : "") + " to Vault?")
                .setMessage("Encrypted at rest with AES-GCM using Android Keystore. Keep your Vault PIN private; loss of device Keystore keys can make vault data unrecoverable.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move", (d, w) -> Vault.moveIn(a, files(list), msg -> {
                    a.removeFavs(files(list)); endSelect(); a.afterOps(); Ui.toast(a, msg);
                })).show();
    }

    void itemMenu(final Entry e) {
        List<String> l = new ArrayList<>();
        List<Runnable> r = new ArrayList<>();
        add(l, r, "Open", () -> open(e));
        if (!e.dir) add(l, r, "Open with…", () -> FileOps.external(a, e.f, true));
        add(l, r, "Rename", () -> rename(e));
        add(l, r, "Copy", () -> { a.setClip(new File[]{e.f}, false); Ui.toast(a, "Copied — open a folder and tap Paste"); });
        add(l, r, "Cut", () -> { a.setClip(new File[]{e.f}, true); Ui.toast(a, "Cut — open a folder and tap Paste"); });
        add(l, r, "Move to…", () -> { List<Entry> one = new ArrayList<>(); one.add(e); moveToQuick(one); });
        add(l, r, "Send via Wi-Fi…", () -> { List<Entry> one = new ArrayList<>(); one.add(e); a.sendViaWifi(files(one)); });
        if (a.safTree != null) add(l, r, "Copy to SD/USB", () -> { List<File> one = new ArrayList<>(); one.add(e.f); a.copyToSafTree(one); });
        add(l, r, "Compress to ZIP", () -> { List<Entry> one = new ArrayList<>(); one.add(e); zip(one); });
        if (e.ext.equals("zip")) add(l, r, "Extract here", () -> unzip(e.f));
        add(l, r, "Delete", () -> { List<Entry> one = new ArrayList<>(); one.add(e); confirmDelete(one); });
        if (!e.dir) add(l, r, "Share", () -> { List<File> one = new ArrayList<>(); one.add(e.f); FileOps.share(a, one); });
        add(l, r, a.isFav(e.f) ? "Remove from favorites" : "Add to favorites", () -> { a.toggleFav(e.f); a.afterOps(); });
        add(l, r, "Label", () -> { List<Entry> one = new ArrayList<>(); one.add(e); labelDialog(one); });
        add(l, r, "Move to Vault", () -> { List<Entry> one = new ArrayList<>(); one.add(e); vaultMove(one); });
        add(l, r, "Properties", () -> properties(e));
        showMenu(a, e.name, l, r);
    }

    // ---------- operations ----------
    void newFolder() {
        final EditText in = input("");
        new AlertDialog.Builder(a).setTitle("New folder").setView(wrap(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (n.isEmpty() || n.contains("/")) { Ui.toast(a, "Invalid name"); return; }
                    File f = new File(cur, n);
                    if (f.exists()) Ui.toast(a, "Already exists");
                    else if (!f.mkdirs()) Ui.toast(a, "Couldn't create folder");
                    a.afterOps();
                }).show();
    }

    void rename(final Entry e) {
        final EditText in = input(e.name);
        new AlertDialog.Builder(a).setTitle("Rename").setView(wrap(in)).setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (n.isEmpty() || n.contains("/") || n.equals(e.name)) return;
                    File t = new File(e.f.getParentFile(), n);
                    if (t.exists()) { Ui.toast(a, "A file with that name exists"); return; }
                    if (e.f.renameTo(t)) { a.renameFav(e.f, t); endSelect(); a.afterOps(); }
                    else Ui.toast(a, "Rename failed");
                }).show();
        in.post(() -> {
            int dot = e.name.lastIndexOf('.');
            in.setSelection(0, dot > 0 && !e.dir ? dot : e.name.length());
        });
    }

    EditText input(String text) {
        EditText in = new EditText(a);
        GlowRing.watchFocus(in);
        in.setSingleLine(true);
        in.setText(text);
        return in;
    }

    View wrap(View v) {
        FrameLayout f = new FrameLayout(a);
        f.setPadding(Ui.dp(22), Ui.dp(8), Ui.dp(22), 0);
        f.addView(v, new FrameLayout.LayoutParams(-1, -2));
        return f;
    }

    void confirmDelete(final List<Entry> list) {
        if (list.isEmpty()) return;
        final boolean useBin = Ui.bool("recyclebin", true);
        final Runnable go = () -> {
            final List<File> fs = files(list);
            FileOps.Done after = msg -> { a.removeFavs(fs); endSelect(); a.afterOps(); Ui.toast(a, msg); };
            if (useBin) RecycleBin.moveToBin(a, fs, after); else FileOps.delete(a, fs, after);
        };
        if (!Ui.bool("confirm", true)) { go.run(); return; }
        new AlertDialog.Builder(a).setTitle("Delete " + list.size() + " item" + (list.size() > 1 ? "s" : "") + "?")
                .setMessage(useBin ? "Moved to Recycle Bin — you can restore it later." : "This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> go.run()).show();
    }

    void zip(List<Entry> list) {
        List<File> fs = files(list);
        File dest = (mode == DIR && cur != null) ? cur : fs.get(0).getParentFile();
        FileOps.zip(a, fs, dest, msg -> { endSelect(); Ui.toast(a, msg); a.afterOps(); });
    }

    void unzip(File z) { FileOps.unzip(a, z, msg -> { Ui.toast(a, msg); a.afterOps(); }); }

    String propText(Entry e, String size, String hash) {
        return "Path: " + e.f.getAbsolutePath() + "\nType: " + (e.dir ? "Folder" : e.ext.isEmpty() ? "File" : e.ext.toUpperCase(Locale.ROOT) + " file")
                + "\nSize: " + size + "\nModified: " + Ui.date(e.mod)
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
        final AlertDialog dlg = new AlertDialog.Builder(a).setTitle(e.name)
                .setMessage(propText(e, e.dir ? "calculating…" : Ui.size(e.len), e.dir ? "" : "calculating…"))
                .setNeutralButton(e.dir ? "" : "Copy SHA-256", null).setPositiveButton("Close", null).show();
        final android.widget.Button copyHash = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (e.dir) copyHash.setVisibility(View.GONE); else {
            copyHash.setEnabled(false);
            copyHash.setOnClickListener(v -> {
                if (digest[0] == null || "unavailable".equals(digest[0])) return;
                android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) { cm.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", digest[0])); Ui.toast(a, "SHA-256 copied"); }
            });
        }
        if (e.dir) a.io.execute(() -> {
            final long[] t = new long[2];
            FileOps.treeSize(e.f, t);
            a.runOnUiThread(() -> dlg.setMessage(propText(e, Ui.size(t[0]) + " • " + t[1] + " files", "")));
        }); else a.io.execute(() -> {
            digest[0] = sha256(e.f);
            a.runOnUiThread(() -> {
                if (!dlg.isShowing()) return;
                dlg.setMessage(propText(e, Ui.size(e.f.length()), digest[0]));
                copyHash.setEnabled(!"unavailable".equals(digest[0]));
            });
        });
    }
}
