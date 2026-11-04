package com.music.echo.offline.files;

import android.app.Application;

public final class LiquidFilesApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashLogger.install(this);
    }
}
