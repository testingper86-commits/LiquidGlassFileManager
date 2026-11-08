package com.music.echo.offline.files;

import android.view.Gravity;
import android.view.View;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import java.util.function.IntConsumer;

/** Builds and owns the persistent four-tab navigation surface. */
final class MainNavigationBar {
    private final ComponentActivity activity;
    private final FrameLayout root;
    private final IntConsumer onTabSelected;
    private final FrameLayout view;
    private final View highlight;
    private final NebulaHomeView.Glyph[] icons = new NebulaHomeView.Glyph[4];
    private final TextView[] labels = new TextView[4];
    private int selectedTab;
    private boolean built;

    MainNavigationBar(ComponentActivity activity, FrameLayout root, IntConsumer onTabSelected) {
        if (activity == null || root == null || onTabSelected == null) throw new IllegalArgumentException("navigation dependencies must not be null");
        this.activity = activity;
        this.root = root;
        this.onTabSelected = onTabSelected;
        view = new FrameLayout(activity);
        highlight = new View(activity);
    }

    void build() {
        if (built) return;
        built = true;
        view.setBackground(NebulaHomeView.navigationSurface());
        view.setElevation(NebulaHomeView.dp(activity, 8));
        view.setPadding(NebulaHomeView.dp(activity, 5), NebulaHomeView.dp(activity, 5), NebulaHomeView.dp(activity, 5), NebulaHomeView.dp(activity, 5));
        highlight.setBackground(NebulaHomeView.navigationHighlight());
        view.addView(highlight, new FrameLayout.LayoutParams(0, -1));
        LinearLayout items = new LinearLayout(activity);
        items.setOrientation(LinearLayout.HORIZONTAL);
        items.setGravity(Gravity.CENTER_VERTICAL);
        view.addView(items, new FrameLayout.LayoutParams(-1, -1));
        String[] glyphs = {"house", "folder", "grid", "settings"};
        String[] tabLabels = {"Home", "Files", "Tools", "Settings"};
        for (int i = 0; i < tabLabels.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(NebulaHomeView.dp(activity, 48));
            item.setBackground(NebulaHomeView.navigationItemSurface());
            icons[i] = new NebulaHomeView.Glyph(activity, glyphs[i], NebulaHomeView.TEXT);
            icons[i].setContentDescription(tabLabels[i]);
            item.addView(icons[i], new LinearLayout.LayoutParams(NebulaHomeView.dp(activity, 21), NebulaHomeView.dp(activity, 21)));
            labels[i] = new TextView(activity);
            labels[i].setText(tabLabels[i]);
            labels[i].setTextSize(10);
            labels[i].setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            labels[i].setTextColor(NebulaHomeView.MUTED);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
            labelParams.topMargin = NebulaHomeView.dp(activity, 3);
            item.addView(labels[i], labelParams);
            item.setContentDescription(tabLabels[i]);
            item.setOnClickListener(v -> onTabSelected.accept(index));
            items.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(-1, NebulaHomeView.dp(activity, 66), Gravity.BOTTOM);
        navParams.setMargins(NebulaHomeView.dp(activity, 14), 0, NebulaHomeView.dp(activity, 14), NebulaHomeView.dp(activity, 12));
        root.addView(view, navParams);
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int itemWidth = Math.max(1, (right - left - NebulaHomeView.dp(activity, 10)) / 4);
            FrameLayout.LayoutParams highlightParams = (FrameLayout.LayoutParams) highlight.getLayoutParams();
            if (highlightParams.width != itemWidth) {
                highlightParams.width = itemWidth;
                highlight.setLayoutParams(highlightParams);
            }
            highlight.setTranslationX(Math.min(3, Math.max(0, selectedTab)) * itemWidth);
        });
        moveHighlight(selectedTab);
    }

    FrameLayout view() { return view; }

    void setVisible(boolean visible) { view.setVisibility(visible ? View.VISIBLE : View.GONE); }

    void applyBottomInset() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        params.bottomMargin = ToxiVisualSystem.BOTTOM_INSET + ToxiVisualSystem.dp(12);
        view.setLayoutParams(params);
    }

    void moveHighlight(int index) {
        selectedTab = Math.max(0, Math.min(3, index));
        int itemWidth = highlight.getLayoutParams().width;
        if (ToxiVisualSystem.booleanPreference("reduce_motion", true)) {
            highlight.setTranslationX(selectedTab * itemWidth);
        } else {
            highlight.animate().translationX(selectedTab * itemWidth).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        for (int i = 0; i < labels.length; i++) {
            int tint = i == selectedTab ? NebulaHomeView.LIME : NebulaHomeView.MUTED;
            labels[i].setTextColor(tint);
            icons[i].setTint(tint);
        }
    }

    void recolor(int selectedIndex) {
        view.setBackground(NebulaHomeView.navigationSurface());
        highlight.setBackground(NebulaHomeView.navigationHighlight());
        moveHighlight(selectedIndex);
    }
}
