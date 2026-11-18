package com.music.echo.offline.files;

/** Shared theme-index persistence boundary used by the runtime and lifecycle regression tests. */
final class ToxiThemePersistence {
    interface Store {
        int readTheme();
        void writeTheme(int index);
    }

    private ToxiThemePersistence() { }

    /** Restores a persisted selection into the active runtime profile, defaulting safely to theme zero. */
    static int restore(Store store) {
        int index = store == null ? 0 : ToxiThemeSystem.normalize(store.readTheme());
        ToxiThemeEngine.activate(index);
        return index;
    }

    /** Normalizes, activates and persists a selection through the same boundary used during restoration. */
    static int save(Store store, int requestedIndex) {
        int index = ToxiThemeSystem.normalize(requestedIndex);
        ToxiThemeEngine.activate(index);
        if (store != null) store.writeTheme(index);
        return index;
    }
}
