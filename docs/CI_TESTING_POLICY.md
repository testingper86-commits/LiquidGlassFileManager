# ToxiFiles CI testing policy

This policy applies to every current and future ToxiFiles build.

## Default build checks
- Always compile the complete Android application and produce the debug APK.
- Keep project/wrapper integrity checks required to run Gradle.
- Do not run broad or unrelated unit-test suites by default.

## Change-scoped tests
- Run only the focused tests for the module affected by the change.
- ToxiFiles 360 unit tests are limited to `ToxiFiles360EngineTest` and `ToxiFiles360StorageTest`, and run only when the corresponding Office source, tests, or build configuration changes.
- Verify Office/OCR offline assets only when files under `app/src/main/assets/toxifiles360/` change.
- Run at most one bounded Android instrumentation smoke test, and only when ToxiFiles 360 runtime code, its instrumentation test, its offline assets, or relevant Android build configuration changes.

## Permanently excluded from routine builds
- Full emulator UI journeys and repeated emulator suites.
- Duplicate tests, broad compatibility matrices, unrelated regression suites, and repeated checks that validate the same behavior.
- Standalone checks already guaranteed by the Android Gradle build (for example, a separate pre-build existence check for a generated shaded JAR).

## Safety requirements
- Never remove compilation, packaging, persistence/save-reopen, original-file preservation, security, or data-integrity checks that are directly relevant to the changed functionality.
- Never skip a failing relevant test merely to make CI green.
- Add or update focused tests when new functionality is introduced, and wire their execution to the affected source/test paths instead of expanding every build into a full test run.
- Expand test scope only when the code change materially requires it or the user explicitly requests it.
