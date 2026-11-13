#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/music/echo/offline/files"
RES = ROOT / "app/src/main/res"
legacy_files = [
    "Ui.java", "Glyphs.java", "Rows.java", "ThemeCatalog.java", "OfflineToolUi.java",
    "GlowRing.java", "GradientRingView.java", "TouchGradientRingDrawable.java", "SquigglySeekBar.java",
]
for name in legacy_files:
    if (JAVA / name).exists():
        raise SystemExit(f"Legacy visual source remains: {name}")

legacy_tokens = ["Ui.", "Glyphs.", "Rows.", "ThemeCatalog.", "OfflineToolUi", "GlowRing.", "GradientRingView", "TouchGradientRingDrawable", "SquigglySeekBar"]
for source in JAVA.glob("*.java"):
    content = source.read_text(encoding="utf-8")
    for token in legacy_tokens:
        if token in content:
            raise SystemExit(f"Legacy visual reference {token!r} in {source.relative_to(ROOT)}")

required = [
    RES / "drawable/ic_toxifiles_notification.xml",
    RES / "drawable/toxifiles_splash.xml",
    RES / "drawable/toxifiles_splash_transparent.xml",
    RES / "drawable-nodpi/toxifiles_master_art.webp",
    RES / "values/themes.xml",
    RES / "values-v31/themes.xml",
]
for path in required:
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"Missing current visual resource: {path.relative_to(ROOT)}")

manifest_root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android_ns = "{http://schemas.android.com/apk/res/android}"
application = manifest_root.find("application")
if application is None:
    raise SystemExit("Android manifest has no application element")
icon = application.get(android_ns + "icon")
round_icon = application.get(android_ns + "roundIcon")
if icon != "@drawable/toxifiles_master_art" or round_icon != "@drawable/toxifiles_master_art":
    raise SystemExit("Launcher manifest must use the current ToxiFiles master artwork")

checks = {
    "MainActivity.java": ["ToxiVisualSystem.surface", "ToxiFileRow"],
    "BrowserView.java": ["ToxiFileRow", "ToxiIcons", "buildGridRow"],
    "ViewerActivity.java": ["ToxiVisualSystem.initialize", "ToxiIcons", "ToxiProgressSeekBar"],
    "HtmlViewerActivity.java": ["ToxiVisualSystem.initialize", "ToxiIcons.REFRESH"],
    "AppLock.java": ["ToxiVisualSystem.booleanPreference"],
    "BackgroundAudioService.java": ["R.drawable.ic_toxifiles_notification"],
    "FileKinds.java": ["ToxiIcons.PDF", "ToxiIcons.TEXT"],
}
for filename, tokens in checks.items():
    content = (JAVA / filename).read_text(encoding="utf-8")
    for token in tokens:
        if token not in content:
            raise SystemExit(f"{filename} is missing new visual integration: {token}")

if (JAVA / "DynamicIslandView.java").exists():
    raise SystemExit("Dynamic Island implementation must remain removed")
for source in JAVA.glob("*.java"):
    content = source.read_text(encoding="utf-8")
    if "DynamicIslandView" in content or "dynamicIsland" in content:
        raise SystemExit(f"Dynamic Island runtime reference remains in {source.relative_to(ROOT)}")

theme = (JAVA / "ToxiThemeSystem.java").read_text(encoding="utf-8")
match = re.search(r'static final String\[\] NAMES = \{(.*?)\};', theme, re.S)
if not match or len(re.findall(r'"[^"]+"', match.group(1))) != 5:
    raise SystemExit("The new visual system must define exactly five curated themes")
touch_engine = JAVA / "ToxiPowerTouchEngine.java"
if not touch_engine.is_file() or "ToxiPowerTouchEngine.attach(view, THEME_INDEX)" not in (JAVA / "ToxiVisualSystem.java").read_text(encoding="utf-8"):
    raise SystemExit("Theme-aware touch engine is not integrated into shared interactive controls")
for token in ["ACCENTS", "ACCENT_ENDS", "CANVAS", "SURFACES", "RAISED", "EDGES", "MUTED", "SOFT_ACCENTS"]:
    if token not in theme:
        raise SystemExit(f"Missing independent theme token group: {token}")

poster_map = {
    0: "theme_poster_onepiece",
    1: "theme_poster_naruto",
    2: "theme_poster_demon",
    3: "theme_poster_dragonball",
    4: "theme_poster_aot",
}
screen = (JAVA / "ThemeScreenController.java").read_text(encoding="utf-8")
for index, resource in poster_map.items():
    if resource not in screen:
        raise SystemExit(f"Theme index {index} is missing poster mapping: {resource}")
    poster = RES / f"drawable-nodpi/{resource}.webp"
    if not poster.is_file() or poster.stat().st_size < 1000:
        raise SystemExit(f"Missing or invalid theme poster: {poster.relative_to(ROOT)}")
    data = poster.read_bytes()
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise SystemExit(f"Theme poster is not a real WebP image: {poster.relative_to(ROOT)}")
print("ToxiFiles visual replacement audit passed: 5 real WebP theme posters, current splash/launcher/notification resources and primary screens integrated.")
