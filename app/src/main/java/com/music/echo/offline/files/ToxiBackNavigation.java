package com.music.echo.offline.files;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import java.util.Objects;

/** Central lifecycle-aware system Back registration for ToxiFiles activities. */
final class ToxiBackNavigation {
    private ToxiBackNavigation() { }

    static void bind(ComponentActivity activity, Runnable handler) {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(handler, "handler");
        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                handler.run();
            }
        });
    }
}
