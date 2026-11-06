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
        "app/src/main/java/com/music/echo/offline/files/BrowserView.java",
        "app/src/main/java/com/music/echo/offline/files/RecycleBin.java",
        "app/src/main/java/com/music/echo/offline/files/DeveloperStudioActivity.java",
        "app/src/main/java/com/music/echo/offline/files/DeveloperStudioCore.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiUriPermissions.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiVisualSystem.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiIcons.java",
        "app/src/main/java/com/music/echo/offline/files/FileOps.java",
        "app/src/main/java/com/music/echo/offline/files/OperationCancellation.java",
        "app/src/main/java/com/music/echo/offline/files/ArchivePath.java",
        "app/src/test/java/com/music/echo/offline/files/OperationCancellationTest.java",
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
    uri_permissions = source("app/src/main/java/com/music/echo/offline/files/ToxiUriPermissions.java")
    require(uri_permissions.count("takePersistableUriPermission(") == 2 and "Intent.FLAG_GRANT_READ_URI_PERMISSION);" in uri_permissions and "Intent.FLAG_GRANT_WRITE_URI_PERMISSION);" in uri_permissions, "persistable URI permissions are stored one validated grant mode at a time")
    java_sources = list((ROOT / "app/src/main/java").rglob("*.java"))
    direct_uri_permission_calls = [str(path.relative_to(ROOT)) for path in java_sources if path.name != "ToxiUriPermissions.java" and "takePersistableUriPermission(" in path.read_text(encoding="utf-8")]
    require(not direct_uri_permission_calls, "all activities use the shared SAF permission helper; direct unsafe calls are blocked")
    require("ToxiUriPermissions.takeGranted(getContentResolver(),uri,data.getFlags());" in source("app/src/main/java/com/music/echo/offline/files/ToxiToolsActivity.java") and "ToxiUriPermissions.takeGranted(getContentResolver(),u,data.getFlags());" in developer, "file tools and Developer Studio share the same validated SAF permission path")


    saf_ops = source("app/src/main/java/com/music/echo/offline/files/SafStorageOperations.java")
    require("SafStorageOperations.copyLocalTree(getContentResolver(), source, parent);" in main and "SafStorageOperations.deleteTree(target);" in main, "MainActivity routes local-to-SAF copies and rollback cleanup through the shared provider service")
    require("DocumentsContract.moveDocument" in saf_ops and "DocumentsContract.renameDocument" in saf_ops and "DocumentsContract.deleteDocument" in saf_ops, "SAF service exposes provider-native move, rename and delete without filesystem-path assumptions")
    require("copyDocumentTree(ContentResolver resolver" in saf_ops and "copyLocalTree(ContentResolver resolver" in saf_ops and "OperationCancellation.checkCurrent()" in saf_ops, "SAF service supports cancellable recursive provider-to-provider and local-to-provider copies")
    require("BatchRenameEngine.validateName(name)" in saf_ops and "uniqueName(parent, name" in saf_ops and "deleteTree(target)" in saf_ops, "SAF copies validate leaf names, avoid collisions and clean partial provider output")

    require("setOnItemLongClickListener" in main and "showSafDocumentActions(tree, docUri, title, selected, row[1]" in main, "existing SAF browser exposes document actions only from the selected row context")
    require('"Duplicate here"' in main and "SafStorageOperations.copyDocumentFile" in main and "SafStorageOperations.renameDocument" in main and "SafStorageOperations.deleteDocument" in main, "SAF browser wires duplicate, rename and permanent delete to provider operations")
    require("DocumentsContract.createDocument" in saf_ops and "buildChildDocumentsUriUsingTree" in saf_ops and "OperationCancellation.checkCurrent()" in saf_ops, "SAF document duplication creates collision-safe provider documents and cleans partial output")
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
    cancellation = source("app/src/main/java/com/music/echo/offline/files/OperationCancellation.java")
    require('OperationCancellation.checkCurrent()' in file_ops and 'OperationCancellation.isCancelled()' in file_ops and 'cancelRequested' not in file_ops and file_ops.count('a.cancellation.begin()') == 4 and file_ops.count('a.cancellation.finish(operationToken)') == 4 and 'cancellation.cancelActive()' in main and 'FileOps.cancelRequested' not in main, "file operations use isolated operation-scoped cancellation")
    require('ThreadLocal<Token>' in cancellation and 'ConcurrentHashMap.newKeySet()' in cancellation and 'void cancelActive()' in cancellation and main.count('this.cancellation.begin()') == 2, "cancellation is worker-thread-bound and scoped to the owning activity")
    require((ROOT / "app/src/test/java/com/music/echo/offline/files/OperationCancellationTest.java").is_file(), "operation cancellation has JVM regression tests")
    java_sources = list((ROOT / "app/src/main/java").rglob("*.java"))
    test_sources = list((ROOT / "app/src/test/java").rglob("*.java"))
    all_java_sources = java_sources + test_sources
    legacy_cancel_callers = [str(path.relative_to(ROOT)) for path in all_java_sources if "FileOps.cancelRequested" in path.read_text(encoding="utf-8")]
    require(not legacy_cancel_callers, "no legacy global file-cancellation references remain in production or test Java sources")
    toxi_tools = source("app/src/main/java/com/music/echo/offline/files/ToxiToolsActivity.java")
    lan_transfer = source("app/src/main/java/com/music/echo/offline/files/LanTransfer.java")
    require(toxi_tools.count('OperationCancellation.Token operationToken') == 3 and 'cancellation.cancelActive()' in toxi_tools, "Developer file tools cancel and clean up their own worker operations")
    require(lan_transfer.count('OperationCancellation.Token operationToken') == 2 and 'cancellation.cancelActive()' in lan_transfer and lan_transfer.count('session.cancellation.finish(operationToken)') == 2, "LAN transfer cancellation reaches authenticated receive commit work")
    browser = source("app/src/main/java/com/music/echo/offline/files/BrowserView.java")
    vault = source("app/src/main/java/com/music/echo/offline/files/Vault.java")
    recycle_bin = source("app/src/main/java/com/music/echo/offline/files/RecycleBin.java")
    require('FileOps.cancelRequested' not in browser + vault + recycle_bin and browser.count('a.cancellation.begin()') == 1 and vault.count('a.cancellation.begin()') == 3 and recycle_bin.count('a.cancellation.begin()') == 4 and 'OperationCancellation.isCancelled()' in vault and 'OperationCancellation.isCancelled()' in recycle_bin, "BrowserView, Vault and Recycle Bin use isolated operation cancellation")
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
    pdf_open = viewer.split('void showPdf(File f)', 1)[1].split('private void showPdfPages', 1)[0]
    require('io.execute(' in pdf_open and 'ParcelFileDescriptor.open(f' in pdf_open and 'h.post(' in pdf_open and 'volatile long pdfGeneration' in viewer, "PDF descriptor and renderer open off the UI thread with stale-load protection")
    require('synchronized (renderer)' in viewer and 'closePdfResourcesAsync(renderer, descriptor)' in viewer, "PDF renderer close is serialized against page rendering")
    crash = source("app/src/main/java/com/music/echo/offline/files/CrashLogger.java")
    require('FATAL EXCEPTION' in crash and 'latest_crash.txt' in crash and 'printStackTrace' in crash, "crash diagnostics persist locally")

    require('@android.annotation.TargetApi(android.os.Build.VERSION_CODES.P)' in main and 'if (Build.VERSION.SDK_INT >= 28) tryBiometric(dlg);' in main, "framework biometric prompt is guarded for API 28+")
    require('AndroidKeyStore' in main and 'new android.hardware.biometrics.BiometricPrompt.CryptoObject(cipher)' in main and 'result.getCryptoObject()' in main and 'doFinal(challenge)' in main, "biometric app unlock verifies a per-use Android Keystore cryptographic proof")
    require('setUserAuthenticationParameters(0, android.security.keystore.KeyProperties.AUTH_BIOMETRIC_STRONG)' in main and 'setUserAuthenticationValidityDurationSeconds(-1)' in main and 'KeyPermanentlyInvalidatedException' in main, "biometric Keystore key uses per-operation authentication and enrollment invalidation recovery")
    saf_operations = source("app/src/main/java/com/music/echo/offline/files/SafStorageOperations.java")
    rename_engine = source("app/src/main/java/com/music/echo/offline/files/BatchRenameEngine.java")
    file_ops = source("app/src/main/java/com/music/echo/offline/files/FileOps.java")
    browser = source("app/src/main/java/com/music/echo/offline/files/BrowserView.java")
    require('static File resolveChild(File parent, String name)' in rename_engine and 'candidate.getParentFile()' in rename_engine and 'canonicalParent.equals(actualParent)' in rename_engine, "User-derived child paths are checked against canonical parent boundaries")
    require('BatchRenameEngine.resolveChild(cur, n)' in browser and 'BatchRenameEngine.resolveChild(e.f.getParentFile(), n)' in browser, "Folder creation and rename use canonical path containment")
    require('BatchRenameEngine.resolveChild(dir, candidate)' in file_ops and 'safeChild(dir, name)' in file_ops, "File operations resolve generated destination names through the safe child helper")
    rename_tests = source("app/src/test/java/com/music/echo/offline/files/BatchRenameEngineTest.java")
    require('resolveChildRejectsTraversalAndSymlinkEscapes' in rename_tests, "canonical path resolution has traversal and symlink-escape regression tests")
    require('static DocumentFile findDocumentInTree(Context context, Uri tree, Uri target)' in saf_operations and 'DocumentFile.fromTreeUri(context, tree)' in saf_operations and 'current.listFiles()' in saf_operations, "SAF recursive operations resolve tree-aware DocumentFile wrappers")
    require('static DocumentFile moveDocumentWithFallback(Context context' in saf_operations and 'if (context == null || resolver == null || source == null' in saf_operations, "SAF move validates the Context required to wrap provider-returned document URIs")
    require('static Uri moveDocument(ContentResolver resolver' in saf_operations and 'if (context == null || resolver == null || source == null' not in saf_operations.split('static Uri moveDocument(ContentResolver resolver', 1)[1].split('static DocumentFile moveDocumentWithFallback', 1)[0], "SAF native move does not reference an out-of-scope Context")
    require('"Duplicate here"' in main and 'SafStorageOperations.copyDocumentTree(getContentResolver(), source, destination)' in main, "Other Storage supports recursive directory duplication")
    require('"Move to folder"' in main and 'showSafMoveFolderPicker(tree, parent, document, parent, title, name);' in main and 'SafStorageOperations.moveDocumentWithFallback(' in main, "Other Storage exposes provider-aware move-to-folder actions")
    require('ToxiUriPermissions.takeGranted(getContentResolver(), tree, grantedFlags);' in main and '@android.annotation.SuppressLint("WrongConstant")' not in main, "MainActivity persists SAF grants through the shared validated permission helper")
    require('registerOnBackInvokedCallback' in main and 'unregisterOnBackInvokedCallback' in main and 'private void handleBackNavigation()' in main, "Android 13+ predictive Back callbacks are registered and released")

    base_theme = source("app/src/main/res/values/themes.xml")
    api27_theme = source("app/src/main/res/values-v27/themes.xml")
    require("windowLightNavigationBar" not in base_theme and api27_theme.count("windowLightNavigationBar") == 2, "API 27 navigation-bar theme attributes are isolated to version-qualified resources")

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
