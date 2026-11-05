# ToxiFiles Android build and testing policy

This policy applies to every current and future ToxiFiles build.

## Required build checks
- Validate the Android project and Gradle wrapper.
- Compile the complete Android application and produce the debug APK on every build.
- Upload the APK artifact after a successful build.

## Change-scoped tests
- Run the focused OfficeDocumentEngineTest and OfficeDocumentStorageTest only when the Office document activity, engine, storage, tests, or relevant build configuration changes.
- Validate retained Office assets only when the Office asset directory changes.
- Preserve save/reopen, original-file preservation, bounded import, and atomic-write checks for the four retained workspaces: Word, Excel, PowerPoint, and PDF.

## Permanently excluded from routine builds
- Full emulator UI journeys, repeated emulator suites, and broad compatibility matrices.
- Duplicate, unrelated, or broad regression tests.
- Tests unrelated to the retained Office document workflows.
- Standalone checks already guaranteed by the Android Gradle build.

## Safety requirements
- Never remove compilation, packaging, persistence/save-reopen, original-file preservation, security, or data-integrity checks directly relevant to changed functionality.
- Never skip a failing relevant test merely to make CI green.
- Add focused tests for changed Office behavior and wire them to affected source/test paths.
- Expand test scope only when a concrete change requires it or the user explicitly requests it.
