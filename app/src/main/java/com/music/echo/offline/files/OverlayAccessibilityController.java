package com.music.echo.offline.files;

import android.view.View;

/** Preserves and restores the accessibility and visibility state behind a full-screen overlay. */
final class OverlayAccessibilityController {
    private View backgroundScreen;
    private View backgroundContent;
    private int screenAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    private int contentAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    private int screenVisibility = View.VISIBLE;

    void hideBackground(View screen, View content) {
        if (screen == null || backgroundScreen != null) return;
        backgroundScreen = screen;
        screenVisibility = screen.getVisibility();
        screenAccessibility = screen.getImportantForAccessibility();
        screen.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (content != null) {
            backgroundContent = content;
            contentAccessibility = content.getImportantForAccessibility();
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    void restore() {
        if (backgroundContent != null) {
            backgroundContent.setImportantForAccessibility(contentAccessibility);
            backgroundContent = null;
            contentAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        }
        if (backgroundScreen == null) return;
        backgroundScreen.setImportantForAccessibility(screenAccessibility);
        backgroundScreen.setVisibility(screenVisibility);
        backgroundScreen = null;
        screenAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        screenVisibility = View.VISIBLE;
    }
}
