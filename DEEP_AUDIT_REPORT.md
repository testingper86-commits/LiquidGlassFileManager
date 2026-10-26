# Liquid Files — fresh repository audit record

## Actual changes in this package
- Changed Android application ID to `com.liquidfiles.offline` and Gradle root project name to `LiquidFilesOffline`.
- Changed launcher app label to `Liquid Files` and the integrated player permission-gate heading to `Liquid Player`.
- Renamed exported backup filename to `liquid-files-backup.json`.
- Corrected privacy messaging that previously claimed there was no INTERNET permission even though the manifest declares it. The app now describes INTERNET as supporting optional local Wi-Fi transfer; no claim is made that the APK has no network permission.
- Added the Gradle wrapper JAR and launcher scripts to the project root package.
- Replaced the nested-ZIP GitHub workflow with a source-root workflow that runs Python structural smoke checks on pushes and pull requests, compiles `assembleDebug`, and uploads the APK only if compilation succeeds.
- Added `tools/project_smoke_test.py` and a PR checklist.

## Checks performed in this environment
- ZIP source archive extracted and reassembled.
- Python structural smoke test: passed 23 assertions.
- Manifest parsed as XML; launcher activity check passed.
- Project archive integrity: checked when packaging.
- Gradle build attempted but **could not start** because this environment could not resolve `services.gradle.org` (`UnknownHostException`). This is an environment network limitation; it is not a successful compile and does not prove the app compiles.

## Not verified
- Android Kotlin/Java compilation, resource linking, APK generation, installation or runtime.
- File copy/move/delete/rename, permission prompts, removable SD/USB storage, LAN transfer, vault, archive handling, audio playback, or video playback on a device.
- Google Play policy compliance for `MANAGE_EXTERNAL_STORAGE`.

## Important scope note
This is a fresh repository package built from the available existing project source, with real identity/privacy/CI/test changes. It is not a ground-up rewrite of every file-manager feature. The static smoke test is a guardrail, not a substitute for device testing.
