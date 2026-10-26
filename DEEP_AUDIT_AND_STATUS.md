# Deep audit and current status

## Current architecture
- Launcher: `com.music.echo.offline.files.MainActivity` (native Java file manager).
- Built-in media viewing/playback: `com.music.echo.offline.files.ViewerActivity`.
- The separate Echo Compose player and MusicService have been removed, along with the Kotlin source tree and unused Kotlin/Compose/Room/Media3/KSP build dependencies.
- Package ID: `com.liquidfiles.offline`; namespace remains `com.music.echo.offline` to avoid an unsafe mass rename of existing Java sources.

## Structural checks
Run `python3 tools/project_smoke_test.py`. It checks project files, manifest and launcher, removed player path, settings section organization, Gradle configuration and workflow shape.

## Not yet verified on a device
- Android Gradle compilation of this exact upgrade pass.
- File copy/move/rename/delete and recycle-bin restore under current Android storage restrictions.
- SD card/USB OTG access on different device vendors.
- Audio/video/PDF/text viewing and playback.
- App lock, encrypted vault, archive extraction, duplicate detection, and Wi-Fi transfer.
- Accessibility, small-screen layout, dark/light theme and performance.

## Platform caveats
`MANAGE_EXTERNAL_STORAGE` is special access on Android 11+ and has distribution-policy implications. Removable storage support depends on Android's Storage Access Framework and device-specific behavior. Layered glass drawables are not true backdrop blur on all Android versions.
