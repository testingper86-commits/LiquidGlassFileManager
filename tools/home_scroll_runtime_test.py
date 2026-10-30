#!/usr/bin/env python3
"""Runtime swipe regression test for ToxiFiles Home on an Android emulator."""
import os, subprocess, time, xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
PKG = "com.liquidfiles.offline.debug"

def run(*args, capture=True):
    p = subprocess.run([ADB, *map(str, args)], text=True, stdout=subprocess.PIPE if capture else None,
                       stderr=subprocess.STDOUT if capture else None, check=True)
    return p.stdout.strip() if capture else ""

def dump(name):
    run("shell", "uiautomator", "dump", "/sdcard/" + name)
    run("pull", "/sdcard/" + name, "dist/" + name)
    return ET.parse("dist/" + name).getroot()

def other_y(root, height):
    matches = []
    for node in root.iter("node"):
        if node.attrib.get("text", "").strip() == "Other":
            bounds = node.attrib.get("bounds", "")
            try:
                left_top, right_bottom = bounds.strip("[]").split("][")
                y1 = int(left_top.split(",")[1]); y2 = int(right_bottom.split(",")[1])
                center = (y1 + y2) // 2
                if center > height // 3:
                    matches.append(center)
            except (ValueError, IndexError):
                pass
    if not matches:
        raise AssertionError("Home category 'Other' was not visible in the accessibility hierarchy")
    return max(matches)

def main():
    os.makedirs("dist", exist_ok=True)
    run("shell", "appops", "set", PKG, "MANAGE_EXTERNAL_STORAGE", "allow")
    run("shell", "settings", "put", "system", "window_animation_scale", "0")
    run("shell", "settings", "put", "system", "transition_animation_scale", "0")
    run("shell", "settings", "put", "system", "animator_duration_scale", "0")
    run("shell", "am", "force-stop", PKG)
    run("shell", "monkey", "-p", PKG, "1")
    time.sleep(12)
    size = run("shell", "wm", "size")
    dims = size.split(":")[-1].strip().split("x")
    width, height = map(int, dims)
    before_tree = dump("toxifiles-home-before.xml")
    if not any(n.attrib.get("scrollable") == "true" for n in before_tree.iter("node")):
        raise AssertionError("No scrollable accessibility container is exposed on Home")
    before = other_y(before_tree, height)
    x = width // 2
    run("shell", "input", "swipe", x, int(height * .76), x, int(height * .30), "700")
    time.sleep(1)
    up_tree = dump("toxifiles-home-after-up.xml")
    after_up = other_y(up_tree, height)
    if before - after_up < 60:
        raise AssertionError(f"Up-swipe did not move Home content enough: Other y {before} -> {after_up}")
    run("shell", "input", "swipe", x, int(height * .30), x, int(height * .76), "700")
    time.sleep(1)
    down_tree = dump("toxifiles-home-after-down.xml")
    after_down = other_y(down_tree, height)
    if after_down - after_up < 60:
        raise AssertionError(f"Down-swipe did not restore Home content: Other y {after_up} -> {after_down}")
    if abs(after_down - before) > 90:
        raise AssertionError(f"Home scroll position did not return near its start: {before} -> {after_down}")
    run("shell", "screencap", "-p", "/sdcard/toxifiles-home-scroll.png")
    run("pull", "/sdcard/toxifiles-home-scroll.png", "dist/toxifiles-home-scroll.png")
    print(f"PASS: Home up-swipe moved Other from y={before} to y={after_up}")
    print(f"PASS: Home down-swipe returned Other to y={after_down}")

if __name__ == "__main__":
    main()
