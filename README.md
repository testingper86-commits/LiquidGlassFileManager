# ToxiFiles — Offline File Manager

ToxiFiles is a native Android file manager with an integrated local media viewer/player. The Android project is committed directly at the repository root; it is not built from a nested project ZIP.

## Product goals

- **File manager first:** browse and manage files, with the existing built-in viewer and audio/video player.
- **Local-first:** normal browsing and playback do not require a remote service or account.
- **Liquid Glass UI:** translucent surfaces, real Android 12+ backdrop blur for overlays where supported, accessibility-aware contrast, reduced-motion support, and VantaGlass as the default theme.
- **Safer operations:** explicit copy/move conflict choices, cancellation for supported long-running operations, atomic temporary-file handling, archive limits, and regression checks.
- **Privacy features:** encrypted Vault data, App Lock, screenshot protection for protected screens, and non-destructive GPS-metadata removal for supported images.

## Build locally

Use Android Studio, JDK 17, and Android SDK 36.

```bash
python3 tools/project_smoke_test.py
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## GitHub Actions

- **ToxiFiles Android Build** runs structural checks, JVM unit tests, Android lint, the debug build, and uploads the debug APK and dependency inventory.
- **ToxiFiles Release Build** runs source validation, unit tests, and Android lint before assembling a release APK. Release signing is mandatory. Configure all four secrets before running a release: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The workflow verifies the APK signature before uploading the signed artifact. It intentionally fails rather than publishing an unsigned release.

## Security and privacy notes

- LAN transfer requires a one-time pairing code, authenticates the peer, encrypts file payloads with AES-GCM, and checks file hashes. Keep the code private and use a trusted local network.
- Vault contents are AES-GCM encrypted with a device-bound Android Keystore key. The Vault PIN gates the in-app UI; it is not itself the encryption key. Clearing app data, uninstalling, or losing the device Keystore key may make vaulted data unrecoverable. Restore important items before uninstalling or resetting the device.
- App Lock and Vault PIN verifiers use salted PBKDF2 with persistent escalating lockout. App Lock is an in-app privacy barrier, not a replacement for the Android device lock.
- Android backup is disabled. File sharing uses non-exported FileProvider URI grants.
- Full file management uses Android's sensitive all-files-access permission. Review current Google Play policy and explain the core file-management purpose if distributing through Play. APK installation also depends on Android's user-controlled package-install permission.
- See [SECURITY.md](SECURITY.md) for the security model and known boundaries.

## Validation status

The latest successful main-branch **ToxiFiles Android Build** run is [run 42480113291](https://github.com/testingper86-commits/LiquidGlassFileManager/actions/runs/42480113291) for commit `23d70697639c1e3a624d155cf4a62a1ffc951aab`. Structural checks, project/security contracts, media lifecycle checks, dependency inventory, Android unit tests, Android lint, debug APK assembly, and both artifact uploads all completed successfully. The run published the `ToxiFiles-debug-APK` artifact.

CI success does not replace real-device acceptance testing. Before release, verify LAN send/receive between two devices, SD/USB read and write behavior, Vault recovery, App Lock lifecycle, media playback, large archives, low-storage failures, landscape/font scaling, and destructive operations. A release build is not distribution-ready until signing secrets are configured, the signed APK passes signature verification, package policy is reviewed, and target-device testing is complete.
