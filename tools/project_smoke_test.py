from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

def require(condition, message):
    if not condition:
        raise AssertionError(message)
    print(f"PASS: {message}")

def source(path):
    return (ROOT / path).read_text(encoding="utf-8")

def main():
    required = [
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/com/music/echo/offline/files/MainActivity.java",
        "app/src/main/java/com/music/echo/offline/files/DeveloperStudioActivity.java",
        "app/src/main/java/com/music/echo/offline/files/DeveloperStudioCore.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiVisualSystem.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiIcons.java",
        "app/src/main/java/com/music/echo/offline/files/FileOps.java",
        "app/src/main/java/com/music/echo/offline/files/ArchivePath.java",
        "app/src/main/java/com/music/echo/offline/files/Vault.java",
        "app/src/main/java/com/music/echo/offline/files/PinHash.java",
        "app/src/main/java/com/music/echo/offline/files/LanTransfer.java",
        "app/src/main/java/com/music/echo/offline/files/Indexer.java",
        "app/src/main/java/com/music/echo/offline/files/HtmlViewerActivity.java",
        "app/src/main/java/com/music/echo/offline/files/ViewerActivity.java",
        "app/src/main/java/com/music/echo/offline/files/CrashLogger.java",
        "tools/developer_studio_smoke_test.py",
        "tools/video_lifecycle_regression_test.py",
    ]
    for item in required:
        p = ROOT / item
        require(p.is_file() and p.stat().st_size > 0, f"required source exists: {item}")

    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    app = manifest.find("application")
    require(app is not None, "manifest has an application node")
    require(app.attrib.get(ANDROID + "allowBackup") == "false", "Android system backup is disabled")
    require(app.attrib.get(ANDROID + "usesCleartextTraffic") == "false", "cleartext HTTP traffic is disabled")
    require(app.attrib.get(ANDROID + "fullBackupContent") == "@xml/backup_rules" and app.attrib.get(ANDROID + "dataExtractionRules") == "@xml/data_extraction_rules", "cloud backup and device-transfer exclusions are explicitly configured")
    backup = ET.parse(ROOT / "app/src/main/res/xml/backup_rules.xml").getroot()
    extraction = ET.parse(ROOT / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
    require(len(backup.findall("exclude")) >= 5 and len(extraction.findall("cloud-backup/exclude")) >= 5 and len(extraction.findall("device-transfer/exclude")) >= 5, "private files, databases, preferences and external state are excluded from backup")
    permissions = {p.attrib.get(ANDROID + "name") for p in manifest.findall("uses-permission")}
    require("android.permission.INTERNET" in permissions and "android.permission.USE_BIOMETRIC" in permissions, "only required local-network and biometric permissions are declared")
    file_paths = ET.parse(ROOT / "app/src/main/res/xml/file_paths.xml").getroot()
    require(not any(node.tag == "root-path" for node in file_paths), "FileProvider does not expose the filesystem root")

    activities = {a.attrib.get(ANDROID + "name"): a for a in app.findall("activity")}
    launcher = []
    for name, activity in activities.items():
        intent_filter = activity.find("intent-filter")
        if intent_filter is None:
            continue
        actions = {n.attrib.get(ANDROID + "name") for n in intent_filter.findall("action")}
        categories = {n.attrib.get(ANDROID + "name") for n in intent_filter.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            launcher.append(name)
    require(launcher == [".files.MainActivity"], "ToxiFiles remains the only launcher activity")
    developer_activity = activities.get(".files.DeveloperStudioActivity")
    require(developer_activity is not None and developer_activity.attrib.get(ANDROID + "exported") == "false" and developer_activity.find("intent-filter") is None, "Developer Studio is a non-exported internal activity")

    main = source("app/src/main/java/com/music/echo/offline/files/MainActivity.java")
    developer = source("app/src/main/java/com/music/echo/offline/files/DeveloperStudioActivity.java")
    require('if (ToxiVisualSystem.booleanPreference("developer_options_unlocked", false)) {' in main and 'startActivity(new Intent(this, DeveloperStudioActivity.class))' in main, "Developer Studio is exposed only after the existing Developer unlock")
    require('private boolean developerUnlocked()' in developer and 'if(!developerUnlocked()){finish();return;}' in developer, "Developer Studio independently rejects direct access while locked")
    require('private static final String[] NAMES' in developer and 'openTool(int index)' in developer, "all Developer tools share one functional internal tool registry")
    require('Intent.ACTION_OPEN_DOCUMENT' in developer and 'Intent.ACTION_OPEN_DOCUMENT_TREE' in developer and 'getContentResolver().openInputStream(uri)' in developer and 'DocumentFile.fromTreeUri(this,root)' in developer, "Developer tools read user-selected files and folders through Android SAF")

    visual = source("app/src/main/java/com/music/echo/offline/files/ToxiVisualSystem.java")
    icons = source("app/src/main/java/com/music/echo/offline/files/ToxiIcons.java")
    require('high_text_contrast_enabled' in visual and 'HIGH_CONTRAST ? 0xFFE8EEF5' in visual, "Android high-contrast mode increases secondary text contrast")
    require('setMinimumWidth(dp(48))' in visual and 'setMinimumHeight(dp(48))' in visual and 'ToxiIcons.accessibilityLabel(symbol)' in visual, "shared icon controls have accessible labels and 48dp touch targets")
    require('static void interactive(View view)' in visual and 'view.setOnTouchListener' not in visual and 'setOnClickListener' in main, "shared interaction styling does not intercept native click dispatch")
    require('Obsidian Alloy' in source("app/src/main/java/com/music/echo/offline/files/ToxiThemeSystem.java") and 'hasIndependentTokens()' in source("app/src/main/java/com/music/echo/offline/files/ToxiThemeSystem.java"), "current theme catalog uses independent design tokens")
    require('settingsScroll.setClipToPadding(true)' in main and 'ToxiVisualSystem.dp(96) + ToxiVisualSystem.BOTTOM_INSET' in main, "Settings reserves scroll space above bottom navigation")
    require('settings.addView(section("About"))' in main and 'About ToxiFiles' in main, "About remains at the bottom of Settings")

    file_ops = source("app/src/main/java/com/music/echo/offline/files/FileOps.java")
    archive_path = source("app/src/main/java/com/music/echo/offline/files/ArchivePath.java")
    require('requestCancel()' in file_ops and 'checkCancelled()' in file_ops and 'Operation cancelled' in file_ops, "file operations support cancellation")
    require('ArchivePath.normalize(entryName, e.isDirectory())' in file_ops and 'initialFreeSpace' in file_ops and 'extractedPaths' in file_ops, "ZIP extraction validates paths, duplicate entries and cumulative storage use")
    require('Unsafe archive entry path rejected' in archive_path and 'segment.isEmpty()' in archive_path and '"..".equals(segment)' in archive_path, "ZIP path normalization rejects traversal and ambiguous segments")
    require((ROOT / "app/src/test/java/com/music/echo/offline/files/ArchivePathTest.java").is_file(), "ZIP path validation has JVM regression tests")
    require('Copied to destination, but source could not be removed' in file_ops and 'Recycle Bin: removed' in source("app/src/main/java/com/music/echo/offline/files/RecycleBin.java"), "move and Recycle Bin operations report cleanup failures accurately")

    vault = source("app/src/main/java/com/music/echo/offline/files/Vault.java")
    pin_hash = source("app/src/main/java/com/music/echo/offline/files/PinHash.java")
    require('AES/GCM/NoPadding' in vault and 'doFinal()' in vault and 'encrypted Vault cleanup was incomplete' in vault, "Vault uses authenticated encryption and reports incomplete cleanup")
    require('iterations < 10_000' in pin_hash and 'iterations > 1_000_000' in pin_hash and 'MessageDigest.isEqual' in pin_hash, "PIN verification bounds PBKDF2 work and compares verifiers in constant time")
    require((ROOT / "app/src/test/java/com/music/echo/offline/files/PinHashTest.java").is_file(), "PIN verification has automated boundary tests")
    lan = source("app/src/main/java/com/music/echo/offline/files/LanTransfer.java")
    require('PBKDF2WithHmacSHA256' in lan and 'AES/GCM/NoPadding' in lan and 'authProof' in lan, "local Wi-Fi transfer authenticates pairing and encrypts payloads")
    require('getCanonicalPath().startsWith(rootPath)' in lan and 'Unsafe incoming filename rejected' in lan, "LAN receive validates names and canonical destination containment")
    require((ROOT / "app/src/test/java/com/music/echo/offline/files/LanTransferCryptoTest.java").is_file(), "LAN transfer has cryptographic and filename regression tests")

    indexer = source("app/src/main/java/com/music/echo/offline/files/Indexer.java")
    require('Files.isSymbolicLink(dir.toPath())' in indexer and 'MAX_INDEX_ENTRIES = 150_000' in indexer and 'truncated = true' in indexer, "file indexing avoids symlink traversal and bounds memory use")
    html = source("app/src/main/java/com/music/echo/offline/files/HtmlViewerActivity.java")
    require('setAllowFileAccess(false)' in html and 'setAllowContentAccess(false)' in html and 'MIXED_CONTENT_NEVER_ALLOW' in html, "built-in HTML viewer blocks local file access and mixed content")
    viewer = source("app/src/main/java/com/music/echo/offline/files/ViewerActivity.java")
    require('TextureView' in viewer and 'setSurfaceTextureListener' in viewer and 'USAGE_MEDIA' in viewer and 'A-B loop enabled' in viewer, "built-in media viewer retains native video and audio playback controls")
    crash = source("app/src/main/java/com/music/echo/offline/files/CrashLogger.java")
    require('FATAL EXCEPTION' in crash and 'latest_crash.txt' in crash and 'printStackTrace' in crash, "crash diagnostics persist locally")

    require('@android.annotation.TargetApi(android.os.Build.VERSION_CODES.P)' in main and 'if (Build.VERSION.SDK_INT >= 28) tryBiometric(dlg);' in main, "framework biometric prompt is guarded for API 28+")
    require('@android.annotation.SuppressLint("WrongConstant")' in main and 'Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION' in main, "SAF persistence uses explicit read/write grant flags")
    require('registerOnBackInvokedCallback' in main and 'unregisterOnBackInvokedCallback' in main and 'private void handleBackNavigation()' in main, "Android 13+ predictive Back callbacks are registered and released")

    gradle_properties = source("gradle.properties")
    require("org.gradle.jvmargs=-Xmx2g" in gradle_properties and "MaxMetaspaceSize=512m" in gradle_properties and "org.gradle.workers.max=1" in gradle_properties, "Gradle CI memory is sufficient for API 36 lint analysis with bounded worker concurrency")
    app_gradle = source("app/build.gradle.kts")
    root_gradle = source("build.gradle.kts")
    wrapper = source("gradle/wrapper/gradle-wrapper.properties")
    require('compileSdk = 36' in app_gradle and 'targetSdk = 36' in app_gradle and 'version "8.10.1"' in root_gradle and 'gradle-8.11.1-bin.zip' in wrapper, "API 36 release uses compatible Android Gradle and wrapper versions")
    workflow = source(".github/workflows/release.yml")
    require('python tools/project_smoke_test.py' in workflow and 'python3 tools/developer_studio_smoke_test.py' in workflow and 'testDebugUnitTest lintDebug' in workflow and 'assembleRelease' in workflow, "release workflow runs source gates, unit tests, lint and release assembly")
    print("\\nAll current-architecture source smoke checks passed. Android compilation and device testing are verified separately.")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
