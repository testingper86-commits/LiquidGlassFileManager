# Video/PiP deep audit — source patch

## Issues addressed in this revision
- PiP transition race: mark PiP as requested before `enterPictureInPictureMode()` so `onPause()`/`onStop()` do not pause playback during the transition; clear the request if entry fails or PiP exits.
- User playback intent is tracked separately from surface availability. A temporary `TextureView` surface loss no longer automatically implies the user wanted playback stopped.
- On texture re-creation, release any stale `Surface` before creating/attaching the replacement surface.
- Reattach the prepared `MediaPlayer`, restore the saved position, and resume only when playback intent says it was playing.
- When the Activity is stopped outside PiP, pause video, clear resume intent, and persist the position to avoid audio-only/ghost playback.
- Aspect-ratio fit transform remains in place for landscape resizing.

## Checks run
- Project structural smoke test: PASS (all existing checks).
- ZIP integrity: PASS.
- Android build: BLOCKED before compilation. Gradle wrapper attempted to download Gradle 8.9, but network DNS resolution for `services.gradle.org` failed (`UnknownHostException`). No device/emulator is attached, so runtime PiP, codec, and rotation behavior cannot be verified here.

## Platform limitation
PiP can continue while Android keeps the Activity in PiP. If the user explicitly removes the task from Recents, force-stops the app, or Android kills the process, this Activity-only player cannot guarantee continued playback. That would require a separate media playback service and a different product behavior.
