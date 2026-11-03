#!/usr/bin/env python3
"""Runtime layout, navigation, and swipe regression tests for ToxiFiles."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
PKG = "com.liquidfiles.offline.debug"
TEST_DIR = "/sdcard/Download/ToxiFilesRuntimeTest"
TEST_FILE = f"{TEST_DIR}/runtime-layout.bin"
RENAMED_TEST_FILE = f"{TEST_DIR}/runtime-layout-renamed.bin"
DIST = "dist"
SCREEN_W = 720
SCREEN_H = 1600


def run(*args):
    proc = subprocess.run([ADB, *map(str, args)], text=True,
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if proc.returncode != 0:
        raise RuntimeError(f"adb {' '.join(map(str, args))} failed ({proc.returncode}): {proc.stdout.strip()}")
    return proc.stdout.strip()


def record(message):
    print(message)
    with open(os.path.join(DIST, "runtime-layout-results.txt"), "a", encoding="utf-8") as stream:
        stream.write(message + "\n")


def parse_bounds(node):
    match = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.attrib.get("bounds", ""))
    return tuple(map(int, match.groups())) if match else None


def dump(name, attempts=4):
    remote = f"/sdcard/{name}.xml"
    local = os.path.join(DIST, f"{name}.xml")
    last_error = "no hierarchy file was produced"
    if os.path.exists(local):
        os.remove(local)
    for attempt in range(attempts):
        run("shell", "rm", "-f", remote)
        try:
            output = run("shell", "uiautomator", "dump", remote)
            last_error = output or "uiautomator dump returned no status text"
        except RuntimeError as error:
            last_error = str(error)
        for _ in range(10):
            probe = subprocess.run([ADB, "shell", "test", "-s", remote], text=True,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            if probe.returncode == 0:
                try:
                    run("pull", remote, local)
                    root = ET.parse(local).getroot()
                    if root.tag != "hierarchy":
                        raise ET.ParseError(f"unexpected hierarchy root: {root.tag!r}")
                    if attempt:
                        record(f"PASS: UI hierarchy capture for {name} recovered after {attempt + 1} attempts")
                    return root
                except (RuntimeError, ET.ParseError) as error:
                    last_error = str(error)
            time.sleep(0.15)
        time.sleep(0.25)
    listing = subprocess.run([ADB, "shell", "ls", "-l", remote], text=True,
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout.strip()
    raise RuntimeError(
        f"uiautomator dump did not produce valid XML for {name!r} after {attempts} attempts; "
        f"last result: {last_error}; remote file: {listing or 'missing'}"
    )


def screenshot(name):
    remote = f"/sdcard/{name}.png"
    local = os.path.join(DIST, f"{name}.png")
    run("shell", "screencap", "-p", remote)
    run("pull", remote, local)


def node_is_visible(node):
    return node.attrib.get("visible-to-user", "true").lower() != "false"


def find_text(root, needle, exact=False):
    target = needle.strip()
    for node in root.iter("node"):
        if not node_is_visible(node):
            continue
        value = node.attrib.get("text", "").strip()
        matched = value == target if exact else target.lower() in value.lower()
        if matched and parse_bounds(node):
            return node
    return None


def find_last_text(root, needle, exact=False):
    target = needle.strip()
    matches = []
    for node in root.iter("node"):
        if not node_is_visible(node):
            continue
        value = node.attrib.get("text", "").strip()
        matched = value == target if exact else target.lower() in value.lower()
        if matched and parse_bounds(node):
            matches.append(node)
    return matches[-1] if matches else None


def find_description(root, description):
    for node in root.iter("node"):
        if node_is_visible(node) and node.attrib.get("content-desc", "").strip() == description:
            if parse_bounds(node):
                return node
    return None


def scroll_view_containing(root, marker):
    for container in root.iter("node"):
        if container.attrib.get("class") != "android.widget.ScrollView" or not node_is_visible(container):
            continue
        if any(node_is_visible(child) and child.attrib.get("text", "").strip() == marker
               and parse_bounds(child) for child in container.iter("node")):
            return container
    raise AssertionError(f"Could not find the visible native ScrollView containing {marker!r}")


def smallest_container_containing(root, required_texts):
    container_classes = {"android.widget.LinearLayout", "android.widget.FrameLayout",
                         "android.widget.ScrollView", "android.widget.ListView"}
    candidates = []
    for container in root.iter("node"):
        if container.attrib.get("class") not in container_classes:
            continue
        texts = {child.attrib.get("text", "").strip() for child in container.iter("node")}
        if all(label in texts for label in required_texts):
            candidates.append(container)
    if not candidates:
        raise AssertionError(f"Could not find a native container holding {required_texts!r}")
    return min(candidates, key=lambda node: sum(1 for _ in node.iter("node")))


def tap_node(node):
    rect = parse_bounds(node)
    if not rect:
        raise AssertionError("Cannot tap a node without valid accessibility bounds")
    left, top, right, bottom = rect
    run("shell", "input", "tap", (left + right) // 2, (top + bottom) // 2)


def swipe_up():
    x = SCREEN_W // 2
    run("shell", "input", "swipe", x, int(SCREEN_H * 0.80), x, int(SCREEN_H * 0.28), "600")
    time.sleep(0.45)


def swipe_down():
    x = SCREEN_W // 2
    run("shell", "input", "swipe", x, int(SCREEN_H * 0.28), x, int(SCREEN_H * 0.80), "600")
    time.sleep(0.45)


def assert_visible(root, text, exact=False):
    if find_text(root, text, exact=exact) is None:
        raise AssertionError(f"Expected visible text not found: {text!r}")
    record(f"PASS: visible UI text: {text}")


def assert_absent(root, text, label):
    if find_text(root, text, exact=True) is not None:
        raise AssertionError(f"{label}: inactive screen content remained accessible: {text!r}")
    record(f"PASS: {label} hides inactive screen content: {text}")


def assert_navigation(root, visible, label):
    labels = {node.attrib.get("text", "").strip() for node in root.iter("node") if node_is_visible(node)}
    found = {"Home", "Files"}.intersection(labels)
    if visible and found != {"Home", "Files"}:
        raise AssertionError(f"{label}: expected Home/Files navigation labels, found {sorted(found)}")
    if not visible and found:
        raise AssertionError(f"{label}: bottom navigation leaked into this screen: {sorted(found)}")
    record(f"PASS: bottom navigation {'visible' if visible else 'hidden'} on {label}")


def assert_scrollable(root, label):
    if not any(node.attrib.get("scrollable") == "true" for node in root.iter("node")):
        raise AssertionError(f"{label}: no scrollable accessibility container")
    record(f"PASS: {label} exposes a scrollable native container")


def container_with_text_and_description(root, text, description):
    container_classes = {"android.widget.LinearLayout", "android.widget.FrameLayout"}
    candidates = []
    for container in root.iter("node"):
        if container.attrib.get("class") not in container_classes:
            continue
        descendants = list(container.iter("node"))
        texts = {child.attrib.get("text", "").strip() for child in descendants}
        descriptions = {child.attrib.get("content-desc", "").strip() for child in descendants}
        if text in texts and description in descriptions:
            candidates.append((len(descendants), container))
    if not candidates:
        raise AssertionError(f"Could not find the row containing {text!r} and {description!r}")
    return min(candidates, key=lambda item: item[0])[1]


def assert_no_sibling_view_overlap(root, label):
    """Reject intersecting visible siblings in native LinearLayout containers.

    FrameLayout siblings are intentionally allowed to overlap (for example, an icon
    centered over its background). LinearLayout siblings are not: intersecting bounds
    there indicate that one section or row is drawing into another.
    """
    checked_pairs = 0
    for parent in root.iter("node"):
        if parent.attrib.get("class") != "android.widget.LinearLayout":
            continue
        siblings = []
        for child in parent:
            if child.tag != "node" or not node_is_visible(child):
                continue
            rect = parse_bounds(child)
            if not rect:
                continue
            left, top, right, bottom = rect
            if right <= left or bottom <= top or bottom <= 0 or top >= SCREEN_H:
                continue
            label_text = (child.attrib.get("text", "").strip()
                          or child.attrib.get("content-desc", "").strip()
                          or child.attrib.get("class", "").rsplit(".", 1)[-1])
            siblings.append((label_text, rect))
        for index, (first_label, first) in enumerate(siblings):
            for second_label, second in siblings[index + 1:]:
                checked_pairs += 1
                overlap_w = min(first[2], second[2]) - max(first[0], second[0])
                overlap_h = min(first[3], second[3]) - max(first[1], second[1])
                if overlap_w > 2 and overlap_h > 2:
                    raise AssertionError(
                        f"{label}: sibling layout bounds overlap: {first_label!r} {first} "
                        f"with {second_label!r} {second}"
                    )
    record(f"PASS: no overlapping sibling layout bounds on {label} ({checked_pairs} pairs checked)")


def assert_no_text_overlap(root, label):
    text_nodes = []
    for node in root.iter("node"):
        if not node_is_visible(node):
            continue
        value = node.attrib.get("text", "").strip()
        cls = node.attrib.get("class", "")
        if not value or not ("TextView" in cls or cls.endswith("Button") or cls.endswith("EditText")):
            continue
        rect = parse_bounds(node)
        if not rect:
            continue
        left, top, right, bottom = rect
        if right <= left or bottom <= top or bottom <= 0 or top >= SCREEN_H:
            continue
        text_nodes.append((value, rect))
    for i, (first_text, first) in enumerate(text_nodes):
        for second_text, second in text_nodes[i + 1:]:
            overlap_w = min(first[2], second[2]) - max(first[0], second[0])
            overlap_h = min(first[3], second[3]) - max(first[1], second[1])
            if overlap_w > 2 and overlap_h > 2:
                raise AssertionError(f"{label}: text bounds overlap: {first_text!r} {first} with {second_text!r} {second}")
    record(f"PASS: no overlapping visible text bounds on {label} ({len(text_nodes)} text controls checked)")
    assert_no_sibling_view_overlap(root, label)


def scroll_until_text(needle, prefix, exact=False, max_swipes=12):
    for attempt in range(max_swipes + 1):
        root = dump(f"{prefix}-{attempt}")
        node = find_text(root, needle, exact=exact)
        if node is not None:
            return root, node
        if attempt < max_swipes:
            swipe_up()
    raise AssertionError(f"Could not scroll to visible text {needle!r} on {prefix}")


def scroll_until_text_down(needle, prefix, exact=False, max_swipes=12):
    """Find a previously visited Settings row after the test has scrolled lower."""
    for attempt in range(max_swipes + 1):
        root = dump(f"{prefix}-{attempt}")
        node = find_text(root, needle, exact=exact)
        if node is not None:
            return root, node
        if attempt < max_swipes:
            swipe_down()
    raise AssertionError(f"Could not scroll down to visible text {needle!r} on {prefix}")


def scroll_until_section(label, prefix, max_swipes=12):
    expected = {f"+  {label.upper()}", f"-  {label.upper()}"}
    for attempt in range(max_swipes + 1):
        root = dump(f"{prefix}-{attempt}")
        for node in root.iter("node"):
            if node_is_visible(node) and node.attrib.get("text", "").strip().upper() in expected and parse_bounds(node):
                return root, node
        if attempt < max_swipes:
            swipe_up()
    raise AssertionError(f"Could not scroll to Settings section {label!r}")


def open_settings():
    root = dump("home-before-settings")
    button = find_description(root, "Settings")
    if button is None:
        raise AssertionError("Home Settings button is missing from the accessibility hierarchy")
    tap_node(button)
    time.sleep(0.65)
    root = dump("settings-open")
    assert_navigation(root, False, "Settings")
    return root


def test_font_styles():
    for style in ("System Sans", "Rounded", "Serif", "Monospace"):
        home = dump(f"home-font-{style.lower().replace(' ', '-')}")
        assert_visible(home, "Your files", exact=True)
        assert_no_text_overlap(home, f"Home / {style}")
        settings = open_settings()
        assert_no_text_overlap(settings, f"Settings / {style}")
        font_row = find_text(settings, "Font style", exact=True)
        if font_row is None:
            raise AssertionError(f"Font style row is not visible for {style}")
        tap_node(font_row)
        time.sleep(0.35)
        dialog = dump(f"font-dialog-{style.lower().replace(' ', '-')}")
        assert_visible(dialog, style, exact=True)
        choice = find_text(dialog, style, exact=True)
        tap_node(choice)
        time.sleep(2.2)
        home = dump(f"home-after-font-{style.lower().replace(' ', '-')}")
        assert_visible(home, "Your files", exact=True)
        assert_no_text_overlap(home, f"Home / {style} after font change")
        screenshot(f"toxifiles-font-{style.lower().replace(' ', '-')}")
        record(f"PASS: {style} font selection and Activity recreation preserve Home layout")


def other_category_node(root):
    for node in root.iter("node"):
        description = node.attrib.get("content-desc", "")
        if node_is_visible(node) and description.startswith("Other,") and "Open category" in description:
            if parse_bounds(node):
                return node
    return None


def scroll_until_other_category(prefix, max_swipes=10):
    for attempt in range(max_swipes + 1):
        root = dump(f"{prefix}-{attempt}")
        node = other_category_node(root)
        if node is not None:
            return root, node
        if attempt < max_swipes:
            swipe_up()
    raise AssertionError("Could not bring the Home Other category card into view")


def main():
    global SCREEN_W, SCREEN_H
    os.makedirs(DIST, exist_ok=True)
    result_path = os.path.join(DIST, "runtime-layout-results.txt")
    if os.path.exists(result_path):
        os.remove(result_path)

    original_font = run("shell", "settings", "get", "system", "font_scale")
    original_size = run("shell", "wm", "size")
    original_density = run("shell", "wm", "density")
    try:
        # Exercise a compact 360 x 800 dp profile with an enlarged system font setting.
        run("shell", "wm", "size", "720x1600")
        run("shell", "wm", "density", "320")
        run("shell", "settings", "put", "system", "font_scale", "1.30")
        run("shell", "settings", "put", "system", "window_animation_scale", "0")
        run("shell", "settings", "put", "system", "transition_animation_scale", "0")
        run("shell", "settings", "put", "system", "animator_duration_scale", "0")
        size = run("shell", "wm", "size")
        matches = re.findall(r"(\d+)x(\d+)", size)
        if not matches:
            raise AssertionError(f"Could not determine emulator dimensions: {size}")
        SCREEN_W, SCREEN_H = map(int, matches[-1])
        record(f"PROFILE: {SCREEN_W}x{SCREEN_H} pixels, density 320 dpi, system font_scale 1.30")

        run("shell", "appops", "set", PKG, "MANAGE_EXTERNAL_STORAGE", "allow")
        run("shell", "rm", "-rf", TEST_DIR)
        run("shell", "mkdir", "-p", TEST_DIR)
        run("shell", "touch", TEST_FILE)
        run("shell", "am", "force-stop", PKG)
        run("shell", "monkey", "-p", PKG, "1")
        time.sleep(12)
        activities = run("shell", "dumpsys", "activity", "activities")
        if PKG not in activities:
            raise AssertionError("ToxiFiles did not become the resumed Android activity")

        # Change through every supported font in the actual native Settings UI.
        test_font_styles()

        # Home scroll must move the lower category and return close to its original position.
        initial_home = dump("toxifiles-home-before-scroll")
        if not any(n.attrib.get("scrollable") == "true" for n in initial_home.iter("node")):
            raise AssertionError("Home does not expose a scrollable accessibility container")
        assert_no_text_overlap(initial_home, "Home before swipe")
        for node in initial_home.iter("node"):
            value = node.attrib.get("text", "").strip().lower()
            if "recently opened" in value or "quick actions" in value:
                raise AssertionError("Removed Recent/Quick Actions content reappeared on Home")
        before_tree, before_node = scroll_until_other_category("toxifiles-home-other-before-scroll")
        assert_no_text_overlap(before_tree, "Home with Other category visible")
        before_rect = parse_bounds(before_node)
        before_y = (before_rect[1] + before_rect[3]) // 2
        screenshot("toxifiles-home-before-scroll")
        swipe_up()
        up_tree = dump("toxifiles-home-after-up-swipe")
        assert_no_text_overlap(up_tree, "Home after up-swipe")
        up_node = other_category_node(up_tree)
        if up_node is None:
            raise AssertionError("Home Other category disappeared after up-swipe")
        up_rect = parse_bounds(up_node)
        up_y = (up_rect[1] + up_rect[3]) // 2
        if before_y - up_y < 60:
            raise AssertionError(f"Home up-swipe moved content too little: Other y {before_y} -> {up_y}")
        swipe_down()
        down_tree = dump("toxifiles-home-after-down-swipe")
        assert_no_text_overlap(down_tree, "Home after down-swipe")
        down_node = other_category_node(down_tree)
        if down_node is None:
            raise AssertionError("Home Other category disappeared after down-swipe")
        down_rect = parse_bounds(down_node)
        down_y = (down_rect[1] + down_rect[3]) // 2
        if down_y - up_y < 60 or abs(down_y - before_y) > 90:
            raise AssertionError(f"Home down-swipe did not restore the original position: {before_y} -> {up_y} -> {down_y}")
        screenshot("toxifiles-home-after-scroll-roundtrip")
        record(f"PASS: Home up/down swipe round-trip moves Other y={before_y}->{up_y}->{down_y}")

        # Expand every regular Settings group and verify each group can be reached by touch/scroll.
        settings = open_settings()
        assert_scrollable(settings, "Settings")
        assert_visible(settings, "Color theme", exact=True)
        assert_visible(settings, "Font style", exact=True)
        assert_no_text_overlap(settings, "Settings initial")
        screenshot("toxifiles-settings-initial")

        # Exercise every supported cyber palette in the actual native theme screen.
        theme_names = ("Neon Cyan", "Matrix Green", "Quantum Violet", "Plasma Blue", "Cyber Magenta")
        original_theme = next((name for name in theme_names if find_text(settings, name, exact=True) is not None), None)
        if original_theme is None:
            raise AssertionError("The current cyber theme is missing from Settings")
        theme_row = find_text(settings, "Color theme", exact=True)
        if theme_row is None:
            raise AssertionError("Color theme row is missing from Settings")
        tap_node(theme_row)
        time.sleep(0.55)
        theme = dump("toxifiles-theme-screen")
        assert_navigation(theme, False, "Theme overlay")
        assert_absent(theme, "Make ToxiFiles work the way you do", "Theme overlay")
        assert_visible(theme, "Cyber Theme Studio", exact=True)
        for name in theme_names:
            assert_visible(theme, name, exact=True)
        theme_content = scroll_view_containing(theme, "AVAILABLE FINISHES")
        assert_no_text_overlap(theme_content, "Theme screen")
        for name in theme_names:
            tile_name = find_last_text(theme, name, exact=True)
            if tile_name is None:
                raise AssertionError(f"Theme tile is missing: {name}")
            tap_node(tile_name)
            time.sleep(0.35)
            theme = dump(f"toxifiles-theme-{name.lower().replace(' ', '-')}")
            assert_visible(theme, name, exact=True)
            theme_content = scroll_view_containing(theme, "AVAILABLE FINISHES")
            assert_no_text_overlap(theme_content, f"Theme {name}")
            record(f"PASS: {name} theme applies without overlapping text bounds")
        original_tile = find_last_text(theme, original_theme, exact=True)
        if original_tile is None:
            raise AssertionError(f"Could not restore original cyber theme: {original_theme}")
        tap_node(original_tile)
        time.sleep(0.35)
        theme = dump("toxifiles-theme-restored")
        assert_visible(theme, original_theme, exact=True)
        run("shell", "input", "keyevent", "4")
        time.sleep(0.45)
        settings = dump("settings-after-theme")
        assert_visible(settings, "Settings", exact=True)
        assert_visible(settings, original_theme, exact=True)
        assert_navigation(settings, False, "Settings after Theme")
        assert_no_text_overlap(settings, "Settings after Theme")
        screenshot("toxifiles-settings-after-theme")
        record("PASS: all five cyber themes can be selected and the original theme is restored")

        sections = [
            ("FILES & FOLDERS", "Show hidden files"),
            ("STORAGE ACCESS", "Storage permission"),
            ("RECYCLE BIN", "Move deleted files to Recycle Bin"),
            ("APP LOCK", "Require unlock to open app"),
            ("SECURE VAULT", "Open Vault"),
            ("WI-FI TRANSFER", "Receive via Wi-Fi"),
            ("SD CARD & USB STORAGE", "Browse other storage"),
            ("ABOUT", "About ToxiFiles"),
        ]
        for index, (label, expected) in enumerate(sections):
            root, heading = scroll_until_section(label, f"settings-section-{index}")
            if heading.attrib.get("text", "").strip().startswith("+"):
                tap_node(heading)
                time.sleep(0.25)
            root, row = scroll_until_text(expected, f"settings-row-{index}", exact=True)
            assert_no_text_overlap(root, f"Settings section {label}")
            record(f"PASS: Settings section {label} expands and exposes {expected}")
        screenshot("toxifiles-settings-expanded")

        # Recycle Bin is a separate native screen; it must not expose the Settings page beneath it.
        settings, recycle_row = scroll_until_text_down("Recycle Bin", "settings-recycle-bin-row", exact=True)
        tap_node(recycle_row)
        time.sleep(0.55)
        recycle_screen = dump("toxifiles-recycle-bin-screen")
        assert_navigation(recycle_screen, False, "Recycle Bin overlay")
        assert_absent(recycle_screen, "Settings", "Recycle Bin overlay")
        assert_visible(recycle_screen, "Recycle Bin", exact=True)
        assert_no_text_overlap(recycle_screen, "Recycle Bin overlay")
        screenshot("toxifiles-recycle-bin-screen")
        run("shell", "input", "keyevent", "4")
        time.sleep(0.55)
        settings = dump("toxifiles-settings-after-recycle-bin")
        assert_visible(settings, "APP LOCK")
        settings, _ = scroll_until_text_down("Settings", "settings-header-after-recycle-bin", exact=True)
        assert_navigation(settings, False, "Settings after Recycle Bin")
        assert_no_text_overlap(settings, "Settings after Recycle Bin")

        # Open About from Settings; the bottom navigation must remain hidden on this overlay
        # and must still be hidden after the overlay closes back to Settings.
        root, about_row = scroll_until_text("About ToxiFiles", "settings-about-row", exact=True)
        tap_node(about_row)
        time.sleep(0.65)
        about = dump("toxifiles-about-screen")
        assert_navigation(about, False, "About overlay")
        assert_absent(about, "AES-256 encrypted, key held in Android Keystore", "About overlay")
        about_content = scroll_view_containing(about, "CREATOR")
        assert_visible(about_content, "About ToxiFiles", exact=True)
        assert_visible(about_content, "CREATOR", exact=True)
        assert_visible(about_content, "ABOUT DEVELOPER", exact=True)
        assert_scrollable(about_content, "About")
        screenshot("toxifiles-about-top")
        assert_no_text_overlap(about_content, "About initial")
        about, _ = scroll_until_text("OPEN-SOURCE ACKNOWLEDGEMENTS", "about-acknowledgements", exact=True)
        about_content = scroll_view_containing(about, "OPEN-SOURCE ACKNOWLEDGEMENTS")
        assert_no_text_overlap(about_content, "About acknowledgements")
        for _ in range(3):
            swipe_up()
        about_bottom = dump("toxifiles-about-bottom")
        about_bottom_content = scroll_view_containing(about_bottom, "ToxiFiles")
        assert_no_text_overlap(about_bottom_content, "About bottom")
        screenshot("toxifiles-about-bottom")
        assert_visible(about_bottom_content, "ToxiFiles", exact=True)
        run("shell", "input", "keyevent", "4")
        time.sleep(0.45)
        for _ in range(6):
            swipe_down()
        settings_after_about = dump("toxifiles-settings-after-about-back")
        assert_visible(settings_after_about, "Settings", exact=True)
        assert_navigation(settings_after_about, False, "Settings after closing About")
        assert_no_text_overlap(settings_after_about, "Settings after closing About")
        screenshot("toxifiles-settings-after-about-back")
        record("PASS: closing About does not re-enable the Settings bottom navigation")

        # Back to Home; navigation must return only after leaving Settings.
        run("shell", "input", "keyevent", "4")
        time.sleep(0.45)
        home = dump("toxifiles-home-after-settings-back")
        assert_visible(home, "Your files", exact=True)
        assert_navigation(home, True, "Home after Settings back")
        assert_no_text_overlap(home, "Home after Settings back")

        # Verify the Other category opens the native grouped menu and all four requested entries.
        home, other = scroll_until_other_category("toxifiles-home-other-menu")
        tap_node(other)
        time.sleep(0.5)
        other_dialog = dump("toxifiles-other-dialog")
        other_labels = ("Other files", "APK files", "System apps", "Downloaded apps")
        for label in other_labels:
            assert_visible(other_dialog, label, exact=True)
        other_menu_content = smallest_container_containing(other_dialog, other_labels)
        assert_no_text_overlap(other_menu_content, "Other menu")
        screenshot("toxifiles-other-dialog")
        other_files_row = find_text(other_dialog, "Other files", exact=True)
        if other_files_row is None:
            raise AssertionError("Other files menu item is missing")
        tap_node(other_files_row)
        time.sleep(0.55)
        other_files = dump("toxifiles-other-files-overlay")
        assert_navigation(other_files, False, "Other files overlay")
        assert_absent(other_files, "Your files", "Other files overlay")
        assert_visible(other_files, "runtime-layout.bin", exact=True)
        assert_no_text_overlap(other_files, "Other files overlay")
        record("PASS: Other files opens its native file list without exposing the Home screen")

        # Rename the isolated .bin fixture through the actual row action and dialog.
        file_row = container_with_text_and_description(other_files, "runtime-layout.bin", "More options")
        more_action = find_description(file_row, "More options")
        if more_action is None:
            raise AssertionError("Other-file row More action is missing")
        tap_node(more_action)
        time.sleep(0.35)
        item_menu = dump("toxifiles-other-file-menu")
        rename_action = find_text(item_menu, "Rename", exact=True)
        if rename_action is None:
            raise AssertionError("File item menu does not expose Rename")
        tap_node(rename_action)
        time.sleep(0.35)
        rename_dialog = dump("toxifiles-other-file-rename")
        edit_field = next((node for node in rename_dialog.iter("node")
                           if node_is_visible(node) and node.attrib.get("class") == "android.widget.EditText"
                           and parse_bounds(node)), None)
        if edit_field is None:
            raise AssertionError("Rename dialog does not expose its native EditText")
        tap_node(edit_field)
        run("shell", "input", "text", "runtime-layout-renamed")
        time.sleep(0.2)
        rename_dialog = dump("toxifiles-other-file-rename-filled")
        assert_visible(rename_dialog, "runtime-layout-renamed.bin", exact=True)
        rename_button = find_last_text(rename_dialog, "Rename", exact=True)
        if rename_button is None:
            raise AssertionError("Rename dialog positive button is missing")
        tap_node(rename_button)
        time.sleep(0.9)
        listing = run("shell", "ls", TEST_DIR)
        if RENAMED_TEST_FILE.split("/")[-1] not in listing or TEST_FILE.split("/")[-1] in listing:
            raise AssertionError(f"Native Rename action did not update the fixture: {listing!r}")
        record("PASS: native file Rename action updates the emulator file")
        run("shell", "input", "keyevent", "4")
        time.sleep(0.45)
        home = dump("toxifiles-home-after-other-overlay")
        assert_visible(home, "Your files", exact=True)
        assert_navigation(home, True, "Home after Other files")
        record("PASS: closing Other files restores Home touch/navigation state")
        record("PASS: Other menu opens and exposes Other files, APK files, System apps, and Downloaded apps")

        # Exercise the remaining Other destinations on the emulator, not only their menu labels.
        other_destinations = (
            ("APK files", "APK files", True),
            ("System apps", "System Apps", False),
            ("Downloaded apps", "Downloaded Apps", False),
        )
        for index, (menu_label, screen_title, full_screen) in enumerate(other_destinations):
            home, other = scroll_until_other_category(f"toxifiles-other-destination-{index}")
            tap_node(other)
            time.sleep(0.45)
            menu = dump(f"toxifiles-other-destination-menu-{index}")
            destination_row = find_text(menu, menu_label, exact=True)
            if destination_row is None:
                raise AssertionError(f"Other menu destination is missing: {menu_label}")
            tap_node(destination_row)
            time.sleep(0.65)
            destination = dump(f"toxifiles-other-destination-screen-{index}")
            assert_visible(destination, screen_title, exact=True)
            if full_screen:
                assert_navigation(destination, False, f"{screen_title} overlay")
                assert_absent(destination, "Your files", f"{screen_title} overlay")
                assert_no_text_overlap(destination, f"{screen_title} overlay")
            else:
                dialog_content = smallest_container_containing(destination, [screen_title])
                assert_no_text_overlap(dialog_content, f"{screen_title} dialog")
            screenshot(f"toxifiles-other-destination-{index}")
            run("shell", "input", "keyevent", "4")
            time.sleep(0.55)
            home = dump(f"toxifiles-home-after-other-destination-{index}")
            assert_visible(home, "Your files", exact=True)
            assert_navigation(home, True, f"Home after {screen_title}")
            record(f"PASS: Other destination {menu_label} opens and returns to Home without layout overlap")

        record("PASS: runtime layout and interaction regression suite completed")
    except Exception as test_error:
        try:
            screenshot("toxifiles-runtime-failure")
            record(f"FAIL: runtime layout and interaction regression stopped: {test_error}")
        except Exception as capture_error:
            record(f"WARNING: failure screenshot could not be captured: {capture_error}")
        raise
    finally:
        # Remove the isolated file fixture and restore the emulator profile even on failure.
        try:
            run("shell", "rm", "-rf", TEST_DIR)
        except Exception as cleanup_error:
            record(f"WARNING: runtime file fixture cleanup failed: {cleanup_error}")
        try:
            run("shell", "wm", "size", "reset")
            run("shell", "wm", "density", "reset")
            if original_font and original_font.lower() != "null":
                run("shell", "settings", "put", "system", "font_scale", original_font)
        except Exception as restore_error:
            record(f"WARNING: emulator profile restore failed: {restore_error}")


if __name__ == "__main__":
    main()
