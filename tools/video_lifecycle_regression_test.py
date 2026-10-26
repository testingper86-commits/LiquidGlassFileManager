from pathlib import Path
p = Path('app/src/main/java/com/music/echo/offline/files/ViewerActivity.java')
s = p.read_text(encoding='utf-8')
checks = {
    'PiP intent set before enterPictureInPictureMode': s.index('videoPipRequested = true;') < s.index('enterPictureInPictureMode(params)'),
    'PiP transition exempted from onPause video pause': 'isInPictureInPictureMode() || videoPipRequested' in s,
    'ordinary background stop pauses video': 'videoUserWantsPlayback = false;' in s[s.index('protected void onStop()'):s.index('onPictureInPictureModeChanged')],
    'surface recreation releases stale Surface': 'videoSurface.release()' in s[s.index('onSurfaceTextureAvailable'):s.index('onSurfaceTextureSizeChanged')],
    'surface recreation reattaches prepared player': 'videoPlayer.setSurface(replacement);' in s,
    'surface recreation resumes only on playback intent': 'videoResumeAfterSurface && videoUserWantsPlayback' in s,
    'surface destruction saves position and detaches': 'videoPendingPosition = active.getCurrentPosition();' in s and 'active.setSurface(null);' in s,
    'landscape aspect-ratio transform retained': 'texture.setTransform(matrix);' in s,
    'PiP manifest support enabled': 'android:supportsPictureInPicture="true"' in Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8'),
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    raise SystemExit(1)
