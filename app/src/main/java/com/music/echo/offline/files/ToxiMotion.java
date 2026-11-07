package com.music.echo.offline.files;

import android.view.View;

/** Low-cost motion primitives for focus and task-state feedback in the new interface. */
final class ToxiMotion {
    private ToxiMotion() { }
    static void attach(View view) { view.setLayerType(View.LAYER_TYPE_NONE, null); }
    static void detach(View view) { view.setAlpha(1f); view.setTranslationZ(0f); }
    static void setActive(View view, boolean active) {
        view.setAlpha(active ? 0.94f : 1f);
        view.setTranslationZ(active ? ToxiVisualSystem.dp(1) : 0f);
    }
    static void watchFocus(View view) {
        view.setOnFocusChangeListener((target, focused) -> {
            target.setTranslationZ(focused ? ToxiVisualSystem.dp(2) : 0f);
            target.setAlpha(focused ? 1f : 0.98f);
        });
    }
}
