package com.music.echo.offline.files;

import android.content.Context;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Fresh ToxiFiles visual language: architectural surfaces, luminous signal accents and a new type scale. */
final class ToxiVisualSystem {
    static float D = 1f;
    static SharedPreferences PREFERENCES;
    static int THEME_INDEX = 0;
    static int ACCENT = ToxiThemeSystem.ACCENTS[0];
    static int CHROME = ToxiThemeSystem.CHROME[0];
    static int TEXT_PRIMARY = 0xFFF1F5F8;
    static int TEXT_SECONDARY = ToxiThemeSystem.MUTED[0];
    static int CANVAS = ToxiThemeSystem.CANVAS[0];
    static int SURFACE_COLOR = ToxiThemeSystem.SURFACES[0];
    static int RAISED_SURFACE = ToxiThemeSystem.RAISED[0];
    static int BLOOM_PRIMARY = 0x263C4A59, BLOOM_SECONDARY = 0x183C4A59, BLOOM_TERTIARY = 0x0C3C4A59;
    static int TOP_INSET = 0, BOTTOM_INSET = 0, IME_INSET = 0;
    static int SURFACE_RED = 20, SURFACE_GREEN = 27, SURFACE_BLUE = 36;
    static int HIGHLIGHT_TOP = 0x1CFFFFFF, HIGHLIGHT_BOTTOM = 0x00FFFFFF, EDGE_STROKE = ToxiThemeSystem.EDGES[0];
    static boolean LIGHT_MODE = false, HIGH_CONTRAST = false;
    static final String[] THEME_NAMES = ToxiThemeSystem.NAMES;
    static final int[] THEME_COLORS = ToxiThemeSystem.ACCENTS;
    static final String[] THEME_DESCRIPTIONS = ToxiThemeSystem.DESCRIPTIONS;
    static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    private ToxiVisualSystem() { }

    static void initialize(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (PREFERENCES == null) PREFERENCES = context.getApplicationContext().getSharedPreferences("lg_settings", Context.MODE_PRIVATE);
        refresh(context);
    }

    /**
     * Re-read persisted appearance preferences and rebuild the active palette.
     * @return true when theme palette, light appearance, or accessibility contrast changed.
     */
    static boolean refresh(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        D = context.getResources().getDisplayMetrics().density;
        if (PREFERENCES == null) PREFERENCES = context.getApplicationContext().getSharedPreferences("lg_settings", Context.MODE_PRIVATE);
        boolean highContrast = Settings.Secure.getInt(context.getContentResolver(), "high_text_contrast_enabled", 0) == 1;
        int requestedIndex = ToxiThemePersistence.restore(themePreferenceStore());
        boolean light = PREFERENCES.getBoolean("light", false);
        boolean changed = requestedIndex != THEME_INDEX || light != LIGHT_MODE || highContrast != HIGH_CONTRAST;
        HIGH_CONTRAST = highContrast;
        applyTheme(requestedIndex, false);
        return changed;
    }

