package com.music.echo.offline.files;

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
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/** Independent NEBULA OS home and tools presentation. All actions delegate to existing app modules. */
final class NebulaHomeView {
    static final int BACKGROUND = Color.rgb(15, 20, 26);
    static final int PANEL = Color.rgb(25, 33, 42);
    static final int PANEL_ALT = Color.rgb(30, 40, 50);
    static final int BORDER = Color.rgb(48, 61, 73);
    static final int LIME = Color.rgb(201, 243, 106);
    static final int ICE = Color.rgb(112, 215, 243);
    static final int VIOLET = Color.rgb(169, 139, 255);
    static final int TEXT = Color.rgb(242, 246, 249);
    static final int MUTED = Color.rgb(145, 159, 171);
    private static final int[] ACCENTS = {LIME, ICE, VIOLET, LIME, ICE, VIOLET};

    private NebulaHomeView() { }

    static void buildHome(MainActivity a, LinearLayout host) {
        host.removeAllViews();
        a.homeScrollFooter = null;
        host.setOrientation(LinearLayout.VERTICAL);
        host.setPadding(dp(a, 18), dp(a, 10), dp(a, 18), 0);
        host.setBackgroundColor(BACKGROUND);

        LinearLayout brand = new LinearLayout(a);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView wordmark = text(a, "TOXIFILES", 22, TEXT, true);
        wordmark.setLetterSpacing(0.12f);
        brand.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout searchButton = new FrameLayout(a);
        searchButton.setContentDescription("Search files");
        searchButton.setBackground(background(PANEL_ALT, BORDER, 15));
        Glyph search = new Glyph(a, "search", ICE);
        searchButton.addView(search, new FrameLayout.LayoutParams(dp(a, 22), dp(a, 22), Gravity.CENTER));
        searchButton.setOnClickListener(v -> a.openSearch());
        searchButton.setFocusable(true);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46));
        brand.addView(searchButton, searchLp);
        host.addView(brand, margins(a, -1, -2, 0, 5, 0, 0));

        TextView subtitle = text(a, "YOUR DIGITAL SPACE", 10, MUTED, true);
        subtitle.setLetterSpacing(0.19f);
        host.addView(subtitle, margins(a, -1, -2, 0, 4, 0, 18));

        if (!a.hasAccess()) {
            LinearLayout access = panel(a, 18);
            access.addView(text(a, "Storage access required", 17, TEXT, true));
            access.addView(text(a, "Allow file access to show your device storage and browse your content.", 12, MUTED, false), margins(a, -1, -2, 0, 7, 0, 14));
            TextView grant = actionText(a, "GRANT ACCESS", LIME);
            grant.setGravity(Gravity.CENTER);
            grant.setBackground(background(PANEL_ALT, BORDER, 12));
            grant.setPadding(0, dp(a, 13), 0, dp(a, 13));
            grant.setOnClickListener(v -> a.showStorageAccess());
            access.addView(grant, new LinearLayout.LayoutParams(-1, -2));
            host.addView(access, margins(a, -1, -2, 0, 0, 0, 16));
            return;
        }

        addStorage(a, host);
        addSectionTitle(a, host, "EXPLORE", "YOUR FILES");
        String[] names = {"All Files", "Images", "Videos", "Audio", "Documents", "Downloads"};
        String[] details = {"Browse storage", "Photo library", "Video collection", "Music & sounds", "PDF & documents", "Recent downloads"};
        String[] glyphs = {"folder-open", "images", "clapperboard", "audio-lines", "files", "download"};
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                final int index = row * 2 + col;
                View card = exploreCard(a, names[index], details[index], glyphs[index], ACCENTS[index]);
                card.setOnClickListener(v -> {
                    switch (index) {
                        case 0: a.selectTab(1); break;
                        case 1: a.openCategory(0); break;
                        case 2: a.openCategory(1); break;
                        case 3: a.openCategory(2); break;
                        case 4: a.openCategory(3); break;
                        case 5: a.openDownloads(); break;
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (col == 0) lp.rightMargin = dp(a, 7);
                else lp.leftMargin = dp(a, 7);
                line.addView(card, lp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(a, 116));
            rowLp.bottomMargin = dp(a, 12);
            host.addView(line, rowLp);
        }
        a.homeScrollFooter = new View(a);
        a.homeScrollFooter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        host.addView(a.homeScrollFooter, new LinearLayout.LayoutParams(-1, dp(a, 116) + ToxiVisualSystem.BOTTOM_INSET));
    }

    static void buildTools(MainActivity a, LinearLayout host) {
        host.removeAllViews();
        host.setOrientation(LinearLayout.VERTICAL);
        host.setPadding(dp(a, 18), dp(a, 22), dp(a, 18), dp(a, 12));
        host.setBackgroundColor(BACKGROUND);

        TextView eyebrow = text(a, "TOXIFILES / WORKSPACE", 10, ICE, true);
        eyebrow.setLetterSpacing(0.15f);
        host.addView(eyebrow);
        host.addView(text(a, "Tools Studio", 28, TEXT, true), margins(a, -1, -2, 0, 8, 0, 4));
        host.addView(text(a, "Precision utilities for your files", 13, MUTED, false), margins(a, -1, -2, 0, 0, 0, 22));
        addSectionTitle(a, host, "TOOLS", "6 UTILITIES");

        String[] names = {"Analyze", "Compare", "Hex Editor", "Integrity", "Archives", "Text Editor"};
        String[] glyphs = {"file-search", "compare", "binary", "shield-check", "archive", "code"};
        String[] details = {"Storage insights", "Folder differences", "Binary data", "File verification", "ZIP management", "Edit text files"};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                final int index = row * 3 + col;
                LinearLayout card = new LinearLayout(a);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_HORIZONTAL);
                card.setPadding(dp(a, 5), dp(a, 15), dp(a, 5), dp(a, 11));
                card.setBackground(background(PANEL, BORDER, 16));
                Glyph icon = new Glyph(a, glyphs[index], ACCENTS[index]);
                card.addView(icon, new LinearLayout.LayoutParams(dp(a, 30), dp(a, 30)));
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
                card.setOnClickListener(v -> {
                    switch (index) {
                        case 0: a.openStorageAnalyzer(); break;
                        case 1: a.launchNebulaTool(2); break;
                        case 2: a.launchNebulaTool(1); break;
                        case 3: a.launchNebulaTool(5); break;
                        case 4: a.launchNebulaTool(4); break;
                        case 5: a.launchNebulaTool(6); break;
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(a, 118), 1f);
                if (col == 0) lp.rightMargin = dp(a, 5);
                else if (col == 1) { lp.leftMargin = dp(a, 5); lp.rightMargin = dp(a, 5); }
                else lp.leftMargin = dp(a, 5);
                line.addView(card, lp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(a, 118));
            rowLp.bottomMargin = dp(a, 10);
            host.addView(line, rowLp);
        }
        View footer = new View(a);
        host.addView(footer, new LinearLayout.LayoutParams(-1, dp(a, 110) + ToxiVisualSystem.BOTTOM_INSET));
    }

    private static void addStorage(MainActivity a, LinearLayout host) {
        File root = Environment.getExternalStorageDirectory();
        long total = root != null ? Math.max(0L, root.getTotalSpace()) : 0L;
        long available = root != null ? Math.max(0L, root.getUsableSpace()) : 0L;
        long used = total > 0 ? Math.max(0L, total - available) : 0L;
        int percent = total > 0 ? (int) Math.max(0L, Math.min(100L, Math.round((double) used * 100.0d / (double) total))) : 0;

        LinearLayout card = panel(a, 17);
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(a);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(a, "STORAGE OVERVIEW", 9, ICE, true));
        copy.addView(text(a, "Device storage", 17, TEXT, true), margins(a, -1, -2, 0, 5, 0, 0));
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
        card.setContentDescription(total > 0 ? "Device storage. " + formatSize(used) + " used, " + formatSize(available) + " available, " + percent + " percent used." : "Device storage information unavailable.");
        host.addView(card, margins(a, -1, -2, 0, 0, 0, 20));
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
        view.setTextColor(color);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private static TextView actionText(MainActivity a, String value, int color) {
        return text(a, value, 12, color, true);
    }

    static GradientDrawable navigationSurface() { return background(PANEL_ALT, BORDER, 22); }

    static GradientDrawable navigationHighlight() { return background(Color.argb(34, 201, 243, 106), Color.argb(88, 201, 243, 106), 16); }

    static Drawable navigationItemSurface() { return new RippleDrawable(ColorStateList.valueOf(Color.argb(28, 255, 255, 255)), null, null); }

    private static GradientDrawable background(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float density = Resources.getSystem().getDisplayMetrics().density;
        d.setCornerRadius(radius * density);
        d.setStroke(Math.max(1, Math.round(density)), stroke);
        return d;
    }

    private static LinearLayout.LayoutParams margins(MainActivity a, int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.setMargins(dp(a, left), dp(a, top), dp(a, right), dp(a, bottom));
        return lp;
    }

    static int dp(MainActivity a, float value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
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
