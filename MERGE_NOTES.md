# Liquid Files — Merge and upgrade notes

- The Java-native file manager is the only app experience and launcher.
- The separate Echo Compose player, player service, Kotlin source tree, and related Kotlin/Compose/Media3/Room/KSP build dependencies were removed in Upgrade Pass 2.
- Audio and video should be opened through the file manager's built-in ViewerActivity.
- Existing file operations, favorites, recycle bin, vault, duplicate finder, local Wi-Fi transfer, archive handling, and bundled icon license/attribution files are retained in source.
- The build workflow runs structural smoke tests and attempts a debug APK compile.
- Android device testing is still required; source presence alone does not prove each feature works.
