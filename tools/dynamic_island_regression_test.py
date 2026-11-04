from pathlib import Path

root = Path(__file__).resolve().parents[1]
island = (root / "app/src/main/java/com/music/echo/offline/files/DynamicIslandView.java").read_text()
main = (root / "app/src/main/java/com/music/echo/offline/files/MainActivity.java").read_text()
application = (root / "app/src/main/java/com/music/echo/offline/files/LiquidFilesApplication.java").read_text()
player = (root / "app/src/main/java/com/music/echo/offline/files/BackgroundAudioService.java").read_text()
viewer = (root / "app/src/main/java/com/music/echo/offline/files/ViewerActivity.java").read_text()
workflow = (root / ".github/workflows/android-build.yml").read_text()
checks = {
    "island is hidden immediately on attachment": 'island.setVisibility(View.GONE)' in island,
    "file-operation count drives island visibility": 'activity.busyCount > 0 || playerActive' in island and 'DynamicIslandView.refresh(this)' in main,
    "Application lifecycle attaches the island and restores active playback visibility": 'DynamicIslandView.attach(activity)' in application and 'DynamicIslandView.refresh((MainActivity) activity)' in application,
    "foreground music playback exposes native interactive transport controls": 'playbackIsland.addView(islandTransportButton("‹", "Previous track", () -> go(-1)))' in viewer and 'playbackIsland.addView(islandTransportButton("›", "Next track", () -> go(1)))' in viewer and 'seekIsland(-10000)' in viewer and 'seekIsland(10000)' in viewer and 'playbackToggleIsland' in viewer,
    "playback island is synchronized on pause and resume transitions": 'syncPlaybackIsland();' in viewer and 'audioWasPlayingBeforeBackground' in viewer,
    "actual media playback drives island visibility": 'DynamicIslandView.setPlayerActive(playing)' in player,
    "playback stop clears island activity": 'DynamicIslandView.setPlayerActive(false)' in player,
    "island settings are available in the main Settings screen": '"Dynamic Island"' in main and 'DynamicIslandView.showSettings(this)' in main,
    "island settings use an aligned glass surface, blue intensity and speed controls": 'setLayout(Math.min(available, dp(420)), -2)' in island and 'scroll.addView(box' in island and 'sw.setMinHeight(dp(48))' in island and 'island_blue_intensity' in island and 'settingsSurface' in island and 'spinnerLp' in island,
    "island offers playback controls during playback": '"Play / resume"' in island and 'BackgroundAudioService.ACTION_STOP' in island,
    "build does not provision or launch an emulator": 'Install and launch on Android 16 emulator' not in workflow and 'avdmanager' not in workflow and 'home_scroll_runtime_test.py' not in workflow,
    "JVM tests and Android lint remain enabled": 'testDebugUnitTest' in workflow and 'lintDebug' in workflow,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
