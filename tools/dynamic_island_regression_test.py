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
    "Viewer audio controls use the shared foreground service": 'BackgroundAudioService.ACTION_START' in viewer and 'BackgroundAudioService.ACTION_SEEK' in viewer and 'BackgroundAudioService.ACTION_PLAY' in viewer and 'BackgroundAudioService.ACTION_PAUSE' in viewer and 'MediaPlayer mp;' not in viewer,
    "Viewer has no duplicate playback capsule or background handoff": 'playbackIsland' not in viewer and 'syncPlaybackIsland' not in viewer and 'backgroundAudioHandoff' not in viewer and 'audioWasPlayingBeforeBackground' not in viewer,
    "actual media playback drives island visibility": 'DynamicIslandView.setPlayerActive(playing)' in player,
    "service exposes real seek, volume and speed transport actions": 'ACTION_SEEK' in player and 'ACTION_VOLUME' in player and 'ACTION_SPEED' in player and 'player.seekTo' in player,
    "playback stop clears island activity": 'DynamicIslandView.setPlayerActive(false)' in player,
    "island settings are available in the main Settings screen": '"Dynamic Island"' in main and 'DynamicIslandView.showSettings(this)' in main,
    "island settings use a glass surface, transparency and speed controls without blue slider": 'Glass opacity' in island and 'Animation speed' in island and 'island_blue_intensity' not in island and 'Island blue intensity' not in island,
    "Island can be dragged and position is persisted within parent bounds": 'MotionEvent.ACTION_MOVE' in island and 'island_position_x' in island and 'island_position_y' in island and 'parent.getWidth() - getWidth()' in island,
    "island offers playback controls during playback": '"Play / resume"' in island and 'BackgroundAudioService.ACTION_STOP' in island,
    "build does not provision or launch an emulator": 'Install and launch on Android 16 emulator' not in workflow and 'avdmanager' not in workflow and 'home_scroll_runtime_test.py' not in workflow,
    "JVM tests and Android lint remain enabled": 'testDebugUnitTest' in workflow and 'lintDebug' in workflow,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
