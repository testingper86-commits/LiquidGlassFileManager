#!/usr/bin/env python3
"""Static regression checks for Dynamic Island visibility and settings ownership."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / "app/src/main/java/com/music/echo/offline/files/MainActivity.java").read_text()
island = (ROOT / "app/src/main/java/com/music/echo/offline/files/DynamicIslandView.java").read_text()
app = (ROOT / "app/src/main/java/com/music/echo/offline/files/LiquidFilesApplication.java").read_text()
audio = (ROOT / "app/src/main/java/com/music/echo/offline/files/BackgroundAudioService.java").read_text()
workflow = (ROOT / ".github/workflows/android-build.yml").read_text()

assert "busyCount > 0 || BackgroundAudioService.playbackActive" in main
assert "DynamicIslandView.sync(this" in main
assert "settings.addView(section(\"Dynamic Island\"))" in main
for key in ("island_rgb", "island_glow", "island_haptics", "island_reduce_motion", "island_transparency", "island_speed"):
    assert key in main, f"Missing main Settings control: {key}"
assert "static void sync(Activity activity, boolean active)" in island
assert "if (!active)" in island
assert "onActivityResumed" in app and "syncDynamicIsland()" in app
assert "playbackActive = playing" in audio and "playbackActive = false" in audio
assert "Install and launch on Android 16 emulator" not in workflow
assert "Enable KVM access" not in workflow
assert "home_scroll_runtime_test.py" not in workflow
print("Dynamic Island visibility, playback state, Settings ownership, and emulator-free CI contracts passed.")
