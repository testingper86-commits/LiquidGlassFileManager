# LiquidFiles visual refresh

This pass upgrades the existing native Android UI without replacing app flows or storage behavior.

- Strengthened the layered glass drawable with a deeper smoked-glass body, accent bloom, top specular sheen, inner rim and crisp outer stroke.
- Increased elevation for primary surfaces, category tiles, action buttons and the floating navigation dock.
- Added original hand-drawn icon glyphs for appearance, sparkle, haptics, visibility, security, lock, Wi-Fi transfer, storage, analytics, download/import, cloud, music and refresh actions.
- Replaced repeated generic settings icons with semantic icons so settings rows are easier to scan.
- Added a glass sheen to file-type tiles while preserving their existing category colors and file-type identity.

The visual changes are source-level only. The Gradle wrapper could not download Gradle in this environment because `services.gradle.org` was unreachable, so compilation and device rendering remain unverified.
