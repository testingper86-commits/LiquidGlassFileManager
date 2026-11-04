from pathlib import Path

root = Path(__file__).resolve().parents[1]
island = (root / "app/src/main/java/com/music/echo/offline/files/DynamicIslandView.java").read_text()
main = (root / "app/src/main/java/com/music/echo/offline/files/MainActivity.java").read_text()
application = (root / "app/src/main/java/com/music/echo/offline/files/LiquidFilesApplication.java").read_text()
player = (root / "app/src/main/java/com/music/echo/offline/files/BackgroundAudioService.java").read_text()
workflow = (root / ".github/workflows/android-build.yml").read_text()
checks = {
    "island is hidden immediately on attachment": 'island.setVisibility(View.GONE)' in island,
    "file-operation count drives island visibility": 'activity.busyCount > 0 || playerActive' in island and 'DynamicIslandView.refresh(this)' in main,
    "Application lifecycle uses the existing Dynamic Island refresh API": 'DynamicIslandView.refresh((MainActivity) activity)' in application and 'syncDynamicIsland()' not in application,
    "actual media playback drives island visibility": 'DynamicIslandView.setPlayerActive(playing)' in player,
    "playback stop clears island activity": 'DynamicIslandView.setPlayerActive(false)' in player,
    "island settings are available in the main Settings screen": '"Dynamic Island"' in main and 'DynamicIslandView.showSettings(this)' in main,
    "island offers playback controls during playback": '"Play / resume"' in island and 'BackgroundAudioService.ACTION_STOP' in island,
    "build does not provision or launch an emulator": 'Install and launch on Android 16 emulator' not in workflow and 'avdmanager' not in workflow and 'home_scroll_runtime_test.py' not in workflow,
    "JVM tests and Android lint remain enabled": 'testDebugUnitTest' in workflow and 'lintDebug' in workflow,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
