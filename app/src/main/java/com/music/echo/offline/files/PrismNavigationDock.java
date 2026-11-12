package com.music.echo.offline.files;

import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Typeface;
import android.graphics.Color;
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
    private static final String[] TAB_NAMES = {"Home", "Files", "Tools", "Settings"};
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
    private boolean selectionPositioned;
    private boolean itemLayoutListenersBound;
    private boolean imeVisible;

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
        ToxiVisualSystem.clipRound(surface, 18);
        applySurfaceRipple();
        surface.setElevation(NebulaHomeView.dp(activity, 5));
        selection.setBackground(NebulaHomeView.navigationHighlight());
        selection.setVisibility(View.INVISIBLE);
        surface.addView(selection);
        String[] symbols = {"house", "folder", "grid", "settings"};
        for (int i = 0; i < TAB_COUNT; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(activity);
            item.setGravity(Gravity.CENTER);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setMinimumWidth(NebulaHomeView.dp(activity, 48));
            item.setMinimumHeight(NebulaHomeView.dp(activity, 48));
            item.setPadding(NebulaHomeView.dp(activity, 3), NebulaHomeView.dp(activity, 3),
                    NebulaHomeView.dp(activity, 3), NebulaHomeView.dp(activity, 3));
            glyphs[i] = new NebulaHomeView.Glyph(activity, symbols[i], NebulaHomeView.TEXT);
            glyphs[i].setContentDescription(TAB_NAMES[i]);
            item.addView(glyphs[i], new LinearLayout.LayoutParams(
                    NebulaHomeView.dp(activity, 21), NebulaHomeView.dp(activity, 21)));
            labels[i] = new TextView(activity);
            labels[i].setText(TAB_NAMES[i]);
            labels[i].setTextSize(9.5f);
            labels[i].setIncludeFontPadding(false);
            labels[i].setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), Typeface.BOLD));
            labels[i].setTextColor(NebulaHomeView.MUTED);
            LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(-2, -2);
            label.topMargin = NebulaHomeView.dp(activity, 2);
            item.addView(labels[i], label);
            item.setContentDescription(TAB_NAMES[i]);
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
        tabGroup.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            selectionPositioned = false;
            layoutSelection();
        });
        if (!itemLayoutListenersBound) {
            for (View item : items) {
                if (item != null) item.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                    selectionPositioned = false;
                    layoutSelection();
                });
            }
            itemLayoutListenersBound = true;
        }
        FrameLayout.LayoutParams params;
        if (rail) {
            params = new FrameLayout.LayoutParams(NebulaHomeView.dp(activity, 82),
                    NebulaHomeView.dp(activity, 340), Gravity.START | Gravity.CENTER_VERTICAL);
            params.setMargins(NebulaHomeView.dp(activity, 14), 0, 0, 0);
        } else {
            params = new FrameLayout.LayoutParams(-1, NebulaHomeView.dp(activity, 64), Gravity.BOTTOM);
            params.setMargins(NebulaHomeView.dp(activity, 14), 0,
                    NebulaHomeView.dp(activity, 14), Math.max(ToxiVisualSystem.BOTTOM_INSET,
                    NebulaHomeView.dp(activity, 24)) + NebulaHomeView.dp(activity, 12));
        }
        if (surface.getParent() == root) root.removeView(surface);
        root.addView(surface, params);
        selectionPositioned = false;
        surface.requestLayout();
        layoutSelection();
        surface.post(this::layoutSelection);
        surface.postOnAnimation(this::layoutSelection);
        tabGroup.post(this::layoutSelection);
        tabGroup.postOnAnimation(this::layoutSelection);
        applySelectionColors();
        if (imeVisible) setImeVisible(true);
    }

    private void layoutSelection() {
        if (!built || surface.getWidth() == 0 || surface.getHeight() == 0) return;
        int index = Math.max(0, Math.min(TAB_COUNT - 1, selectedTab));
        View target = items[index];
        if (target == null || target.getWidth() == 0 || target.getHeight() == 0) return;
        int x = tabGroup.getLeft() + target.getLeft();
        int y = tabGroup.getTop() + target.getTop();
        int inset = NebulaHomeView.dp(activity, 5);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                Math.max(0, target.getWidth() - inset * 2),
                Math.max(0, target.getHeight() - inset * 2));
        selection.animate().cancel();
        selection.setLayoutParams(p);
        selection.setTranslationX(x + inset);
        selection.setTranslationY(y + target.getHeight() - NebulaHomeView.dp(activity, 5));
        selection.setAlpha(1f);
        selection.setVisibility(View.VISIBLE);
        selectionPositioned = true;
    }

    FrameLayout view() { return surface; }

    void setVisible(boolean visible) { surface.setVisibility(visible ? View.VISIBLE : View.GONE); }

    void setImeVisible(boolean visible) {
        imeVisible = visible;
        if (rail) return;
        surface.animate().cancel();
        surface.setTranslationY(visible ? surface.getHeight() + NebulaHomeView.dp(activity, 28) : 0f);
        surface.setAlpha(visible ? 0f : 1f);
        surface.setImportantForAccessibility(visible ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    void applyBottomInset() {
        if (rail || surface.getParent() != root || surface.getLayoutParams() == null) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
        params.bottomMargin = Math.max(ToxiVisualSystem.BOTTOM_INSET, NebulaHomeView.dp(activity, 24))
                + NebulaHomeView.dp(activity, 12);
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
            if (!selectionPositioned || ToxiVisualSystem.booleanPreference("reduce_motion", false)) {
                selectionPositioned = false;
                layoutSelection();
            } else {
                if (target.getWidth() == 0 || target.getHeight() == 0) {
                    surface.postOnAnimation(() -> moveHighlight(selectedTab));
                    return;
                }
                int left = tabGroup.getLeft() + target.getLeft();
                int top = tabGroup.getTop() + target.getTop();
                int inset = NebulaHomeView.dp(activity, 5);
                selection.animate().translationX(left + inset).translationY(top + inset).setDuration(145)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            }
        }
        applySelectionColors();
    }

    private void applySurfaceRipple() {
        int accent = NebulaHomeView.LIME;
        surface.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(30, Color.red(accent),
                        Color.green(accent), Color.blue(accent))), null, null));
    }

    private void applySelectionColors() {
        for (int i = 0; i < TAB_COUNT; i++) {
            if (labels[i] == null || glyphs[i] == null) continue;
            int color = i == selectedTab ? NebulaHomeView.LIME : NebulaHomeView.MUTED;
            labels[i].setTextColor(color);
            glyphs[i].setTint(color);
            items[i].setSelected(i == selectedTab);
            items[i].setActivated(i == selectedTab);
            items[i].setContentDescription(TAB_NAMES[i] + (i == selectedTab ? ", selected" : ""));
        }
    }

    void recolor(int selectedIndex) {
        surface.setBackground(NebulaHomeView.navigationSurface());
        applySurfaceRipple();
        selection.setBackground(NebulaHomeView.navigationHighlight());
        moveHighlight(selectedIndex);
    }
}
