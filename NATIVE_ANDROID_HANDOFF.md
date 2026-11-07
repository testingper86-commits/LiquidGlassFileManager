# ToxiFiles Native UI Handoff

## Scope
The production application remains native Android Java/XML/programmatic Android Views. This change keeps existing navigation, indexing, file operations, ViewerActivity, and BackgroundAudioService intact. No WebView and no duplicate media player are introduced.

## Home design
- Near-black background and restrained layered glass surfaces from `Ui.glass(...)`.
- Cyan accent for the `TOXIFILES · OFFLINE` brand/status line.
- Existing storage overview and category tiles retained.
- Added a distinct `Recently opened` empty state; it does not claim to persist recents until a real history source is wired.

## Settings design
- Existing native settings controls and section grouping retained; subtitle uses ToxiFiles branding.
- Existing theme, blur, file management, storage, recycle bin, App Lock, vault, diagnostics, and transfer controls are preserved.

## Design tokens
Use `Ui.bg`, `Ui.WHITE`, `Ui.SUB`, `Ui.accent`, `Ui.glass(radius, strong)`, and `Ui.dp(...)` for consistent colors, spacing, and surfaces. `Ui.glass` is a layered translucent drawable, not a real-time backdrop blur. Android 12+ blur behavior remains behind the existing `blur` setting.

## Verification required
Run the Android CI workflow and test on a physical Android device. Validate storage permission flow, category taps, Files navigation, settings controls, ViewerActivity, audio playback/background service, scrolling/insets, and accessibility text scaling before release. This handoff alone is not release approval.
