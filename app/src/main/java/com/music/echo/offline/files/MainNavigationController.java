package com.music.echo.offline.files;

import android.os.Environment;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.io.File;

/** Coordinates tab transitions, overlay-aware Back handling and the persistent navigation surface. */
final class MainNavigationController {
    private final MainActivity activity;
    private MainNavigationBar navigationBar;

    MainNavigationController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    void build() {
        if (navigationBar != null) return;
        navigationBar = new MainNavigationBar(activity, activity.root, this::selectTab);
        navigationBar.build();
    }

    void selectTab(int index) {
        View[] screens = activity.screens;
        if (screens == null || index < 0 || index >= screens.length || navigationBar == null) return;
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
        if (ToxiVisualSystem.booleanPreference("reduce_motion", false)) {
            selected.setAlpha(1f);
            selected.setTranslationY(0f);
        } else {
            selected.setAlpha(0f);
            selected.setTranslationY(ToxiVisualSystem.dp(8));
            selected.animate().alpha(1f).translationY(0f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        if (index == 0) {
            activity.buildHome();
        } else if (index == 1) {
            if (activity.filesView.cur == null) {
                String configured = ToxiVisualSystem.PREFERENCES.getString("default_folder", null);
                File start = configured != null && new File(configured).isDirectory()
                        ? new File(configured) : Environment.getExternalStorageDirectory();
                activity.filesView.showDir(start);
            }
        } else if (index == 2) {
            activity.buildTools();
        } else {
            activity.buildSettings();
        }
        moveHighlight(index);
    }

    void handleBackNavigation() {
        if (activity.currentOverlay() == activity.listView
                && activity.listView.handleBack()) return;
        if (activity.currentOverlay() != null) {
            activity.closeOverlay();
            return;
        }
        if (activity.tab == 1 && activity.filesView.handleBack()) return;
        if (activity.tab != 0) {
            selectTab(0);
            return;
        }
        activity.finish();
    }

    void syncVisibility(boolean selecting) {
        if (navigationBar == null) return;
        boolean hidden = activity.currentOverlay() != null || selecting;
        navigationBar.setVisible(!hidden);
    }

    void applyBottomInset() {
        if (navigationBar != null) navigationBar.applyBottomInset();
    }

    void moveHighlight(int index) {
        if (navigationBar != null) navigationBar.moveHighlight(index);
    }

    void recolor(int selectedIndex) {
        if (navigationBar != null) navigationBar.recolor(selectedIndex);
    }
}
