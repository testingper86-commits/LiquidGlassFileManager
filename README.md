# Liquid Files — Offline File Manager

A file-manager-first Android app with a built-in **Liquid Player** for local music playback. This repository contains the Android source directly at the root (not a nested project ZIP), plus automated source checks and a GitHub Actions build workflow.

## Core design goals
- File manager is the launch experience; the player is integrated rather than a separate app.
- Local file browsing and playback are designed to work on-device. Optional LAN transfer may use the Android INTERNET permission; this is disclosed in the UI and manifest.
- Keep the existing Java file operations, built-in viewer, settings, and bundled icon attributions. The separate Echo-derived player project and its unused build dependencies have been removed.
- Liquid Glass surfaces are layered translucent/tinted surfaces; do not assume true backdrop blur on every Android version.

## Build with GitHub Actions
1. Create a **new empty repository**.
2. Extract this ZIP and upload its **contents** to the repository root. Do not upload this project as a ZIP nested inside GitHub. Include hidden `.github` files.
3. Commit to `main` (or `master`) or open a pull request.
4. Open **Actions → Android Build & Checks**. The workflow runs repository smoke tests and then `./gradlew assembleDebug`.
5. Download the `LiquidFiles-debug-apk` artifact only after the run succeeds.

## Local build
Use Android Studio with JDK 17 and Android SDK 35. From the project root run `./gradlew assembleDebug`. On Windows, run `gradlew.bat assembleDebug`.

## Automated checks
`python3 tools/project_smoke_test.py` checks project layout, Gradle wrapper files, manifest declarations, the file-manager-first launcher, the native file manager and removed separate player path, and consistency of privacy messaging with the INTERNET permission. GitHub Actions also compiles the Android app. These checks do not replace device tests.

## Important limits
- No successful Android build or device test is claimed until GitHub Actions completes successfully and the APK is installed and exercised on a device.
- `MANAGE_EXTERNAL_STORAGE` is a sensitive Android permission. Review current Play policy and explain the user-facing purpose if distributing through Google Play.
- LAN transfer, removable storage, archive handling, vault security, media playback, and all destructive file operations still need device-level verification.


## Current UI development pass

The active redesign focuses on a consistent layered-glass visual language, clearer browser spacing, better file-row hierarchy, grouped settings, updated launcher branding, and richer controls in the existing built-in audio player (including Repeat and Shuffle). The separate Echo player is not part of the intended app architecture.

Per the project owner's instruction, APK builds and device/runtime tests are deferred until all planned UI and feature-integration changes are consolidated into one final ZIP. Do not treat this working source as release-verified.
