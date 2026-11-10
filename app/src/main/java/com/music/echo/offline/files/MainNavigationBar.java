package com.music.echo.offline.files;

import android.widget.FrameLayout;
import java.util.function.IntConsumer;

/**
 * Stable compatibility boundary for existing navigation callers.
 * The active visual implementation is PrismNavigationDock.
 */
final class MainNavigationBar {
    private final PrismNavigationDock delegate;
    private final IntConsumer onTabSelected;

    MainNavigationBar(MainActivity activity, FrameLayout root, IntConsumer onTabSelected) {
        if (activity == null || root == null || onTabSelected == null) {
            throw new IllegalArgumentException("navigation dependencies must not be null");
        }
        this.onTabSelected = onTabSelected;
        delegate = new PrismNavigationDock(activity, root, this::selectTab);
    }

    private void selectTab(int index) {
        onTabSelected.accept(index);
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
