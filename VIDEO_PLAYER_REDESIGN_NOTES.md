# Video Player UI Redesign

## Changes
- Removed the video player's Audio only, Aspect, Tracks, and Intro/Outro controls and their marker-based playback behavior.
- Switched video playback to immersive edge-to-edge mode. Android system bars can still be revealed with a swipe.
- Replaced the default Android MediaController overlay with an integrated play/pause button, seek bar, elapsed time, and duration display to prevent the native controller from overlapping the custom controls.
- Reorganized secondary actions into two aligned rows: Speed / Repeat / Rotate and PiP / Subtitles / Lock.
- Tapping the video toggles the top and bottom control overlays; the lock action hides controls and a tap unlocks them.
- Preserved video resume position, playback speed, repeat, rotation, picture-in-picture, external subtitles, brightness/volume gestures, and automatic next-video playback.
- Uses the clean grouping and alignment principles associated with File Manager Plus as a reference; no third-party assets were copied.

## Validation
- `python tools/project_smoke_test.py` passes.
- All Android XML files parse successfully.
- Android Gradle compilation could not be verified in this environment because `services.gradle.org` could not be resolved. Run the GitHub Actions build before installing this revision.
