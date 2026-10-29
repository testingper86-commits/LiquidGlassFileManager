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
    static int theme = 0, accent = Color.rgb(224, 224, 224), blob1 = Color.argb(24,255,255,255), blob2 = Color.argb(15,255,255,255), blob3 = Color.argb(8,255,255,255);
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
    static final String[] THEMES = {"Monochrome Glass"};
    static final int[] DOTS = {Color.rgb(220,220,220)};
    static final SimpleDateFormat DF = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    static void init(Context c) {
        D = c.getResources().getDisplayMetrics().density;
        highContrast = Settings.Secure.getInt(c.getContentResolver(), "high_text_contrast_enabled", 0) == 1;
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("lg_settings", Context.MODE_PRIVATE);
        // Fixed monochrome glass appearance; legacy theme preferences are ignored.
        setLight(false, false);
        setTheme(0, false);
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

    static void setTheme(int t, boolean save) {
        theme=0; accent=Color.rgb(224,224,224);
        blob1=Color.argb(24,255,255,255); blob2=Color.argb(15,255,255,255); blob3=Color.argb(8,255,255,255);
        if(save && sp!=null) sp.edit().putInt("theme",0).apply();
    }

    static boolean bool(String k, boolean d) { return sp.getBoolean(k, d); }
    static void set(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }

    /** Translucent monochrome glass with restrained highlights and a fine rim. */
    static Drawable glass(float radiusDp, boolean strong) {
        float rad=dp(radiusDp); int a=highContrast ? (strong ? 118 : 88) : (strong ? 30 : 18);
        GradientDrawable base=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.argb(Math.min(84,a+18),255,255,255),Color.argb(a,255,255,255),Color.argb(Math.max(6,a-8),220,220,220)});
        base.setCornerRadius(rad);
        GradientDrawable hi=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.argb(28,255,255,255),Color.argb(7,255,255,255),Color.argb(0,255,255,255)});
        hi.setCornerRadius(rad);
        GradientDrawable edge=new GradientDrawable(); edge.setColor(Color.TRANSPARENT); edge.setCornerRadius(rad);
        int ea=highContrast?170:(strong?76:48); edge.setStroke(Math.max(1,dp(strong?1.0f:0.7f)),Color.argb(ea,255,255,255));
        return new LayerDrawable(new Drawable[]{base,hi,edge});
    }

    static Drawable accentPill(float radiusDp) {
        GradientDrawable g=new GradientDrawable(); g.setColor(Color.argb(46,255,255,255));
        g.setCornerRadius(dp(radiusDp)); g.setStroke(Math.max(1,dp(1)),Color.argb(105,255,255,255)); return g;
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
        v.setOnTouchListener((x, e) -> {
            // The clean glass interface defaults to reduced motion; enable spring feedback only by opting out.
            if (bool("reduce_motion", true) || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) return false;
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
