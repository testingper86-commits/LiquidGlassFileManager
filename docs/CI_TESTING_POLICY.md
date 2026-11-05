# ToxiFiles Android build and testing policy

This policy applies to every current and future ToxiFiles build.

## Required build checks
- Validate the Android project and Gradle wrapper.
- Compile the complete Android application and produce the debug APK on every build.
- Upload the APK artifact after a successful build.

## Change-scoped tests
- Add and run focused tests only for logic, persistence, security, or data-integrity behavior directly changed by a commit.
- When the built-in PDF viewer changes, validate PDF loading, page rendering, navigation, and failure handling with targeted checks where the Android test environment supports them.
- Do not retain tests, fixtures, assets, or dependencies for removed features.

## Permanently excluded from routine builds
- Full emulator UI journeys, repeated emulator suites, and broad compatibility matrices.
- Duplicate, unrelated, or broad regression tests.
- Standalone checks already guaranteed by the Android Gradle build.

## Safety requirements
- Never remove compilation, packaging, persistence/save-reopen, original-file preservation, security, or data-integrity checks directly relevant to changed functionality.
- Never skip a failing relevant test merely to make CI green.
- Expand test scope only when a concrete change requires it or the user explicitly requests it.
