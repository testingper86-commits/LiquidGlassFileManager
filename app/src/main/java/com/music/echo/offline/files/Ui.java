package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.animation.ValueAnimator;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.util.TypedValue;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Shared look & feel helpers: theme colours, glass drawables, small view factories. */
final class Ui {
    static float D = 3f;
    static SharedPreferences sp;
    static int theme = 0, accent = Color.WHITE, blob1 = Color.argb(24,255,255,255), blob2 = Color.argb(15,255,255,255), blob3 = Color.argb(8,255,255,255);
    static int top = 0, bottom = 0;
    // Not "final" on purpose: javac only inlines static-final primitives initialized with a
    // constant expression. WHITE (a literal) would be baked into every call site and could
    // never be swapped for a light theme; keeping it a real mutable field means one call to
    // setLight() recolors every screen that reads Ui.WHITE / Ui.SUB, without editing each file.
    static int WHITE = 0xFFFFFFFF;
    static int SUB = Color.argb(175, 225, 230, 244);
    static boolean light = false;
    static boolean highContrast = false;
    static int bg = Color.rgb(2, 3, 5);
    static int panelR = 10, panelG = 14, panelB = 26;
    static int hiTop = Color.argb(56, 255, 255, 255), hiBot = Color.argb(6, 255, 255, 255);
    static int edgeStroke = Color.argb(72, 255, 255, 255);
    static final String[] THEMES = ThemeCatalog.NAMES;
    static final int[] DOTS = ThemeCatalog.COLORS;
    static final String[] THEME_NOTES = ThemeCatalog.NOTES;

    static String themeNote(int index) { return ThemeCatalog.note(index); }
    static final SimpleDateFormat DF = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    static void init(Context c) {
        D = c.getResources().getDisplayMetrics().density;
        highContrast = Settings.Secure.getInt(c.getContentResolver(), "high_text_contrast_enabled", 0) == 1;
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("lg_settings", Context.MODE_PRIVATE);
         // Restore the saved color-glass finish while retaining the dark liquid-glass base.
         setLight(false, false);
         setTheme(sp.getInt("theme", 0), false);
    }

    static void setLight(boolean l, boolean save) {
        light = false;
        if (save && sp != null) sp.edit().putBoolean("light", false).apply();
        WHITE = 0xFFFFFFFF; SUB = Color.argb(highContrast ? 245 : 190,225,225,225);
        bg = Color.rgb(5,5,6); panelR=18; panelG=18; panelB=20;
        hiTop=Color.argb(66,255,255,255); hiBot=Color.argb(3,255,255,255);
        edgeStroke=Color.argb(105,255,255,255);
    }

    static String fontFamily() {
        if (sp == null) return "sans-serif";
        switch (sp.getInt("font_family", 0)) {
            case 1: return "sans-serif-rounded";
            case 2: return "serif";
            case 3: return "monospace";
            default: return "sans-serif";
        }
    }

    static int dp(float v) { return Math.round(v * D); }

    static float textSizePx(Context c, float sp) {
        float systemScale = c.getResources().getConfiguration().fontScale;
        float safeScale = Math.max(0.90f, Math.min(systemScale, 1.15f));
        return sp * c.getResources().getDisplayMetrics().density * safeScale;
    }

    static void setTheme(int t, boolean save) {
        theme = Math.max(0, Math.min(THEMES.length - 1, t));
        accent = DOTS[theme];
        blob1 = Color.argb(28, Color.red(accent), Color.green(accent), Color.blue(accent));
        blob2 = Color.argb(18, Color.red(accent), Color.green(accent), Color.blue(accent));
        blob3 = Color.argb(10, Color.red(accent), Color.green(accent), Color.blue(accent));
        panelR = Color.red(accent); panelG = Color.green(accent); panelB = Color.blue(accent);
        if (save && sp != null) sp.edit().putInt("theme", theme).apply();
    }

    static boolean bool(String k, boolean d) { return sp.getBoolean(k, d); }
    static void set(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }

