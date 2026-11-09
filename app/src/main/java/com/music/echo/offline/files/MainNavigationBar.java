package com.music.echo.offline.files;

import android.widget.FrameLayout;
import java.util.function.IntConsumer;

/**
 * Stable compatibility boundary for existing navigation callers.
 * The active visual implementation is PrismNavigationDock.
 */
final class MainNavigationBar {
    private final PrismNavigationDock delegate;

    MainNavigationBar(MainActivity activity, FrameLayout root, IntConsumer onTabSelected) {
        if (activity == null || root == null || onTabSelected == null) {
            throw new IllegalArgumentException("navigation dependencies must not be null");
        }
        delegate = new PrismNavigationDock(activity, root, onTabSelected);
    }

    void build() {
        delegate.build();
    }

    void setVisible(boolean visible) {
        delegate.setVisible(visible);
    }

    void applyBottomInset() {
        delegate.applyBottomInset();
    }

    void moveHighlight(int index) {
        delegate.moveHighlight(index);
    }

    void recolor(int selectedIndex) {
        delegate.recolor(selectedIndex);
    }
}
