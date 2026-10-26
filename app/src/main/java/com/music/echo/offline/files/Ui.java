package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
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
    static int theme = 0, accent = Color.rgb(150, 175, 255), blob1, blob2, blob3;
    static int top = 0, bottom = 0;
    // Not "final" on purpose: javac only inlines static-final primitives initialized with a
    // constant expression. WHITE (a literal) would be baked into every call site and could
    // never be swapped for a light theme; keeping it a real mutable field means one call to
    // setLight() recolors every screen that reads Ui.WHITE / Ui.SUB, without editing each file.
    static int WHITE = 0xFFFFFFFF;
    static int SUB = Color.argb(175, 225, 230, 244);
    static boolean light = false;
    static int bg = Color.rgb(2, 3, 5);
    static int panelR = 10, panelG = 14, panelB = 26;
    static int hiTop = Color.argb(56, 255, 255, 255), hiBot = Color.argb(6, 255, 255, 255);
    static int edgeStroke = Color.argb(72, 255, 255, 255);
    static final String[] THEMES = {"BlackViscosity", "ShadowMelt", "DarkMirror VR", "PitchGloss", "AbyssFluid", "JetGlaze", "MidnightGloss", "InkMirror", "SlickDark", "ObsidianCanvas", "OnyxFlow", "VantaGlass", "LiquidObsidian", "GlossEngine", "DarkFluid"};
    static final int[] DOTS = {Color.rgb(0,229,255),Color.rgb(255,64,190),Color.rgb(70,110,255),Color.rgb(245,170,85),Color.rgb(0,190,220),Color.rgb(120,90,255),Color.rgb(50,190,255),Color.rgb(210,80,255),Color.rgb(0,220,190),Color.rgb(100,140,255),Color.rgb(30,230,220),Color.rgb(180,190,210),Color.rgb(80,120,255),Color.rgb(255,110,190),Color.rgb(70,210,255)};
    static final SimpleDateFormat DF = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    static void init(Context c) {
        D = c.getResources().getDisplayMetrics().density;
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("lg_settings", Context.MODE_PRIVATE);
        // Migrate the previous default (index 0) to VantaGlass once; preserve any other saved theme.
        if (!sp.getBoolean("vantaglass_default_migrated", false)) {
            if (!sp.contains("theme") || sp.getInt("theme", 11) == 0) sp.edit().putInt("theme", 11).apply();
            sp.edit().putBoolean("vantaglass_default_migrated", true).apply();
        }
        setLight(sp.getBoolean("light", false), false);
        setTheme(sp.getInt("theme", 11), false);
    }

    static void setLight(boolean l, boolean save) {
        light = l;
        if (save) sp.edit().putBoolean("light", l).apply();
        if (l) {
            WHITE = Color.rgb(18, 20, 26);
            SUB = Color.argb(210, 70, 75, 90);
            bg = Color.rgb(236, 238, 244);
            panelR = 255; panelG = 255; panelB = 255;
            hiTop = Color.argb(150, 255, 255, 255); hiBot = Color.argb(10, 255, 255, 255);
            edgeStroke = Color.argb(50, 20, 22, 30);
        } else {
            WHITE = 0xFFFFFFFF;
            SUB = Color.argb(175, 225, 230, 244);
            bg = Color.rgb(2, 3, 5);
            panelR = 5; panelG = 8; panelB = 12;
            hiTop = Color.argb(76, 255, 255, 255); hiBot = Color.argb(10, 255, 255, 255);
            edgeStroke = Color.argb(112, 235, 250, 255);
        }
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

    static void setTheme(int t, boolean save) {
        theme = t;
        if (save) sp.edit().putInt("theme", t).apply();
        switch (t) {
            case 1: accent = Color.rgb(255, 190, 120); blob1 = Color.argb(115, 255, 150, 70); blob2 = Color.argb(95, 255, 80, 90); blob3 = Color.argb(70, 255, 210, 120); break;
            case 2: accent = Color.rgb(225, 228, 235); blob1 = Color.argb(70, 255, 255, 255); blob2 = Color.argb(55, 170, 180, 200); blob3 = Color.argb(40, 255, 255, 255); break;
            case 3: accent = Color.rgb(120, 235, 200); blob1 = Color.argb(105, 30, 200, 160); blob2 = Color.argb(90, 70, 120, 255); blob3 = Color.argb(70, 150, 255, 200); break;
            case 4: accent = Color.rgb(190, 145, 255); blob1 = Color.argb(115, 145, 70, 235); blob2 = Color.argb(95, 90, 90, 255); blob3 = Color.argb(70, 220, 150, 255); break;
            case 5: accent = Color.rgb(255, 150, 190); blob1 = Color.argb(110, 245, 90, 155); blob2 = Color.argb(90, 180, 100, 230); blob3 = Color.argb(65, 255, 190, 220); break;
            case 6: accent = Color.rgb(90, 210, 220); blob1 = Color.argb(110, 20, 170, 190); blob2 = Color.argb(90, 50, 120, 220); blob3 = Color.argb(70, 120, 245, 230); break;
            case 7: accent = Color.rgb(255, 130, 105); blob1 = Color.argb(110, 255, 95, 80); blob2 = Color.argb(90, 255, 155, 75); blob3 = Color.argb(70, 255, 210, 120); break;
            case 8: accent=Color.rgb(0,220,190); blob1=Color.argb(110,0,220,190); blob2=Color.argb(85,0,120,255); blob3=Color.argb(65,180,80,255); break;
            case 9: accent=Color.rgb(100,140,255); blob1=Color.argb(110,45,70,180); blob2=Color.argb(85,0,210,255); blob3=Color.argb(65,190,80,255); break;
            case 10: accent=Color.rgb(30,230,220); blob1=Color.argb(110,0,130,145); blob2=Color.argb(85,40,80,220); blob3=Color.argb(65,0,220,180); break;
            case 11: accent=Color.rgb(135,235,255); blob1=Color.argb(76,30,190,225); blob2=Color.argb(54,90,125,255); blob3=Color.argb(34,180,245,255); break;
            case 12: accent=Color.rgb(80,120,255); blob1=Color.argb(110,40,70,210); blob2=Color.argb(85,0,200,255); blob3=Color.argb(65,180,60,255); break;
            case 13: accent=Color.rgb(255,110,190); blob1=Color.argb(110,190,45,145); blob2=Color.argb(85,65,80,255); blob3=Color.argb(65,255,175,90); break;
            case 14: accent=Color.rgb(70,210,255); blob1=Color.argb(110,0,145,220); blob2=Color.argb(85,170,65,230); blob3=Color.argb(65,255,170,80); break;
            default: accent = Color.rgb(135, 235, 255); blob1 = Color.argb(76, 30, 190, 225); blob2 = Color.argb(54, 90, 125, 255); blob3 = Color.argb(34, 180, 245, 255);
        }
    }

    static boolean bool(String k, boolean d) { return sp.getBoolean(k, d); }
    static void set(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }

    /**
     * Layered Liquid Glass panel: readable tinted body, subtle colour bloom, top specular
     * highlight and a fine rim. This is a translucent layered drawable, not a backdrop blur.
     */
    static Drawable glass(float radiusDp, boolean strong) {
        float rad = dp(radiusDp);
        // Stronger "hard glass" treatment: a deeper smoked body, saturated edge bloom,
        // crisp specular highlight and two fine rims. This is a drawable-only effect and
        // intentionally does not pretend to be a real-time backdrop blur.
        int bodyAlpha = strong ? (light ? 226 : 202) : (light ? 194 : 158);
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(Math.min(250, bodyAlpha + 34), panelR, panelG, panelB),
                        Color.argb(bodyAlpha, panelR, panelG, panelB),
                        Color.argb(Math.max(38, bodyAlpha - 38), panelR, panelG, panelB)
                });
        base.setCornerRadius(rad);

        GradientDrawable bloom = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(light ? 56 : 94, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(light ? 16 : 28, Color.red(blob2), Color.green(blob2), Color.blue(blob2)),
                        Color.argb(0, 255, 255, 255)
                });
        bloom.setCornerRadius(rad);

        GradientDrawable hi = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(light ? 168 : 142, 255, 255, 255),
                        Color.argb(light ? 48 : 34, 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                });
        hi.setCornerRadius(rad);

        GradientDrawable innerRim = new GradientDrawable();
        innerRim.setColor(Color.TRANSPARENT);
        innerRim.setCornerRadius(Math.max(0, rad - dp(2)));
        innerRim.setStroke(Math.max(1, dp(0.7f)), Color.argb(light ? 100 : 86, 255, 255, 255));

        GradientDrawable edge = new GradientDrawable();
        edge.setColor(Color.TRANSPARENT);
        edge.setCornerRadius(rad);
        int edgeAlpha = strong ? (light ? 205 : 176) : (light ? 160 : 132);
        edge.setStroke(Math.max(1, dp(strong ? 1.15f : 0.9f)),
                Color.argb(edgeAlpha, Color.red(edgeStroke), Color.green(edgeStroke), Color.blue(edgeStroke)));

        return new LayerDrawable(new Drawable[]{base, bloom, hi, innerRim, edge});
    }

    static Drawable accentPill(float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(105, Color.red(accent), Color.green(accent), Color.blue(accent)));
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(1)), Color.argb(130, 255, 255, 255));
        return g;
    }

    static TextView tv(Context c, CharSequence t, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create(fontFamily(), bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setIncludeFontPadding(false);
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
        v.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) effect.playTouch();
            return false;
        });
        return v;
    }

    static ImageView iconBtn(Context c, int glyph, int color) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(Glyphs.get(glyph, color));
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
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
        v.setOnTouchListener((x, e) -> {
            // The clean glass interface defaults to reduced motion; enable spring feedback only by opting out.
            if (bool("reduce_motion", true)) return false;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) { playRing(x); x.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start(); }
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                x.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(new OvershootInterpolator(1.1f)).start();
            return false;
        });
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