    /** Translucent color-tinted glass with restrained highlights and a fine rim. */
    static Drawable glass(float radiusDp, boolean strong) {
        float rad=dp(radiusDp);
        int a=highContrast ? (strong ? 118 : 88) : (strong ? 24 : 12);
        int ar=Color.red(accent), ag=Color.green(accent), ab=Color.blue(accent);
        GradientDrawable base=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(Math.min(34,a+10),ar,ag,ab),Color.argb(a,ar,ag,ab),Color.argb(Math.max(2,a-3),ar,ag,ab)});
        base.setCornerRadius(rad);
        GradientDrawable bloom=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(strong?12:7,ar,ag,ab),Color.argb(0,ar,ag,ab)});
        bloom.setCornerRadius(rad);
        GradientDrawable hi=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(18,255,255,255),Color.argb(5,255,255,255),Color.argb(0,255,255,255)});
        hi.setCornerRadius(rad);
        GradientDrawable edge=new GradientDrawable(); edge.setColor(Color.TRANSPARENT); edge.setCornerRadius(rad);
        int ea=highContrast?150:(strong?66:38);
        edge.setStroke(Math.max(1,dp(strong?1.0f:0.7f)),Color.argb(ea,255,255,255));
        return new LayerDrawable(new Drawable[]{base,bloom,hi,edge});
    }

    static Drawable accentPill(float radiusDp) {
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(74,Color.red(accent),Color.green(accent),Color.blue(accent)),Color.argb(28,Color.red(accent),Color.green(accent),Color.blue(accent))});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1,dp(1)),Color.argb(150,Color.red(accent),Color.green(accent),Color.blue(accent)));
        return g;
    }

    static TextView tv(Context c, CharSequence t, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        // Use a bounded, density-aware pixel size. Android's unrestricted system fontScale
        // previously expanded every TextView while many custom cards/headers had fixed dp
        // geometry, producing app-wide clipping and overlap on large-font devices.
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(textSizePx(c, sp)));
        v.setTextColor(color);
        v.setTypeface(Typeface.create(fontFamily(), bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setIncludeFontPadding(true);
        v.setLineSpacing(dp(2), 1.0f);
        v.setHorizontallyScrolling(false);
        if (Build.VERSION.SDK_INT >= 28) v.setFallbackLineSpacing(true);
        if (Build.VERSION.SDK_INT >= 23) {
            v.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
            v.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        }
        return v;
    }

    static void playRing(View v) {
        Drawable background = v.getBackground();
        if (background instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) background;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                Drawable layer = layers.getDrawable(i);
                if (layer instanceof TouchGradientRingDrawable) {
                    ((TouchGradientRingDrawable) layer).playTouch();
                    return;
                }
            }
        }
    }

    static ImageView ringIcon(ImageView v) {
        Drawable existing = v.getBackground();
        if (existing == null) existing = new GradientDrawable();
        TouchGradientRingDrawable effect = new TouchGradientRingDrawable(v.getContext());
        v.setBackground(new LayerDrawable(new Drawable[]{existing, effect}));
        v.setClipToOutline(false);
        // Do not install a touch listener on icon controls: click handling belongs to
        // the owning view and must remain the only input path.
        return v;
    }

    static ImageView iconBtn(Context c, int glyph, int color) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(Glyphs.get(glyph, color));
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setMinimumWidth(dp(48));
        v.setMinimumHeight(dp(48));
        v.setContentDescription(Glyphs.label(glyph));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setBackground(glass(16, true));
        v.setElevation(dp(3));
        v.setClickable(true);
        v.setFocusable(true);
        press(v);
        return v;
    }

    /** Springy "liquid" press feedback; never consumes the touch so clicks still work. */
    static void press(View v) {
        // Intentionally no OnTouchListener. The previous global touch-animation hook
        // competed with control-specific listeners and added work to every pointer event.
        // Native OnClickListener dispatch remains untouched and immediate.
    }

    static void round(View v, final float rDp) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(rDp));
            }
        });
        v.setClipToOutline(true);
    }

    static void tick(View v) {
        if (bool("haptic", true)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    static void toast(Context c, String m) { Toast.makeText(c, m, Toast.LENGTH_SHORT).show(); }

    static String size(long n) {
        if (n < 1024) return n + " B";
        if (n < 1048576L) return (n / 1024) + " KB";
        if (n < 1073741824L) return String.format(Locale.US, "%.1f MB", n / 1048576.0);
        return String.format(Locale.US, "%.2f GB", n / 1073741824.0);
    }

    static String date(long t) { return DF.format(new Date(t)); }
}
