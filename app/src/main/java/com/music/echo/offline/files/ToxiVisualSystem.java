package com.music.echo.offline.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
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
    static int TEXT_PRIMARY = 0xFFF1F5F8;
    static int TEXT_SECONDARY = ToxiThemeSystem.MUTED[0];
    static int CANVAS = ToxiThemeSystem.CANVAS[0];
    static int BLOOM_PRIMARY = 0x263C4A59, BLOOM_SECONDARY = 0x183C4A59, BLOOM_TERTIARY = 0x0C3C4A59;
    static int TOP_INSET = 0, BOTTOM_INSET = 0;
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
        if (PREFERENCES == null) return "sans-serif";
        switch (PREFERENCES.getInt("font_family", 0)) {
            case 1: return "sans-serif-rounded";
            case 2: return "serif";
            case 3: return "monospace";
            default: return "sans-serif";
        }
    }

    static void applyTheme(int index, boolean save) {
        THEME_INDEX = ToxiThemeSystem.normalize(index);
        ACCENT = ToxiThemeSystem.ACCENTS[THEME_INDEX];
        CANVAS = ToxiThemeSystem.CANVAS[THEME_INDEX];
        TEXT_SECONDARY = ToxiThemeSystem.MUTED[THEME_INDEX];
        EDGE_STROKE = ToxiThemeSystem.EDGES[THEME_INDEX];
        if (save && PREFERENCES != null) PREFERENCES.edit().putInt("theme", THEME_INDEX).apply();
    }

    static String themeDescription(int index) { return ToxiThemeSystem.description(index); }
    static boolean booleanPreference(String key, boolean fallback) { return PREFERENCES != null && PREFERENCES.getBoolean(key, fallback); }
    static void setBooleanPreference(String key, boolean value) { if (PREFERENCES != null) PREFERENCES.edit().putBoolean(key, value).apply(); }

    /** Matte, precision-cut panel with a theme-owned edge and restrained top reflection. */
    static Drawable surface(float radiusDp, boolean raised) {
        int fill = raised ? ToxiThemeSystem.RAISED[THEME_INDEX] : ToxiThemeSystem.SURFACES[THEME_INDEX];
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(Math.min(255, Color.red(fill) + 8), Math.min(255, Color.green(fill) + 8), Math.min(255, Color.blue(fill) + 8)), fill});
        panel.setCornerRadius(dp(radiusDp));
        panel.setStroke(Math.max(1, dp(0.8f)), EDGE_STROKE);
        return panel;
    }

    static Drawable accentSurface(float radiusDp) {
        GradientDrawable accent = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{ToxiThemeSystem.ACCENTS[THEME_INDEX], ToxiThemeSystem.ACCENT_ENDS[THEME_INDEX]});
        accent.setCornerRadius(dp(radiusDp));
        accent.setStroke(Math.max(1, dp(0.8f)), Color.argb(145, 255, 255, 255));
        return accent;
    }

    static TextView text(Context context, CharSequence value, float sp, int color, boolean strong) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(fontFamily(), strong ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(3), 1f);
        view.setHorizontallyScrolling(false);
        if (Build.VERSION.SDK_INT >= 23) view.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        if (Build.VERSION.SDK_INT >= 29) view.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
        return view;
    }

    static ImageView iconButton(Context context, int symbol, int color) {
        ImageView view = new ImageView(context);
        view.setImageDrawable(ToxiIcons.symbol(symbol, color));
        view.setPadding(dp(13), dp(13), dp(13), dp(13));
        view.setMinimumWidth(dp(48));
        view.setMinimumHeight(dp(48));
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
            mask.setCornerRadius(dp(14));
            view.setForeground(new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask));
        }
    }

    static void clipRound(View view, final float radiusDp) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View target, Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), dp(radiusDp));
            }
        });
        view.setClipToOutline(true);
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
