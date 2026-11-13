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
        D = context.getResources().getDisplayMetrics().density;
        HIGH_CONTRAST = Settings.Secure.getInt(context.getContentResolver(), "high_text_contrast_enabled", 0) == 1;
        if (PREFERENCES == null) PREFERENCES = context.getApplicationContext().getSharedPreferences("lg_settings", Context.MODE_PRIVATE);
        applyTheme(PREFERENCES.getInt("theme", 0), false);
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
            default: return ToxiThemeEngine.profile().typeface;
        }
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
        if (save && PREFERENCES != null) PREFERENCES.edit().putInt("theme", THEME_INDEX).apply();
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
        TextView title = dialog.findViewById(android.R.id.alertTitle);
        if (title != null) {
            title.setTextColor(TEXT_PRIMARY);
            title.setTypeface(Typeface.create(fontFamily(), Typeface.BOLD));
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
            button.setTextColor(ACCENT);
            button.setTypeface(Typeface.create(fontFamily(), Typeface.BOLD));
            button.setAllCaps(false);
            button.setMinHeight(dp(ToxiThemeEngine.spacing(44)));
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
                for (int i = 0; i < list.getChildCount(); i++) styleDialogTree(list.getChildAt(i));
            });
        }
        View content = dialog.findViewById(android.R.id.content);
        if (content != null) styleDialogTree(content);
    }

    private static void styleDialogTree(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(view instanceof Button ? ACCENT
                    : (view.getId() == android.R.id.message ? TEXT_SECONDARY : TEXT_PRIMARY));
            text.setTypeface(Typeface.create(fontFamily(), text.getTypeface() != null
                    && text.getTypeface().isBold() ? Typeface.BOLD : Typeface.NORMAL));
        }
        if (view instanceof EditText) {
            EditText input = (EditText) view;
            input.setTextColor(TEXT_PRIMARY);
            input.setHintTextColor(TEXT_SECONDARY);
            if (Build.VERSION.SDK_INT >= 21) input.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleDialogTree(group.getChildAt(i));
        }
    }

    /** High-contrast foreground for controls rendered on the active accent material. */
    static int accentForeground() {
        return Color.luminance(ACCENT) > 0.42f ? 0xFF101820 : Color.WHITE;
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

    /** Prism liquid material: layered tint, a specular crown and a theme-aware glass edge. */
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
        base.setCornerRadius(dp(ToxiThemeEngine.radius(radiusDp)));
        ToxiThemeEngine.Profile profile = ToxiThemeEngine.profile();
        int titaniumEdge = blend(EDGE_STROKE, CHROME,
                LIGHT_MODE ? 0.30f : 0.30f + (0.38f * profile.edgeStrength));
        base.setStroke(Math.max(1, dp(ToxiThemeEngine.spacing(0.9f))), Color.argb(ToxiThemeEngine.edgeAlpha(220),
                Color.red(titaniumEdge), Color.green(titaniumEdge), Color.blue(titaniumEdge)));

        int crownAlpha = ToxiThemeEngine.highlightAlpha(LIGHT_MODE ? 42 : (raised ? 58 : 44));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(crownAlpha, 255, 255, 255),
                        Color.argb(LIGHT_MODE ? 5 : 9, 255, 255, 255), Color.TRANSPARENT});
        glass.setCornerRadius(dp(ToxiThemeEngine.radius(radiusDp)));
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
        accent.setCornerRadius(dp(ToxiThemeEngine.radius(radiusDp)));
        accent.setStroke(Math.max(1, dp(ToxiThemeEngine.spacing(0.9f))), Color.argb(ToxiThemeEngine.edgeAlpha(LIGHT_MODE ? 140 : 175),
                Color.red(CHROME), Color.green(CHROME), Color.blue(CHROME)));
        return accent;
    }

    static TextView text(Context context, CharSequence value, float sp, int color, boolean strong) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(fontFamily(), strong ? Typeface.BOLD : Typeface.NORMAL));
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
        view.setMinimumWidth(dp(ToxiThemeEngine.spacing(48)));
        view.setMinimumHeight(dp(ToxiThemeEngine.spacing(48)));
        view.setContentDescription(ToxiIcons.accessibilityLabel(symbol));
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setBackground(surface(14, true));
        view.setClickable(true);
        view.setFocusable(true);
        interactive(view);
        return view;
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
        ToxiPowerTouchEngine.attach(view, THEME_INDEX);
    }

    static void clipRound(View view, final float radiusDp) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View target, Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), dp(ToxiThemeEngine.radius(radiusDp)));
            }
        });
        view.setClipToOutline(true);
    }


    /** Staggered entrance for screen-level panels; disabled when the user requests reduced motion. */
    static void revealChildren(ViewGroup group) {
        if (group == null || booleanPreference("reduce_motion", false)) return;
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
        if (view == null || booleanPreference("reduce_motion", false)) return;
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
