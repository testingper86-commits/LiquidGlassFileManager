#!/usr/bin/env python3
"""Fast, offline structural regression checks for Liquid Files."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'

def require(condition, message):
    if not condition:
        raise AssertionError(message)
    print(f'PASS: {message}')

def main():
    required = [
        'settings.gradle.kts', 'build.gradle.kts', 'app/build.gradle.kts',
        'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
        'gradlew', 'gradlew.bat', 'app/src/main/AndroidManifest.xml',
        'app/src/main/java/com/music/echo/offline/files/MainActivity.java',
        'app/src/main/java/com/music/echo/offline/files/FileOps.java',
        '.github/workflows/android-build.yml',
    ]
    for rel in required:
        path = ROOT / rel
        require(path.is_file() and path.stat().st_size > 0, f'required project file exists: {rel}')

    manifest_path = ROOT / 'app/src/main/AndroidManifest.xml'
    manifest = ET.parse(manifest_path).getroot()
    perms = {n.attrib.get(ANDROID + 'name') for n in manifest.findall('uses-permission')}
    require('android.permission.INTERNET' in perms, 'manifest declares INTERNET permission used by optional LAN features')
    require('android.permission.FOREGROUND_SERVICE' in perms and 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK' in perms, 'foreground media-playback permissions declared for built-in background audio')
    app = manifest.find('application')
    require(app is not None, 'manifest has application node')
    require(app.attrib.get(ANDROID + 'allowBackup') == 'false', 'Android system backup is disabled for private app state')
    paths = ET.parse(ROOT / 'app/src/main/res/xml/file_paths.xml').getroot()
    path_names = {node.tag for node in paths}
    require(not any(node.tag == 'root-path' for node in paths), 'FileProvider does not expose the filesystem root')
    require(any(node.tag == 'external-path' and node.attrib.get('path') == '.' for node in paths), 'FileProvider external access is limited to shared storage, without filesystem-root access')
    lan = (ROOT / 'app/src/main/java/com/music/echo/offline/files/LanTransfer.java').read_text()
    require('Unsafe incoming filename rejected' in lan and 'getCanonicalPath().startsWith(rootPath)' in lan, 'LAN receive validates filenames and canonical destination containment')
    require('setSoTimeout(120_000)' in lan and 'Transfer confirmation timed out' in lan, 'LAN receive has socket and consent timeouts')
    require('Not enough free space for this transfer' in lan and 'count > 500' in lan, 'LAN receive bounds transfer metadata and checks free space')
    require('PBKDF2WithHmacSHA256' in lan and 'HmacSHA256' in lan and 'AES/GCM/NoPadding' in lan and 'Pairing code is incorrect' in lan, 'LAN transfer uses one-time pairing authentication and AES-GCM encryption')
    main_src = (ROOT / 'app/src/main/java/com/music/echo/offline/files/MainActivity.java').read_text()
    require('10-character pairing code' in main_src and 'Files are encrypted during transfer' in main_src, 'LAN UI communicates pairing and encrypted-transfer requirements')
    require('portableSettingKeys()' in main_src and 'Settings file exceeds the 1 MB limit' in main_src, 'settings import is size-limited and restricted to portable preferences')
    require('if (!allowed.contains(k)) continue;' in main_src and 'applock_on' not in main_src[main_src.index('private static java.util.Set<String> portableSettingKeys()'):main_src.index('// ---------------- LARGEST FILES')], 'settings import cannot overwrite App Lock or other non-portable security preferences')
    pin_hash = (ROOT / 'app/src/main/java/com/music/echo/offline/files/PinHash.java').read_text()
    require('PBKDF2WithHmacSHA256' in pin_hash and 'SecureRandom' in pin_hash and 'MessageDigest.isEqual' in pin_hash, 'PIN verifiers use salted PBKDF2 and constant-time comparison')
    app_lock = (ROOT / 'app/src/main/java/com/music/echo/offline/files/AppLock.java').read_text()
    vault = (ROOT / 'app/src/main/java/com/music/echo/offline/files/Vault.java').read_text()
    require('failed_attempts' in app_lock and 'blocked_until' in app_lock and 'Math.min(15 * 60_000L' in app_lock, 'App Lock applies persistent escalating PIN lockout')
    require('failed_attempts' in vault and 'blocked_until' in vault and 'Math.min(15 * 60_000L' in vault, 'Vault applies persistent escalating PIN lockout')
    require('UUID.randomUUID()' in vault and 'UUID.randomUUID()' in (ROOT / 'app/src/main/java/com/music/echo/offline/files/RecycleBin.java').read_text(), 'Vault and Recycle Bin wrapper names avoid timestamp collisions')
    require('writeMetadata(wrapper, parent.getAbsolutePath())' in vault and 'readMetadata(meta)' in vault and 'META_FILE = \".vault_meta\"' in vault, 'Vault original-path metadata is encrypted at rest')
    browser = (ROOT / 'app/src/main/java/com/music/echo/offline/files/BrowserView.java').read_text()
    require('Copy SHA-256' in browser and 'MessageDigest.getInstance(\"SHA-256\")' in browser and 'SHA-256 copied' in browser, 'file properties can compute and copy a SHA-256 digest')
    require('androidx.documentfile:documentfile:1.0.1' in (ROOT / 'app/build.gradle.kts').read_text() and 'copyToSafTree' in main_src and 'copyLocalToSaf' in main_src, 'SD/USB storage supports recursive outbound copies through the Storage Access Framework')
    require('FLAG_GRANT_WRITE_URI_PERMISSION' in main_src and 'saf_tree' in main_src, 'SD/USB read/write access is persisted for later sessions')
    require('Encrypted at rest with AES-GCM' in browser and 'it does not encrypt them' not in browser, 'Vault UI accurately describes encryption at rest')
    require('Symbolic links are not copied' in (ROOT / 'app/src/main/java/com/music/echo/offline/files/FileOps.java').read_text() and 'Symbolic links cannot be stored in Vault' in vault, 'recursive file operations reject symbolic links')
    file_ops = (ROOT / 'app/src/main/java/com/music/echo/offline/files/FileOps.java').read_text()
    require('Archive has too many entries' in file_ops and 'Archive expanded-size limit exceeded' in file_ops and 'deleteTree(dest)' in file_ops, 'archive extraction bounds expansion and removes partial output on failure')
    require("Can't read folder" in file_ops and 't.delete(); throw copyError' in file_ops and 'Zip failed:' in file_ops, 'failed copies and archives clean partial outputs and reject unreadable directories')
    require('kids == null' in vault and 'encrypted folder' in vault, 'Vault refuses incomplete directory reads instead of encrypting/restoring partial trees')
    require('requestCancel()' in file_ops and 'checkCancelled()' in file_ops and 'Operation cancelled' in file_ops, 'file operations expose cancellation checks with partial-output cleanup')
    require('Tap to request cancellation' in main_src and 'tap to cancel' in main_src and 'LanTransfer.cancel()' in main_src, 'progress control exposes cancellation for file and LAN operations')
    require('FLAG_SECURE' in main_src and 'v == vaultScroll' in main_src, 'Vault and App Lock surfaces block screenshots')
    require('onUserLeaveHint()' in main_src and 'ACTION_SCREEN_OFF' in main_src and 'unlocked = false; // re-lock every time the app leaves the foreground' not in main_src, 'App Lock re-arms on actual app departure and screen-off, not every Activity pause')
    require('if (overlayView == vaultScroll) closeOverlay()' in main_src, 'Vault overlay closes and re-locks when the user backgrounds the app')
    glyphs = (ROOT / 'app/src/main/java/com/music/echo/offline/files/Glyphs.java').read_text()
    ui_src = (ROOT / 'app/src/main/java/com/music/echo/offline/files/Ui.java').read_text()
    require('setContentDescription(Glyphs.label(glyph))' in ui_src and 'static String label(int kind)' in glyphs, 'vector action buttons have screen-reader labels')
    require('setMinimumWidth(dp(48))' in ui_src and 'setMinimumHeight(dp(48))' in ui_src, 'icon action controls declare 48dp minimum touch targets')
    require('isHighTextContrastEnabled()' in ui_src and 'highContrast ? (strong ? 248 : 224)' in ui_src, 'glass surfaces strengthen contrast when Android high-contrast text is enabled')
    require('ValueAnimator.areAnimatorsEnabled()' in ui_src, 'interaction motion respects Android system animation settings')
    indexer = (ROOT / 'app/src/main/java/com/music/echo/offline/files/Indexer.java').read_text()
    require('Files.isSymbolicLink(dir.toPath())' in indexer and 'MAX_INDEX_ENTRIES = 150_000' in indexer and 'truncated = true' in indexer, 'indexer avoids symlink traversal and bounds large-library memory use')
    require('quick search and overview are limited' in main_src, 'large-library index truncation is communicated to users')
    launcher = []
    for activity in app.findall('activity'):
        filt = activity.find('intent-filter')
        if filt is not None:
            actions = {x.attrib.get(ANDROID + 'name') for x in filt.findall('action')}
            cats = {x.attrib.get(ANDROID + 'name') for x in filt.findall('category')}
            if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in cats:
                launcher.append(activity.attrib.get(ANDROID + 'name'))
    require(launcher == ['.files.MainActivity'], 'file manager remains the only launcher activity')

    file_ui = (ROOT / 'app/src/main/java/com/music/echo/offline/files/MainActivity.java').read_text()
    settings = (ROOT / 'app/src/main/java/com/music/echo/offline/files/MainActivity.java').read_text()
    require('Internet permission' in file_ui and 'local Wi-Fi transfer' in file_ui and
            'No claim of zero network traffic is made here.' in file_ui,
            'file-manager privacy text accurately describes network permission')
    require('startActivity(new Intent(this, com.music.echo.offline.ui.MainActivity.class))' not in file_ui,
            'file-manager Audio tab does not launch the separate Compose player')
    activity_names = {a.attrib.get(ANDROID + 'name') for a in app.findall('activity')}
    service_names = {a.attrib.get(ANDROID + 'name') for a in app.findall('service')}
    require('.ui.MainActivity' not in activity_names and '.playback.MusicService' not in service_names,
            'separate Compose player and MusicService are not registered in the app manifest')
    require('.files.BackgroundAudioService' in service_names, 'built-in foreground audio service is registered')
    require('Export settings' in settings and 'Import settings' in settings, 'file manager settings backup and restore remain available')
    require('SETTINGS = 44' in (ROOT / 'app/src/main/java/com/music/echo/offline/files/Glyphs.java').read_text(), 'dedicated gear-shaped settings icon is available')
    require('setTheme(sp.getInt(\"theme\", 11), false)' in (ROOT / 'app/src/main/java/com/music/echo/offline/files/Ui.java').read_text(), 'VantaGlass is the default theme')
    require('Haptic feedback' in settings and 'Backup & Restore' in settings, 'haptics and backup controls are inside developer options')
    require('ImageView sb = Ui.iconBtn(this, Glyphs.SEARCH' not in settings, 'main page has no top-level search shortcut')
    require('String[] lb = {\"Home\", \"Files\"}' in settings, 'main navigation no longer exposes Favorites')
    require('case FileKinds.APK: return 6;' in (ROOT / 'app/src/main/java/com/music/echo/offline/files/Indexer.java').read_text(), 'APK files are categorized under Other')
    require('toUpperCase(java.util.Locale.ROOT)' in settings, 'settings categories use consistent compact section headings')
    require('No internet permission' not in settings,
            'no contradictory claim that INTERNET permission is absent')

    gradle = (ROOT / 'app/build.gradle.kts').read_text()
    require('applicationId = "com.liquidfiles.offline"' in gradle, 'fresh application ID is configured')
    plugins_block = gradle.split('plugins {', 1)[1].split('}', 1)[0] if 'plugins {' in gradle else gradle
    require('org.jetbrains.kotlin' not in plugins_block and 'com.google.devtools.ksp' not in plugins_block, 'unused Kotlin and KSP build plugins removed')
    require(not (ROOT / 'app/src/main/kotlin').exists(), 'unused Echo-derived Compose player source tree removed')
    workflow = (ROOT / '.github/workflows/android-build.yml').read_text()
    require('pull_request:' in workflow and 'assembleDebug' in workflow,
            'CI runs on pull requests and compiles debug APK')
    require('project ZIP not found' not in workflow and 'unzip -q' not in workflow,
            'CI builds source at repository root, not a nested ZIP')

    service_path = ROOT / 'app/src/main/java/com/music/echo/offline/files/BackgroundAudioService.java'
    service = service_path.read_text()
    require('private void prepareNext()' in service and 'private void promoteNext()' in service,
            'background audio preloads and promotes the next queue item')
    require('private final Runnable crossfadeTick' in service and 'crossfadeStartedAt' in service,
            'background crossfade lifecycle is present')
    require('if (player != mp)' in service and 'if (player == mp)' in service,
            'stale asynchronous player callbacks cannot replace current playback')
    require('handler.postDelayed(this, 250)' in service and 'if (player != null && prepared)' in service,
            'background progress ticker remains scheduled while prepared')
    require('player.pause()' in service and 'nextPlayer.pause()' in service,
            'pause path accounts for both sides of an active crossfade')
    require('pausedForTransientFocusLoss' in service and 'change == AudioManager.AUDIOFOCUS_LOSS' in service,
            'permanent audio-focus loss does not automatically resume playback')
    require('ACTION_PAUSE.equals(action)) { pausedForTransientFocusLoss = false; pause(); }' in service,
            'explicit notification pause clears transient-focus auto-resume intent')
    require('focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED' in service and 'if (focusGranted)' in service,
            'background playback respects denied audio focus')
    require('abandonAudioFocusRequest(focusRequest)' in service and 'private void stopPlayback()' in service,
            'audio focus is released when playback stops')
    require('releaseNext();\n                prepareNext();' in service,
            'same-track handoff refreshes preloaded queue item')
    require('playbackVolume = intent == null ? 1.0f' in service and 'setVolume(playbackVolume, playbackVolume)' in service,
            'background playback honors the foreground volume setting')
    viewer = (ROOT / 'app/src/main/java/com/music/echo/offline/files/ViewerActivity.java').read_text()
    require('new SquigglySeekBar(this)' in viewer and viewer.count('new SquigglySeekBar(this)') >= 2,
            'audio and video use the clean squiggly playback control')
    squiggle = (ROOT / 'app/src/main/java/com/music/echo/offline/files/SquigglySeekBar.java').read_text()
    require('extends SeekBar' in squiggle and 'Math.sin' in squiggle and 'drawPath(path,paint)' in squiggle,
            'custom squiggly progress control draws the played section while retaining native seek interaction')
    require('new SquigglySeekBar(this)' in viewer and viewer.count('new SquigglySeekBar(this)') >= 2,
            'audio and video playback use the new squiggly seek control')
    require('USAGE_MEDIA' in viewer and 'CONTENT_TYPE_MUSIC' in viewer,
            'foreground player declares standard media audio attributes')
    require('putInt("audio_volume", value)' in viewer and 'putExtra("volume", audioVolume)' in viewer,
            'audio volume persists and transfers to background playback')
    require('void showQueueEditor()' in viewer and 'Collections.swap(paths, position, position - 1)' in viewer
            and 'paths.remove(position)' in viewer, 'playback queue supports reordering and removal')
    require('Frame −/＋' not in viewer and 'frameForward' not in viewer
            and 'if (!videoLoop) advanceToNextVideo();' in viewer,
            'unsupported frame-step control is removed and videos auto-advance on completion unless repeat is enabled')
    require('Audio only' not in viewer and 'Aspect ratio' not in viewer and 'Audio & subtitle tracks' not in viewer
            and 'Skip intro / outro' not in viewer and 'video_marker_' not in viewer,
            'removed video-player audio-only, aspect, track picker, and intro/outro controls')
    require('enterVideoImmersiveMode();' in viewer and 'WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE' in viewer
            and 'LinearLayout primaryRow' in viewer and 'LinearLayout secondaryRow' in viewer
            and 'setMediaController' not in viewer and 'SeekBar seekBar' in viewer
            and 'formatVideoTime' in viewer,
            'video playback uses immersive full-screen mode and an integrated seek/play control dock')
    require('TextureView' in viewer and 'setSurfaceTextureListener' in viewer and 'player.setSurface(output)' in viewer
            and 'new VideoView' not in viewer and 'Subtitles' not in viewer and 'subtitleOverlay' not in viewer,
            'video frames render through TextureView and subtitle UI is removed')
    require('new SquigglySeekBar(this)' in viewer and 'videoProgressAnimation' not in viewer and 'audioProgressAnimation' not in viewer,
            'audio and video playback use only the custom squiggly progress control')
    require('sound.json' not in viewer and 'wave.json' not in viewer and 'LottieAnimationView' not in viewer,
            'music and video players contain no sound/wave JSON visualizers')
    assets = ROOT / 'app/src/main/assets'
    require(not (assets / 'sound.json').exists() and not (assets / 'wave.json').exists(),
            'sound.json and wave.json are removed from packaged Android assets')
    require('com.airbnb.android:lottie' not in gradle, 'unused Lottie runtime dependency is removed')
    main_activity = (ROOT / 'app/src/main/java/com/music/echo/offline/files/MainActivity.java').read_text()
    require('categoryName = Indexer.CAT_NAMES[cat]' in main_activity and 'System Apps' in main_activity and 'Downloaded Apps' in main_activity and 'Other files and APKs' in main_activity,
            'APK files are grouped under Other with installed-app choices available there')
    require('settingsBtn.setContentDescription("Settings")' in main_activity and 'settingsBtn.setOnClickListener(v -> selectTab(3))' in main_activity,
            'Settings is accessed from the top-right home header')
    require('Usage & diagnostics' in main_activity and 'Built-in tools' in main_activity and 'phone\'s separate apps' in main_activity and 'Debug options' not in main_activity,
            'developer options refer to integrated file-manager tools rather than mobile apps')
    browser = (ROOT / 'app/src/main/java/com/music/echo/offline/files/BrowserView.java').read_text()
    require('normalizedTitle.contains("video") || normalizedTitle.contains("audio") || normalizedTitle.contains("music")' in browser,
            'Videos and Music/Audio categories default to grid view')
    require('Open LiquidDrive dashboard' not in main_activity and not (ROOT / 'app/src/main/java/com/music/echo/offline/files/DashboardActivity.java').exists(),
            'LiquidDrive dashboard shortcut and activity are removed')
    require('Liquid Glass Studio' in main_activity and 'AVAILABLE FINISHES' in main_activity and 'marks[index]' in main_activity and 'finishNotes[index]' in main_activity,
            'theme chooser has dedicated studio layout, distinct icons and finish descriptions')
    require('void openTextEditor(File file)' in viewer and 'Text file saved' in viewer,
            'built-in text editor can edit and save supported small text files')
    require('bool("reduce_motion", true)' in (ROOT / 'app/src/main/java/com/music/echo/offline/files/Ui.java').read_text(),
            'decorative motion is reduced by default')
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    crash_logger = (ROOT / 'app/src/main/java/com/music/echo/offline/files/CrashLogger.java').read_text()
    crash_app = (ROOT / 'app/src/main/java/com/music/echo/offline/files/LiquidFilesApplication.java').read_text()
    require('android:name=".files.LiquidFilesApplication"' in manifest and 'CrashLogger.install(this)' in crash_app,
            'uncaught crash logging is installed at application startup')
    require('FATAL EXCEPTION' in crash_logger and 'latest_crash.txt' in crash_logger and 'printStackTrace' in crash_logger,
            'crash logger persists Java exception stack traces locally')
    require('Crash logs & diagnostics' in settings and 'maybeOfferCrashReport' in settings and 'shareCrashReport' in settings,
            'crash reports can be offered after restart and manually shared from settings')
    require('<files-path name="crash_logs" path="crash-logs/" />' in (ROOT / 'app/src/main/res/xml/file_paths.xml').read_text(),
            'private crash report path is shareable through FileProvider')
    print('\nAll structural smoke checks passed. This is not a substitute for Android compilation or device testing.')

if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print(f'FAIL: {exc}', file=sys.stderr)
        sys.exit(1)
