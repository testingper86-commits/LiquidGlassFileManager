package com.music.echo.offline.files;

import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Owns full-screen overlay transitions, secure-vault state and background restoration. */
final class MainOverlayController {
    private final MainActivity activity;
    private final OverlayAccessibilityController accessibility = new OverlayAccessibilityController();
    private View current;

    MainOverlayController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }

    View current() { return current; }

    void show(View overlay) {
        if (overlay == null) throw new IllegalArgumentException("overlay must not be null");
        if (current != null && current != overlay) close();
        if (current == null) {
            View backgroundScreen = activity.screens[activity.tab];
            accessibility.hideBackground(backgroundScreen, backgroundScreen == activity.settingsScroll ? activity.settings : null);
        }
        current = overlay;
        int alpha = ToxiVisualSystem.HIGH_CONTRAST ? 255 : 248;
        overlay.setBackgroundColor(Color.argb(alpha, Color.red(ToxiVisualSystem.CANVAS), Color.green(ToxiVisualSystem.CANVAS), Color.blue(ToxiVisualSystem.CANVAS)));
        if (overlay == activity.vaultScroll) activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        boolean blur = Build.VERSION.SDK_INT >= 31 && ToxiVisualSystem.booleanPreference("blur", false) && !ToxiVisualSystem.HIGH_CONTRAST;
        if (blur) {
            activity.screens[activity.tab].setRenderEffect(android.graphics.RenderEffect.createBlurEffect(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), android.graphics.Shader.TileMode.CLAMP));
            activity.screens[activity.tab].setVisibility(View.VISIBLE);
        } else {
            activity.screens[activity.tab].setVisibility(View.INVISIBLE);
        }
        overlay.setVisibility(View.VISIBLE);
        if (ToxiVisualSystem.booleanPreference("reduce_motion", false)) {
            overlay.setAlpha(1f);
            overlay.setTranslationY(0f);
        } else {
            overlay.setAlpha(0f);
            overlay.setTranslationY(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
            overlay.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
        }
        activity.syncNavigationVisibility(false);
    }

    void close() {
        if (current == null) {
            accessibility.restore();
            return;
        }
        boolean wasVault = current == activity.vaultScroll;
        if (current == activity.listView) {
            activity.listView.closeSearch();
            activity.listView.selecting = false;
            activity.listView.sel.clear();
            activity.pendingCat = -1;
        }
        current.setVisibility(View.GONE);
        current = null;
        if (wasVault) activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 31) activity.screens[activity.tab].setRenderEffect(null);
        activity.screens[activity.tab].setVisibility(View.VISIBLE);
        accessibility.restore();
        activity.syncNavigationVisibility(false);
        if (activity.tab == 0) activity.buildHome();
    }
}
