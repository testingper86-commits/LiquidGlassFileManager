# Built-in player status — source implementation, not yet APK-verified

## Audio playback implementation in source
- Local file playback, play/pause, previous/next, seek, shuffle, repeat off/one/all, speed, per-player volume, sleep timer, cover art/metadata, local `.lrc`/`.txt` lyrics, timestamp-synced `.lrc` line display, saved position, and a selectable file-list queue.
- Best-effort Android Equalizer/BassBoost tied to the active MediaPlayer session.
- Foreground `BackgroundAudioService` with media notification actions, Android `MediaSession`, previous/play/pause/next/stop, seek state, audio focus handling, queue handoff, repeat-one/repeat-all and shuffle-aware progression.

## Video playback implementation in source
- Native VideoView playback, seek controls, double-tap ±10 seconds, brightness and volume swipe gestures, playback speed, repeat, orientation toggle, saved position, PiP entry on supported Android, external SRT selection/parsing and timed overlay, embedded track selection where exposed by the decoder, aspect display options, screen-control lock, audio-only display mode, intro/outro markers, and approximate 40 ms seek stepping.
- Media decoding, true frame stepping, track exposure and visual scaling are constrained by Android's built-in MediaPlayer/VideoView and the particular codec/device. The UI reports limitations rather than promising frame-accurate behavior.

## Remaining engineering work before the final APK round
- The foreground Activity player and background service still use separate MediaPlayer instances. The background service now preloads the next queue item and performs a 2.5-second crossfade where a next item is available, reducing transition gaps during background playback. This is not sample-accurate gapless playback; Android MediaPlayer does not expose a reliable sample-accurate gapless guarantee across arbitrary local formats.
- Bluetooth/headset integration relies on Android MediaSession/media-button routing and still requires real-device verification.
- External subtitle selection is implemented for SRT-style text; embedded subtitle behavior varies by decoder. Picture-in-Picture and foreground service behavior depend on OS/device settings. The video frame-step control is approximate seeking, not guaranteed frame-accurate stepping.
- Static project smoke checks pass. No final APK build or physical-device test has been run for this source revision, by user request. A source audit also found and fixed duplicate field declarations in BackgroundAudioService and corrected repeat-all behavior for a one-track queue. User requested that APK/device testing be reserved for the final round.

### Latest source hardening
- Background service sleep timer is replaced/cleared deterministically during a new playback handoff.
- Audio focus request is retained for proper release.
- Both players pause/resume together during a crossfade, with fade progression frozen while paused.
- Not APK-tested yet; these are source changes only pending the final consolidated verification.
- Background audio players now request a partial wake lock for screen-off playback; battery and OEM behavior remain device-test items.

## Latest source hardening (not APK-verified)
- Background playback progress ticker remains scheduled while paused and is restarted on resume.
- Added guards against stale asynchronous MediaPlayer callbacks after rapid queue changes.
- Foreground local audio playback now declares standard media/music audio attributes.
- Structural smoke checks now include regression assertions for the background service lifecycle and approximate frame stepping.

## Still outstanding before final APK verification
- Review/implement any remaining foreground/background playback-state edge cases and practical queue-editing gaps.
- Verify build and runtime behavior in the final consolidated test pass only; no APK compilation or physical-device testing has been performed for this revision.


### Audio focus lifecycle hardening (source-only, not APK-verified)
- Transient audio-focus loss pauses playback and records whether playback was active; focus gain resumes only when that transient interruption caused the pause.
- Permanent audio-focus loss and explicit notification/media-session pause or stop clear the auto-resume intent, avoiding unexpected playback restarting after the user paused it.
- Starting a new track clears stale transient-focus resume state.
- Structural smoke tests now assert the focus-loss distinction. These checks do not replace Android compilation or device validation.


### Foreground/background audio handoff hardening (2026-10-23)
- The built-in player's volume slider now persists its selected gain and passes it to background playback; the service applies it to active and crossfaded tracks.
- Background playback now checks Android's audio-focus result and does not start a track if focus is denied. Focus is abandoned when playback is stopped.
- Same-track activity handoff reconciles the queue index and rebuilds the preloaded next item, avoiding stale queue transitions.
- Structural regression checks cover these paths. These checks do not establish APK compilation or device behavior.

- The built-in queue dialog now supports selecting a track, moving items up/down, and removing entries while keeping at least one item.

### Final consolidated verification attempt (2026-10-23)
- Structural smoke tests pass, including queue editing, audio-focus handling, volume handoff, and queue transition regressions.
- All Android XML resources parse successfully.
- Final Gradle APK build was attempted, but the environment could not resolve `services.gradle.org` (`UnknownHostException`) to download Gradle 8.9. Therefore compilation and physical-device tests remain unverified; no APK is claimed as built.
