#!/usr/bin/env python3
from pathlib import Path
import re

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
    RES / "drawable/ic_toxifiles_brand_mark.xml",
    RES / "drawable/ic_toxifiles_app_icon.xml",
    RES / "drawable/ic_toxifiles_notification.xml",
    RES / "drawable/toxifiles_splash.xml",
    RES / "values/themes.xml",
    RES / "values-v31/themes.xml",
]
for path in required:
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f"Missing new brand resource: {path.relative_to(ROOT)}")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if '@drawable/ic_toxifiles_app_icon' not in manifest:
    raise SystemExit("Launcher manifest does not use the new ToxiFiles identity")

checks = {
    "MainActivity.java": ["ic_toxifiles_brand_mark", "ToxiVisualSystem.surface", "ToxiFileRow"],
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
print("ToxiFiles visual replacement audit passed: legacy systems removed; new identity and all primary screens integrated.")
