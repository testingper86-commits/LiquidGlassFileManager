package com.music.echo.offline.files;

import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/**
 * Prism navigation architecture.
 *
 * Compact displays use a floating bottom dock; expanded displays use a persistent
 * vertical command rail. Both modes share one selection model and callback path.
 */
final class PrismNavigationDock {
    private static final int TAB_COUNT = 4;
    private final MainActivity activity;
    private final FrameLayout root;
    private final IntConsumer onTabSelected;
    private final FrameLayout surface;
    private final View selection;
    private final View[] items = new View[TAB_COUNT];
    private LinearLayout tabGroup;
    private final NebulaHomeView.Glyph[] glyphs = new NebulaHomeView.Glyph[TAB_COUNT];
    private final TextView[] labels = new TextView[TAB_COUNT];
    private int selectedTab;
    private boolean built;
    private boolean rail;
    private boolean itemLayoutListenersBound;

    PrismNavigationDock(MainActivity activity, FrameLayout root, IntConsumer onTabSelected) {
        if (activity == null || root == null || onTabSelected == null) {
            throw new IllegalArgumentException("navigation dependencies must not be null");
        }
        this.activity = activity;
        this.root = root;
        this.surface = new FrameLayout(activity);
        this.selection = new View(activity);
        this.onTabSelected = onTabSelected;
    }

