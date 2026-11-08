package com.music.echo.offline.files;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import androidx.activity.ComponentActivity;

/** Routes Activity lifecycle and screen-off events to the app-lock and vault policies. */
final class AppLockLifecycleController {
    private final ComponentActivity activity;
    private final Runnable screenOffAction;
    private final Runnable resumeAction;
    private final Runnable userLeaveAction;
    private boolean registered;
    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) screenOffAction.run();
        }
    };

    AppLockLifecycleController(ComponentActivity activity, Runnable screenOffAction, Runnable resumeAction, Runnable userLeaveAction) {
        if (activity == null || screenOffAction == null || resumeAction == null || userLeaveAction == null) {
            throw new IllegalArgumentException("activity and lifecycle callbacks must not be null");
        }
        this.activity = activity;
        this.screenOffAction = screenOffAction;
        this.resumeAction = resumeAction;
        this.userLeaveAction = userLeaveAction;
    }

    void register() {
        if (registered) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(screenOffReceiver, filter);
        registered = true;
    }

    void onResume() { resumeAction.run(); }

    void onUserLeaveHint() { userLeaveAction.run(); }

    void unregister() {
        if (!registered) return;
        try {
            activity.unregisterReceiver(screenOffReceiver);
        } catch (RuntimeException alreadyUnregistered) {
            // Activity teardown can race with framework receiver cleanup.
        } finally {
            registered = false;
        }
    }
}
