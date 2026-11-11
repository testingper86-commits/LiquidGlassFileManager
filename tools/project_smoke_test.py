from pathlib import Path
import re
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

def jpeg_size(path):
    data = path.read_bytes()
    require(data[:2] == b"\xff\xd8", "creator portrait is a valid JPEG")
    i = 2
    while i + 9 < len(data):
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        i += 2
        if marker in (0xD8, 0xD9):
            continue
        if i + 2 > len(data):
            break
        length = int.from_bytes(data[i:i + 2], "big")
        if 0xC0 <= marker <= 0xC3 or 0xC5 <= marker <= 0xC7 or 0xC9 <= marker <= 0xCB or 0xCD <= marker <= 0xCF:
            require(length >= 7 and i + 7 <= len(data), "creator portrait JPEG dimensions are readable")
            return int.from_bytes(data[i + 5:i + 7], "big"), int.from_bytes(data[i + 3:i + 5], "big")
        i += max(2, length)
    raise AssertionError("creator portrait JPEG dimensions were not found")

def main():
    required = [
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/com/music/echo/offline/files/MainActivity.java",
        "app/src/main/java/com/music/echo/offline/files/SettingsBackupManager.java",
        "app/src/main/java/com/music/echo/offline/files/AboutScreenBuilder.java",
        "app/src/main/java/com/music/echo/offline/files/BrowserView.java",
        "app/src/main/java/com/music/echo/offline/files/RecycleBin.java",
        "app/src/main/java/com/music/echo/offline/files/DeveloperStudioActivity.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiBackNavigation.java",
        "app/src/main/java/com/music/echo/offline/files/HtmlViewerActivity.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiToolsActivity.java",
        "app/src/main/java/com/music/echo/offline/files/ViewerActivity.java",
        "core/src/main/java/com/music/echo/offline/files/DeveloperStudioCore.java",
        "foundation/src/main/java/com/music/echo/offline/files/ToxiUriPermissions.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiVisualSystem.java",
        "app/src/main/java/com/music/echo/offline/files/ToxiIcons.java",
        "app/src/main/java/com/music/echo/offline/files/FileOps.java",
        "core/src/main/java/com/music/echo/offline/files/OperationCancellation.java",
        "core/src/main/java/com/music/echo/offline/files/TaskScheduler.java",
        "core/src/main/java/com/music/echo/offline/files/StorageIndexEngine.java",
        "core/src/main/java/com/music/echo/offline/files/FileOperationEngine.java",
        "core/src/main/java/com/music/echo/offline/files/ArchiveOperations.java",
        "core/src/test/java/com/music/echo/offline/files/ArchiveOperationsTest.java",
        "core/src/test/java/com/music/echo/offline/files/FileOperationEngineTest.java",
        "core/src/test/java/com/music/echo/offline/files/StorageIndexEngineTest.java",
        "foundation/build.gradle.kts",
        "foundation/src/main/AndroidManifest.xml",
        "foundation/src/main/java/com/music/echo/offline/files/TaskCoordinator.java",
        "foundation/src/main/java/com/music/echo/offline/files/SafStorageOperations.java",
        "foundation/src/main/java/com/music/echo/offline/files/ToxiUriPermissions.java",
        "foundation/src/main/java/com/music/echo/offline/files/CrashLogger.java",
        "core/src/test/java/com/music/echo/offline/files/TaskSchedulerTest.java",
        "core/src/main/java/com/music/echo/offline/files/DuplicateFinder.java",
        "core/src/main/java/com/music/echo/offline/files/ArchivePath.java",
        "app/src/test/java/com/music/echo/offline/files/OperationCancellationTest.java",
        "core/src/test/java/com/music/echo/offline/files/DeveloperStudioCoreTest.java",
        "core/src/test/java/com/music/echo/offline/files/BatchRenameEngineTest.java",
        "core/src/test/java/com/music/echo/offline/files/PinHashTest.java",
        "core/src/main/java/com/music/echo/offline/files/SafePathResolver.java",
        "core/src/test/java/com/music/echo/offline/files/SafePathResolverTest.java",
        "app/src/main/java/com/music/echo/offline/files/Vault.java",
        "core/src/main/java/com/music/echo/offline/files/PinHash.java",
        "app/src/main/java/com/music/echo/offline/files/LanTransfer.java",
        "app/src/main/java/com/music/echo/offline/files/Indexer.java",
        "app/src/main/java/com/music/echo/offline/files/DupFinder.java",
        "core/src/test/java/com/music/echo/offline/files/DuplicateFinderTest.java",
        "app/src/main/java/com/music/echo/offline/files/HtmlViewerActivity.java",
        "app/src/main/java/com/music/echo/offline/files/ViewerActivity.java",
        "foundation/src/main/java/com/music/echo/offline/files/CrashLogger.java",
        "tools/developer_studio_smoke_test.py",
        "tools/video_lifecycle_regression_test.py",
    ]
    for item in required:
        p = ROOT / item
        require(p.is_file() and p.stat().st_size > 0, f"required source exists: {item}")

    settings = source("settings.gradle.kts")
    app_gradle = source("app/build.gradle.kts")
    foundation_gradle = source("foundation/build.gradle.kts")
    require('include(":app", ":core", ":foundation")' in settings and 'add("implementation", project(":core"))' in app_gradle and 'add("implementation", project(":foundation"))' in app_gradle and 'add("implementation", project(":core"))' in foundation_gradle and (ROOT / "core/build.gradle.kts").is_file(), "app, Android foundation and platform-neutral core modules have one-way dependency direction")
    require('apply(plugin = "com.android.library")' in foundation_gradle and 'extensions.configure<LibraryExtension>("android")' in foundation_gradle and 'apply(plugin = "com.android.application")' in app_gradle and 'extensions.configure<ApplicationExtension>("android")' in app_gradle and 'namespace = "com.music.echo.foundation"' in foundation_gradle and 'compileSdk = 36' in foundation_gradle and 'minSdk = 26' in foundation_gradle and 'add("implementation",' in app_gradle and 'add("testImplementation",' in app_gradle and 'add("implementation",' in foundation_gradle, "Android modules use shared AGP classpath, typed extensions and explicit dependency configuration names")
    require(all((ROOT / "core/src/main/java/com/music/echo/offline/files" / name).is_file() for name in ("OperationCancellation.java", "ArchivePath.java", "DeveloperStudioCore.java", "BatchRenameEngine.java", "PinHash.java", "SafePathResolver.java")), "core module owns cancellation, archive/path safety, Developer Studio logic and PIN hashing")
    task_scheduler = source("core/src/main/java/com/music/echo/offline/files/TaskScheduler.java")
    task_coordinator = source("foundation/src/main/java/com/music/echo/offline/files/TaskCoordinator.java")
    task_scheduler_tests = source("core/src/test/java/com/music/echo/offline/files/TaskSchedulerTest.java")
    require("android." not in task_scheduler and "ArrayBlockingQueue" in task_scheduler and "ThreadPoolExecutor.AbortPolicy" in task_scheduler and "close(boolean cancelRunning)" in task_scheduler, "core scheduler owns bounded execution lanes and lifecycle cancellation without Android dependencies")
    require("new TaskScheduler(() ->" in task_coordinator and "THREAD_PRIORITY_BACKGROUND" in task_coordinator and "TaskScheduler.ScopedExecutor" in task_coordinator, "Android app adapter applies background thread priority while delegating scheduling to core")
    foundation_services = [source("foundation/src/main/java/com/music/echo/offline/files/" + name) for name in ("TaskCoordinator.java", "SafStorageOperations.java", "ToxiUriPermissions.java", "CrashLogger.java")]
    require(all("com.music.echo.offline.R" not in item and "MainActivity" not in item and "FileOps" not in item and "ToxiVisualSystem" not in item for item in foundation_services), "Android foundation services have no reverse dependency on app features or UI resources")
    require("boundedQueuesRejectOverloadAndInitializerRunsOnWorkers" in task_scheduler_tests and "cancellingScopeInterruptsRunningWork" in task_scheduler_tests, "core scheduler tests cover queue bounds, worker initialization and cancellation")
    dependency_audit = source("tools/dependency_audit.py")
    app_build = source("app/build.gradle.kts")
    require('pom_licenses' in dependency_audit and 'bundled_notices' in dependency_audit and 'OWNER REVIEW REQUIRED' in dependency_audit, "dependency inventory extracts POM licenses and bundled notices without claiming legal approval")
    dependency_audit_tests = source("tools/dependency_audit_test.py")
    require('test_pom_license_metadata_with_maven_namespace' in dependency_audit_tests and 'test_bundled_license_and_notice_files' in dependency_audit_tests, "dependency license metadata parser has regression tests")

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
    indexer = source("app/src/main/java/com/music/echo/offline/files/Indexer.java")
    require("volatile long finished;" in indexer, "index completion timestamp is safely published from the indexing worker to UI readers")
    require("incomplete = true;" in indexer.split("catch (RuntimeException startError)", 1)[1].split("postCompletionCallbacks()", 1)[0] and "Storage refresh failed. Showing the last successful index." in main and "Storage scan incomplete. Some files may be missing." in main, "storage scan queue failures and incomplete traversals are surfaced instead of silently reporting a fully ready index")
    storage_maintenance_index = source("app/src/main/java/com/music/echo/offline/files/StorageMaintenanceScreenController.java")
    require("static final class PublishedSnapshot" in indexer and "volatile PublishedSnapshot published" in indexer and "published = next;" in indexer and "Collections.unmodifiableList" in indexer and "Indexer.I.snapshot()" in storage_maintenance_index and "ix.snapshot()" in main and "Indexer.I.all" not in main and "ix.all" not in main, "storage index generations publish atomically and UI consumers capture one immutable snapshot")
    browser = source("app/src/main/java/com/music/echo/offline/files/BrowserView.java")
    require("boolean executeIo(Runnable task, Runnable onRejected)" in main and browser.count("a.executeIo(") == 9 and "a.io.execute(" not in browser and browser.count("a.executeOperation(") == 3 and "a.ops.execute(" not in browser and browser.count("a.runUiIfAlive(") == 6 and "a.runOnUiThread(" not in browser, "BrowserView background work uses lifecycle-safe dispatch and restores directory loading state when the bounded queue rejects work")
    destroy_lifecycle = main.split("@Override protected void onDestroy()", 1)[1].split("// ---------------- index", 1)[0]
    task_runtime = source("app/src/main/java/com/music/echo/offline/files/ActivityTaskRuntime.java")
    require("taskRuntime.shutdown();" in destroy_lifecycle and "cancellation.shutDown();" in task_runtime and "cancellation.cancelActive();" in task_runtime and "LanTransfer.cancel();" in task_runtime and task_runtime.index("LanTransfer.cancel();") < task_runtime.index("scope.close(true);"), "Activity task runtime permanently closes operation cancellation, the independent LAN transfer session, and scoped workers in order")
    settings_controller = source("app/src/main/java/com/music/echo/offline/files/SettingsScreenController.java")
    require("int binCount = RecycleBin.count(activity);" in settings_controller and "RecycleBin.list(activity).size()" not in settings_controller, "Settings uses a lightweight Recycle Bin count instead of loading every item on the UI thread")
    require("settingsScreenController = new SettingsScreenController(this);" in main and "settingsScreenController.build();" in main and "activity.showCrashLogOptions()" in settings_controller and "void showCrashLogOptions()" in main, "MainActivity delegates Settings composition while preserving the diagnostics action boundary")
    recycle_ui = source("app/src/main/java/com/music/echo/offline/files/RecycleBinScreenController.java")
    require("render(null, true);" in recycle_ui and "activity.executeIo(() ->" in recycle_ui and "RecycleBin.list(activity)" in recycle_ui and "activity.runUiIfAlive(() -> render(items, false))" in recycle_ui and "activity.recycle.addView" in recycle_ui, "Recycle Bin screen controller enumerates on IO and renders loading/results through the lifecycle-safe dispatcher")
    require("recycleBinScreen = new RecycleBinScreenController(this);" in main and "recycleBinScreen.show();" in main and "recycleBinScreen.reload();" in main, "MainActivity delegates Recycle Bin screen lifecycle to its dedicated controller")
    recycle_bin = source("app/src/main/java/com/music/echo/offline/files/RecycleBin.java")
    recycle_list = recycle_bin.split("static List<Item> list(Context c)", 1)[1].split("private static void scheduleExpiryCleanup", 1)[0]
    require("scheduleExpiryCleanup(expired);" in recycle_list and "FileOps.deleteTree(w)" not in recycle_list and "TaskCoordinator.execute(TaskCoordinator.Lane.IO" in recycle_bin and "EXPIRY_CLEANUP_PENDING" in recycle_bin, "expired Recycle Bin trees are purged asynchronously on the bounded IO lane, not during UI listing")
    recycle_bin = source("app/src/main/java/com/music/echo/offline/files/RecycleBin.java")
    require("void executeOperation(Runnable task)" in main and recycle_bin.count("a.executeOperation(") == 4 and "a.ops.execute(" not in recycle_bin and "a.runOnUiThread(" not in recycle_bin and recycle_bin.count("a.runUiIfAlive(") >= 5, "RecycleBin operations handle bounded-queue rejection and suppress callbacks after Activity destruction")
    lan_transfer = source("app/src/main/java/com/music/echo/offline/files/LanTransfer.java")
    lan_hash = lan_transfer.split("private static String sha256(File f)", 1)[1].split("private static String hex(", 1)[0]
    require("OperationCancellation.checkCurrent();" in lan_hash and "private static String sha256(File f) throws IOException" in lan_transfer and lan_transfer.count("OperationCancellation.checkCurrent();") >= 10, "LAN transfers cooperatively stop file hashing and encrypted send/receive loops when cancelled")
    storage_maintenance = source("app/src/main/java/com/music/echo/offline/files/StorageMaintenanceScreenController.java")
    cleanup_scan = storage_maintenance.split("void openCleanup()", 1)[1].split("void buildEmptyFolders(", 1)[0]
    require("OperationCancellation.Token operationToken = activity.cancellation.begin();" in cleanup_scan and cleanup_scan.count("OperationCancellation.isCancelled()") >= 3 and "activity.cancellation.finish(operationToken);" in cleanup_scan, "empty-folder scanning stops promptly when its Activity-scoped cancellation token is cancelled")
    require("storageMaintenanceScreen = new StorageMaintenanceScreenController(this);" in main and "storageMaintenanceScreen.openDuplicates();" in main and "storageMaintenanceScreen.openCleanup();" in main, "MainActivity delegates duplicate and empty-folder screen workflows to the storage-maintenance controller")
    saf_browser_dispatch = source("app/src/main/java/com/music/echo/offline/files/SafBrowserController.java")
    require(main.count("io.execute(") == 1 and main.count("ops.execute(") == 1 and main.count("executeIo(") + storage_maintenance.count("activity.executeIo(") + saf_browser_dispatch.count("activity.executeIo(") >= 9 and main.count("executeOperation(") + saf_browser_dispatch.count("activity.executeOperation(") == 4 and "catch (RejectedExecutionException rejected)" in main and "busy(false);" in main, "bounded activity, storage-maintenance and SAF submissions recover from queue rejection without leaving operations busy")
    require(main.count("runOnUiThread(") == 1 and main.count("runUiIfAlive(") + storage_maintenance.count("activity.runUiIfAlive(") + source("app/src/main/java/com/music/echo/offline/files/LanTransferScreenController.java").count("activity.runUiIfAlive(") + saf_browser_dispatch.count("activity.runUiIfAlive(") >= 23, "activity, storage-maintenance, Wi-Fi transfer and SAF callbacks use the destruction-safe dispatcher")
    file_ops = source("app/src/main/java/com/music/echo/offline/files/FileOps.java")
    require("private static void execute(MainActivity a, Done done, String failureMessage, Runnable operation)" in file_ops and file_ops.count("execute(a, d,") == 4 and file_ops.count("a.ops.execute(") == 1 and "operation could not be queued" in file_ops and "catch (RuntimeException failure)" in file_ops, "file operations always clear busy state and report queue rejection or unexpected worker failures")
    settings_backup = source("app/src/main/java/com/music/echo/offline/files/SettingsBackupManager.java")
    about_builder = source("app/src/main/java/com/music/echo/offline/files/AboutScreenBuilder.java")
    require("AboutScreenBuilder.populate(this, about, this::closeOverlay, versionName())" in main and "addAboutGridSection" not in main and "aboutText(" in about_builder and "openExternalUrl(" in about_builder, "About profile content and layout helpers are isolated from MainActivity")
    require("SettingsBackupManager.exportToDownloads(this, ToxiVisualSystem.PREFERENCES)" in main and "SettingsBackupManager.importFromUri(this, file, ToxiVisualSystem.PREFERENCES" in main and "portableSettingKeys" not in main and "MAX_IMPORT_BYTES = 1024 * 1024" in settings_backup and "ToxiUriPermissions.openInputStream(context, uri)" in settings_backup and "editor.commit()" in settings_backup, "settings backup serialization, bounded SAF import and allow-listed preference validation are isolated from MainActivity")
    developer = source("app/src/main/java/com/music/echo/offline/files/DeveloperStudioActivity.java")
    settings_controller = source("app/src/main/java/com/music/echo/offline/files/SettingsScreenController.java")
    require('if (ToxiVisualSystem.booleanPreference("developer_options_unlocked", false)) {' in settings_controller and 'activity.startActivity(new Intent(activity, DeveloperStudioActivity.class))' in settings_controller, "Developer Studio is exposed only after the existing Developer unlock")
    require('private boolean developerUnlocked()' in developer and 'if(!developerUnlocked()){finish();return;}' in developer, "Developer Studio independently rejects direct access while locked")
    require('private static final String[] NAMES' in developer and 'openTool(int index)' in developer, "all Developer tools share one functional internal tool registry")
    require('Intent.ACTION_OPEN_DOCUMENT' in developer and 'Intent.ACTION_OPEN_DOCUMENT_TREE' in developer and 'ToxiUriPermissions.openInputStream(this,uri)' in developer and 'DocumentFile.fromTreeUri(this,root)' in developer, "Developer tools read user-selected files and folders through Android SAF")
    uri_permissions = source("foundation/src/main/java/com/music/echo/offline/files/ToxiUriPermissions.java")
    require(uri_permissions.count("takePersistableUriPermission(") == 2 and "Intent.FLAG_GRANT_READ_URI_PERMISSION);" in uri_permissions and "Intent.FLAG_GRANT_WRITE_URI_PERMISSION);" in uri_permissions, "persistable URI permissions are stored one validated grant mode at a time")
    java_sources = list((ROOT / "app/src/main/java").rglob("*.java")) + list((ROOT / "foundation/src/main/java").rglob("*.java"))
    direct_uri_permission_calls = [str(path.relative_to(ROOT)) for path in java_sources if path.name != "ToxiUriPermissions.java" and "takePersistableUriPermission(" in path.read_text(encoding="utf-8")]
    require(not direct_uri_permission_calls, "all activities use the shared SAF permission helper; direct unsafe calls are blocked")
    require("ToxiUriPermissions.takeGranted(getContentResolver(),uri,data.getFlags());" in source("app/src/main/java/com/music/echo/offline/files/ToxiToolsActivity.java") and "ToxiUriPermissions.takeGranted(getContentResolver(),u,data.getFlags());" in developer, "file tools and Developer Studio share the same validated SAF permission path")


    saf_ops = source("foundation/src/main/java/com/music/echo/offline/files/SafStorageOperations.java")
    saf_browser = source("app/src/main/java/com/music/echo/offline/files/SafBrowserController.java")
    require("SafStorageOperations.copyLocalFilesToTree(" in saf_browser and "static BatchCopyResult copyLocalFilesToTree(Context context, ContentResolver resolver" in saf_ops and "DocumentFile.fromTreeUri(context, tree)" in saf_ops and "static Uri treeRootDocumentUri(Uri tree)" in saf_ops and "static Uri documentUriInTree(Uri tree, String documentId)" in saf_ops and "static String documentId(Uri document)" in saf_ops and "DocumentsContract" not in main and "DocumentFile" not in main and "deleteSafTree" not in main and "static DocumentFile copyLocalTree(ContentResolver resolver, File source" in saf_ops and "CopyProgressListener" in saf_ops and "OperationCancellation.checkCurrent()" in saf_ops and "DocumentFile.fromTreeUri(this, safTree)" not in main, "SAF tree resolution, document URI construction, destination validation, batch copying and rollback cleanup are owned by the shared provider service")
    require("for (File file : files)" not in main and "static BatchCopyResult copyLocalFiles(" in saf_ops, "SAF batch copy orchestration and stop-on-first-failure behavior are service-owned")
    traversal_budget = source("core/src/main/java/com/music/echo/offline/files/TreeTraversalBudget.java")
    traversal_budget_test = source("core/src/test/java/com/music/echo/offline/files/TreeTraversalBudgetTest.java")
    require("budget.enter(depth);" in saf_ops and saf_ops.count("OperationCancellation.checkCurrent();") >= 4 and "new TreeTraversalBudget()" in saf_ops and "visitedDirectories" in saf_ops and "repeated or cyclic directory" in saf_ops and "DEFAULT_MAX_ENTRIES" in saf_ops and "Directory nesting exceeds the supported limit" in traversal_budget and "Tree contains more than the supported limit" in traversal_budget and "OperationCancellation.checkCurrent()" not in traversal_budget and all(name in traversal_budget_test for name in ("allowsTheConfiguredEntryLimit", "rejectsTheFirstEntryBeyondTheLimit", "rejectsExcessiveDirectoryDepth", "rejectsInvalidLimits")), "SAF copy enforces cancellable bounded traversal while rollback cleanup remains runnable after cancellation")
    require("DocumentsContract.moveDocument" in saf_ops and "DocumentsContract.renameDocument" in saf_ops and "DocumentsContract.deleteDocument" in saf_ops, "SAF service exposes provider-native move, rename and delete without filesystem-path assumptions")
    require("copyDocumentTree(ContentResolver resolver" in saf_ops and "copyLocalTree(ContentResolver resolver" in saf_ops and "OperationCancellation.checkCurrent()" in saf_ops, "SAF service supports cancellable recursive provider-to-provider and local-to-provider copies")
    require("BatchRenameEngine.validateName(name)" in saf_ops and "uniqueName(parent, name" in saf_ops and "deleteTree(target)" in saf_ops, "SAF copies validate leaf names, avoid collisions and clean partial provider output")
    saf_delete = saf_ops.split("static void deleteTree(DocumentFile target)", 1)[1].split("private static String documentIdentity", 1)[0]
    require("ArrayDeque<DocumentDeleteNode>" in saf_delete and "deleteAfterChildren" in saf_delete and "new TreeTraversalBudget(" in saf_delete and "Integer.MAX_VALUE" in saf_delete and "deleteTree(child" not in saf_delete, "SAF rollback deletion is iterative, cycle-aware and bounded by entry count without recursive stack depth")
    rename_engine = source("core/src/main/java/com/music/echo/offline/files/BatchRenameEngine.java")
    require("parentPath.resolve(name).normalize()" in rename_engine and "candidate.startsWith(parentPath)" in rename_engine and "LinkOption.NOFOLLOW_LINKS" in rename_engine and "BatchRenameEngine.moveChild(e.f, t)" in source("app/src/main/java/com/music/echo/offline/files/BrowserView.java"), "filesystem rename uses normalized direct-child paths and refuses symlink traversal")

    require("setOnItemLongClickListener" in saf_browser and "showSafDocumentActions(tree, docUri, title, selected, row.name" in saf_browser and "SafStorageOperations.listChildren(activity.getContentResolver(), tree, docUri)" in saf_browser, "existing SAF browser uses typed provider entries and exposes document actions only from the selected row context")
    contains_document = saf_ops.split("private static boolean containsDocument(ContentResolver resolver", 1)[1].split("/** Resolves the moving document", 1)[0]
    move_destinations = saf_ops.split("static ArrayList<DirectoryEntry> listMoveDestinations(", 1)[1].split("static Uri treeRootDocumentUri", 1)[0]
    require("static ArrayList<DirectoryEntry> listChildren(ContentResolver resolver" in saf_ops and "DEFAULT_MAX_ENTRIES" in saf_ops and "OperationCancellation.checkCurrent()" in saf_ops and "static boolean containsDocument(ContentResolver resolver" in saf_ops and "containsDocument(resolver, tree, child, sourceDocument, budget)" in move_destinations and "TreeTraversalBudget budget = new TreeTraversalBudget();" in move_destinations and "budget.enter(0);" in contains_document and "budget.enter(0);" in move_destinations, "SAF descendant checks share one global provider-entry budget across the complete move-destination discovery operation")
    require("static ArrayList<DirectoryEntry> listMoveDestinations(Context context, ContentResolver resolver" in saf_ops and "findDocumentInTree(context, tree, sourceDocument)" in saf_ops and "SafStorageOperations.listMoveDestinations(" in saf_browser and "safDocument(" not in saf_browser and "SafStorageOperations.sameDocument(currentFolder, sourceParent)" in saf_browser, "SAF move destination discovery, source-type resolution and self/descendant filtering are owned by the shared storage service")
    require("static File importDocument(Context context, Uri document" in saf_ops and "ToxiUriPermissions.openInputStream(context, document)" in saf_ops and "resolver.openInputStream(document)" not in saf_ops and "activity.getApplicationContext(), doc, download, name" in saf_browser, "SAF imports validate the selected document URI and explicit read grant before opening provider streams")
    require('"Duplicate here"' in saf_browser and "SafStorageOperations.duplicateDocumentInTree" in saf_browser and "SafStorageOperations.renameDocument" in saf_browser and "SafStorageOperations.deleteDocument" in saf_browser, "SAF browser delegates duplicate, rename and permanent delete to provider operations")
    require("DocumentsContract.createDocument" in saf_ops and "buildChildDocumentsUriUsingTree" in saf_ops and "OperationCancellation.checkCurrent()" in saf_ops, "SAF document duplication creates collision-safe provider documents and cleans partial output")
    visual = source("app/src/main/java/com/music/echo/offline/files/ToxiVisualSystem.java")
    icons = source("app/src/main/java/com/music/echo/offline/files/ToxiIcons.java")
    portrait = ROOT / "app/src/main/res/drawable-nodpi/chandu_profile.jpg"
    require(portrait.is_file() and portrait.stat().st_size > 0, "supplied creator portrait is packaged")
    portrait_width, portrait_height = jpeg_size(portrait)
    require(portrait_width == portrait_height, "creator portrait is a clean square image")
    about_builder = source("app/src/main/java/com/music/echo/offline/files/AboutScreenBuilder.java")
    require('R.drawable.chandu_profile' in about_builder and 'ImageView.ScaleType.CENTER_CROP' in about_builder and 'outline.setOval' in about_builder, "About renders the supplied portrait as a circular center-cropped avatar")
    dock = source("app/src/main/java/com/music/echo/offline/files/PrismNavigationDock.java")
    require('selection.setVisibility(View.INVISIBLE)' in dock and 'selection.setVisibility(View.VISIBLE)' in dock and 'surface.postOnAnimation(this::layoutSelection)' in dock and 'itemLayoutListenersBound' in dock, "bottom dock defers selection until first layout and avoids duplicate geometry listeners")
    require('high_text_contrast_enabled' in visual and 'HIGH_CONTRAST ? 0xFFE8EEF5' in visual, "Android high-contrast mode increases secondary text contrast")
    require('setMinimumWidth(dp(48))' in visual and 'setMinimumHeight(dp(48))' in visual and 'ToxiIcons.accessibilityLabel(symbol)' in visual, "shared icon controls have accessible labels and 48dp touch targets")
    require('static void interactive(View view)' in visual and 'view.setOnTouchListener' not in visual and 'setOnClickListener' in main, "shared interaction styling does not intercept native click dispatch")
    require('Obsidian Alloy' in source("app/src/main/java/com/music/echo/offline/files/ToxiThemeSystem.java") and 'hasIndependentTokens()' in source("app/src/main/java/com/music/echo/offline/files/ToxiThemeSystem.java"), "current theme catalog uses independent design tokens")
    theme_screen = source("app/src/main/java/com/music/echo/offline/files/ThemeScreenController.java")
    require("themeScreenController = new ThemeScreenController(this);" in main and "themeScreenController.open();" in main and "themeScreenController.build();" in main and "ToxiVisualSystem.applyTheme(themeIndex, true);" in theme_screen and "activity.recolor();" in theme_screen and "activity.themeScroll.addView" in theme_screen, "MainActivity delegates Appearance Lab rendering and live theme previews to its screen controller")
    require('settingsScroll.setClipToPadding(true)' in main and 'ToxiVisualSystem.dp(96) + ToxiVisualSystem.BOTTOM_INSET' in main, "Settings reserves scroll space above bottom navigation")
    require('activity.settings.addView(activity.section("About"))' in settings_controller and 'About ToxiFiles' in settings_controller, "About remains at the bottom of Settings")

    file_ops = source("app/src/main/java/com/music/echo/offline/files/FileOps.java")
    archive_path = source("core/src/main/java/com/music/echo/offline/files/ArchivePath.java")
    cancellation = source("core/src/main/java/com/music/echo/offline/files/OperationCancellation.java")
    require('OperationCancellation.checkCurrent()' in file_ops and 'OperationCancellation.isCancelled()' in file_ops and 'cancelRequested' not in file_ops and file_ops.count('a.cancellation.begin()') == 4 and file_ops.count('a.cancellation.finish(operationToken)') == 4 and 'cancellation.cancelActive()' in main and 'FileOps.cancelRequested' not in main, "file operations use isolated operation-scoped cancellation")
    require('ThreadLocal<Token>' in cancellation and 'ConcurrentHashMap.newKeySet()' in cancellation and 'void cancelActive()' in cancellation and saf_browser.count('activity.cancellation.begin()') == 3 and saf_browser.count('activity.cancellation.finish(operationToken)') == 3 and browser.count('a.cancellation.begin()') == 1 and browser.count('a.cancellation.finish(operationToken)') == 1, "cancellation is worker-thread-bound and scoped to the owning activity")
    require((ROOT / "app/src/test/java/com/music/echo/offline/files/OperationCancellationTest.java").is_file(), "operation cancellation has JVM regression tests")
    java_sources = list((ROOT / "app/src/main/java").rglob("*.java")) + list((ROOT / "foundation/src/main/java").rglob("*.java"))
    test_sources = list((ROOT / "app/src/test/java").rglob("*.java")) + list((ROOT / "core/src/test/java").rglob("*.java"))
    all_java_sources = java_sources + test_sources
    legacy_cancel_callers = [str(path.relative_to(ROOT)) for path in all_java_sources if "FileOps.cancelRequested" in path.read_text(encoding="utf-8")]
    require(not legacy_cancel_callers, "no legacy global file-cancellation references remain in production or test Java sources")
    toxi_tools = source("app/src/main/java/com/music/echo/offline/files/ToxiToolsActivity.java")
    lan_transfer = source("app/src/main/java/com/music/echo/offline/files/LanTransfer.java")
    require(toxi_tools.count('OperationCancellation.Token operationToken') == 3 and 'cancellation.cancelActive()' in toxi_tools, "Developer file tools cancel and clean up their own worker operations")
    require(lan_transfer.count('OperationCancellation.Token operationToken') == 2 and 'cancellation.cancelActive()' in lan_transfer and lan_transfer.count('session.cancellation.finish(operationToken)') == 2, "LAN transfer cancellation reaches authenticated receive commit work")
    browser = source("app/src/main/java/com/music/echo/offline/files/BrowserView.java")
    file_row = source("app/src/main/java/com/music/echo/offline/files/ToxiFileRow.java")
    require("ToxiFileRow.holder(cv)" in browser and "if (h == null)" in browser and "cv = ToxiFileRow.create(a)" in browser and "(ToxiFileRow.Holder) cv.getTag()" not in browser and "static Holder holder(View view)" in file_row and "holder.root != view" in file_row, "ListView recycling validates holder tags and recreates malformed convertView rows before binding")
    vault = source("app/src/main/java/com/music/echo/offline/files/Vault.java")
    recycle_bin = source("app/src/main/java/com/music/echo/offline/files/RecycleBin.java")
    require('FileOps.cancelRequested' not in browser + vault + recycle_bin and browser.count('a.cancellation.begin()') == 1 and vault.count('a.cancellation.begin()') == 3 and recycle_bin.count('a.cancellation.begin()') == 4 and 'OperationCancellation.isCancelled()' in vault and 'OperationCancellation.isCancelled()' in recycle_bin, "BrowserView, Vault and Recycle Bin use isolated operation cancellation")
    archive_ops = source("core/src/main/java/com/music/echo/offline/files/ArchiveOperations.java")
    archive_ops_tests = source("core/src/test/java/com/music/echo/offline/files/ArchiveOperationsTest.java")
    require('ArchiveOperations.createZip(fs, destDir)' in file_ops and 'ArchiveOperations.extractZip(zip, zip.getParentFile()' in file_ops and 'ArchiveOperations.listZip(zip' in file_ops, "FileOps delegates ZIP creation, extraction and listing to the shared archive engine")
    archive_contract = "".join(archive_ops.split())
    require('ArchivePath.normalize(rawName,entry.isDirectory())' in archive_contract and 'initialFreeSpace' in archive_contract and 'extractedPaths' in archive_contract and 'ensureDirectories(root,output)' in archive_contract and 'MAX_TOTAL_BYTES' in archive_contract, "core archive engine rejects zip-slip, duplicate paths, symlinks and expanded-size exhaustion")
    require('Symbolic links cannot be archived' in archive_ops and 'rejectsSymbolicLinksAsArchiveRoots' in archive_ops_tests, "archive creation rejects a selected symlink root instead of emitting an empty archive")
    require('Unsafe archive entry path rejected' in archive_path and 'segment.isEmpty()' in archive_path and '"..".equals(segment)' in archive_path, "ZIP path normalization rejects traversal and ambiguous segments")
    require((ROOT / "core/src/test/java/com/music/echo/offline/files/ArchivePathTest.java").is_file(), "ZIP path validation has JVM regression tests")
    require('Copied to destination, but source could not be removed' in file_ops and 'Recycle Bin: removed' in source("app/src/main/java/com/music/echo/offline/files/RecycleBin.java"), "move and Recycle Bin operations report cleanup failures accurately")

    vault = source("app/src/main/java/com/music/echo/offline/files/Vault.java")
    vault_screen = source("app/src/main/java/com/music/echo/offline/files/VaultScreenController.java")
    require("vaultScreenController = new VaultScreenController(this);" in main and "vaultScreenController.showSetPinDialog();" in main and "vaultScreenController.promptUnlock();" in main and "vaultScreenController.build();" in main and "Vault.list(activity)" in vault_screen and "Vault.restoreOut(activity" in vault_screen and "Vault.deleteForever(activity" in vault_screen, "MainActivity delegates Vault PIN, rendering and item actions to the dedicated Vault screen controller")
    pin_hash = source("core/src/main/java/com/music/echo/offline/files/PinHash.java")
    require('AES/GCM/NoPadding' in vault and 'doFinal()' in vault and 'encrypted Vault cleanup was incomplete' in vault, "Vault uses authenticated encryption and reports incomplete cleanup")
    require('iterations < 10_000' in pin_hash and 'iterations > 1_000_000' in pin_hash and 'MessageDigest.isEqual' in pin_hash, "PIN verification bounds PBKDF2 work and compares verifiers in constant time")
    require((ROOT / "core/src/test/java/com/music/echo/offline/files/PinHashTest.java").is_file(), "PIN verification has automated boundary tests")
    lan = source("app/src/main/java/com/music/echo/offline/files/LanTransfer.java")
    lan_screen = source("app/src/main/java/com/music/echo/offline/files/LanTransferScreenController.java")
    require("lanTransferScreen = new LanTransferScreenController(this);" in main and "lanTransferScreen.receive();" in main and "lanTransferScreen.send(files);" in main, "MainActivity delegates Wi-Fi transfer presentation to its dedicated controller")
    require("LanTransfer.receive(destination" in lan_screen and "LanTransfer.send(ip, code, files" in lan_screen and "code.length() != 10" in lan_screen and lan_screen.count("activity.runUiIfAlive(") >= 5 and "LanTransfer.cancel()" in lan_screen, "Wi-Fi transfer UI validates pairing input and dispatches lifecycle-safe receive/send callbacks")
    require('PBKDF2WithHmacSHA256' in lan and 'AES/GCM/NoPadding' in lan and 'authProof' in lan, "local Wi-Fi transfer authenticates pairing and encrypts payloads")
    require('getCanonicalPath().startsWith(rootPath)' in lan and 'Unsafe incoming filename rejected' in lan, "LAN receive validates names and canonical destination containment")
    require((ROOT / "app/src/test/java/com/music/echo/offline/files/LanTransferCryptoTest.java").is_file(), "LAN transfer has cryptographic and filename regression tests")

    indexer = source("app/src/main/java/com/music/echo/offline/files/Indexer.java")
    storage_index = source("core/src/main/java/com/music/echo/offline/files/StorageIndexEngine.java")
    storage_index_tests = source("core/src/test/java/com/music/echo/offline/files/StorageIndexEngineTest.java")
    require("StorageIndexEngine.scan(Environment.getExternalStorageDirectory()" in indexer and "new Entry(snapshot.file" in indexer, "Android index adapter consumes core snapshots and retains app-only categorization")
    require("OperationCancellation.Token operationToken = cancellation.begin()" in indexer and "cancellation.finish(operationToken)" in indexer, "storage index worker binds cancellation to its actual background thread")
    require("Files.newDirectoryStream" in storage_index and "NOFOLLOW_LINKS" in storage_index and "MAX_INDEX_ENTRIES = 150_000" in storage_index and "OperationCancellation.checkCurrent()" in storage_index, "core storage traversal streams directories, rejects symlinks, checks cancellation and bounds entries")
    require("skipsHiddenEntriesAndRootAndroidDirectory" in storage_index_tests and "skipsSymbolicLinksWithoutFollowingTheirTargets" in storage_index_tests and "enforcesEntryLimitAndMarksResultsIncomplete" in storage_index_tests and "cancellationStopsTraversalInsteadOfPublishingPartialResults" in storage_index_tests, "core storage scanner has regression coverage for hidden paths, symlinks, limits and cancellation")
    storage_analyzer = source("app/src/main/java/com/music/echo/offline/files/StorageAnalyzerController.java")
    require("StorageIndexEngine.scan(rootDir, 60" in storage_analyzer and "StorageIndexEngine.MAX_INDEX_ENTRIES, true, true" in storage_analyzer and "scan.incomplete" in storage_analyzer and "OperationCancellation.Token operationToken = activity.cancellation.begin()" in storage_analyzer and "activity.cancellation.finish(operationToken)" in storage_analyzer, "storage analyzer reuses bounded core traversal, binds cancellation to its worker and discloses incomplete scans")
    require("storageAnalyzerController = new StorageAnalyzerController(this);" in main and "storageAnalyzerController.open();" in main and "StorageIndexEngine.scan(rootDir" not in main, "MainActivity delegates storage analysis to its dedicated controller")
    require("includesHiddenEntriesAndRootAndroidDirectoryWhenRequested" in storage_index_tests, "core scanner supports explicit full-storage analysis without changing index defaults")
    dup_adapter = source("app/src/main/java/com/music/echo/offline/files/DupFinder.java")
    dup_finder = source("core/src/main/java/com/music/echo/offline/files/DuplicateFinder.java")
    dup_tests = source("core/src/test/java/com/music/echo/offline/files/DuplicateFinderTest.java")
    require('DuplicateFinder.find(snapshots)' in dup_adapter and 'new DuplicateFinder.Snapshot' in dup_adapter, "app duplicate adapter delegates to core snapshots")
    require(('snapshot.length < 0' in dup_finder or 'snapshot.length<0' in dup_finder) and 'throws IOException' in dup_finder and 'return null' not in dup_finder and 'Files.isSymbolicLink' in dup_finder and 'OperationCancellation.checkCurrent()' in dup_finder, "core duplicate detection rejects stale/symlink entries and propagates hash failures")
    require('catch (IOException scanFailure)' in storage_maintenance and 'Duplicate scan failed:' in storage_maintenance and 'activity.cancellation.finish(operationToken)' in storage_maintenance, "duplicate scan reports incomplete I/O failures to the user")
    require('includesEmptyFilesAsExactDuplicates' in dup_tests and 'reportsDisappearingFilesInsteadOfReturningPartialResults' in dup_tests and 'honorsScopedOperationCancellation' in dup_tests, "duplicate scanner has regression coverage for empty and disappearing files")
    html = source("app/src/main/java/com/music/echo/offline/files/HtmlViewerActivity.java")
    require('setJavaScriptEnabled(false)' in html and 'setDomStorageEnabled(false)' in html and 'setAllowFileAccess(false)' in html and 'setAllowContentAccess(false)' in html and 'MIXED_CONTENT_NEVER_ALLOW' in html and 'SafePathResolver.resolveExistingFile(rootPath, path)' in html and 'Uri.decode(path)' not in html, "built-in HTML viewer disables active scripts and confines every asset to a canonical real-path root")
    safe_paths = source("core/src/main/java/com/music/echo/offline/files/SafePathResolver.java")
    safe_path_tests = source("core/src/test/java/com/music/echo/offline/files/SafePathResolverTest.java")
    require('candidate.startsWith(root)' in safe_paths and 'canonical.startsWith(root)' in safe_paths and 'toRealPath()' in safe_paths and 'Files.isRegularFile' in safe_paths, "HTML asset path resolver enforces canonical segment-aware containment and symlink resolution")
    require('rejectsParentTraversalAbsoluteAndBackslashPaths' in safe_path_tests and 'rejectsSymlinkTargetsOutsideTheRoot' in safe_path_tests and 'preservesLiteralPercentEncodedLookingFileNamesWithoutDecodingAgain' in safe_path_tests, "canonical asset path resolver has traversal and symlink escape regression tests")
    viewer = source("app/src/main/java/com/music/echo/offline/files/ViewerActivity.java")
    require('TextureView' in viewer and 'setSurfaceTextureListener' in viewer and 'USAGE_MEDIA' in viewer and 'A-B loop enabled' in viewer, "built-in media viewer retains native video and audio playback controls")
    pdf_open = viewer.split('void showPdf(File f)', 1)[1].split('private void showPdfPages', 1)[0]
    require('io.execute(' in pdf_open and 'ParcelFileDescriptor.open(f' in pdf_open and 'h.post(' in pdf_open and 'volatile long pdfGeneration' in viewer, "PDF descriptor and renderer open off the UI thread with stale-load protection")
    require('synchronized (renderer)' in viewer and 'closePdfResourcesAsync(renderer, descriptor)' in viewer, "PDF renderer close is serialized against page rendering")
    crash = source("foundation/src/main/java/com/music/echo/offline/files/CrashLogger.java")
    require('FATAL EXCEPTION' in crash and 'latest_crash.txt' in crash and 'printStackTrace' in crash, "crash diagnostics persist locally")
    require("private static synchronized void writeCrash" in crash and "public static synchronized boolean deleteReports(Context context)" in crash and "new AtomicFile(new File(dir, LATEST))" in crash and "latest.delete()" in crash, "crash report writes and deletion are serialized and AtomicFile recovery state is removed")
    clipboard = source("app/src/main/java/com/music/echo/offline/files/ClipboardController.java")
    require("files.clone()" in clipboard and "File[] snapshot()" in clipboard and "void clear()" in clipboard and "boolean isCut()" in clipboard, "clipboard controller isolates copy/cut state and returns defensive file snapshots")
    require("clipboard.set(files, cut)" in main and "a.clipboard.snapshot()" in browser and "a.clipboard.isCut()" in browser and not re.search(r"\ba\.clip(?:Cut)?\b", browser), "file browser consumes the shared clipboard controller without direct mutable state access")
    favorites = source("app/src/main/java/com/music/echo/offline/files/FavoritesRepository.java")
    require("LinkedHashSet" in favorites and "getStringSet(\"paths\", null)" in favorites and "new HashSet<>(paths)" in favorites and "void rename(File from, File to)" in favorites, "favorites repository preserves ordered in-memory paths and defensive local persistence")
    require("favoritesRepository.load(" in main and "favoritesRepository.toggle(f)" in main and "favoritesRepository.remove(fs)" in main and "favoritesRepository.rename(from, to)" in main, "MainActivity delegates favorite persistence and mutations to the existing favorites repository")
    main_overlay = source("app/src/main/java/com/music/echo/offline/files/MainOverlayController.java")
    require("FLAG_SECURE" in main_overlay and "setRenderEffect" in main_overlay and "accessibility.hideBackground" in main_overlay and "accessibility.restore()" in main_overlay and "activity.listView.closeSearch()" in main_overlay, "overlay controller owns secure vault, blur, accessibility restoration and list cleanup")
    require("overlayController.show(v)" in main and "overlayController.close()" in main and "overlayController.current()" in main and "View overlayView;" not in main, "MainActivity delegates overlay state and transitions to MainOverlayController")
    overlay_accessibility = source("app/src/main/java/com/music/echo/offline/files/OverlayAccessibilityController.java")
    require("IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS" in overlay_accessibility and "setImportantForAccessibility(screenAccessibility)" in overlay_accessibility and "setVisibility(screenVisibility)" in overlay_accessibility, "overlay accessibility controller restores the exact background accessibility and visibility state")
    require("accessibility.hideBackground(backgroundScreen" in main_overlay and "accessibility.restore()" in main_overlay and "overlayBackgroundScreen" not in main, "MainOverlayController delegates overlay accessibility capture and restoration to its dedicated controller")
    navigation_bar = source("app/src/main/java/com/music/echo/offline/files/MainNavigationBar.java")
    require("onTabSelected.accept(index)" in navigation_bar and "void applyBottomInset()" in navigation_bar and "void moveHighlight(int index)" in navigation_bar and "void recolor(int selectedIndex)" in navigation_bar, "bottom navigation owns tab selection, safe-area placement, animated highlight and theme updates")
    main_navigation = source("app/src/main/java/com/music/echo/offline/files/MainNavigationController.java")
    require("activity.closeOverlay();" in main_navigation and "activity.tab = index;" in main_navigation and "activity.buildHome();" in main_navigation and "activity.buildTools();" in main_navigation and "activity.buildSettings();" in main_navigation and "moveHighlight(index);" in main_navigation, "navigation controller owns tab transitions and invokes the existing feature screen builders")
    require("activity.currentOverlay() == activity.listView" in main_navigation and "activity.currentOverlay() != null" in main_navigation and "activity.listView.handleBack()" in main_navigation and "activity.filesView.handleBack()" in main_navigation and "activity.finish();" in main_navigation and "View currentOverlay()" in main and "navigationController.selectTab(i);" in main and "navigationController.handleBackNavigation();" in main, "navigation controller preserves overlay, file-browser and root Back priority through the activity overlay boundary")
    require("navigationBar.build();" in main_navigation and "navigationBar.setVisible(!hidden);" in main_navigation and "navigationBar.recolor(selectedIndex);" in main_navigation and "navigationController.applyBottomInset();" in main and "navigationController.recolor(tab);" in main, "navigation controller owns bottom navigation presentation, insets and theme updates")
    app_lock_lifecycle = source("app/src/main/java/com/music/echo/offline/files/AppLockLifecycleController.java")
    require("Intent.ACTION_SCREEN_OFF" in app_lock_lifecycle and "Context.RECEIVER_NOT_EXPORTED" in app_lock_lifecycle and "activity.unregisterReceiver(screenOffReceiver)" in app_lock_lifecycle and "registered = false" in app_lock_lifecycle, "App Lock screen-off receiver uses Android 13+ non-exported registration and safe teardown")
    require("appLockLifecycle.register();" in main and "appLockLifecycle.onResume();" in main and "appLockLifecycle.onUserLeaveHint();" in main and "appLockLifecycle.unregister();" in main, "MainActivity delegates App Lock and vault lifecycle transitions to the lifecycle controller")
    storage_permissions = source("app/src/main/java/com/music/echo/offline/files/StoragePermissionController.java")
    require("Environment.isExternalStorageManager()" in storage_permissions and "REQUEST_LEGACY_STORAGE = 77" in storage_permissions and "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION" in storage_permissions and "ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION" in storage_permissions, "storage permission policy handles modern and legacy Android access flows")
    require("storagePermissions.hasAccess()" in main and "storagePermissions.requestLegacyPermissions()" in main and "storagePermissions.showAccessPrompt()" in main, "MainActivity delegates storage access decisions and prompts to the permission controller")
    crash_ui = source("app/src/main/java/com/music/echo/offline/files/CrashReportController.java")
    require("CrashLogger.deleteReports(activity)" in crash_ui and "FileProvider.getUriForFile(activity" in crash_ui and "activity.isFinishing() || activity.isDestroyed()" in crash_ui and 'name.startsWith("crash-")' not in main, "crash report UI delegates safe local deletion and FileProvider sharing to its lifecycle controller")
    require("crashReports.maybeOfferCrashReport()" in main and main.count("crashReports.showCrashLogOptions()") == 2, "startup and settings diagnostics entry points delegate to the crash-report controller")

    app_lock_screen = source("app/src/main/java/com/music/echo/offline/files/AppLockScreenController.java")
    require("appLockScreen = new AppLockScreenController(this);" in main and "appLockScreen.showSetPinDialog();" in main and "appLockScreen.showLockScreen();" in main, "MainActivity delegates App Lock UI to its dedicated screen controller")
    require('@android.annotation.TargetApi(android.os.Build.VERSION_CODES.P)' in app_lock_screen and 'if (Build.VERSION.SDK_INT >= 28) tryBiometric(dialog);' in app_lock_screen, "framework biometric prompt is guarded for API 28+")
    biometric_keystore = source("app/src/main/java/com/music/echo/offline/files/BiometricUnlockKeyStore.java")
    require('BiometricUnlockKeyStore.createCipher()' in app_lock_screen and 'new android.hardware.biometrics.BiometricPrompt.CryptoObject(cipher)' in app_lock_screen and 'result.getCryptoObject()' in app_lock_screen and 'doFinal(challenge)' in app_lock_screen, "biometric app unlock verifies a per-use Android Keystore cryptographic proof")
    require('AndroidKeyStore' in biometric_keystore and 'setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)' in biometric_keystore and 'setUserAuthenticationValidityDurationSeconds(-1)' in biometric_keystore and 'KeyPermanentlyInvalidatedException' in biometric_keystore and 'setInvalidatedByBiometricEnrollment(true)' in biometric_keystore, "biometric Keystore key uses per-operation authentication and enrollment invalidation recovery")
    saf_operations = source("foundation/src/main/java/com/music/echo/offline/files/SafStorageOperations.java")
    rename_engine = source("core/src/main/java/com/music/echo/offline/files/BatchRenameEngine.java")
    file_ops = source("app/src/main/java/com/music/echo/offline/files/FileOps.java")
    browser = source("app/src/main/java/com/music/echo/offline/files/BrowserView.java")
    require('static File resolveChild(File parent, String name)' in rename_engine and 'candidate.startsWith(parentPath)' in rename_engine and 'candidate.toRealPath()' in rename_engine and 'LinkOption.NOFOLLOW_LINKS' in rename_engine, "User-derived child paths are normalized, parent-contained and symlink-checked")
    require('for (char c : name.toCharArray())' in rename_engine, "batch rename validates each bounded filename character without an indexed loop")
    require('BatchRenameEngine.resolveChild(cur, n)' in browser and 'BatchRenameEngine.resolveChild(e.f.getParentFile(), n)' in browser, "Folder creation and rename use canonical path containment")
    file_engine = source("core/src/main/java/com/music/echo/offline/files/FileOperationEngine.java")
    require('FileOperationEngine.unique(dir, name)' in file_ops and 'FileOperationEngine.copy(source, target)' in file_ops and 'FileOperationEngine.deleteTree(file)' in file_ops and 'BatchRenameEngine.resolveChild(directory, name)' in file_engine and 'Files.newDirectoryStream(source)' in file_engine, "FileOps delegates local copy, collision handling and cleanup to the shared streaming core engine")
    require('deleteTreeOrThrow(file.toPath(), true)' in file_engine and 'deleteTreeOrThrow(staging.toPath(), false)' in file_engine and 'if (cancellable) OperationCancellation.checkCurrent();' in file_engine and 'else if (OperationCancellation.isCancelled()) { cancelled = true; break; }' in file_ops, "recursive deletion responds to cancellation while rollback cleanup remains uninterrupted")
    require('cancelledRecursiveDeleteStopsBeforeRemovingTheTree' in source("core/src/test/java/com/music/echo/offline/files/FileOperationEngineTest.java"), "recursive file deletion has cancellation regression coverage")
    external_open = file_ops.split('static void external(Activity a, File f, boolean chooser)', 1)[1].split('static void install(Activity activity, File file, java.util.concurrent.Executor executor)', 1)[0]
    require('e.kind == FileKinds.APK' in external_open and 'Use the Install action for APK files' in external_open, "generic ACTION_VIEW handoff refuses APKs and keeps installation on the explicit PackageInstaller path")
    require(file_ops.count('codeql[java/android/arbitrary-apk-installation]') == 1, "the guarded generic APK intent has a documented CodeQL false-positive suppression")
    hash_core = source("core/src/main/java/com/music/echo/offline/files/ToxiToolCore.java")
    hash_ui = source("app/src/main/java/com/music/echo/offline/files/ToxiToolsActivity.java")
    require('MD5' not in hash_core and 'SHA-1' not in hash_core and 'MessageDigest.getInstance("SHA-256")' in hash_core and 'MessageDigest.getInstance("SHA-512")' in hash_core and 'CRC32' in hash_core and 'SHA-256' in hash_ui and 'SHA-512' in hash_ui and 'CRC32' in hash_ui, "file integrity studio uses SHA-256 and SHA-512 with CRC32 clearly separated from cryptographic hashes")
    sql_editor = source("app/src/main/java/com/music/echo/offline/files/DeveloperStudioActivity.java")
    uri_guard = source("foundation/src/main/java/com/music/echo/offline/files/ToxiUriPermissions.java")
    require('codeql[java/sql-injection]' in sql_editor and 'lgtm[java/sql-injection]' in sql_editor and 'DeveloperStudioCore.isReadOnlySql(sql)' in sql_editor and 'SQLiteDatabase.OPEN_READONLY' in sql_editor, "developer SQL editor is explicitly bounded to one read-only query on a read-only database")
    require('codeql[java/android/unsafe-content-uri-resolution]' in uri_guard and uri_guard.count('lgtm[java/android/unsafe-content-uri-resolution]') == 2 and 'DocumentsContract.isDocumentUri(context, uri)' in uri_guard and 'checkUriPermission(uri, Process.myPid(), Process.myUid(), requiredPermission)' in uri_guard, "SAF URI resolution requires document URI validation and the exact read/write grant before resolver access")
    root_gradle = source("build.gradle.kts")
    app_gradle = source("app/build.gradle.kts")
    foundation_gradle = source("foundation/build.gradle.kts")
    patched_build_substitutions = (
        'substitute(module("org.apache.commons:commons-lang3:3.16.0")).using(module("org.apache.commons:commons-lang3:3.18.0"))',
        'substitute(module("org.apache.httpcomponents:httpclient:4.5.6")).using(module("org.apache.httpcomponents:httpclient:4.5.14"))',
        'substitute(module("org.apache.httpcomponents:httpmime:4.5.6")).using(module("org.apache.httpcomponents:httpmime:4.5.14"))',
        'substitute(module("com.google.guava:guava:33.4.0-jre")).using(module("com.google.guava:guava:33.7.2-jre"))',
        'substitute(module("com.google.guava:failureaccess:1.0.2")).using(module("com.google.guava:failureaccess:1.0.3"))',
        'substitute(module("org.bouncycastle:bcprov-jdk18on:1.80.2")).using(module("org.bouncycastle:bcprov-jdk18on:1.85"))',
        'substitute(module("org.bouncycastle:bcpkix-jdk18on:1.80.2")).using(module("org.bouncycastle:bcpkix-jdk18on:1.85"))',
        'substitute(module("org.bouncycastle:bcutil-jdk18on:1.80.2")).using(module("org.bouncycastle:bcutil-jdk18on:1.85"))',
    )
    require('subprojects {' in root_gradle and 'if (path == ":app" || path == ":foundation")' in root_gradle and 'configurations.getByName("classpath").resolutionStrategy.dependencySubstitution' in root_gradle and 'classpath("com.android.tools.build:gradle:9.5.0")' in root_gradle and all(rule in root_gradle for rule in patched_build_substitutions), "Android app and foundation plugin classpaths substitute vulnerable transitive coordinates with patched binary-compatible versions")
    require("enableKotlin = false" in app_gradle and "enableKotlin = false" in foundation_gradle, "Java-only Android modules disable unused built-in Kotlin compiler tasks")
    apk_install = file_ops.split('static void install(Activity activity, File file, java.util.concurrent.Executor executor)', 1)[1].split('static void share(Activity a, List<File> fs)', 1)[0]
    apk_receiver = source("app/src/main/java/com/music/echo/offline/files/ApkInstallStatusReceiver.java")
    manifest = source("app/src/main/AndroidManifest.xml")
    file_open = source("app/src/main/java/com/music/echo/offline/files/FileOpenCoordinator.java")
    require('PackageInstaller.SessionParams.MODE_FULL_INSTALL' in apk_install and 'session.commit(callback.getIntentSender())' in apk_install and 'Files.isSymbolicLink(file.toPath())' in apk_install and 'PendingIntent.FLAG_MUTABLE' in apk_install and 'FileOps.install(activity, file, activity.io)' in file_open and 'canRequestPackageInstalls()' in apk_install, "APK install streams a validated file through PackageInstaller on the lifecycle IO executor")
    require('FileKinds.isHtmlExtension(extension)' in file_open and 'kind == FileKinds.PDF' in file_open and 'ps.size() > 400' in file_open and 'FileOps.external(activity, file, true)' in file_open and 'fileOpenCoordinator.open(e, ctx)' in main and 'fileOpenCoordinator.showZip(file)' in main, "file opening preserves mandatory document viewers, bounded media context, archives, APKs and external chooser routing")
    require('STATUS_PENDING_USER_ACTION' in apk_receiver and 'Intent.EXTRA_INTENT' in apk_receiver and 'FLAG_ACTIVITY_NEW_TASK' in apk_receiver and '.files.ApkInstallStatusReceiver' in manifest and 'android:exported="false"' in manifest, "PackageInstaller status receiver launches only Android's explicit user-confirmation UI")
    rename_tests = source("core/src/test/java/com/music/echo/offline/files/BatchRenameEngineTest.java")
    require('resolveChildRejectsTraversalAndSymlinkEscapes' in rename_tests, "canonical path resolution has traversal and symlink-escape regression tests")
    require('catch (RuntimeException providerFailure)' in saf_operations and 'Storage provider failed during partial-output cleanup' in saf_operations, "SAF rollback converts unchecked provider failures into cleanup IOExceptions without masking the original operation failure")
    require('static DocumentFile findDocumentInTree(Context context, Uri tree, Uri target)' in saf_operations and 'DocumentFile.fromTreeUri(context, tree)' in saf_operations and 'current.listFiles()' in saf_operations, "SAF recursive operations resolve tree-aware DocumentFile wrappers")
    find_document = saf_operations.split('static DocumentFile findDocumentInTree(Context context, Uri tree, Uri target)', 1)[1].split('static Uri moveDocument(', 1)[0]
    require('int scannedEntries = 0;' in find_document and '++scannedEntries > TreeTraversalBudget.DEFAULT_MAX_ENTRIES' in find_document and 'OperationCancellation.checkCurrent();' in find_document.split('for (DocumentFile child : children)', 1)[1].split('throw new IOException("Document is no longer available', 1)[0], "SAF document resolution bounds total provider entries and checks cancellation within large child lists")
    require('static DocumentFile moveDocumentWithFallback(Context context' in saf_operations and 'if (context == null || resolver == null || source == null' in saf_operations, "SAF move validates the Context required to wrap provider-returned document URIs")
    require('static Uri moveDocument(ContentResolver resolver' in saf_operations and 'if (context == null || resolver == null || source == null' not in saf_operations.split('static Uri moveDocument(ContentResolver resolver', 1)[1].split('static DocumentFile moveDocumentWithFallback', 1)[0], "SAF native move does not reference an out-of-scope Context")
    require('"Duplicate here"' in saf_browser and 'SafStorageOperations.duplicateDocumentInTree(' in saf_browser and 'static void duplicateDocumentInTree(Context context' in saf_operations and 'copyDocumentTree(resolver, source, destination)' in saf_operations, "Other Storage delegates validated recursive directory duplication to the shared storage service")
    require('"Move to folder"' in saf_browser and 'showSafMoveFolderPicker(tree, parent, document, parent, title, name);' in saf_browser and 'SafStorageOperations.moveDocumentInTree(' in saf_browser and 'static DocumentFile moveDocumentInTree(Context context' in saf_operations, "Other Storage delegates SAF move resolution, validation and execution to the shared storage service")
    require('ToxiUriPermissions.takeGranted(activity.getContentResolver(), tree, grantedFlags)' in saf_browser and 'ToxiUriPermissions.persistedFlags(activity.getContentResolver(), restoredTree)' in saf_browser and 'preferences.edit().remove("saf_tree").remove("saf_flags").apply()' in saf_browser and '@android.annotation.SuppressLint("WrongConstant")' not in main, "MainActivity restores only SAF trees with a confirmed persisted read grant and does not retain transient URIs")
    require("safBrowserController = new SafBrowserController(this);" in main and "safBrowserController.restorePersistedTree();" in main and "safBrowserController.onTreeResult(data);" in main and "safBrowserController.open();" in main and "safBrowserController.copyToSafTree(files);" in main and "boolean hasSafTree()" in main and "boolean hasTree()" in saf_browser_dispatch and "activity.cancellation.begin()" in saf_browser_dispatch and "activity.cancellation.finish(operationToken)" in saf_browser_dispatch and "a.hasSafTree()" in browser and "a.safTree" not in browser, "SAF picker and persisted-grant state remain encapsulated, BrowserView queries destination availability through MainActivity, and SAF mutations use activity-scoped cancellation")
    require("new android.graphics.drawable.BitmapDrawable(activity.getResources(), bitmap)" in about_builder and "BitmapDrawable(getResources(), bitmap)" not in about_builder, "Extracted About screen resolves resources from its owning Activity")
    back_navigation = source("app/src/main/java/com/music/echo/offline/files/ToxiBackNavigation.java")
    back_activities = [main, source("app/src/main/java/com/music/echo/offline/files/DeveloperStudioActivity.java"), source("app/src/main/java/com/music/echo/offline/files/HtmlViewerActivity.java"), source("app/src/main/java/com/music/echo/offline/files/ToxiToolsActivity.java"), source("app/src/main/java/com/music/echo/offline/files/ViewerActivity.java")]
    indexer = source("app/src/main/java/com/music/echo/offline/files/Indexer.java")
    require("WeakReference<Runnable>" in indexer and "completionCallbacks" in indexer and "registerCompletionCallback(done)" in indexer and "postCompletionCallbacks()" in indexer and "Indexer.I.start(indexDoneCallback)" in main and "if (isFinishing() || isDestroyed()) return;" in main, "global indexing delivers completion only to live, weakly held Activity callbacks")
    require("void runUiIfAlive(Runnable action)" in main and "if (action == null || isFinishing() || isDestroyed()) return;" in main and "if (!isFinishing() && !isDestroyed()) action.run();" in main and "a.runOnUiThread(" not in file_ops and "a.runUiIfAlive(" in file_ops, "file-operation progress and completion callbacks are discarded after Activity destruction")
    require('taskRuntime.shutdown();\n        super.onDestroy();' in main, "MainActivity delegates complete worker and cancellation shutdown before superclass destruction")
    require("getOnBackPressedDispatcher().addCallback(activity" in back_navigation and "new OnBackPressedCallback(true)" in back_navigation and all("extends ComponentActivity" in activity and "ToxiBackNavigation.bind(this," in activity and "@Override public void onBackPressed" not in activity for activity in back_activities), "All activities use lifecycle-aware AndroidX predictive Back callbacks")

    base_theme = source("app/src/main/res/values/themes.xml")
    api27_theme = source("app/src/main/res/values-v27/themes.xml")
    require("windowLightNavigationBar" not in base_theme and api27_theme.count("windowLightNavigationBar") == 2, "API 27 navigation-bar theme attributes are isolated to version-qualified resources")

    gradle_properties = source("gradle.properties")
    require("org.gradle.jvmargs=-Xmx2g" in gradle_properties and "MaxMetaspaceSize=512m" in gradle_properties and "org.gradle.workers.max=1" in gradle_properties, "Gradle CI memory is sufficient for API 36 lint analysis with bounded worker concurrency")
    app_gradle = source("app/build.gradle.kts")
    root_gradle = source("build.gradle.kts")
    wrapper = source("gradle/wrapper/gradle-wrapper.properties")
    wrapper_url = re.search(r"^distributionUrl=(.+)$", wrapper, re.MULTILINE)
    wrapper_version = re.search(r"gradle-(\d+)\.(\d+)\.(\d+)-bin\.zip", wrapper_url.group(1)) if wrapper_url else None
    wrapper_checksum = re.search(r"^distributionSha256Sum=([0-9a-fA-F]{64})$", wrapper, re.MULTILINE)
    require('compileSdk = 36' in app_gradle and 'targetSdk = 36' in app_gradle and 'classpath("com.android.tools.build:gradle:9.5.0")' in root_gradle and wrapper_url is not None and wrapper_url.group(1).startswith(r"https\://services.gradle.org/distributions/") and wrapper_version is not None and tuple(map(int, wrapper_version.groups())) >= (9, 6, 0) and wrapper_checksum is not None, "API 36 release uses compatible Android Gradle and a pinned, checksummed compatible wrapper distribution")
    workflow = source(".github/workflows/release.yml")
    require('python tools/project_smoke_test.py' in workflow and 'python3 tools/developer_studio_smoke_test.py' in workflow and ':core:test :app:testDebugUnitTest :app:lintDebug' in workflow and 'assembleRelease' in workflow, "release workflow runs source gates, unit tests, lint and release assembly")
    viewer = source("app/src/main/java/com/music/echo/offline/files/ViewerActivity.java")
    require("com.google.mlkit:text-recognition:16.0.1" in app_gradle and "com.google.mlkit.vision.text.TextRecognition" in viewer and "TextRecognizerOptions.DEFAULT_OPTIONS" in viewer and "InputImage.fromBitmap" in viewer and "Extract English text" in viewer and "addOnCompleteListener" in viewer and "recognizer.close()" in viewer and "ocr" not in manifest.lower(), "bundled Latin-script English OCR is integrated into the image editor with asynchronous processing and deterministic recognizer cleanup")
    require("void openImageEditor()" in viewer and all(label in viewer for label in ["Rotate 90°", "Flip", "Crop", "B&W", "Save copy"]), "image editing, transforms and save-copy workflow remain available after OCR removal")
    require("splits {" not in app_gradle and "isMinifyEnabled = true" in app_gradle and "isShrinkResources = true" in app_gradle, "single universal APK uses R8 and resource shrinking without redundant ABI split packages")
    require(not (ROOT / "patches").exists() and not list(ROOT.glob(".toxifiles-*")), "temporary patch archives and applied-state marker files are removed from the source repository")
    print("\\nAll current-architecture source smoke checks passed. Android compilation and device testing are verified separately.")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
