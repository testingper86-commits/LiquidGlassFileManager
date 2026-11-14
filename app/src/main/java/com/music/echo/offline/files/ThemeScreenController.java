package com.music.echo.offline.files;

import com.music.echo.offline.R;
import android.graphics.Color;
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
    ThemeScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }
    void open() { build(); activity.showOverlay(activity.themeScroll); }

    private static int themePoster(int index) {
        switch (ToxiThemeSystem.normalize(index)) {
            case 0: return R.drawable.theme_poster_onepiece;
            case 1: return R.drawable.theme_poster_naruto;
            case 2: return R.drawable.theme_poster_demon;
            case 3: return R.drawable.theme_poster_dragonball;
            default: return R.drawable.theme_poster_aot;
        }
    }
    void build() {
        activity.themeScroll.removeAllViews();
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(132)));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> activity.closeOverlay());
        header.addView(back, new LinearLayout.LayoutParams(
                ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48)),
                ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48))));
        TextView title = ToxiVisualSystem.text(activity, "Theme Studio", 23, ToxiVisualSystem.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.leftMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));
        header.addView(title, titleLp);
        panel.addView(header);
        title.setLetterSpacing(-0.025f);
        TextView subtitle = ToxiVisualSystem.text(activity, "Five anime worlds. One unified, adaptive filespace.", 13, ToxiVisualSystem.TEXT_SECONDARY, false);
        subtitle.setLineSpacing(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), 1.04f);
        subtitle.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
        panel.addView(subtitle);
        TextView label = ToxiVisualSystem.text(activity, "CHOOSE YOUR WORLD", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        label.setLetterSpacing(0.14f);
        label.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, 0,
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        panel.addView(label);
        for (int i = 0; i < ToxiVisualSystem.THEME_NAMES.length; i++) {
            final int themeIndex = i;
            boolean selected = i == ToxiVisualSystem.THEME_INDEX;
            FrameLayout tile = new FrameLayout(activity);
            tile.setClipChildren(true);
            tile.setClipToPadding(true);
            tile.setBackground(ToxiVisualSystem.surface(22, true));
            tile.setClipToOutline(true);

            ImageView poster = new ImageView(activity);
            poster.setImageResource(themePoster(i));
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setAlpha(selected ? 1f : 0.88f);
            poster.setContentDescription(ToxiVisualSystem.THEME_NAMES[i] + " cinematic poster artwork");
            tile.addView(poster, new FrameLayout.LayoutParams(-1, -1));

            View shade = new View(activity);
            android.graphics.drawable.GradientDrawable shadeGradient =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{Color.argb(12, 0, 0, 0), Color.argb(78, 0, 0, 0),
                                    Color.argb(232, 0, 0, 0)});
            shade.setBackground(shadeGradient);
            tile.addView(shade, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.BOTTOM);
            copy.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)));
            TextView themeName = ToxiVisualSystem.text(activity, ToxiVisualSystem.THEME_NAMES[i],
                    17, Color.WHITE, true);
            themeName.setMaxLines(2);
            themeName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(themeName);
            TextView description = ToxiVisualSystem.text(activity,
                    ToxiVisualSystem.themeDescription(i), 11,
                    Color.argb(225, 245, 248, 250), false);
            description.setMaxLines(1);
            description.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
            copy.addView(description, descriptionParams);
            tile.addView(copy, new FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM));

            TextView state = ToxiVisualSystem.text(activity,
                    selected ? "✓  ACTIVE THEME" : "TAP TO APPLY",
                    9.5f, selected ? ToxiVisualSystem.accentForeground() : Color.WHITE, true);
            state.setLetterSpacing(0.08f);
            state.setGravity(Gravity.CENTER);
            state.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)));
            state.setBackground(selected ? ToxiVisualSystem.accentSurface(14)
                    : ToxiVisualSystem.surface(14, false));
            FrameLayout.LayoutParams stateParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.END);
            stateParams.setMargins(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0);
            tile.addView(state, stateParams);
            tile.setContentDescription(ToxiVisualSystem.THEME_NAMES[i] + ". "
                    + ToxiVisualSystem.themeDescription(i) + (selected ? ". Active theme" : ". Double tap to apply"));
            tile.setFocusable(true);
            tile.setOnClickListener(v -> {
                ToxiVisualSystem.feedback(v);
                ToxiVisualSystem.applyTheme(themeIndex, true);
                activity.recolor();
                activity.themeScroll.post(this::build);
            });
            ToxiVisualSystem.interactive(tile);
            LinearLayout.LayoutParams tileParams = activity.lpm(-1,
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(148)), 0, 0, 0, 10);
            panel.addView(tile, tileParams);
        }
        activity.themeScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        panel.setAlpha(1f);
    }

}
