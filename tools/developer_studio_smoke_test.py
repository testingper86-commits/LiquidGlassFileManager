#!/usr/bin/env python3
"""Focused source-level acceptance checks for the restricted offline Developer Studio."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/com/music/echo/offline/files"
activity = (BASE / "DeveloperStudioActivity.java").read_text(encoding="utf-8")
core = (ROOT / "core/src/main/java/com/music/echo/offline/files/DeveloperStudioCore.java").read_text(encoding="utf-8")
main = (BASE / "MainActivity.java").read_text(encoding="utf-8")
tools_activity = (BASE / "ToxiToolsActivity.java").read_text(encoding="utf-8")
settings_backup = (BASE / "SettingsBackupManager.java").read_text(encoding="utf-8")
uri_permissions = (ROOT / "foundation/src/main/java/com/music/echo/offline/files/ToxiUriPermissions.java").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

TOOLS = [
    "File Relationship Explorer", "APK Inspection Laboratory",
    "Database Explorer & SQL Query Studio", "Code Diff Studio",
    "Code Formatter & Validator", "Regex Testing Laboratory",
    "Code Symbol Navigator", "Dependency Inspector",
    "Binary Structure Analyzer", "Color & Asset Inspector",
    "JSON & XML Studio", "File Signature Analyzer",
]
checks = []
def require(condition, message):
    if not condition:
        raise SystemExit("FAIL: " + message)
    checks.append(message)

for name in TOOLS:
    require(name in activity, "tool present: " + name)
require("developer_options_unlocked" in activity and "onResume()" in activity and "finish()" in activity,
        "activity rechecks the existing Developer unlock gate")
manifest_root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android_ns = "{http://schemas.android.com/apk/res/android}"
app_node = manifest_root.find("application")
dev_activity = next((item for item in app_node.findall("activity") if item.attrib.get(android_ns + "name") == ".files.DeveloperStudioActivity"), None)
require(dev_activity is not None and dev_activity.attrib.get(android_ns + "exported") == "false" and dev_activity.find("intent-filter") is None,
        "Developer Studio activity is private and non-exported")
require(main.count("new Intent(this, DeveloperStudioActivity.class)") == 1 and "Developer Studio" in main,
        "single Developer-only entry point exists in Settings")
require("EXTRA_FILE_PATH" not in activity and "tool_index" not in activity,
        "Developer tools are not routed through ordinary file-opening extras")
require("SQLiteDatabase.OPEN_READONLY" in activity and "rawQuery" in activity,
        "SQLite inspector opens databases read-only and executes queries locally")
require("ACTION_OPEN_DOCUMENT" in activity and "ACTION_OPEN_DOCUMENT_TREE" in activity,
        "file access uses Android document pickers and scoped URI grants")
require("DocumentsContract.isDocumentUri" in uri_permissions and "DocumentsContract.isTreeUri" in uri_permissions and "requireSafUri" in uri_permissions,
        "ContentResolver access is restricted to validated SAF document and tree URIs")
require("checkUriPermission(uri, Process.myPid(), Process.myUid(), requiredPermission)" in uri_permissions,
        "every SAF resolver operation requires the app's explicit per-URI grant")
require("FLAG_GRANT_READ_URI_PERMISSION" in uri_permissions and "FLAG_GRANT_WRITE_URI_PERMISSION" in uri_permissions,
        "SAF read, query and write operations enforce their distinct permission modes")
require("ToxiUriPermissions.openInputStream(this,uri)" in activity and "ToxiUriPermissions.openInputStream(this,uri)" in tools_activity and "SettingsBackupManager.importFromUri(this, file, ToxiVisualSystem.PREFERENCES" in main and "ToxiUriPermissions.openInputStream(context, uri)" in settings_backup,
        "Developer Studio, built-in tools and extracted settings backup use the validated SAF stream boundary")
for forbidden in ("java.net.", "HttpURLConnection", "OkHttpClient", "WebView", "ProcessBuilder", "Runtime.getRuntime", "DexClassLoader"):
    require(forbidden not in activity and forbidden not in core,
            "no remote networking or inspected-code execution reference: " + forbidden)
require("No code was loaded, installed or executed" in activity,
        "APK inspection explicitly remains non-executing")
require("validateRegexPattern" in core and "MAX_DIFF_LINES" in core and "50_000" in core and "512" in core,
        "regex and diff engines enforce conservative pattern and input complexity limits")
require("isReadOnlySql" in core and "DeveloperStudioCore.isReadOnlySql(sql)" in activity,
        "SQL Studio accepts only bounded read-only statements through a tokenizer")
require("parseProgressCount" in core and "Pattern.compile" not in main and "Pattern.compile" not in (BASE / "DynamicIslandView.java").read_text(encoding="utf-8"),
        "progress parsing is bounded and does not use regex backtracking")
require("MAX_RELATION_FILES" in core and "MAX_TEXT_CHARS" in core,
        "source analysis has explicit input bounds")
require("private void handleBackNavigation()" in activity and "@Override public void onBackPressed(){handleBackNavigation();}" in activity,
        "Developer Studio hardware back uses the screen-aware navigation handler")
require("current>=0" in activity and "cancelWork();showHome();" in activity and "else{finish();}" in activity,
        "tool back returns to the 12-tool index and index back exits to ToxiFiles")
require("ToxiVisualSystem.iconButton(this,ToxiIcons.BACK" in activity and "setOnClickListener(v->handleBackNavigation())" in activity,
        "Developer Studio index and tool screens expose a proper accessible back icon")
for index, icon in (("REL", "FOLDER"), ("APK", "APK"), ("DB", "DATABASE"), ("DIFF", "FILE"), ("FORMAT", "TEXT"), ("REGEX", "SEARCH"), ("SYMBOL", "INFO"), ("DEP", "ANALYTICS"), ("BINARY", "ARCHIVE"), ("COLOR", "PALETTE"), ("JSON_XML", "DOC")):
    require("case " + index + ":return ToxiIcons." + icon in activity,
            "Developer Studio tool has its own semantic icon: " + index)
require("default:return ToxiIcons.SHIELD" in activity, "file signature analyzer has its own shield icon")
require("ToxiVisualSystem.iconButton(this,ToxiIcons.BACK" in tools_activity and "@Override public void onBackPressed(){handleBackNavigation();}" in tools_activity,
        "built-in tool screens expose a visible back icon and matching hardware-back action")
require("@Override public void onBackPressed() { handleBackNavigation(); }" in main and "private void handleBackNavigation()" in main,
        "main application hardware back continues through its central navigation handler")
print(f"PASS: {len(checks)} Developer Studio source checks")
