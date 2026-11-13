package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.StatFs;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;

/** Independent NEBULA OS home and tools presentation. All actions delegate to existing app modules. */
final class NebulaHomeView {
    static int BACKGROUND = ToxiVisualSystem.CANVAS;
    static int PANEL = ToxiVisualSystem.SURFACE_COLOR;
    static int PANEL_ALT = ToxiVisualSystem.RAISED_SURFACE;
    static int BORDER = ToxiVisualSystem.EDGE_STROKE;
    static int LIME = ToxiVisualSystem.ACCENT;
    static int ICE = ToxiThemeSystem.ACCENT_ENDS[0];
    static int VIOLET = ToxiIcons.mix(ToxiVisualSystem.ACCENT, ToxiVisualSystem.TEXT_PRIMARY, 0.28f);
    static int TEXT = ToxiVisualSystem.TEXT_PRIMARY;
    static int MUTED = ToxiVisualSystem.TEXT_SECONDARY;
    private static final int[] ACCENTS = new int[7];

    private static void syncPalette() {
        BACKGROUND = ToxiVisualSystem.CANVAS;
        PANEL = ToxiVisualSystem.SURFACE_COLOR;
        PANEL_ALT = ToxiVisualSystem.RAISED_SURFACE;
        BORDER = ToxiVisualSystem.EDGE_STROKE;
        LIME = ToxiVisualSystem.ACCENT;
        ICE = ToxiThemeSystem.ACCENT_ENDS[ToxiVisualSystem.THEME_INDEX];
        VIOLET = ToxiIcons.mix(ToxiVisualSystem.ACCENT, ToxiVisualSystem.TEXT_PRIMARY, 0.28f);
        TEXT = ToxiVisualSystem.TEXT_PRIMARY;
        MUTED = ToxiVisualSystem.TEXT_SECONDARY;
        int[] accents = {LIME, ICE, VIOLET, LIME, ICE, VIOLET, LIME};
        System.arraycopy(accents, 0, ACCENTS, 0, ACCENTS.length);
    }

    private NebulaHomeView() { }

