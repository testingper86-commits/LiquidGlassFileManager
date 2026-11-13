package com.music.echo.offline.files;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.io.File;
import android.os.Environment;

/** Coordinates the restored four-tab floating dock. */
final class MainNavigationController {
    private final MainActivity activity;
    private PrismNavigationDock navigationBar;
    private boolean imeVisible;

    MainNavigationController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }
    void build() {
        if (navigationBar != null) return;
        navigationBar = new PrismNavigationDock(activity, activity.root, this::selectTab);
        navigationBar.build();
    }
    void selectTab(int index) {
        if (navigationBar == null || index < 0 || index > 3) return;
        navigationBar.moveHighlight(index);
        View[] screens = activity.screens;
        if (screens == null || index >= screens.length) return;
        activity.closeOverlay();
        activity.tab = index;
        syncVisibility(false);
        for (int i = 0; i < screens.length; i++) {
            View screen = screens[i];
            screen.animate().cancel();
            screen.setVisibility(i == index ? View.VISIBLE : View.GONE);
            screen.setAlpha(1f);
            screen.setTranslationY(0f);
        }
        View selected = screens[index];
        if (!ToxiVisualSystem.booleanPreference("reduce_motion", false)) {
            selected.setAlpha(0f); selected.setTranslationY(ToxiVisualSystem.dp(8));
            selected.animate().alpha(1f).translationY(0f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        if (index == 0) {
            activity.buildHome();
            activity.homeScroll.post(() -> activity.homeScroll.scrollTo(0, 0));
        } else if (index == 1 && activity.filesView.cur == null) {
            String configured = ToxiVisualSystem.PREFERENCES.getString("default_folder", null);
            File start = configured != null && new File(configured).isDirectory()
                    ? new File(configured) : Environment.getExternalStorageDirectory();
            activity.filesView.showDir(start);
        } else if (index == 2) activity.buildTools();
        else activity.buildSettings();
    }
    void handleBackNavigation() {
        if (activity.currentOverlay() == activity.listView && activity.listView.handleBack()) return;
        if (activity.currentOverlay() != null) { activity.closeOverlay(); return; }
        if (activity.tab == 1 && activity.filesView.handleBack()) return;
        if (activity.tab != 0) { selectTab(0); return; }
        activity.finish();
    }
    void syncVisibility(boolean selecting) {
        if (navigationBar != null) navigationBar.setVisible(activity.currentOverlay() == null && !selecting);
    }
    void applyBottomInset() { if (navigationBar != null) { navigationBar.applyBottomInset(); navigationBar.setImeVisible(imeVisible); } }
    void setImeVisible(boolean visible) { imeVisible = visible; if (navigationBar != null) navigationBar.setImeVisible(visible); }
    void moveHighlight(int index) { if (navigationBar != null) navigationBar.moveHighlight(index); }
    void recolor(int selectedIndex) { if (navigationBar != null) navigationBar.recolor(Math.max(0, Math.min(3, selectedIndex))); }
}