    /**
     * Apply icon contrast and optional opaque system-bar surfaces to an Activity window.
     * edgeToEdge keeps system bars transparent so the themed app canvas is visible behind them.
     * Existing immersive/fullscreen flags are preserved.
     */
    static void applyWindowChrome(Window window, boolean edgeToEdge) {
        if (window == null) return;
        if (edgeToEdge) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else {
            window.setStatusBarColor(CANVAS);
            window.setNavigationBarColor(CANVAS);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.setNavigationBarDividerColor(EDGE_STROKE);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT < 23) return;
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) lightFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(LIGHT_MODE ? (flags | lightFlags) : (flags & ~lightFlags));
    }

    static int dp(float value) { return Math.round(value * D); }

    static float textSizePx(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }

    static String fontFamily() {
        int preference = PREFERENCES == null ? 0 : PREFERENCES.getInt("font_family", 0);
        switch (preference) {
            case 1: return "sans-serif-rounded";
            case 2: return "serif";
            case 3: return "monospace";
            default: return ToxiThemeEngine.bodyTypeface();
        }
    }

    /** Display typography follows the active identity unless the user chose a global font. */
    static String headingFontFamily() {
        int preference = PREFERENCES == null ? 0 : PREFERENCES.getInt("font_family", 0);
        return preference == 0 ? ToxiThemeEngine.headingTypeface() : fontFamily();
    }

    /** Technical metadata uses a monospaced face without overriding explicit font preferences. */
    static String monoFontFamily() {
        int preference = PREFERENCES == null ? 0 : PREFERENCES.getInt("font_family", 0);
        return preference == 0 ? ToxiThemeEngine.monoTypeface() : fontFamily();
    }

    private static ToxiThemePersistence.Store themePreferenceStore() {
        return new ToxiThemePersistence.Store() {
            @Override public int readTheme() { return PREFERENCES.getInt("theme", 0); }
            @Override public void writeTheme(int index) {
                PREFERENCES.edit().putInt("theme", index).apply();
            }
        };
    }

    static void applyTheme(int index, boolean save) {
        THEME_INDEX = ToxiThemeSystem.normalize(index);
        ToxiThemeEngine.activate(THEME_INDEX);
        LIGHT_MODE = PREFERENCES != null && PREFERENCES.getBoolean("light", false);
        ACCENT = ToxiThemeSystem.ACCENTS[THEME_INDEX];
        CHROME = ToxiThemeSystem.CHROME[THEME_INDEX];
        CANVAS = LIGHT_MODE ? ToxiThemeSystem.LIGHT_CANVAS[THEME_INDEX] : ToxiThemeSystem.CANVAS[THEME_INDEX];
        SURFACE_COLOR = LIGHT_MODE ? ToxiThemeSystem.LIGHT_SURFACES[THEME_INDEX] : ToxiThemeSystem.SURFACES[THEME_INDEX];
        RAISED_SURFACE = LIGHT_MODE ? ToxiThemeSystem.LIGHT_RAISED[THEME_INDEX] : ToxiThemeSystem.RAISED[THEME_INDEX];
        TEXT_PRIMARY = LIGHT_MODE ? 0xFF182331 : 0xFFF1F5F8;
        TEXT_SECONDARY = HIGH_CONTRAST ? 0xFFE8EEF5 : ToxiThemeSystem.MUTED[THEME_INDEX];
        if (LIGHT_MODE) TEXT_SECONDARY = HIGH_CONTRAST ? 0xFF263548 : ToxiThemeSystem.LIGHT_MUTED[THEME_INDEX];
        EDGE_STROKE = LIGHT_MODE ? ToxiThemeSystem.LIGHT_EDGES[THEME_INDEX] : ToxiThemeSystem.EDGES[THEME_INDEX];
        BLOOM_PRIMARY = Color.argb(LIGHT_MODE ? 20 : 38, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT));
        BLOOM_SECONDARY = Color.argb(LIGHT_MODE ? 12 : 24, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT));
        BLOOM_TERTIARY = Color.argb(LIGHT_MODE ? 7 : 12, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT));
        if (save && PREFERENCES != null) ToxiThemePersistence.save(themePreferenceStore(), THEME_INDEX);
    }

    /** All native prompts use the active theme material and typography. */
    static AlertDialog.Builder dialogBuilder(Context context) {
        return new ThemedDialogBuilder(context);
    }

    private static final class ThemedDialogBuilder extends AlertDialog.Builder {
        ThemedDialogBuilder(Context context) { super(context); }

        @Override public AlertDialog show() {
            AlertDialog dialog = super.show();
            styleDialog(dialog);
            return dialog;
        }

        @Override public AlertDialog create() {
            AlertDialog dialog = super.create();
            dialog.setOnShowListener(ignored -> styleDialog(dialog));
            return dialog;
        }
    }

    static void styleDialog(AlertDialog dialog) {
        if (dialog == null) return;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(surface(24, true));
            dialog.getWindow().setDimAmount(LIGHT_MODE ? 0.28f : 0.58f);
        }
        int titleId = dialog.getContext().getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId == 0) titleId = android.R.id.title;
        TextView title = dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(TEXT_PRIMARY);
            title.setTypeface(Typeface.create(headingFontFamily(), Typeface.BOLD));
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(TEXT_SECONDARY);
            message.setTypeface(Typeface.create(fontFamily(), Typeface.NORMAL));
            message.setLineSpacing(dp(ToxiThemeEngine.spacing(3)), 1f);
        }
        int[] buttonIds = {android.R.id.button1, android.R.id.button2, android.R.id.button3};
        for (int id : buttonIds) {
            Button button = dialog.findViewById(id);
            if (button == null) continue;
            button.setTextColor(signalTextColor());
            button.setTypeface(Typeface.create(fontFamily(), Typeface.BOLD));
            button.setAllCaps(false);
            button.setMinHeight(dp(ToxiThemeEngine.touchTarget(44)));
        }
        ListView list = dialog.getListView();
        if (list != null) {
            list.setBackgroundColor(Color.TRANSPARENT);
            list.setDivider(new android.graphics.drawable.ColorDrawable(EDGE_STROKE));
            list.setDividerHeight(Math.max(1, dp(ToxiThemeEngine.spacing(1))));
            GradientDrawable selector = new GradientDrawable();
            selector.setColor(Color.argb(32, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT)));
            selector.setCornerRadius(dp(ToxiThemeEngine.radius(10)));
            list.setSelector(selector);
            list.post(() -> {
                for (int i = 0; i < list.getChildCount(); i++) styleDialogTree(list.getChildAt(i), true);
            });
        }
        View content = dialog.findViewById(android.R.id.content);
        if (content != null) styleDialogTree(content, false);
    }

    private static void styleDialogTree(View view, boolean styleListText) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (view instanceof Button) text.setTextColor(signalTextColor());
            else if (view.getId() == android.R.id.message) text.setTextColor(TEXT_SECONDARY);
            else if (styleListText) text.setTextColor(TEXT_PRIMARY);
            boolean bold = text.getTypeface() != null && text.getTypeface().isBold();
            text.setTypeface(Typeface.create(bold ? headingFontFamily() : fontFamily(), bold ? Typeface.BOLD : Typeface.NORMAL));
        }
        if (view instanceof EditText) {
            EditText input = (EditText) view;
            input.setTextColor(TEXT_PRIMARY);
            input.setHintTextColor(TEXT_SECONDARY);
            if (Build.VERSION.SDK_INT >= 21) input.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleDialogTree(group.getChildAt(i), styleListText);
            }
        }
    }

    /** Accent-colored text uses a darker identity tint in light mode for readable contrast. */
    static int signalTextColor() {
        return LIGHT_MODE ? ToxiThemeSystem.LIGHT_MUTED[THEME_INDEX] : ACCENT;
    }

    /** High-contrast foreground for controls rendered on the active accent material. */
    static int accentForeground() {
        float luminance = Color.luminance(ACCENT);
        float darkContrast = (luminance + 0.05f) / 0.05f;
        float lightContrast = 1.05f / (luminance + 0.05f);
        return darkContrast >= lightContrast ? 0xFF101820 : Color.WHITE;
    }

    static int accentForeground(int alpha) {
        int color = accentForeground();
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    static String themeDescription(int index) { return ToxiThemeSystem.description(index); }
    static boolean booleanPreference(String key, boolean fallback) { return PREFERENCES != null && PREFERENCES.getBoolean(key, fallback); }
    static void setBooleanPreference(String key, boolean value) { if (PREFERENCES != null) PREFERENCES.edit().putBoolean(key, value).apply(); }

    private static GradientDrawable.Orientation materialOrientation() {
        switch (ToxiThemeEngine.profile().material) {
            case CHAKRA_FORGED: return GradientDrawable.Orientation.LEFT_RIGHT;
            case BREATHING_STEEL: return GradientDrawable.Orientation.BL_TR;
            case AURA_ALLOY: return GradientDrawable.Orientation.TOP_BOTTOM;
            case MILITARY_TITANIUM: return GradientDrawable.Orientation.RIGHT_LEFT;
            case OCEAN_TITANIUM:
            default: return GradientDrawable.Orientation.TL_BR;
        }
    }

    /** One profile-derived corner model is shared by drawable surfaces and clipped views. */
    static float[] profileCornerRadii(float radiusDp) {
        float radius = dp(ToxiThemeEngine.radius(radiusDp));
        float topLeft = radius, topRight = radius, bottomRight = radius, bottomLeft = radius;
        switch (ToxiThemeEngine.profile().geometry) {
            case VOYAGE:
                topRight = bottomLeft = radius * 0.72f;
                break;
            case SHINOBI:
                topLeft = bottomRight = radius * 0.48f;
                break;
            case BREATHING:
                topLeft = topRight = radius * 0.68f;
                bottomRight = bottomLeft = radius * 1.16f;
                break;
            case ULTRA:
                topLeft = topRight = bottomRight = bottomLeft = radius * 1.28f;
                break;
            case FORTRESS:
                topLeft = topRight = bottomRight = bottomLeft = Math.max(dp(3), radius * 0.38f);
                break;
        }
        return new float[]{topLeft, topLeft, topRight, topRight,
                bottomRight, bottomRight, bottomLeft, bottomLeft};
    }

    /** Apply the profile's silhouette rather than reducing every identity to one rounded rectangle. */
    private static void applyGeometry(GradientDrawable drawable, float radiusDp) {
        drawable.setCornerRadii(profileCornerRadii(radiusDp));
    }

    /** Prism liquid material: layered tint, a specular crown and a theme-aware glass edge. */
    static Drawable auroraBackground() { return new ToxiAuroraDrawable(); }

    static Drawable surface(float radiusDp, boolean raised) {
        int fill = raised ? RAISED_SURFACE : SURFACE_COLOR;
        int tintAlpha = LIGHT_MODE ? 14 : (raised ? 48 : 36);
        int accentTint = Color.argb(tintAlpha, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT));
        int lowerTint = Color.argb(LIGHT_MODE ? 8 : 20, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT));
        int top = Color.rgb(Math.min(255, Color.red(fill) + (LIGHT_MODE ? 7 : 17)),
                Math.min(255, Color.green(fill) + (LIGHT_MODE ? 8 : 20)),
                Math.min(255, Color.blue(fill) + (LIGHT_MODE ? 9 : 23)));
        GradientDrawable base = new GradientDrawable(materialOrientation(),
                new int[]{top, blend(fill, accentTint), fill, blend(CANVAS, lowerTint)});
        applyGeometry(base, radiusDp);
        ToxiThemeEngine.Profile profile = ToxiThemeEngine.profile();
        int titaniumEdge = blend(EDGE_STROKE, CHROME,
                LIGHT_MODE ? 0.30f : 0.30f + (0.38f * profile.edgeStrength));
        base.setStroke(Math.max(1, dp(ToxiThemeEngine.spacing(0.9f))), Color.argb(ToxiThemeEngine.edgeAlpha(220),
                Color.red(titaniumEdge), Color.green(titaniumEdge), Color.blue(titaniumEdge)));

        int crownAlpha = ToxiThemeEngine.highlightAlpha(LIGHT_MODE ? 42 : (raised ? 58 : 44));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(crownAlpha, 255, 255, 255),
                        Color.argb(LIGHT_MODE ? 5 : 9, 255, 255, 255), Color.TRANSPARENT});
        applyGeometry(glass, radiusDp);
        glass.setStroke(Math.max(1, dp(ToxiThemeEngine.spacing(0.6f))), Color.argb(ToxiThemeEngine.edgeAlpha(LIGHT_MODE ? 68 : 104),
                Color.red(CHROME), Color.green(CHROME), Color.blue(CHROME)));
        android.graphics.drawable.LayerDrawable layered =
                new android.graphics.drawable.LayerDrawable(new Drawable[]{base, glass});
        int inset = Math.max(1, dp(ToxiThemeEngine.spacing(1)));
        layered.setLayerInset(1, inset, inset, inset, inset);
        return layered;
    }

    private static int blend(int color, int overlay) {
        float alpha = Color.alpha(overlay) / 255f;
        return Color.rgb(
                Math.round(Color.red(color) * (1f - alpha) + Color.red(overlay) * alpha),
                Math.round(Color.green(color) * (1f - alpha) + Color.green(overlay) * alpha),
                Math.round(Color.blue(color) * (1f - alpha) + Color.blue(overlay) * alpha));
    }

    private static int blend(int color, int overlay, float amount) {
        float alpha = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(color) * (1f - alpha) + Color.red(overlay) * alpha),
                Math.round(Color.green(color) * (1f - alpha) + Color.green(overlay) * alpha),
                Math.round(Color.blue(color) * (1f - alpha) + Color.blue(overlay) * alpha));
    }

    static Drawable accentSurface(float radiusDp) {
        GradientDrawable accent = new GradientDrawable(materialOrientation(),
                new int[]{ToxiThemeSystem.ACCENTS[THEME_INDEX], ToxiThemeSystem.ACCENT_ENDS[THEME_INDEX]});
        applyGeometry(accent, radiusDp);
        accent.setStroke(Math.max(1, dp(ToxiThemeEngine.spacing(0.9f))), Color.argb(ToxiThemeEngine.edgeAlpha(LIGHT_MODE ? 140 : 175),
                Color.red(CHROME), Color.green(CHROME), Color.blue(CHROME)));
        return accent;
    }

    static TextView text(Context context, CharSequence value, float sp, int color, boolean strong) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color == ACCENT ? signalTextColor() : color);
        view.setTypeface(Typeface.create(strong ? headingFontFamily() : fontFamily(), strong ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(ToxiThemeEngine.spacing(3)), 1f);
        view.setHorizontallyScrolling(false);
        if (Build.VERSION.SDK_INT >= 23) view.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        if (Build.VERSION.SDK_INT >= 29) view.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
        return view;
    }

    static ImageView iconButton(Context context, int symbol, int color) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(ToxiIcons.symbol(symbol, color));
        view.setPadding(dp(ToxiThemeEngine.spacing(13)), dp(ToxiThemeEngine.spacing(13)), dp(ToxiThemeEngine.spacing(13)), dp(ToxiThemeEngine.spacing(13)));
        view.setMinimumWidth(dp(ToxiThemeEngine.touchTarget(48)));
        view.setMinimumHeight(dp(ToxiThemeEngine.touchTarget(48)));
        view.setContentDescription(ToxiIcons.accessibilityLabel(symbol));
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setBackground(surface(14, true));
        view.setClickable(true);
        view.setFocusable(true);
        interactive(view);
        return view;
    }

    static void styleSeekBar(android.widget.SeekBar seekBar) {
        if (seekBar == null || Build.VERSION.SDK_INT < 21) return;
        seekBar.setProgressTintList(ColorStateList.valueOf(signalTextColor()));
        seekBar.setThumbTintList(ColorStateList.valueOf(signalTextColor()));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(EDGE_STROKE));
        seekBar.setSplitTrack(false);
    }

    static void interactive(View view) {
        view.setClickable(true);
        view.setFocusable(true);
        if (Build.VERSION.SDK_INT >= 23) {
            int rippleColor = Color.argb(48, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT));
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadius(dp(ToxiThemeEngine.radius(14)));
            view.setForeground(new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask));
        }
        ToxiPowerTouchEngine.attach(view);
    }

    static void clipRound(View view, final float radiusDp) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View target, Outline outline) {
                int width = target.getWidth();
                int height = target.getHeight();
                if (width <= 0 || height <= 0) {
                    outline.setEmpty();
                    return;
                }
                float[] radii = profileCornerRadii(radiusDp);
                float maxRadius = Math.max(Math.max(radii[0], radii[2]),
                        Math.max(radii[4], radii[6]));
                float limit = Math.min(width, height) * 0.5f;
                if (maxRadius > limit) {
                    float scale = limit / maxRadius;
                    for (int i = 0; i < radii.length; i++) radii[i] *= scale;
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    android.graphics.Path silhouette = new android.graphics.Path();
                    silhouette.addRoundRect(new android.graphics.RectF(0, 0, width, height),
                            radii, android.graphics.Path.Direction.CW);
                    outline.setConvexPath(silhouette);
                } else {
                    outline.setRoundRect(0, 0, width, height,
                            dp(ToxiThemeEngine.radius(radiusDp)));
                }
            }
        });
        view.setClipToOutline(true);
    }


    static boolean motionAllowed(Context context) {
        if (booleanPreference("reduce_motion", false)) return false;
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (RuntimeException unavailable) {
            return true;
        }
    }

    /** Staggered entrance for screen-level panels; follows both app and system motion settings. */
    static void revealChildren(ViewGroup group) {
        if (group == null || !motionAllowed(group.getContext())) return;
        group.post(() -> {
            if (!group.isAttachedToWindow()) return;
            int visibleIndex = 0;
            for (int i = 0; i < group.getChildCount(); i++) {
                final View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                final long delay = Math.min(visibleIndex++, 8) * ToxiThemeEngine.motionDuration(28L);
                child.animate().cancel();
                child.setAlpha(0f);
                child.setTranslationY(dp(ToxiThemeEngine.motionDistance(9f)));
                child.animate().alpha(1f).translationY(0f).setStartDelay(delay)
                        .setDuration(ToxiThemeEngine.motionDuration(210L))
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }
        });
    }

    /** Restrained spring response for touch targets, with no animation under reduced motion. */
    static void touchSpring(View view) {
        if (view == null || !motionAllowed(view.getContext())) return;
        view.animate().cancel();
        float pressScale = 1f - (0.025f * ToxiThemeEngine.profile().motionScale);
        view.setScaleX(pressScale);
        view.setScaleY(pressScale);
        view.animate().scaleX(1f).scaleY(1f).setDuration(ToxiThemeEngine.motionDuration(180L))
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.15f)).start();
    }

    static void feedback(View view) {
        if (booleanPreference("haptic", true)) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    static void toast(Context context, String message) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576L) return (bytes / 1024) + " KB";
        if (bytes < 1073741824L) return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
    }

    static String formatDate(long time) { return DATE_FORMAT.format(new Date(time)); }
}
