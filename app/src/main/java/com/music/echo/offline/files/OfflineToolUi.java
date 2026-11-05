package com.music.echo.offline.files;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Isolated native component kit for newly implemented offline tools.
 *
 * Existing screens must not be migrated through this class as part of unrelated work.
 * It deliberately reads the currently selected theme without changing global UI state.
 */
public final class OfflineToolUi {
    private static final String[] THEME_NAMES = {
            "Cyborg Titanium", "Neural", "Overdrive", "Phantom",
            "Matrix Hacker", "Cyber Hacker", "Dark Web", "Ghost Protocol"
    };

    private static final int COLOR_TEXT = Color.rgb(247, 249, 255);
    private static final int COLOR_MUTED = Color.rgb(190, 198, 213);
    private static final int COLOR_SUCCESS = Color.rgb(87, 220, 157);
    private static final int COLOR_WARNING = Color.rgb(255, 190, 83);
    private static final int COLOR_ERROR = Color.rgb(255, 105, 115);
    private static final int COLOR_INFO = Color.rgb(111, 190, 255);

    public enum State { INFO, SUCCESS, WARNING, ERROR }

    private OfflineToolUi() { }

    public static String[] themeNames() {
        return THEME_NAMES.clone();
    }

    public static int selectedThemeIndex(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences("lg_settings", Context.MODE_PRIVATE);
        int value = preferences.getInt("theme", 0);
        return Math.max(0, Math.min(ThemeCatalog.COLORS.length - 1, value));
    }

    public static String selectedThemeName(Context context) {
        return THEME_NAMES[selectedThemeIndex(context)];
    }

    public static int accentColor(Context context) {
        return ThemeCatalog.COLORS[selectedThemeIndex(context)];
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setClipToPadding(false);
        layout.setClipChildren(false);
        return layout;
    }

    public static LinearLayout toolbar(Context context, String heading, String supportingText) {
        LinearLayout bar = column(context);
        bar.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 14));

        TextView title = text(context, heading, 22, COLOR_TEXT, true);
        title.setMaxLines(2);
        bar.addView(title, matchWrap());

        if (supportingText != null && !supportingText.trim().isEmpty()) {
            TextView subtitle = text(context, supportingText, 13, COLOR_MUTED, false);
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(context, 5);
            bar.addView(subtitle, params);
        }
        return bar;
    }

    public static LinearLayout surface(Context context, int paddingDp, boolean emphasized) {
        LinearLayout card = column(context);
        int accent = accentColor(context);
        int alpha = emphasized ? 34 : 22;
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(alpha + 8, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(alpha, 18, 21, 30)});
        fill.setCornerRadius(dp(context, 20));
        fill.setStroke(dp(context, 1), Color.argb(emphasized ? 105 : 70, 255, 255, 255));
        card.setBackground(fill);
        card.setPadding(dp(context, paddingDp), dp(context, paddingDp),
                dp(context, paddingDp), dp(context, paddingDp));
        card.setClipToOutline(true);
        card.setElevation(dp(context, 2));
        return card;
    }

    public static TextView actionButton(Context context, String label, boolean primary,
                                         View.OnClickListener listener) {
        TextView button = text(context, label, 14, COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 48));
        button.setMinWidth(dp(context, 48));
        button.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(label);
        int accent = accentColor(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 14));
        if (primary) {
            shape.setColor(Color.argb(68, Color.red(accent), Color.green(accent), Color.blue(accent)));
            shape.setStroke(dp(context, 1), Color.argb(190, Color.red(accent),
                    Color.green(accent), Color.blue(accent)));
        } else {
            shape.setColor(Color.argb(18, 255, 255, 255));
            shape.setStroke(dp(context, 1), Color.argb(74, 255, 255, 255));
        }
        if (Build.VERSION.SDK_INT >= 21) {
            button.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(Color.argb(55, 255, 255, 255)), shape, null));
        } else {
            button.setBackground(shape);
        }
        if (listener != null) button.setOnClickListener(listener);
        return button;
    }

    public static LinearLayout stateCard(Context context, State state, String heading,
                                         String message) {
        LinearLayout card = surface(context, 16, false);
        int color = stateColor(state);
        View marker = new View(context);
        GradientDrawable markerDrawable = new GradientDrawable();
        markerDrawable.setColor(color);
        markerDrawable.setCornerRadius(dp(context, 3));
        marker.setBackground(markerDrawable);
        card.addView(marker, new LinearLayout.LayoutParams(dp(context, 34), dp(context, 4)));

        TextView title = text(context, heading, 15, COLOR_TEXT, true);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(context, 11);
        card.addView(title, titleParams);

        if (message != null && !message.trim().isEmpty()) {
            TextView detail = text(context, message, 13, COLOR_MUTED, false);
            LinearLayout.LayoutParams detailParams = matchWrap();
            detailParams.topMargin = dp(context, 5);
            card.addView(detail, detailParams);
        }
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        card.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        return card;
    }

    public static LinearLayout loadingState(Context context, String message) {
        LinearLayout content = column(context);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(context, 20), dp(context, 24), dp(context, 20), dp(context, 24));

        ProgressBar progress = new ProgressBar(context);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setIndeterminateTintList(ColorStateList.valueOf(accentColor(context)));
        }
        content.addView(progress, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));

        TextView label = text(context, message == null ? "Loading" : message,
                13, COLOR_MUTED, false);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(context, 12);
        content.addView(label, params);
        return content;
    }

    public static LinearLayout emptyState(Context context, String heading, String message,
                                          String actionLabel, View.OnClickListener action) {
        LinearLayout empty = surface(context, 22, false);
        empty.setGravity(Gravity.CENTER);
        TextView title = text(context, heading, 17, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        empty.addView(title, matchWrap());

        if (message != null && !message.trim().isEmpty()) {
            TextView detail = text(context, message, 13, COLOR_MUTED, false);
            detail.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams detailParams = matchWrap();
            detailParams.topMargin = dp(context, 7);
            empty.addView(detail, detailParams);
        }

        if (actionLabel != null && action != null) {
            TextView button = actionButton(context, actionLabel, true, action);
            LinearLayout.LayoutParams buttonParams = wrapWrap();
            buttonParams.topMargin = dp(context, 16);
            empty.addView(button, buttonParams);
        }
        return empty;
    }

    /** Runs a short entrance transition only when Android's system animation scale permits it. */
    public static void animateIn(View view) {
        if (view == null || !animationsAllowed(view.getContext())) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(view.getContext(), 7));
        view.animate().alpha(1f).translationY(0f).setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        view.setAlpha(1f);
                        view.setTranslationY(0f);
                    }
                }).start();
    }

    public static boolean animationsAllowed(Context context) {
        try {
            float animatorScale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            float transitionScale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.TRANSITION_ANIMATION_SCALE, 1f);
            AccessibilityManager manager = (AccessibilityManager)
                    context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            return animatorScale > 0f && transitionScale > 0f
                    && (manager == null || !manager.isTouchExplorationEnabled());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static TextView text(Context context, CharSequence value, float sp,
                                 int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.create(
                "sans-serif", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(context, 2), 1f);
        view.setHorizontallyScrolling(false);
        view.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
        view.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        return view;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int stateColor(State state) {
        if (state == State.SUCCESS) return COLOR_SUCCESS;
        if (state == State.WARNING) return COLOR_WARNING;
        if (state == State.ERROR) return COLOR_ERROR;
        return COLOR_INFO;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
