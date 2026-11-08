# ToxiFiles architecture boundaries

## Gradle modules

- `:app` is the Android application boundary. It owns activities, Android framework integration, storage providers, UI, and application lifecycle. `StoragePermissionController` isolates platform storage-permission checks and settings prompts; `CrashReportController` owns crash-report presentation and sharing; `ActivityTaskRuntime` owns activity-scoped worker queues and ordered cancellation shutdown; `AppLockLifecycleController` routes screen-off, resume, and app-background events to App Lock and vault policies; `MainNavigationBar` owns bottom-tab presentation, insets, selection and theme updates; `OverlayAccessibilityController` preserves and restores accessibility state behind full-screen overlays; `MainOverlayController` owns overlay transitions, secure-vault window flags, blur, and list cleanup; `FavoritesRepository` owns the single favorites path set and its local persistence; `FileOpenCoordinator` owns file-kind routing to built-in viewers, archives, APK installation, and Android choosers.
- `:foundation` is an Android library for reusable platform services: validated SAF URI grants, provider-aware document operations, local crash diagnostics, and Android worker-priority policy. It depends on `:core` and never on app features.
- `:core` is a Java 17 library for platform-neutral primitives shared by the application. Its extracted components are operation-scoped cancellation, bounded background scheduling and storage indexing, strict ZIP-entry and filesystem child-path validation, read-only Developer Studio parsing/analysis, PIN-verifier primitives, and canonical filesystem path resolution. These utilities remain Android-free and have JVM regression tests in the core module. The bounded offline tool engine also lives here: strict text decoding, bounded reads, hashes, binary/hex conversion, folder comparison, safe ZIP rewriting, and bounded streaming storage traversal with cancellation and symlink protection.

The core module intentionally has no Android SDK dependency. Feature screens and app-specific orchestration remain in `:app`; reusable Android services live in `:foundation`; platform-neutral algorithms live in `:core`. Dependencies flow from `:app` to `:foundation` and `:core`, and from `:foundation` to `:core`, never in reverse. `TaskCoordinator` is the Android priority-policy adapter; the queue limits, lane ownership, and cancellation scopes live in the Android-free `TaskScheduler`.

## Migration rule

Move a component into `:core` only after verifying that it has no Android framework, AndroidX, application-context, or resource dependency. Preserve its package name during extraction where this avoids unnecessary call-site churn. Keep pure-Java tests in `:core` and Android integration tests in `:app`; CI executes both test tasks and Android lint after each extraction.

## Current boundary limitation

`MainActivity` remains a large legacy composition root. It is not split automatically because its UI, lifecycle, navigation, and storage state are tightly coupled. Further extraction must proceed by cohesive services with explicit ownership and tests, not by moving arbitrary methods into empty packages.

## Six shared service domains

1. **File Operations Engine** — `FileOperationEngine` owns staged local copy, atomic collision-free names, safe tree deletion and sizing; `BatchRenameEngine` owns batch rename validation; `ArchiveOperations` owns streaming ZIP create/list/extract; `SafStorageOperations` owns provider-aware SAF copy/move/delete.
2. **Storage & Indexing** — `StorageIndexEngine` performs bounded, streaming, no-follow traversal; `Indexer` is the Android adapter that classifies and publishes snapshots.
3. **Performance & Resource Management** — `TaskScheduler` owns bounded lanes and scoped work; `TaskCoordinator` applies Android thread-priority policy; `OperationCancellation` propagates cancellation into worker operations.
4. **Security & Permissions** — `SafePathResolver`, `ArchivePath`, and `PinHash` are platform-neutral guards; `ToxiUriPermissions` owns validated persistable SAF grants; biometric and app-lock UI remain app-owned.
5. **Reliability & Recovery** — `CrashLogger` records local diagnostics; `OperationCancellation` prevents stale work; recycle-bin and install-confirmation lifecycle adapters remain app-owned.
6. **Shared UI & Presentation** — `ToxiVisualSystem`, icon, theme, and reusable row components remain in `:app` because they depend on Android resources and application presentation state.

## Feature layer ownership

The existing File Manager, Developer Studio, Archive Manager, File Transfers, Private Vault, Document Tools, and Settings surfaces remain app-level features. They consume the shared services above; they are not duplicated into empty Gradle modules. The current extraction deliberately establishes stable service boundaries first. `MainActivity` remains the legacy composition root noted below, and independent feature-module extraction is not claimed as complete.
