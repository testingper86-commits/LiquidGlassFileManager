package com.music.echo.offline.files;

import com.music.echo.offline.R;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
    /** Local card glass: clean specular top edge and fine inset perimeter without global overdraw. */
    private static android.graphics.drawable.Drawable glassCardBackground() {
        android.graphics.drawable.Drawable material = ToxiVisualSystem.surface(24, true);
        GradientDrawable specular = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x48FFFFFF, 0x16FFFFFF, 0x04FFFFFF, 0x00FFFFFF});
        specular.setCornerRadii(ToxiVisualSystem.profileCornerRadii(24f));
        specular.setStroke(Math.max(1, ToxiVisualSystem.dp(0.8f)), 0x70FFFFFF);
        GradientDrawable innerRim = new GradientDrawable();
        innerRim.setColor(Color.TRANSPARENT);
        innerRim.setCornerRadii(ToxiVisualSystem.profileCornerRadii(22f));
        innerRim.setStroke(Math.max(1, ToxiVisualSystem.dp(0.55f)), 0x28FFFFFF);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{material, specular, innerRim});
        int outerInset = Math.max(1, ToxiVisualSystem.dp(1));
        layers.setLayerInset(1, outerInset, outerInset, outerInset, outerInset);
        int innerInset = Math.max(2, ToxiVisualSystem.dp(3));
        layers.setLayerInset(2, innerInset, innerInset, innerInset, innerInset);
        return layers;
    }

    private void updateSelection(int selectedIndex) {
        if (themeTiles == null || themeStates == null) return;
        for (int i = 0; i < themeTiles.length; i++) {
            boolean active = i == selectedIndex;
            themeStates[i].setText(active ? "âœ“  SELECTED" : "SELECT WORLD");
            themeStates[i].setTextColor(active ? ToxiVisualSystem.accentForeground() : Color.WHITE);
            themeStates[i].setBackground(active ? ToxiVisualSystem.accentSurface(14) : ToxiVisualSystem.surface(14, false));
            themeTiles[i].setBackground(glassCardBackground());
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
        TextView label = ToxiVisualSystem.text(activity, "THEME COLLECTION  Â·  05 WORLDS", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
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
            tile.setBackground(glassCardBackground());
            ToxiVisualSystem.clipRound(tile, 24f);

            // Reserve a responsive artwork lane; never let poster pixels cover the text column.
            int cardWidth = activity.getResources().getDisplayMetrics().widthPixels
                    - ToxiVisualSystem.dp(32 + 32);
            int posterWidth = Math.max(ToxiVisualSystem.dp(104),
                    Math.min(ToxiVisualSystem.dp(140), Math.round(cardWidth * 0.37f)));
            ImageView poster = new ImageView(activity);
            poster.setScaleType(ImageView.ScaleType.FIT_CENTE);
            poster.setAdjustViewBounds(false);
            poster.setPadding(ToxiVisualSystem.dp(4), ToxiVisualSystem.dp(8),
                    ToxiVisualSystem.dp(4), ToxiVisualSystem.dp(8));
            loadPosterAsync(poster, themePoster(i));
            poster.setContentDescription(ToxiVisualSystem.THEME_NAMES[i] + " theme artwork");
            FrameLayout.LayoutParams posterParams = new FrameLayout.LayoutParams(
                    posterWidth, -1, Gravity.END | Gravity.CENTER_VERTICAL);
            tile.addView(poster, posterParams);
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
            int textRightInset = posterWidth + ToxiVisualSystem.dp(8);
            content.setPadding(ToxiVisualSystem.dp(17), ToxiVisualSystem.dp(14),
                    textRightInset, ToxiVisualSystem.dp(14));
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
            content.addView(Y\ØÜš\[Û‹\ØÔ\˜[\ÊNÂˆ^šY]ÈÝ]HHÞUš\ÝX[Þ\Ý[K^
XÝ]š]KÙ[XÝYÈ¸§$ÈÑSPÕQˆˆ”ÑSPÕÓÔ“‹ˆKY‹Ù[XÝYÈÞU[YTÞ\Ý[KPÐÑS•ÖÚWHˆÛÛÜ‹•ÒUKYJNÂˆÝ]KœÙ]]\”ÜXÚ[™ÊŒÙŠNÂˆÝ]KœÙ]Ü˜]š]JÜ˜]š]KÑS•TŠNÂˆÝ]KœÙ]Y[™ÊÞUš\ÝX[Þ\Ý[K™
LJKÞUš\ÝX[Þ\Ý[K™

KÞUš\ÝX[Þ\Ý[K™
LJKÞUš\ÝX[Þ\Ý[K™

JNÂˆÝ]KœÙ]˜XÚÙÜ›Ý[™
Ù[XÝYÈÞUš\ÝX[Þ\Ý[K˜XØÙ[Ý\™˜XÙJM
HˆÞUš\ÝX[Þ\Ý[KœÝ\™˜XÙJM˜[ÙJJNÂˆ[™X\“^[Ý]“^[Ý]\˜[\ÈÝ]T\˜[\ÈH™]È[™X\“^[Ý]“^[Ý]\˜[\ÊL‹LŠNÂˆÝ]T\˜[\ËÜX\™Ú[ˆHÞUš\ÝX[Þ\Ý[K™
L
NÂˆÛÛ[˜YšY]ÊÝ]KÝ]T\˜[\ÊNÂˆ[K˜YšY]ÊÛÛ[™]Èœ˜[YS^[Ý]“^[Ý]\˜[\ÊLKLJJNÂˆ[YU[\ÖÚWHH[NÂˆ[YTÝ]\ÖÚWHHÝ]NÂˆ[KœÙ]ÛÛ[\ØÜš\[ÛŠÞUš\ÝX[Þ\Ý[K•SQWÓSQTÖÚWH
È‹ˆˆ
ÈÞUš\ÝX[Þ\Ý[K[YQ\ØÜš\[ÛŠJBˆ
È
Ù[XÝYÈ‹ˆXÝ]™H[YHˆˆ‹ˆ\È\HŠJNÂˆ[KœÙ]›ØÝ\ØX›J˜[ÙJNÂˆ[KœÙ]ÛÛXÚÓ\Ý[™\ŠˆOˆÂˆÞUš\ÝX[Þ\Ý[K™™YY˜XÚÊŠNÂˆYˆ
[YR[™^OHÞUš\ÝX[Þ\Ý[K•SQWÒS‘V
H™]\›ŽÂˆÞUš\ÝX[Þ\Ý[K˜\U[YJ[YR[™^YJNÂˆ\]TÙ[XÝ[ÛŠ[YR[™^
NÂˆ[YPÚ[™ÙYHYNÂˆJNÂˆÞUš\ÝX[Þ\Ý[Kš[\˜XÝ]™J[JNÂˆØ\™Ë˜YšY]Ê[KXÝ]š]K›JLKÞUš\ÝX[Þ\Ý[K™
M
KL
JNÂˆ[KœÙ][JYŠNÂˆ[KœÙ]˜[œÛ][Û–JŠNÂˆBˆ[™[˜YšY]ÊØ\™Ë™]È[™X\“^[Ý]“^[Ý]\˜[\ÊLKLŠJNÂˆXÝ]š]K[YTØÜ›ÛœÙ]š[šY]ÜÜ
˜[ÙJNÂˆXÝ]š]K[YTØÜ›ÛœÙ]Û\ÔY[™Ê˜[ÙJNÂˆXÝ]š]K[YTØÜ›ÛœÙ]\ØÙ[™[›ØÝ\ØXš[]J[™›ÚYšY]Ë•šY]ÑÜ›Ý\‘“ÐÕT×ÐQ•T—ÑTÐÑS‘S•ÊNÂˆXÝ]š]K[YTØÜ›Û˜YšY]Ê[™[™]ÈØÜ›ÛšY]Ë“^[Ý]\˜[\ÊLKLŠJNÂˆ[™[œÙ][JYŠNÂˆXÝ]š]K[YTØÜ›ÛœÜÝ


HOˆÂˆYˆ
XÝ]š]K[YTØÜ›Û™Ù]Ú[ÛÝ[

Hˆ
HÂˆ[˜[™ÙHHX]›X^
XÝ]š]K[YTØÜ›Û™Ù]Ú[]

K™Ù]™ZYÚ

BˆHXÝ]š]K[YTØÜ›Û™Ù]ZYÚ

H
ÈXÝ]š]K[YTØÜ›Û™Ù]Y[™Ð›ÝÛJ
JNÂˆXÝ]š]K[YTØÜ›ÛœØÜ›ÛÊX]›Z[Š™\ÝÜ™TØÜ›ÛK˜[™ÙJJNÂˆBˆJNÂˆBŸ