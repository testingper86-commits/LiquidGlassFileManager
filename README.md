# ToxiFiles — Offline File Manager

ToxiFiles is a native Android file manager with an integrated local media viewer/player.

## Product and runtime

- File management, local media playback, document viewing, archives, LAN transfer, Vault, App Lock, and the existing utility tools.
- Local-first operation; normal browsing and playback do not require an account or remote service.
- Liquid Glass interface with accessibility-aware contrast and reduced-motion support.
- English/Latin OCR with numeric recognition. The OCR engine is supplied by Google Play Services and its model is downloaded on demand; it is not bundled into each APK.
- Release builds are split by CPU architecture. Install only the APK matching the target device ABI.

## Build locally

Use Android Studio, JDK 17, and Android SDK 36.

```bash
python3 tools/project_smoke_test.py
./gradlew --no-daemon :core:test :app:testDebugUnitTest :app:lintDebug
./gradlew --no-daemon assembleRelease
```

Release APKs are written to `app/build/outputs/apk/release/`. Each device-specific APK must be no larger than **5 MiB**. The CI workflows enforce this limit and fail if any split exceeds it. The limit is per ABI-specific APK, not a universal APK containing every native architecture.

## GitHub Actions

- **ToxiFiles Android Build** runs source/security checks, JVM tests, Android lint, assembles ABI-specific release APKs, enforces the 5 MiB per-APK budget, and uploads the APKs plus dependency inventory.
- **ToxiFiles Release Build** runs source validation, tests, lint, release assembly, the same 5 MiB budget check, and signature verification. Release signing is mandatory. Configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` before creating a release.

## Security and privacy notes

- LAN transfer uses one-time pairing, authenticated peers, AES-GCM payload encryption, and file hashes. Use a trusted local network.
- Vault contents are AES-GCM encrypted with a device-bound Android Keystore key. The Vault PIN gates the in-app UI; it is not the encryption key. Clearing app data, uninstalling, or losing the device Keystore key may make vaulted data unrecoverable.
- App Lock is an in-app privacy barrier, not a replacement for the Android device lock.
- Android backup is disabled. File sharing uses non-exported FileProvider URI grants.
- Full file management uses Android's sensitive all-files-access permission. Review current Google Play policy before distribution.
- See [SECURITY.md](SECURITY.md) for the security model and known boundaries.

## Acceptance testing

CI validates source contracts, unit tests, lint, release assembly, and artifact size. Before distribution, verify LAN transfer, SD/USB storage access, Vault recovery, App Lock lifecycle, media playback, large archives, low-storage failures, landscape/font scaling, and destructive operations on supported devices.
