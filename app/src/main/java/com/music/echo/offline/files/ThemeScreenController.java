package com.music.echo.offline.files;

import com.music.echo.offline.R;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Five-theme Theme Studio with persistent live application. */
final class ThemeScreenController {
    private final MainActivity activity;
    private FrameLayout[] themeTiles;
    private TextView[] themeStates;
    private boolean themeChanged;
    private static final ExecutorService ARTWORK_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final LruCache<Integer, Bitmap> POSTER_CACHE = new LruCache<Integer, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(Integer key, Bitmap value) { return value.getByteCount(); }
    };
    ThemeScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }
    void open() {
        activity.themeScroll.setSaveEnabled(false);
        activity.themeScroll.scrollTo(0, 0);
        themeChanged = false;
        build();
        activity.showOverlay(activity.themeScroll);
    }
    private static int themePoster(int index) {
        switch (ToxiThemeSystem.normalize(index)) {
            case 0: return R.drawable.theme_poster_onepiece;
            case 1: return R.drawable.theme_poster_naruto;
            case 2: return R.drawable.theme_poster_demon;
            case 3: return R.drawable.theme_poster_dragonball;
            default: return R.drawable.theme_poster_aot;
        }
    }
    private void loadPosterAsync(ImageView target, int resourceId) {
        Bitmap cached = POSTER_CACHE.get(resourceId);
        if (cached != null && !cached.isRecycled()) { target.setImageBitmap(cached); return; }
        target.setBackground(ToxiVisualSystem.surface(16, false));
        target.setTag(resourceId);
        ARTWORK_EXECUTOR.execute(() -> {
            Bitmap bitmap = POSTER_CACHE.get(resourceId);
            if (bitmap == null || bitmap.isRecycled()) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeResource(activity.getResources(), resourceId, bounds);
                int targetPx = Math.max(320, ToxiVisualSystem.dp(220));
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx * 2) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                bitmap = BitmapFactory.decodeResource(activity.getResources(), resourceId, options);
                if (bitmap != null) POSTER_CACHE.put(resourceId, bitmap);
            }
            final Bitmap result = bitmap;
            if (result != null) target.post(() -> {
                if (target.getTag() != null && ((Integer) target.getTag()) == resourceId) {
                    target.setImageBitmap(result);
                    target.setTag(null);
                }
            });
        });
    }
    private void updateSelection(int selectedIndex) {
        if (themeTiles == null || themeStates == null) return;
        for (int i = 0; i < themeTiles.length; i++) {
            boolean active = i == selectedIndex;
            themeStates[i].setText(active ? "✓  SELECTED" : "SELECT WORLD");
            themeStates[i].setTextColor(active ? ToxiVisualSystem.accentForeground() : Color.WHITE);
            themeStates[i].setBackground(active ? ToxiVisualSystem.accentSurface(14) : ToxiVisualSystem.surface(14, false));
            themeTiles[i].setBackground(ToxiVisualSystem.surface(active ? 24 : 20, active));
            themeTiles[i].setSelected(active);
            themeTiles[i].setContentDescription(ToxiVisualSystem.THEME_NAMES[i]
                    + (active ? ". Active theme" : ". Tap to apply theme"));
        }
    }
    void build() {
        activity.themeScroll.setSaveEnabled(false);
        int restoreScrollY = Math.max(0, activity.themeScroll.getScrollY());
        activity.themeScroll.setFocusable(true);
        activity.themeScroll.setFocusableInTouchMode(true);
        activity.themeScroll.removeAllViews();
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setFocusable(false);
        panel.setFocusableInTouchMode(false);
        panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        int backTarget = ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48));
        back.setContentDescription("Back");
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setFocusable(true);
        back.setClickable(true);
        back.setMinimumWidth(backTarget);
        back.setMinimumHeight(backTarget);
        back.setOnClickListener(v -> {
            activity.closeOverlay();
            if (themeChanged) { themeChanged = false; activity.recolor(); }
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(backTarget, backTarget);
        backParams.gravity = Gravity.CENTER_VERTICAL;
        header.addView(back, backParams);
        TextView title = ToxiVisualSystem.text(activity, "Your worlds", 25, ToxiVisualSystem.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.leftMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));
        header.addView(title, titleLp);
        panel.addView(header);
        title.setLetterSpacing(-0.025f);
        TextView subtitle = ToxiVisualSystem.text(activity, "Choose a world. Make it yours.", 13, ToxiVisualSystem.TEXT_SECONDARY, false);
        subtitle.setLineSpacing(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), 1.04f);
        subtitle.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
        panel.addView(subtitle);
        TextView label = ToxiVisualSystem.text(activity, "THEME COLLECTION  ·  05 WORLDS", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        label.setLetterSpacing(0.14f);
        label.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        panel.addView(label);
        LinearLayout cards = new LinearLayout(activity);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        cards.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        themeTiles = new FrameLayout[ToxiVisualSystem.THEME_NAMES.length];
        themeStates = new TextView[ToxiVisualSystem.THEME_NAMES.length];
        for (int i = 0; i < ToxiVisualSystem.THEME_NAMES.length; i++) {
            final int themeIndex = i;
            boolean selected = i == ToxiVisualSystem.THEME_INDEX;
            FrameLayout tile = new FrameLayout(activity);
            tile.setClipChildren(true);
            tile.setClipToPadding(true);
            tile.setBackground(ToxiVisualSystem.surface(24, true));
            ToxiVisualSystem.clipRound(tile, 24f);
            ImageView poster = new ImageView(activity);
            loadPosterAsync(poster, themePoster(i));
            poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
            poster.setContentDescription(ToxiVisualSystem.THEME_NAMES[i] + " theme artwork");
            tile.addView(poster, new FrameLayout.LayoutParams(ToxiVisualSystem.dp(154), -1, Gravity.END));
            View wash = new View(activity);
            android.graphics.drawable.GradientDrawable washGradient =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[]{Color.rgb(8, 12, 19), Color.rgb(8, 12, 19),
                                    Color.argb(228, 8, 12, 19), Color.argb(0, 8, 12, 19)});
            washGradient.setCornerRadii(ToxiVisualSystem.profileCornerRadii(24f));
            wash.setBackground(washGradient);
            tile.addView(wash, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setPadding(ToxiVisualSystem.dp(17), ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(150), ToxiVisualSystem.dp(14));
            TextView ordinal = ToxiVisualSystem.text(activity, String.format(java.util.Locale.US, "WORLD  0%d", i + 1), 9.5f, ToxiThemeSystem.ACCENT_ENDS[i], true);
            ordinal.setLetterSpacing(0.16f);
            content.addView(ordinal);
            TextView themeName = ToxiVisualSystem.text(activity, ToxiVisualSystem.THEME_NAMES[i], 19, Color.WHITE, true);
            themeName.setMaxLines(2);
            themeName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
            nameParams.topMargin = ToxiVisualSystem.dp(5);
            content.addView(themeName, nameParams);
            TextView description = ToxiVisualSystem.text(activity, ToxiVisualSystem.themeDescription(i), 11.5f, Color.argb(220, 238, 243, 248), false);
            description.setMaxLines(2);
            description.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
            descParams.topMargin = ToxiVisualSystem.dp(4);
            content.addView(description, descParams);
            TextView state = ToxiVisualSystem.text(activity, selected ? "✓  SELECTED" : "SELECT WORLD",
                    9.5f, selected ? ToxiThemeSystem.ACCENTS[i] : Color.WHITE, true);
            state.setLetterSpacing(0.07f);
            state.setGravity(Gravity.CENTER);
            state.setPadding(ToxiVisualSystem.dp(11), ToxiVisualSystem.dp(8), ToxiVisualSystem.dp(11), ToxiVisualSystem.dp(8));
            state.setBackground(selected ? ToxiVisualSystem.accentSurface(14) : ToxiVisualSystem.surface(14, false));
            LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
            stateParams.topMargin = ToxiVisualSystem.dp(10);
            content.addView(state, stateParams);
            tile.addView(content, new FrameLayout.LayoutParams(-1, -1));
            themeTiles[i] = tile;
            themeStates[i] = state;
            tile.setContentDescription(ToxiVisualSystem.THEME_NAMES[i] + ". " + ToxiVisualSystem.themeDescription(i)
                    + (selected ? ". Active theme" : ". Tap to apply"));
            tile.setFocusable(false);
            tile.setOnClickListener(v -> {
                ToxiVisualSystem.feedback(v);
                if (themeIndex == ToxiVisualSystem.THEME_INDEX) return;
                ToxiVisualSystem.applyTheme(themeIndex, true);
                updateSelection(themeIndex);
                themeChanged = true;
            });
            ToxiVisualSystem.interactive(tile);
            cards.addView(tile, activity.lpm(-1, ToxiVisualSystem.dp(164), 0, 0, 0, 10));
            tile.setAlpha(1f);
            tile.setTranslationY(0f);
        }
        panel.addView(cards, new LinearLayout.LayoutParams(-1, -2));
        activity.themeScroll.setFillViewport(false);
        activity.themeScroll.setClipToPadding(false);
        activity.themeScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        activity.themeScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        panel.setAlpha(1f);
        activity.themeScroll.post(() -> {
            if (activity.themeScroll.getChildCount() > 0) {
                int range = Math.max(0, activity.themeScroll.getChildAt(0).getHeight()
                        - activity.themeScroll.getHeight() + activity.themeScroll.getPaddingBottom());
                activity.themeScroll.scrollTo(0, Math.min(restoreScrollY, range));
            }
        });
    }
}