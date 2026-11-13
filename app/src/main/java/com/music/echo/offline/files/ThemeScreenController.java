package com.music.echo.offline.files;

import com.music.echo.offline.R;
import android.graphics.Color;
import android.view.Gravity;
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
        ImageView back = new ImageView(activity);
        back.setImageDrawable(ToxiIcons.symbol(ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> activity.closeOverlay());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40))));
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
        LinearLayout current = new LinearLayout(activity);
        current.setGravity(Gravity.CENTER_VERTICAL);
        current.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        current.setBackground(ToxiVisualSystem.surface(24, true));
        FrameLayout mark = new FrameLayout(activity);
        mark.setBackground(ToxiVisualSystem.accentSurface(16));
        ImageView glyph = new ImageView(activity);
        glyph.setImageResource(themePoster(ToxiVisualSystem.THEME_INDEX));
        glyph.setScaleType(ImageView.ScaleType.CENTER_CROP);
        glyph.setPadding(0, 0, 0, 0);
        mark.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        current.addView(mark, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(62)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(82))));
        LinearLayout active = new LinearLayout(activity);
        active.setOrientation(LinearLayout.VERTICAL);
        active.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), 0);
        active.addView(ToxiVisualSystem.text(activity, "ACTIVE IDENTITY", 10, ToxiVisualSystem.ACCENT, true));
        TextView activeName = ToxiVisualSystem.text(activity, ToxiVisualSystem.THEME_NAMES[ToxiVisualSystem.THEME_INDEX], 14, ToxiVisualSystem.TEXT_PRIMARY, true);
        activeName.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, 0);
        active.addView(activeName);
        current.addView(active, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView check = new ImageView(activity);
        check.setImageDrawable(ToxiIcons.symbol(ToxiIcons.CHECK, ToxiVisualSystem.ACCENT));
        check.setContentDescription("Active theme");
        current.addView(check, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(30)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(42))));
        panel.addView(current, activity.lpm(-1, -2, 0, 0, 0, 12));
        addLivePreview(panel);
        TextView label = ToxiVisualSystem.text(activity, "FIVE COMPLETE THEME IDENTITIES", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        label.setLetterSpacing(0.12f);
        label.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        panel.addView(label);
        for (int i = 0; i < ToxiVisualSystem.THEME_NAMES.length; i++) {
            final int themeIndex = i;
            boolean selected = i == ToxiVisualSystem.THEME_INDEX;
            LinearLayout tile = new LinearLayout(activity);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)));
            tile.setBackground(ToxiVisualSystem.surface(22, true));
            tile.setElevation(0f);
            tile.setAlpha(selected ? 1f : 0.94f);
            tile.setMinimumHeight(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(122)));
            ImageView poster = new ImageView(activity);
            poster.setImageResource(themePoster(i));
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackground(ToxiVisualSystem.surface(14, true));
            poster.setClipToOutline(true);
            poster.setContentDescription(ToxiVisualSystem.THEME_NAMES[i] + " poster artwork");
            tile.addView(poster, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(76)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(104))));
            LinearLayout text = new LinearLayout(activity);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), 0);
            TextView themeName = ToxiVisualSystem.text(activity, ToxiVisualSystem.THEME_NAMES[i], 14, selected ? ToxiVisualSystem.ACCENT : ToxiVisualSystem.TEXT_PRIMARY, selected);
            themeName.setMaxLines(2);
            themeName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(themeName);
            TextView description = ToxiVisualSystem.text(activity,
                selected ? "ACTIVE  /  " + ToxiVisualSystem.themeDescription(i) : ToxiVisualSystem.themeDescription(i),
                10.5f, ToxiVisualSystem.TEXT_SECONDARY, false);
            description.setMaxLines(2);
            description.setEllipsize(android.text.TextUtils.TruncateAt.END);
            description.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, 0);
            text.addView(description);
            tile.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView state = new ImageView(activity);
            state.setImageDrawable(ToxiIcons.symbol(selected ? ToxiIcons.CHECK : ToxiIcons.NEXT,
                selected ? ToxiVisualSystem.ACCENT : ToxiVisualSystem.TEXT_SECONDARY));
            state.setContentDescription(selected ? "Selected theme" : "Apply theme");
            tile.addView(state, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(42))));
            tile.setOnClickListener(v -> {
                ToxiVisualSystem.feedback(v);
                ToxiVisualSystem.applyTheme(themeIndex, true);
                activity.recolor();
                activity.themeScroll.post(this::build);
            });
            ToxiVisualSystem.interactive(tile);
            panel.addView(tile, activity.lpm(-1, -2, 0, 0, 0, 6));
        }
        activity.themeScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        panel.setAlpha(1f);
    }

    /** Preview the actual file-row component and theme-owned action treatment. */
    private void addLivePreview(LinearLayout panel) {
        TextView label = ToxiVisualSystem.text(activity, "LIVE COMPONENT PREVIEW",
                10, ToxiVisualSystem.TEXT_SECONDARY, true);
        label.setLetterSpacing(0.13f);
        label.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, 0,
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)));
        panel.addView(label);

        LinearLayout preview = new LinearLayout(activity);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        preview.setBackground(ToxiVisualSystem.surface(22, true));

        View sampleRow = ToxiFileRow.create(activity);
        ToxiFileRow.Holder holder = ToxiFileRow.holder(sampleRow);
        if (holder != null) {
            holder.icon.setImageDrawable(ToxiIcons.symbol(ToxiIcons.FOLDER, ToxiVisualSystem.ACCENT));
            holder.name.setText("Project files");
            holder.sub.setText("12 items  ·  Updated just now");
            holder.more.setOnClickListener(view -> ToxiVisualSystem.feedback(view));
            sampleRow.setContentDescription("Preview file row: Project files, 12 items");
            preview.addView(sampleRow);
        }

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), 0, 0);
        TextView identity = ToxiVisualSystem.text(activity,
                ToxiThemeEngine.profile().identity.toUpperCase(java.util.Locale.ROOT),
                9, ToxiVisualSystem.ACCENT, true);
        identity.setLetterSpacing(0.09f);
        actions.addView(identity, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView action = ToxiVisualSystem.text(activity, "OPEN FOLDER", 10,
                Color.WHITE, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)),
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        action.setBackground(ToxiVisualSystem.accentSurface(12));
        action.setOnClickListener(view -> ToxiVisualSystem.feedback(view));
        ToxiVisualSystem.interactive(action);
        actions.addView(action, new LinearLayout.LayoutParams(-2, -2));
        preview.addView(actions);
        panel.addView(preview, activity.lpm(-1, -2, 0, 0, 0, 12));
    }

}