    static void buildHome(MainActivity a, LinearLayout host) {
        syncPalette();
        host.removeAllViews();
        a.homeScrollFooter = null;
        host.setOrientation(LinearLayout.VERTICAL);
        host.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), 0);
        host.setBackgroundColor(BACKGROUND);

        LinearLayout mast = new LinearLayout(a);
        mast.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(a);
        mark.setImageResource(R.drawable.toxifiles_master_art);
        mark.setContentDescription("ToxiFiles master brand artwork");
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setBackground(background(PANEL_ALT, BORDER, 11));
        mark.setPadding(dp(a, 3), dp(a, 3), dp(a, 3), dp(a, 3));
        mast.addView(mark, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46)));
        LinearLayout identity = new LinearLayout(a);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(a, 12), 0, 0, 0);
        TextView word = text(a, "TOXIFILES", 19, LIME, true);
        word.setLetterSpacing(0.16f);
        identity.addView(word);
        TextView edition = text(a, "PRISM  /  PERSONAL FILESPACE", 8.5f, MUTED, true);
        edition.setLetterSpacing(0.13f);
        identity.addView(edition, margins(a, -2, -2, 0, 4, 0, 0));
        mast.addView(identity, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView live = text(a, "●  LOCAL", 9, LIME, true);
        live.setLetterSpacing(0.08f);
        live.setBackground(background(PANEL_ALT, BORDER, 18));
        live.setPadding(dp(a, 11), dp(a, 8), dp(a, 11), dp(a, 8));
        mast.addView(live);
        host.addView(mast, margins(a, -1, -2, 0, 0, 0, 19));

        TextView overline = text(a, "YOUR FILESPACE", 9, ICE, true);
        overline.setLetterSpacing(0.2f);
        host.addView(overline);
        TextView headline = text(a, "Everything\nin its element.", 31, TEXT, true);
        headline.setLineSpacing(dp(a, 1), 0.98f);
        host.addView(headline, margins(a, -1, -2, 0, 8, 0, 7));
        TextView descriptor = text(a, "A calm, precise view of the content on this device.", 12, MUTED, false);
        host.addView(descriptor, margins(a, -1, -2, 0, 0, 0, 16));

        if (!a.hasAccess()) {
            LinearLayout access = panel(a, 22);
            access.addView(text(a, "Connect your device storage", 17, TEXT, true));
            access.addView(text(a, "Grant access to reveal your files, folders and storage intelligence.", 12, MUTED, false), margins(a, -1, -2, 0, 8, 0, 15));
            TextView grant = actionText(a, "AUTHORIZE STORAGE  ↗", LIME);
            grant.setGravity(Gravity.CENTER);
            grant.setBackground(background(PANEL_ALT, BORDER, 15));
            grant.setPadding(0, dp(a, 14), 0, dp(a, 14));
            grant.setOnClickListener(v -> a.showStorageAccess());
            ToxiVisualSystem.interactive(grant);
            access.addView(grant, new LinearLayout.LayoutParams(-1, -2));
            host.addView(access);
            return;
        }

        addStorage(a, host);
        LinearLayout section = new LinearLayout(a);
        section.setGravity(Gravity.CENTER_VERTICAL);
        TextView sectionTitle = text(a, "EXPLORE YOUR CONTENT", 10, TEXT, true);
        sectionTitle.setLetterSpacing(0.14f);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        section.addView(text(a, "05 CHANNELS", 8.5f, MUTED, true));
        host.addView(section, margins(a, -1, -2, 0, 3, 0, 13));

        String[] names = {"Images", "Videos", "Audio", "Documents", "Downloads"};
        String[] details = {"Visual library", "Motion library", "Sound library", "Reading & records", "Recently received"};
        String[] glyphs = {"images", "clapperboard", "audio-lines", "files", "download"};
        int[] accents = {LIME, ICE, VIOLET, ICE, LIME};
        int columns = gridColumns(a);
        int rowCount = (names.length + columns - 1) / columns;
        for (int row = 0; row < rowCount; row++) {
            LinearLayout gridRow = new LinearLayout(a);
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            int rowItems = Math.min(columns, names.length - row * columns);
            for (int col = 0; col < rowItems; col++) {
                final int index = row * columns + col;
                LinearLayout item = new LinearLayout(a);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.TOP);
                item.setPadding(dp(a, 13), dp(a, 13), dp(a, 13), dp(a, 13));
                item.setBackground(ToxiVisualSystem.surface(20, index == 0));
                LinearLayout iconLine = new LinearLayout(a);
                iconLine.setGravity(Gravity.CENTER_VERTICAL);
                FrameLayout iconWell = new FrameLayout(a);
                iconWell.setBackground(background(PANEL_ALT, BORDER, 14));
                Glyph icon = new Glyph(a, glyphs[index], accents[index]);
                iconWell.addView(icon, new FrameLayout.LayoutParams(dp(a, 27), dp(a, 27), Gravity.CENTER));
                iconLine.addView(iconWell, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46)));
                TextView arrow = text(a, "↗", 17, accents[index], true);
                LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(0, -2, 1f);
                arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                iconLine.addView(arrow, arrowLp);
                item.addView(iconLine, new LinearLayout.LayoutParams(-1, -2));
                TextView categoryName = text(a, names[index], 14, TEXT, true);
                categoryName.setSingleLine(true);
                categoryName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.addView(categoryName, margins(a, -1, -2, 0, 12, 0, 0));
                TextView detail = text(a, details[index], 10, MUTED, false);
                detail.setMaxLines(2);
                detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.addView(detail, margins(a, -1, -2, 0, 5, 0, 0));
                item.setContentDescription(names[index] + ". " + details[index]);
                item.setFocusable(true);
                item.setOnClickListener(v -> {
                    ToxiVisualSystem.feedback(v);
                    if (index == 0) a.openCategory(0);
                    else if (index == 1) a.openCategory(1);
                    else if (index == 2) a.openCategory(2);
                    else if (index == 3) a.openCategory(3);
                    else a.openDownloads();
                });
                ToxiVisualSystem.interactive(item);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        rowItems == 1 ? -1 : 0, dp(a, 134), rowItems == 1 ? 0f : 1f);
                if (rowItems > 1) {
                    if (col == 0) cardLp.rightMargin = dp(a, 6);
                    else if (col == rowItems - 1) cardLp.leftMargin = dp(a, 6);
                    else {
                        cardLp.leftMargin = dp(a, 6);
                        cardLp.rightMargin = dp(a, 6);
                    }
                }
                gridRow.addView(item, cardLp);
                animateHomeCard(a, item, index);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(a, 134));
            rowLp.bottomMargin = dp(a, 10);
            host.addView(gridRow, rowLp);
        }
        a.homeScrollFooter = new View(a);
        a.homeScrollFooter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        host.addView(a.homeScrollFooter, new LinearLayout.LayoutParams(-1,
                dp(a, 110) + ToxiVisualSystem.BOTTOM_INSET));
    }

    private static int gridColumns(MainActivity a) {
        int widthDp = a.getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 900) return 4;
        if (widthDp >= 600) return 3;
        return 2;
    }

    private static void animateHomeCard(MainActivity a, View card, int index) {
        if (ToxiVisualSystem.booleanPreference("reduce_motion", false)
                || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
            card.setAlpha(1f);
            card.setTranslationY(0f);
            return;
        }
        card.setAlpha(0f);
        card.setTranslationY(dp(a, 10));
        card.post(() -> {
            if (!card.isAttachedToWindow()) {
                card.setAlpha(1f);
                card.setTranslationY(0f);
                return;
            }
            card.animate().cancel();
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * 35L)
                    .setDuration(ToxiThemeEngine.motionDuration(220L))
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
    }

    static void buildTools(MainActivity a, LinearLayout host) {
        syncPalette();
        host.removeAllViews();
        host.setOrientation(LinearLayout.VERTICAL);
        host.setPadding(dp(a, 18), dp(a, 22), dp(a, 18), dp(a, 12));
        host.setBackgroundColor(BACKGROUND);

        TextView eyebrow = text(a, "TOXIFILES / WORKSPACE", 10, ICE, true);
        eyebrow.setLetterSpacing(0.15f);
        host.addView(eyebrow);
        host.addView(text(a, "Tools Studio", 28, TEXT, true), margins(a, -1, -2, 0, 8, 0, 4));
        host.addView(text(a, "Seven focused utilities. Each opens directly.", 13, MUTED, false), margins(a, -1, -2, 0, 0, 0, 22));
        addSectionTitle(a, host, "OFFLINE UTILITIES", "7 TOOLS");
        String[] names = {"Dual-Pane Explorer", "Hex Editor", "Folder Comparator", "Encoding Converter", "ZIP Editor", "Integrity Studio", "Text Editor"};
        String[] glyphs = {"columns-2", "binary", "compare", "languages", "archive", "shield-check", "code"};
        String[] details = {"Two folders side by side", "Inspect and edit bytes", "Compare folder contents", "Convert text encodings", "Manage ZIP contents", "Verify file checksums", "Edit and search text"};
        int columns = gridColumns(a);
        int rowCount = (names.length + columns - 1) / columns;
        for (int row = 0; row < rowCount; row++) {
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            int rowItems = Math.min(columns, names.length - row * columns);
            for (int col = 0; col < rowItems; col++) {
                final int index = row * columns + col;
                LinearLayout card = new LinearLayout(a);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_HORIZONTAL);
                card.setPadding(dp(a, 8), dp(a, 15), dp(a, 8), dp(a, 11));
                card.setBackground(toolCardSurface(ACCENTS[index]));
                card.setElevation(dp(a, 2));
                FrameLayout iconWell = new FrameLayout(a);
                iconWell.setBackground(background(Color.argb(28, Color.red(ACCENTS[index]), Color.green(ACCENTS[index]), Color.blue(ACCENTS[index])), Color.argb(90, Color.red(ACCENTS[index]), Color.green(ACCENTS[index]), Color.blue(ACCENTS[index])), 14));
                Glyph icon = new Glyph(a, glyphs[index], ACCENTS[index]);
                iconWell.addView(icon, new FrameLayout.LayoutParams(dp(a, 29), dp(a, 29), Gravity.CENTER));
                card.addView(iconWell, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
                TextView name = text(a, names[index], 12, TEXT, true);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(2);
                card.addView(name, margins(a, -1, -2, 0, 10, 0, 0));
                TextView detail = text(a, details[index], 9, MUTED, false);
                detail.setGravity(Gravity.CENTER);
                detail.setMaxLines(2);
                card.addView(detail, margins(a, -1, -2, 0, 4, 0, 0));
                card.setContentDescription(names[index] + ". " + details[index]);
                card.setFocusable(true);
                card.setOnClickListener(v -> a.launchNebulaTool(index));
                ToxiVisualSystem.interactive(card);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        rowItems == 1 ? -1 : 0, dp(a, 122), rowItems == 1 ? 0f : 1f);
                if (rowItems > 1) {
                    if (col == 0) lp.rightMargin = dp(a, 6);
                    else if (col == rowItems - 1) lp.leftMargin = dp(a, 6);
                    else {
                        lp.leftMargin = dp(a, 6);
                        lp.rightMargin = dp(a, 6);
                    }
                }
                line.addView(card, lp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(a, 122));
            rowLp.bottomMargin = dp(a, 11);
            host.addView(line, rowLp);
        }
        View footer = new View(a);
        host.addView(footer, new LinearLayout.LayoutParams(-1, dp(a, 110) + ToxiVisualSystem.BOTTOM_INSET));
    }

    private static void addStorage(MainActivity a, LinearLayout host) {
        StorageCapacity capacity = readStorageCapacity(a);
        long total = capacity.total;
        long available = capacity.available;
        long used = capacity.used;
        int percent = total > 0 ? (int) Math.max(0L, Math.min(100L,
                Math.round((double) used * 100.0d / (double) total))) : -1;

        LinearLayout card = panel(a, 17);
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(a);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(a, "STORAGE OVERVIEW", 9, ICE, true));
        copy.addView(text(a, capacity.label, 17, TEXT, true), margins(a, -1, -2, 0, 5, 0, 0));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView pct = text(a, total > 0 ? percent + "%" : "--", 22, LIME, true);
        top.addView(pct);
        card.addView(top);

        ProgressBar progress = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(total > 0 ? percent : 0);
        progress.setProgressTintList(ColorStateList.valueOf(LIME));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        progress.setIndeterminate(false);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(a, 7));
        progressLp.topMargin = dp(a, 17);
        progressLp.bottomMargin = dp(a, 13);
        card.addView(progress, progressLp);

        LinearLayout values = new LinearLayout(a);
        values.setOrientation(LinearLayout.HORIZONTAL);
        values.addView(storageValue(a, "USED", total > 0 ? formatSize(used) : "Unavailable", LIME), new LinearLayout.LayoutParams(0, -2, 1f));
        values.addView(storageValue(a, "AVAILABLE", total > 0 ? formatSize(available) : "Unavailable", ICE), new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(values);
        if (capacity.error != null) card.addView(text(a, capacity.error, 9, MUTED, false), margins(a, -1, -2, 0, 8, 0, 0));
        card.setContentDescription(total > 0 ? capacity.label + ". " + formatSize(used) + " used, " + formatSize(available) + " available, " + percent + " percent used. Tap to analyze storage." : "Device storage information unavailable. Tap to analyze storage.");
        card.setFocusable(true);
        card.setOnClickListener(v -> a.openStorageAnalyzer());
        ToxiVisualSystem.interactive(card);
        host.addView(card, margins(a, -1, -2, 0, 0, 0, 20));
    }

    private static final class StorageCapacity {
        final String label;
        final long total, available, used;
        final String error;
        StorageCapacity(String label, long total, long available, String error) {
            this.label = label;
            this.total = Math.max(0L, total);
            this.available = Math.max(0L, Math.min(this.total, available));
            this.used = this.total - this.available;
            this.error = error;
        }
    }

    private static StorageCapacity readStorageCapacity(MainActivity activity) {
        File root = android.os.Environment.getExternalStorageDirectory();
        if (root == null) return new StorageCapacity("Shared storage", 0L, 0L, "Storage volume unavailable");
        try {
            StatFs stats = new StatFs(root.getAbsolutePath());
            long total = stats.getTotalBytes();
            long available = Math.min(total, Math.max(0L, stats.getAvailableBytes()));
            if (total <= 0L) return new StorageCapacity("Shared storage", 0L, 0L, "System did not report volume capacity");
            return new StorageCapacity("Primary shared storage", total, available, null);
        } catch (IllegalArgumentException | SecurityException failure) {
            return new StorageCapacity("Shared storage", 0L, 0L, "Storage statistics unavailable");
        }
    }

    private static View storageValue(MainActivity a, String label, String value, int accent) {
        LinearLayout block = new LinearLayout(a);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(text(a, label, 9, MUTED, true));
        block.addView(text(a, value, 13, accent, true), margins(a, -1, -2, 0, 4, 0, 0));
        return block;
    }

    private static View exploreCard(MainActivity a, String name, String detail, String glyph, int accent) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(a, 13), dp(a, 11), dp(a, 11), dp(a, 10));
        card.setBackground(background(PANEL, BORDER, 16));
        Glyph icon = new Glyph(a, glyph, accent);
        card.addView(icon, new LinearLayout.LayoutParams(dp(a, 28), dp(a, 28)));
        card.addView(text(a, name, 13, TEXT, true), margins(a, -1, -2, 0, 7, 0, 0));
        TextView sub = text(a, detail, 10, MUTED, false);
        sub.setMaxLines(1);
        card.addView(sub, margins(a, -1, -2, 0, 3, 0, 0));
        card.setContentDescription(name + ". " + detail);
        card.setFocusable(true);
        return card;
    }

    private static void addSectionTitle(MainActivity a, LinearLayout host, String title, String trailing) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = text(a, title, 11, TEXT, true);
        left.setLetterSpacing(0.12f);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView right = text(a, trailing, 9, MUTED, true);
        right.setLetterSpacing(0.1f);
        row.addView(right);
        host.addView(row, margins(a, -1, -2, 0, 0, 0, 11));
    }

    private static Drawable toolCardSurface(int accent) {
        // Keep utility cards on the same theme-owned liquid-glass material as Home,
        // Theme Studio, navigation and the file-management surfaces. The glyph remains
        // the per-tool signal; the card itself must never introduce a second palette.
        return ToxiVisualSystem.surface(18, true);
    }

    private static int dpRadius(int value) {
        return Math.max(1, ToxiVisualSystem.dp(ToxiThemeEngine.radius(value)));
    }

    private static LinearLayout panel(MainActivity a, int radius) {
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a, 15), dp(a, 15), dp(a, 15), dp(a, 14));
        panel.setBackground(background(PANEL, BORDER, radius));
        return panel;
    }

    private static TextView text(MainActivity a, String value, float size, int color, boolean medium) {
        TextView view = new TextView(a);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color == LIME ? ToxiVisualSystem.signalTextColor() : color);
        view.setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), medium ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private static TextView actionText(MainActivity a, String value, int color) {
        return text(a, value, 12, color, true);
    }

    static GradientDrawable navigationSurface() {
        syncPalette();
        int top = Color.argb(246, Color.red(BACKGROUND), Color.green(BACKGROUND), Color.blue(BACKGROUND));
        int bottom = Color.argb(238, Color.red(PANEL), Color.green(PANEL), Color.blue(PANEL));
        int edge = Color.argb(185, Color.red(BORDER), Color.green(BORDER), Color.blue(BORDER));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        glass.setCornerRadius(ToxiVisualSystem.dp(ToxiThemeEngine.radius(25)));
        glass.setStroke(Math.max(1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1f))), edge);
        return glass;
    }

    static GradientDrawable navigationHighlight() {
        syncPalette();
        GradientDrawable active = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(48, Color.red(LIME), Color.green(LIME), Color.blue(LIME)),
                        Color.argb(22, Color.red(ICE), Color.green(ICE), Color.blue(ICE))});
        active.setCornerRadius(ToxiVisualSystem.dp(ToxiThemeEngine.radius(18)));
        active.setStroke(Math.max(1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1f))),
                Color.argb(190, Color.red(LIME), Color.green(LIME), Color.blue(LIME)));
        return active;
    }

    static Drawable navigationItemSurface() { syncPalette(); return new RippleDrawable(ColorStateList.valueOf(Color.argb(32, Color.red(TEXT), Color.green(TEXT), Color.blue(TEXT))), null, null); }

    private static GradientDrawable background(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(ToxiVisualSystem.dp(ToxiThemeEngine.radius(radius)));
        d.setStroke(Math.max(1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1f))), stroke);
        return d;
    }

    private static LinearLayout.LayoutParams margins(MainActivity a, int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.setMargins(dp(a, left), dp(a, top), dp(a, right), dp(a, bottom));
        return lp;
    }

    static int dp(MainActivity a, float value) {
        return Math.round(ToxiThemeEngine.spacing(value) * a.getResources().getDisplayMetrics().density);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do { value /= 1024.0; unit++; } while (value >= 1024.0 && unit < units.length - 1);
        return String.format(java.util.Locale.ROOT, value >= 10 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    /** Compact geometric outline icon set used only by the NEBULA surfaces. */
    static final class Glyph extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String name;
        private int tint;
        private final Path path = new Path();
        private final RectF rect = new RectF();

        Glyph(android.content.Context context, String name, int tint) {
            super(context);
            this.name = name;
            this.tint = tint;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setFocusable(false);
        }

        void setTint(int color) { tint = color; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            if (scale <= 0f) return;
            canvas.save();
            canvas.translate((getWidth() - 24f * scale) / 2f, (getHeight() - 24f * scale) / 2f);
            canvas.scale(scale, scale);
            paint.setColor(tint);
            paint.setStrokeWidth(1.7f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            switch (name) {
                case "search":
                case "file-search":
                    if (name.equals("file-search")) {
                        path.reset(); path.moveTo(4, 2.5f); path.lineTo(14, 2.5f); path.lineTo(18, 6.5f); path.lineTo(18, 11);
                        path.moveTo(14, 2.5f); path.lineTo(14, 7); path.lineTo(18, 7); canvas.drawPath(path, paint);
                        canvas.drawLine(4, 2.5f, 4, 20.5f, paint); canvas.drawLine(4, 20.5f, 11, 20.5f, paint);
                    }
                    canvas.drawCircle(name.equals("search") ? 10 : 13, name.equals("search") ? 10 : 13, 6.2f, paint);
                    canvas.drawLine(name.equals("search") ? 14.5f : 17.5f, name.equals("search") ? 14.5f : 17.5f, 21.5f, 21.5f, paint);
                    break;
                case "folder": case "folder-open":
                    path.reset(); path.moveTo(2.5f, 6); path.lineTo(9, 6); path.lineTo(11, 8.5f); path.lineTo(21.5f, 8.5f);
                    path.lineTo(19.5f, 19.5f); path.lineTo(4, 19.5f); path.close(); canvas.drawPath(path, paint);
                    if (name.equals("folder-open")) { canvas.drawLine(2.5f, 10, 21, 10, paint); canvas.drawLine(4, 19.5f, 7, 12.5f, paint); canvas.drawLine(7, 12.5f, 22, 12.5f, paint); }
                    break;
                case "images":
                    rect.set(2.5f, 3, 21.5f, 21); canvas.drawRoundRect(rect, 3, 3, paint);
                    canvas.drawCircle(15.5f, 8, 1.6f, paint);
                    path.reset(); path.moveTo(4, 18); path.lineTo(9, 12.5f); path.lineTo(12, 15.5f); path.lineTo(15, 12); path.lineTo(20, 18); canvas.drawPath(path, paint); break;
                case "clapperboard":
                    rect.set(2.5f, 7, 21.5f, 21); canvas.drawRoundRect(rect, 2, 2, paint);
                    path.reset(); path.moveTo(2.5f, 7); path.lineTo(20, 3); path.lineTo(22, 8); path.lineTo(3.5f, 12); path.close(); canvas.drawPath(path, paint);
                    canvas.drawLine(8, 5.7f, 10, 10.5f, paint); canvas.drawLine(14, 4.3f, 16, 9.2f, paint); break;
                case "audio-lines":
                    float[] heights = {7, 13, 18, 10, 15, 7, 12};
                    for (int i = 0; i < heights.length; i++) canvas.drawLine(3 + i * 3, 12 - heights[i] / 2, 3 + i * 3, 12 + heights[i] / 2, paint); break;
                case "files":
                    path.reset(); path.moveTo(5, 2.5f); path.lineTo(15, 2.5f); path.lineTo(20, 7.5f); path.lineTo(20, 21); path.lineTo(5, 21); path.close(); canvas.drawPath(path, paint);
                    canvas.drawLine(15, 2.5f, 15, 7.5f, paint); canvas.drawLine(15, 7.5f, 20, 7.5f, paint);
                    canvas.drawLine(8, 12, 17, 12, paint); canvas.drawLine(8, 16, 16, 16, paint); break;
                case "download":
                    canvas.drawLine(12, 2.5f, 12, 15, paint); path.reset(); path.moveTo(6.5f, 10); path.lineTo(12, 15.5f); path.lineTo(17.5f, 10); canvas.drawPath(path, paint);
                    canvas.drawLine(4, 18, 4, 21, paint); canvas.drawLine(4, 21, 20, 21, paint); canvas.drawLine(20, 21, 20, 18, paint); break;
                case "compare":
                    canvas.drawLine(3, 7, 20, 7, paint); path.reset(); path.moveTo(16, 3); path.lineTo(20, 7); path.lineTo(16, 11); canvas.drawPath(path, paint);
                    canvas.drawLine(21, 17, 4, 17, paint); path.reset(); path.moveTo(8, 13); path.lineTo(4, 17); path.lineTo(8, 21); canvas.drawPath(path, paint); break;
                case "binary":
                    paint.setStyle(Paint.Style.FILL); paint.setTypeface(Typeface.create("monospace", Typeface.BOLD)); paint.setTextSize(9); canvas.drawText("01", 2, 10, paint); canvas.drawText("10", 8, 20, paint); break;
                case "shield-check":
                    path.reset(); path.moveTo(12, 2.5f); path.lineTo(20, 5.5f); path.lineTo(19, 13); path.lineTo(16, 18); path.lineTo(12, 21.5f); path.lineTo(8, 18); path.lineTo(5, 13); path.lineTo(4, 5.5f); path.close(); canvas.drawPath(path, paint);
                    path.reset(); path.moveTo(8, 12); path.lineTo(11, 15); path.lineTo(16.5f, 9); canvas.drawPath(path, paint); break;
                case "archive":
                    rect.set(3, 8, 21, 21); canvas.drawRoundRect(rect, 2, 2, paint); rect.set(2, 3, 22, 8); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint);
                    canvas.drawLine(9, 13, 15, 13, paint); canvas.drawLine(10, 16, 14, 16, paint); break;
                case "code":
                    path.reset(); path.moveTo(8.5f, 6); path.lineTo(3, 12); path.lineTo(8.5f, 18); canvas.drawPath(path, paint);
                    path.reset(); path.moveTo(15.5f, 6); path.lineTo(21, 12); path.lineTo(15.5f, 18); canvas.drawPath(path, paint);
                    canvas.drawLine(14, 4, 10, 20, paint); break;
                case "columns-2":
                    rect.set(2.5f, 3, 10.5f, 21); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint);
                    rect.set(13.5f, 3, 21.5f, 21); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint);
                    canvas.drawLine(12, 4, 12, 20, paint); break;
                case "languages":
                    canvas.drawCircle(12, 12, 9, paint);
                    canvas.drawOval(8, 3, 16, 21, paint);
                    canvas.drawLine(4, 9, 20, 9, paint);
                    canvas.drawLine(4, 15, 20, 15, paint); break;
                case "house":
                    path.reset(); path.moveTo(2.5f, 11); path.lineTo(12, 3); path.lineTo(21.5f, 11); path.moveTo(5, 9.5f); path.lineTo(5, 21); path.lineTo(19, 21); path.lineTo(19, 9.5f); path.moveTo(9.5f, 21); path.lineTo(9.5f, 14); path.lineTo(14.5f, 14); path.lineTo(14.5f, 21); canvas.drawPath(path, paint); break;
                case "grid":
                    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) { rect.set(3 + x * 10, 3 + y * 10, 11 + x * 10, 11 + y * 10); canvas.drawRoundRect(rect, 1.8f, 1.8f, paint); } break;
                case "settings":
                    canvas.drawCircle(12, 12, 4, paint); canvas.drawCircle(12, 12, 8, paint);
                    for (int i = 0; i < 8; i++) { double angle = Math.PI * i / 4; float x1 = 12 + (float)Math.cos(angle) * 9; float y1 = 12 + (float)Math.sin(angle) * 9; float x2 = 12 + (float)Math.cos(angle) * 11; float y2 = 12 + (float)Math.sin(angle) * 11; canvas.drawLine(x1, y1, x2, y2, paint); } break;
                default:
                    canvas.drawCircle(12, 12, 8, paint); canvas.drawCircle(12, 12, 2, paint); break;
            }
            canvas.restore();
        }
    }
}
