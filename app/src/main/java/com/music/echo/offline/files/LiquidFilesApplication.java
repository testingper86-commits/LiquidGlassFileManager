package com.music.echo.offline.files;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public final class LiquidFilesApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashLogger.install(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof MainActivity) ((MainActivity) activity).syncDynamicIsland();
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {
                if (activity instanceof MainActivity) DynamicIslandView.detach(activity);
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {
                if (activity instanceof MainActivity) DynamicIslandView.detach(activity);
            }
        });
    }
}
