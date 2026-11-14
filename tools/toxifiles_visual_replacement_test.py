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
visual_system = (JAVA / "ToxiVisualSystem.java").read_text(encoding="utf-8")
if not touch_engine.is_file() or "ToxiPowerTouchEngine.attach(view);" not in visual_system:
    raise SystemExit("Theme-aware touch engine is not integrated into shared interactive controls")
if "ToxiThemeEngine.activate(THEME_INDEX);" not in visual_system:
    raise SystemExit("Shared touch controls are not synchronized with the active theme profile")
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
# Full application-wide anime theme acceptance gate.
theme_engine = (JAVA / "ToxiThemeEngine.java").read_text(encoding="utf-8")
visual_system = (JAVA / "ToxiVisualSystem.java").read_text(encoding="utf-8")
main_activity = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
navigation = (JAVA / "PrismNavigationDock.java").read_text(encoding="utf-8")
home = (JAVA / "NebulaHomeView.java").read_text(encoding="utf-8")
settings = (JAVA / "SettingsScreenController.java").read_text(encoding="utf-8")
browser = (JAVA / "BrowserView.java").read_text(encoding="utf-8")
identity_view = (JAVA / "ToxiThemeIdentityView.java").read_text(encoding="utf-8")
theme_test = (ROOT / "app/src/test/java/com/music/echo/offline/files/ToxiThemeSystemTest.java").read_text(encoding="utf-8")

for token in ["Geometry", "Material", "cornerScale", "spacingScale", "iconStroke", "motionScale"]:
    if token not in theme_engine:
        raise SystemExit(f"Theme engine is missing an independent profile mechanism: {token}")
for token in ["static boolean refresh(Context context)", "boolean changed = requestedIndex != THEME_INDEX",
               "PREFERENCES.getInt(", "applyWindowChrome(Window window, boolean edgeToEdge)",
               "styleDialog(dialog)", "fontFamily()", "ToxiPowerTouchEngine.attach(view);"]:
    if token not in visual_system:
        raise SystemExit(f"Shared visual runtime is missing required mechanism: {token}")
if "ToxiVisualSystem.refresh(this)" not in main_activity or "void recolor()" not in main_activity:
    raise SystemExit("MainActivity does not refresh persisted theme state and rebuild on resume")
if "ToxiVisualSystem.applyWindowChrome(getWindow(), true);" not in main_activity:
    raise SystemExit("MainActivity does not apply shared edge-to-edge system bar contrast")
if "getWindow().setStatusBarColor(Color.TRANSPARENT);" in main_activity or "getWindow().setNavigationBarColor(Color.TRANSPARENT);" in main_activity:
    raise SystemExit("MainActivity must route system bar setup through the shared window-chrome mechanism")
overlay_controller = (JAVA / "MainOverlayController.java").read_text(encoding="utf-8")
if "int selectedTab = tab;" not in main_activity or "overlayController.refreshAppearance();" not in main_activity:
    raise SystemExit("Theme refresh must preserve the active tab and delegate active overlay restoration")
if "void refreshAppearance()" not in overlay_controller or "current.setVisibility(View.VISIBLE)" not in overlay_controller or "backgroundScreen.setVisibility(blur ? View.VISIBLE : View.INVISIBLE)" not in overlay_controller:
    raise SystemExit("Overlay manager must preserve blur, visibility and active overlay semantics during theme refresh")
for name in ["ViewerActivity.java", "HtmlViewerActivity.java"]:
    content = (JAVA / name).read_text(encoding="utf-8")
    if "ToxiVisualSystem.applyWindowChrome(getWindow(), true);" not in content or "ToxiVisualSystem.refresh(this);" not in content:
        raise SystemExit(f"{name} is missing shared edge-to-edge theme refresh")
for name in ["ToxiToolsActivity.java", "DeveloperStudioActivity.java"]:
    content = (JAVA / name).read_text(encoding="utf-8")
    if not re.search(r"ToxiVisualSystem\.applyWindowChrome\(getWindow\(\),\s*false\);", content):
        raise SystemExit(f"{name} is missing shared themed system bars")
if not all(token in navigation for token in ['"Home", "Files", "Tools", "Settings"', "recolor(", "navigationSurface()"]):
    raise SystemExit("The four original navigation destinations and theme-aware dock must remain")
for token in ["syncPalette()", "BACKGROUND = ToxiVisualSystem.CANVAS", "LIME = ToxiVisualSystem.ACCENT", "ICE = ToxiThemeSystem.ACCENT_ENDS"]:
    if token not in home:
        raise SystemExit(f"Home/Tools theme palette is missing: {token}")
# Each primary destination must render the active poster, not only recolor generic controls.
for filename, content, token in [
    ("NebulaHomeView.java", home, "ToxiThemeIdentityView.create(a, 112"),
    ("SettingsScreenController.java", settings, "ToxiThemeIdentityView.create(activity, 116"),
    ("BrowserView.java", browser, "ToxiThemeIdentityView.create(a, 68"),
]:
    if token not in content:
        raise SystemExit(f"{filename} is missing its live anime identity surface")
for token in ["theme_poster_onepiece", "theme_poster_naruto", "theme_poster_demon",
              "theme_poster_dragonball", "theme_poster_aot", "void refresh()",
              "overlay.refresh()", "ToxiVisualSystem.THEME_NAMES[index]"]:
    if token not in identity_view:
        raise SystemExit(f"Shared anime identity surface is missing: {token}")
if "identityBanner.refresh();" not in browser:
    raise SystemExit("Files screen does not refresh its active anime artwork after theme changes")
theme_screen = (JAVA / "ThemeScreenController.java").read_text(encoding="utf-8")
for token in ["ToxiVisualSystem.THEME_NAMES", "ToxiVisualSystem.applyTheme", "activity.recolor()"]:
    if token not in theme_screen and token not in settings:
        raise SystemExit(f"Theme Studio and Settings must support live theme selection: {token}")
for token in ["activeProfileCanBeSwitchedAndRestoredWithoutStaleTokens", "eachThemeHasDistinctAccentAndChromeIdentity"]:
    if token not in theme_test:
        raise SystemExit(f"Theme unit regression test missing: {token}")
if "ToxiVisualSystem.refresh(this);" not in main_activity:
    raise SystemExit("Main screen must reload persisted appearance state after returning to foreground")

print("ToxiFiles visual replacement audit passed: five themes, five real WebP posters, shared live theme runtime, edge-to-edge system bar contract, all primary activities, metallic home/tools surfaces and four-tab navigation.")

