#!/usr/bin/env python3
"""Runtime layout, navigation, and swipe regression tests for ToxiFiles."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
PKG = "com.liquidfiles.offline.debug"
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


def dump(name):
    remote = f"/sdcard/{name}.xml"
    local = os.path.join(DIST, f"{name}.xml")
    run("shell", "uiautomator", "dump", remote)
    run("pull", remote, local)
    return ET.parse(local).getroot()


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


def find_description(root, description):
    for node in root.iter("node"):
        if node_is_visible(node) and node.attrib.get("content-desc", "").strip() == description:
            if parse_bounds(node):
                return node
    return None


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


def scroll_until_text(needle, prefix, exact=False, max_swipes=12):
    for attempt in range(max_swipes + 1):
        root = dump(f"{prefix}-{attempt}")
        node = find_text(root, needle, exact=exact)
        if node is not None:
            return root, node
        if attempt < max_swipes:
            swipe_up()
    raise AssertionError(f"Could not scroll to visible text {needle!r} on {prefix}")


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

        # Open About from Settings; the bottom navigation must remain hidden on this overlay
        # and must still be hidden after the overlay closes back to Settings.
        root, about_row = scroll_until_text("About ToxiFiles", "settings-about-row", exact=True)
        tap_node(about_row)
        time.sleep(0.65)
        about = dump("toxifiles-about-screen")
        assert_navigation(about, False, "About overlay")
        assert_visible(about, "About ToxiFiles", exact=True)
        assert_visible(about, "CREATOR", exact=True)
        assert_visible(about, "ABOUT DEVELOPER", exact=True)
        assert_scrollable(about, "About")
        assert_no_text_overlap(about, "About initial")
        screenshot("toxifiles-about-top")
        about, _ = scroll_until_text("OPEN-SOURCE ACKNOWLEDGEMENTS", "about-acknowledgements", exact=True)
        assert_no_text_overlap(about, "About acknowledgements")
        for _ in range(3):
            swipe_up()
        about_bottom = dump("toxifiles-about-bottom")
        assert_no_text_overlap(about_bottom, "About bottom")
        screenshot("toxifiles-about-bottom")
        assert_visible(about_bottom, "ToxiFiles", exact=True)
        run("shell", "input", "keyevent", "4")
        time.sleep(0.45)
        for _ in range(6):
            swipe_down()
        settings_after_about = dump("toxifiles-settings-after-about-back")
        assert_visible(settings_after_about, "Settings", exact=True)
        assert_navigation(settings_after_about, False, "Settings after closing About")
        assert_no_text_overlap(settings_after_about, "Settings after closing About")
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
        for label in ("Other files", "APK files", "System apps", "Downloaded apps"):
            assert_visible(other_dialog, label, exact=True)
        assert_no_text_overlap(other_dialog, "Other menu")
        screenshot("toxifiles-other-dialog")
        close_button = find_text(other_dialog, "Close", exact=True)
        if close_button is None:
            raise AssertionError("Other menu Close button is missing")
        tap_node(close_button)
        time.sleep(0.35)
        record("PASS: Other menu opens and exposes Other files, APK files, System apps, and Downloaded apps")

        record("PASS: runtime layout and interaction regression suite completed")
    finally:
        # Restore the emulator profile even when an assertion fails.
        try:
            run("shell", "wm", "size", "reset")
            run("shell", "wm", "density", "reset")
            if original_font and original_font.lower() != "null":
                run("shell", "settings", "put", "system", "font_scale", original_font)
        except Exception as restore_error:
            record(f"WARNING: emulator profile restore failed: {restore_error}")


if __name__ == "__main__":
    main()
