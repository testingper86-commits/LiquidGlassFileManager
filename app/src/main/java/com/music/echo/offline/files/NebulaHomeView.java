package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
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

    // The same themed wallpaper is used by multiple screens. Keep one immutable,
    // screen-sized bitmap instead of decoding a full-resolution copy per screen.
    private static Bitmap cachedWallpaperBitmap;
    private static int cachedWallpaperResource = 0;

    private static synchronized Bitmap loadThemeWallpaper(Resources resources, int resourceId) {
        if (cachedWallpaperBitmap != null && !cachedWallpaperBitmap.isRecycled()
                && cachedWallpaperResource == resourceId) {
            return cachedWallpaperBitmap;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resourceId, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            cachedWallpaperBitmap = null;
            cachedWallpaperResource = 0;
            return null;
        }

        android.util.DisplayMetrics metrics = resources.getDisplayMetrics();
        int targetWidth = Math.max(1, metrics.widthPixels);
        int targetHeight = Math.max(1, metrics.heightPixels);
        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= targetWidth
                && bounds.outHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;
        Bitmap decoded = BitmapFactory.decodeResource(resources, resourceId, options);
        if (decoded != null) {
            cachedWallpaperBitmap = decoded;
            cachedWallpaperResource = resourceId;
        }
        return decoded;
    }
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

    static Drawable wallpaper(MainActivity a) {
        syncPalette();
        return new ThemeWallpaperDrawable(a);
    }

    static void buildHome(MainActivity a, LinearLayout host) {
        syncPalette();
        host.removeAllViews();
        a.homeScrollFooter = null;
        host.setOrientation(LinearLayout.VERTICAL);
        host.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), 0);
        a.homeScroll.setBackground(sectionWallpaper(a, 0));
        host.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout mast = new LinearLayout(a);
        mast.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = new LinearLayout(a);
        identity.setOrientation(LinearLayout.VERTICAL);
        TextView word = text(a, "TOXIFILES", 22, TEXT, true);
        word.setLetterSpacing(0.16f);
        identity.addView(word);
        TextView edition = text(a, "PRISM  /  PERSONAL FILESPACE", 9.5f, MUTED, true);
        edition.setLetterSpacing(0.13f);
        identity.addView(edition, margins(a, -2, -2, 0, 4, 0, 0));
        mast.addView(identity, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView live = text(a, "â—  LOCAL", 9, LIME, true);
        live.setLetterSpacing(0.08f);
        live.setBackground(background(PANEL_ALT, BORDER, 18));
        live.setPadding(dp(a, 11), dp(a, 8), dp(a, 11), dp(a, 8));
        mast.addView(live);
        host.addView(mast, margins(a, -1, -2, 0, 0, 0, 0));

        TextView headline = text(a, "Everything\nin its element.", 33, TEXT, true);
        headline.setLineSpacing(dp(a, 1), 0.98f);
        host.addView(headline, margins(a, -1, -2, 0, 12, 0, 7));
        TextView descriptor = text(a, "A calm, precise view of the content on this device.", 13.5f, MUTED, false);
        host.addView(descriptor, margins(a, -1, -2, 0, 0, 0, 16));

        if (!a.hasAccess()) {
            LinearLayout access = panel(a, 22);
            access.addView(text(a, "Connect your device storage", 17, TEXT, true));
            access.addView(text(a, "Grant access to reveal your files, folders and storage intelligence.", 12, MUTED, false), margins(a, -1, -2, 0, 8, 0, 15));
            TextView grant = actionText(a, "AUTHORIZE STORAGE  â†—", LIME);
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
        section.addView(text(a, "04 LIBRARIES", 8.5f, MUTED, true));
        host.addView(section, margins(a, -1, -2, 0, 3, 0, 13));

        String[] names = {"Images", "Videos", "Music", "Documents"};
        String[] details = {"Visual library", "Motion library", "Sound library", "File library"};
        String[] glyphs = {"images", "clapperboard", "audio-lines", "files"};
        int[] accents = {LIME, ICE, VIOLET, ICE};
        int columns = gridColumns(a);
        int rowCount = (names.length + columns - 1) / columns;
        for (int row = 0; row < rowCount; row++) {
            LinearLayout gridRow = new LinearLayout(a);
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            int rowItems = Math.min(columns, names.length - row * columns);
            for (int col = 0; col < rowItems; col++) {
                final int index = row * columns + col;
                LinearLayout item = categoryCard(a, names[index], details[index], glyphs[index], accents[index]);
                item.setContentDescription(names[index] + ". " + details[index] + ". Open category.");
                item.setFocusable(true);
                item.setOnClickListener(v -> {
                    ToxiVisualSystem.feedback(v);
                    a.openCategory(index);
                });
                ToxiVisualSystem.interactive(item);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        rowItems == 1 ? -1 : 0, dp(a, 104), rowItems == 1 ? 0f : 1f);
                if (rowItems > 1) {
                    if (col == 0) cardLp.rightMargin = dp(a, 6);
                    else if (col == rowItems - 1) cardLp.leftMargin = dp(a, 6);
                    else { cardLp.leftMargin = dp(a, 6); cardLp.rightMargin = dp(a, 6); }
                }
                gridRow.addView(item, cardLp);
                animateHomeCard(a, item, index);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(a, 104));
            rowLp.bottomMargin = dp(a, 9);
            host.addView(gridRow, rowLp);
        }
        addOtherQuickAccess(a, host);
        a.homeScrollFooter = new View(a);
        a.homeScrollFooter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        host.addView(a.homeScrollFooter, new LinearLayout.LayoutParams(-1,
                dp(a, 110) + ToxiVisualSystem.BOTTOM_INSET));
    }

    private static LinearLayout categoryCard(MainActivity a, String name, String detail, String glyph, int accent) {
        FrameLayout card = new FrameLayout(a);
        card.setBackground(categoryGlassSurface(accent));
        card.setClipToOutline(true);

        ImageView artwork = new ImageView(a);
        artwork.setImageResource(themeArtwork());
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setAlpha(0.48f);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(artwork, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(a);
        GradientDrawable shadeDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(74, Color.red(BACKGROUND), Color.green(BACKGROUND), Color.blue(BACKGROUND)),
                        Color.argb(154, Color.red(BACKGROUND), Color.green(BACKGROUND), Color.blue(BACKGROUND)),
                        Color.argb(216, Color.red(BACKGROUND), Color.green(BACKGROUND), Color.blue(BACKGROUND))});
        shadeDrawable.setCornerRadius(dp(a, 22));
        shade.setBackground(shadeDrawable);
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP);
        content.setPadding(dp(a, 13), dp(a, 12), dp(a, 13), dp(a, 11));
        LinearLayout iconLine = new LinearLayout(a);
        iconLine.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout iconWell = new FrameLayout(a);
        iconWell.setBackground(background(Color.argb(48, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent)), 14));
        Glyph icon = new Glyph(a, glyph, accent);
        iconWell.addView(icon, new FrameLayout.LayoutParams(dp(a, 27), dp(a, 27), Gravity.CENTER));
        iconLine.addView(iconWell, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46)));
        TextView arrow = text(a, "â†—", 19, accent, true);
        arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        iconLine.addView(arrow, new LinearLayout.LayoutParams(0, -1, 1f));
        content.addView(iconLine, new LinearLayout.LayoutParams(-1, dp(a, 46)));
        View spacer = new View(a);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView title = text(a, name, 15.5f, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(title, margins(a, -1, -2, 0, 0, 0, 3));
        TextView subtitle = text(a, detail, 10.5f, MUTED, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(subtitle);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return contentCardWrapper(a, card);
    }

    private static LinearLayout contentCardWrapper(MainActivity a, FrameLayout card) {
        LinearLayout wrapper = new LinearLayout(a);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(card, new LinearLayout.LayoutParams(-1, -1));
        wrapper.setClipChildren(true);
        wrapper.setClipToPadding(true);
        return wrapper;
    }

    private static Drawable categoryGlassSurface(int accent) {
        int base = PANEL_ALT;
        int topAlpha = ToxiVisualSystem.LIGHT_MODE ? 218 : 86;
        int bottomAlpha = ToxiVisualSystem.LIGHT_MODE ? 188 : 54;
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(topAlpha, Color.red(base), Color.green(base), Color.blue(base)),
                        Color.argb(bottomAlpha, Color.red(PANEL), Color.green(PANEL), Color.blue(PANEL)),
                        Color.argb(ToxiVisualSystem.LIGHT_MODE ? 176 : 42, Color.red(BACKGROUND), Color.green(BACKGROUND), Color.blue(BACKGROUND))});
        glass.setCornerRadius(ToxiVisualSystem.dp(ToxiThemeEngine.radius(22)));
        int edge = Color.argb(ToxiThemeEngine.edgeAlpha(225), Color.red(accent), Color.green(accent), Color.blue(accent));
        glass.setStroke(Math.max(1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1.1f))), edge);
        return glass;
    }

    private static void addOtherQuickAccess(MainActivity a, LinearLayout host) {
        FrameLayout card = new FrameLayout(a);
        card.setBackground(categoryGlassSurface(ICE));
        card.setClipToOutline(true);
        ImageView artwork = new ImageView(a);
        artwork.setImageResource(themeArtwork());
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setAlpha(0.46f);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(a);
        GradientDrawable overlay = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(118, Color.red(BACKGROUND), Color.green(BACKGROUND), Color.blue(BACKGROUND)),
                        Color.argb(210, Color.red(BACKGROUND), Color.green(BACKGROUND), Color.blue(BACKGROUND))});
        overlay.setCornerRadius(dp(a, 20));
        shade.setBackground(overlay);
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 15), dp(a, 12), dp(a, 15), dp(a, 12));
        FrameLayout iconWell = new FrameLayout(a);
        iconWell.setBackground(background(Color.argb(48, Color.red(ICE), Color.green(ICE), Color.blue(ICE)), BORDER, 13));
        Glyph icon = new Glyph(a, "grid", ICE);
        iconWell.addView(icon, new FrameLayout.LayoutParams(dp(a, 23), dp(a, 23), Gravity.CENTER));
        row.addView(iconWell, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46)));
        LinearLayout copy = new LinearLayout(a);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(a, 13), 0, 0, 0);
        copy.addView(text(a, "Other sections", 15, TEXT, true));
        copy.addView(text(a, "APK files Â· System apps Â· Downloaded apps Â· Other files", 10.5f, MUTED, false),
                margins(a, -1, -2, 0, 4, 0, 0));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(text(a, "â†—", 20, ICE, true));
        card.addView(row, new FrameLayout.LayoutParams(-1, -1));
        card.setContentDescription("Other sections. APK files, system apps, downloaded apps and other files.");
        card.setFocusable(true);
        card.setOnClickListener(v -> { ToxiVisualSystem.feedback(v); a.showOtherSections(); });
        ToxiVisualSystem.interactive(card);
        host.addView(card, margins(a, -1, dp(a, 78), 0, 3, 0, 4));
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
        a.toolsScroll.setBackground(sectionWallpaper(a, 2));
        host.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout toolsHeader = new LinearLayout(a);
        toolsHeader.setGravity(Gravity.CENTER_VERTICAL);
        toolsHeader.setPadding(dp(a, 16), dp(a, 6), dp(a, 12), dp(a, 6));
        toolsHeader.setBackground(ToxiVisualSystem.surface(24, true));
        toolsHeader.addView(text(a, "Tools", 24, TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        host.addView(toolsHeader, margins(a, -1, -2, 0, 0, 0, 18));
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

        LinearLayout card = panel(a, 20);
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView storageMark = text(a, "â–¤", 23, LIME, true);
        storageMark.setGravity(Gravity.CENTER);
        top.addView(storageMark, new LinearLayout.LayoutParams(dp(a, 34), dp(a, 36)));
        LinearLayout copy = new LinearLayout(a);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(a, 9), 0, 0, 0);
        TextView storageLabel = text(a, "STORAGE OVERVIEW", 9.5f, ICE, true);
        storageLabel.setLetterSpacing(0.06f);
        copy.addView(storageLabel);
        copy.addView(text(a, capacity.label, 17.5f, TEXT, true), margins(a, -1, -2, 0, 5, 0, 0));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView pct = text(a, total > 0 ? percent + "%" : "--", 27, LIME, true);
        top.addView(pct);
        card.addView(top);

        ProgressBar progress = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(total > 0 ? percent : 0);
        progress.setProgressTintList(ColorStateList.valueOf(LIME));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        progress.setIndeterminate(false);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(a, 8));
        progressLp.topMargin = dp(a, 12);
        progressLp.bottomMargin = dp(a, 9);
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
        host.addView(card, margins(a, -1, -2, 0, 0, 0, 16));
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
        block.addView(text(a, value, 15, accent, true), margins(a, -1, -2, 0, 4, 0, 0));
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
        panel.setBackground(homeGlassSurface(radius, true));
        return panel;
    }

    private static Drawable homeGlassSurface(int radius, boolean raised) {
        int base = raised ? PANEL_ALT : PANEL;
        int alpha = ToxiVisualSystem.LIGHT_MODE ? 178 : 48;
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(Math.min(86, alpha + 20), Color.red(base), Color.green(base), Color.blue(base)),
                        Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)),
                        Color.argb(Math.max(24, alpha - 12), Color.red(BACKGROUND), Color.green(BACKGROUND), Color.blue(BACKGROUND))});
        glass.setCornerRadius(ToxiVisualSystem.dp(ToxiThemeEngine.radius(radius)));
        int edge = Color.argb(ToxiThemeEngin¢ëiºÛkºwµç_ºYhºÚn¶Æ¯yÛhþiíýø¥zÏÜ¢jh²*?¢ëiºßÞÅç)¢W¬µÚ.¶ÜmN‹â{ayû¥–‹­¦ëkºw(uê)zæžßßŠW¬ýÊ&¦‹"£ú.¶›­ýì^rš%zË]¢ëmÆÚÞiÓ«ºÇ‚8ÃÎHƒ!Ó8âÜjßæžßßŠW¬ýÊ&¦‹"£ú.¶›­ýì^rš%zË]¢ëmÆßáy§g×M?š{~)^³÷(šš,ŠèºÚn·÷±yÊh•ë-v‹­·µ¨¥Ÿ]4þiíýø¥zÏÜ¢jh²*?¢ëiºßÞÅç)¢W¬µÚ.¶Üm