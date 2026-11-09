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

/** Owns the Appearance Lab theme catalog and live theme-preview screen. */
final class ThemeScreenController {
    private final MainActivity activity;

    ThemeScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void open() {
        build();
        activity.showOverlay(activity.themeScroll);
    }

    void build() {
        activity.themeScroll.removeAllViews();
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(26), ToxiVisualSystem.dp(20), ToxiVisualSystem.dp(132));

        TextView title = ToxiVisualSystem.text(activity, "Appearance Lab", 28, ToxiVisualSystem.TEXT_PRIMARY, true);
        title.setPadding(ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(4), ToxiVisualSystem.dp(2), ToxiVisualSystem.dp(2));
        panel.addView(title);
        TextView subtitle = ToxiVisualSystem.text(activity, "Eight independent color systems. One consistent interface.", 13, ToxiVisualSystem.TEXT_SECONDARY, false);
        subtitle.setPadding(ToxiVisualSystem.dp(3), 0, ToxiVisualSystem.dp(3), ToxiVisualSystem.dp(18)); panel.addView(subtitle);

        LinearLayout current = new LinearLayout(activity);
        current.setGravity(Gravity.CENTER_VERTICAL);
        current.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(16));
        current.setBackground(ToxiVisualSystem.surface(24, true));
        FrameLayout currentIcon = new FrameLayout(activity);
        currentIcon.setBackground(ToxiVisualSystem.accentSurface(16));
        ImageView currentGlyph = new ImageView(activity); currentGlyph.setImageResource(R.drawable.ic_toxifiles_brand_mark); currentGlyph.setPadding(ToxiVisualSystem.dp(7), ToxiVisualSystem.dp(7), ToxiVisualSystem.dp(7), ToxiVisualSystem.dp(7));
        currentIcon.addView(currentGlyph, new FrameLayout.LayoutParams(-1, -1));
        current.addView(currentIcon, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(54), ToxiVisualSystem.dp(54)));
        LinearLayout currentText = new LinearLayout(activity); currentText.setOrientation(LinearLayout.VERTICAL);
        currentText.setPadding(ToxiVisualSystem.dp(14), 0, ToxiVisualSystem.dp(8), 0);
        currentText.addView(ToxiVisualSystem.text(activity, "ACTIVE IDENTITY", 10, ToxiVisualSystem.ACCENT, true));
        TextView activeName = ToxiVisualSystem.text(activity, ToxiVisualSystem.THEME_NAMES[ToxiVisualSystem.THEME_INDEX], 17, ToxiVisualSystem.TEXT_PRIMARY, true);
        activeName.setPadding(0, ToxiVisualSystem.dp(4), 0, 0); currentText.addView(activeName);
        current.addView(currentText, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView activeMark = new ImageView(activity);
        activeMark.setImageDrawable(ToxiIcons.symbol(ToxiIcons.CHECK, ToxiVisualSystem.ACCENT));
        activeMark.setContentDescription("Active theme");
        current.addView(activeMark, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(30), ToxiVisualSystem.dp(42)));
        panel.addView(current, activity.lpm(-1, -2, 0, 0, 0, 20));

        TextView allLabel = ToxiVisualSystem.text(activity, "AVAILABLE THEMES", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        allLabel.setLetterSpacing(0.12f); allLabel.setPadding(ToxiVisualSystem.dp(3), 0, 0, ToxiVisualSystem.dp(8)); panel.addView(allLabel);
        String[] finishNotes = ToxiVisualSystem.THEME_DESCRIPTIONS;
        for (int index = 0; index < ToxiVisualSystem.THEME_NAMES.length; index++) {
            final int themeIndex = index;
            boolean selected = index == ToxiVisualSystem.THEME_INDEX;
            LinearLayout tile = new LinearLayout(activity);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(ToxiVisualSystem.dp(15), ToxiVisualSystem.dp(14), ToxiVisualSystem.dp(15), ToxiVisualSystem.dp(14));
            tile.setBackground(selected ? ToxiVisualSystem.accentSurface(22) : ToxiVisualSystem.surface(22, true));
            tile.setElevation(ToxiVisualSystem.dp(selected ? 3 : 1));

            FrameLayout swatch = new FrameLayout(activity);
            android.graphics.drawable.GradientDrawable swatchBg = new android.graphics.drawable.GradientDrawable();
            swatchBg.setColors(new int[]{ToxiThemeSystem.ACCENTS[index], ToxiThemeSystem.ACCENT_ENDS[index]}); swatchBg.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR); swatchBg.setCornerRadius(ToxiVisualSystem.dp(14));
            swatchBg.setStroke(ToxiVisualSystem.dp(1), Color.argb(130, 255, 255, 255));
            swatch.setBackground(swatchBg);
            tile.addView(swatch, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(46), ToxiVisualSystem.dp(46)));

            LinearLayout labels = new LinearLayout(activity); labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(ToxiVisualSystem.dp(13), 0, ToxiVisualSystem.dp(8), 0);
            labels.addView(ToxiVisualSystem.text(activity, ToxiVisualSystem.THEME_NAMES[index], 14.5f, ToxiVisualSystem.TEXT_PRIMARY, selected));
            TextView desc = ToxiVisualSystem.text(activity, selected ? "Currently applied \u00B7 " + ToxiVisualSystem.themeDescription(index) : ToxiVisualSystem.themeDescription(index), 11, ToxiVisualSystem.TEXT_SECONDARY, false);
            desc.setPadding(0, ToxiVisualSystem.dp(3), 0, 0); labels.addView(desc);
            tile.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView status = new ImageView(activity);
            status.setImageDrawable(ToxiIcons.symbol(selected ? ToxiIcons.CHECK : ToxiIcons.NEXT, selected ? ToxiVisualSystem.ACCENT : ToxiVisualSystem.TEXT_SECONDARY));
            status.setContentDescription(selected ? "Selected theme" : "Preview theme");
            tile.addView(status, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(28), ToxiVisualSystem.dp(42)));

            tile.setOnClickListener(v -> {
                ToxiVisualSystem.feedback(v);
                ToxiVisualSystem.applyTheme(themeIndex, true);
                activity.recolor();
                build();
            });
            ToxiVisualSystem.interactive(tile);
            panel.addView(tile, activity.lpm(-1, -2, 0, 0, 0, 8));
        }
        activity.themeScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        ToxiVisualSystem.revealChildren(panel);
    }


}