    void build() {
        if (built) return;
        built = true;
        surface.setBackground(NebulaHomeView.navigationSurface());
        ToxiVisualSystem.clipRound(surface, 26);
        surface.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x2279F2C0), null, null));
        surface.setElevation(NebulaHomeView.dp(activity, 10));
        selection.setBackground(NebulaHomeView.navigationHighlight());
        selection.setVisibility(View.INVISIBLE);
        surface.addView(selection);
        String[] symbols = {"house", "folder", "grid", "settings"};
        String[] names = {"Home", "Files", "Tools", "Settings"};
        for (int i = 0; i < TAB_COUNT; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(activity);
            item.setGravity(Gravity.CENTER);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setMinimumWidth(NebulaHomeView.dp(activity, 52));
            item.setMinimumHeight(NebulaHomeView.dp(activity, 52));
            item.setPadding(NebulaHomeView.dp(activity, 3), NebulaHomeView.dp(activity, 4),
                    NebulaHomeView.dp(activity, 3), NebulaHomeView.dp(activity, 4));
            glyphs[i] = new NebulaHomeView.Glyph(activity, symbols[i], NebulaHomeView.TEXT);
            glyphs[i].setContentDescription(names[i]);
            item.addView(glyphs[i], new LinearLayout.LayoutParams(
                    NebulaHomeView.dp(activity, 22), NebulaHomeView.dp(activity, 22)));
            labels[i] = new TextView(activity);
            labels[i].setText(names[i]);
            labels[i].setTextSize(10);
            labels[i].setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), Typeface.BOLD));
            labels[i].setTextColor(NebulaHomeView.MUTED);
            LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(-2, -2);
            label.topMargin = NebulaHomeView.dp(activity, 3);
            item.addView(labels[i], label);
            item.setContentDescription(names[i]);
            item.setFocusable(true);
            item.setOnClickListener(v -> {
                ToxiVisualSystem.feedback(v);
                if (!ToxiVisualSystem.booleanPreference("reduce_motion", false)) {
                    v.animate().cancel();
                    v.setScaleX(0.94f);
                    v.setScaleY(0.94f);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(190)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.25f)).start();
                }
                onTabSelected.accept(index);
            });
            ToxiVisualSystem.interactive(item);
            items[i] = item;
        }
        rebuildGeometry();
        surface.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutSelection());
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            boolean expanded = activity.getResources().getConfiguration().screenWidthDp >= 700;
            if (expanded != rail) rebuildGeometry();
        });
        moveHighlight(selectedTab);
    }

    private void rebuildGeometry() {
        if (!built && surface.getParent() != null) return;
        rail = activity.getResources().getConfiguration().screenWidthDp >= 700;
        surface.removeAllViews();
        surface.setBackground(NebulaHomeView.navigationSurface());
        surface.addView(selection);
        tabGroup = new LinearLayout(activity);
        tabGroup.setOrientation(rail ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        tabGroup.setGravity(Gravity.CENTER);
        tabGroup.setBaselineAligned(false);
        int inset = NebulaHomeView.dp(activity, 5);
        surface.setPadding(inset, inset, inset, inset);
        for (View item : items) {
            if (item == null) continue;
            LinearLayout.LayoutParams lp = rail
                    ? new LinearLayout.LayoutParams(-1, 0, 1f)
                    : new LinearLayout.LayoutParams(0, -1, 1f);
            tabGroup.addView(item, lp);
        }
        surface.addView(tabGroup, new FrameLayout.LayoutParams(-1, -1));
        tabGroup.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutSelection());
        if (!itemLayoutListenersBound) {
            for (View item : items) {
                if (item != null) item.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutSelection());
            }
            itemLayoutListenersBound = true;
        }
        FrameLayout.LayoutParams params;
        if (rail) {
            params = new FrameLayout.LayoutParams(NebulaHomeView.dp(activity, 82),
                    NebulaHomeView.dp(activity, 340), Gravity.START | Gravity.CENTER_VERTICAL);
            params.setMargins(NebulaHomeView.dp(activity, 14), 0, 0, 0);
        } else {
            params = new FrameLayout.LayoutParams(-1, NebulaHomeView.dp(activity, 72), Gravity.BOTTOM);
            params.setMargins(NebulaHomeView.dp(activity, 22), 0,
                    NebulaHomeView.dp(activity, 22), NebulaHomeView.dp(activity, 10));
        }
        if (surface.getParent() == root) root.removeView(surface);
        root.addView(surface, params);
        surface.requestLayout();
        layoutSelection();
        surface.post(this::layoutSelection);
        surface.postOnAnimation(this::layoutSelection);
        tabGroup.post(this::layoutSelection);
        applySelectionColors();
    }

    private void layoutSelection() {
        if (!built || surface.getWidth() == 0 || surface.getHeight() == 0) return;
        int index = Math.max(0, Math.min(TAB_COUNT - 1, selectedTab));
        View target = items[index];
        if (target == null || target.getWidth() == 0 || target.getHeight() == 0) return;
        int x = Math.round(tabGroup.getX() + target.getX());
        int y = Math.round(tabGroup.getY() + target.getY());
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(target.getWidth(), target.getHeight());
        p.leftMargin = x;
        p.topMargin = y;
        selection.setLayoutParams(p);
        selection.setVisibility(View.VISIBLE);
    }

    FrameLayout view() { return surface; }

    void setVisible(boolean visible) { surface.setVisibility(visible ? View.VISIBLE : View.GONE); }

    void applyBottomInset() {
        if (rail || surface.getLayoutParams() == null) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
        params.bottomMargin = Math.max(ToxiVisualSystem.BOTTOM_INSET, NebulaHomeView.dp(activity, 24))
                + NebulaHomeView.dp(activity, 10);
        surface.setLayoutParams(params);
        surface.requestLayout();
        surface.postOnAnimation(this::layoutSelection);
    }

    void moveHighlight(int index) {
        selectedTab = Math.max(0, Math.min(TAB_COUNT - 1, index));
        if (!built) return;
        View target = items[selectedTab];
        if (target != null) {
            selection.animate().cancel();
            if (ToxiVisualSystem.booleanPreference("reduce_motion", false)) {
                layoutSelection();
            } else {
                if (target.getWidth() == 0 || target.getHeight() == 0) {
                    surface.postOnAnimation(() -> moveHighlight(selectedTab));
                    return;
                }
                int left = Math.round(tabGroup.getX() + target.getX());
                int top = Math.round(tabGroup.getY() + target.getY());
                selection.animate().x(left).y(top).setDuration(210)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
        applySelectionColors();
    }

    private void applySelectionColors() {
        for (int i = 0; i < TAB_COUNT; i++) {
            if (labels[i] == null || glyphs[i] == null) continue;
            int color = i == selectedTab ? NebulaHomeView.LIME : NebulaHomeView.MUTED;
            labels[i].setTextColor(color);
            glyphs[i].setTint(color);
            items[i].setSelected(i == selectedTab);
        }
    }

    void recolor(int selectedIndex) {
        surface.setBackground(NebulaHomeView.navigationSurface());
        selection.setBackground(NebulaHomeView.navigationHighlight());
        moveHighlight(selectedIndex);
    }
}
