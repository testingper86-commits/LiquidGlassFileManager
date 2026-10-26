package com.music.echo.offline.files;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.music.echo.offline.R;
import java.io.File;
import java.util.ArrayList;

/** Lightweight foreground playback handoff used while the viewer Activity is in the background. */
public final class BackgroundAudioService extends Service {
    public static final String ACTION_START = "com.liquidfiles.offline.audio.START";
    public static final String ACTION_PLAY = "com.liquidfiles.offline.audio.PLAY";
    public static final String ACTION_PAUSE = "com.liquidfiles.offline.audio.PAUSE";
    public static final String ACTION_STOP = "com.liquidfiles.offline.audio.STOP";
    public static volatile String activePath = "";
    public static volatile int latestPosition = 0;
    private static final String CHANNEL = "liquidfiles_playback";
    private static final int NOTIFICATION_ID = 4812;
    private MediaPlayer player;
    private MediaPlayer nextPlayer;
    private boolean nextPrepared = false;
    private boolean crossfadeActive = false;
    private long crossfadeStartedAt = 0L;
    private int nextQueueIndex = -1;
    private MediaSession session;
    private String path = "";
    private boolean prepared = false;
    private ArrayList<String> queue = new ArrayList<>();
    private int queueIndex = 0;
    private boolean repeatAll = false;
    private boolean repeatOne = false;
    private boolean shuffle = false;
    private final java.util.Random random = new java.util.Random();
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener focusListener;
    private boolean pausedForTransientFocusLoss = false;
    private android.media.AudioFocusRequest focusRequest;
    private float playbackVolume = 1.0f;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler sleepHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepStopper;
    private final Runnable progressTick = new Runnable() { @Override public void run() {
        // Keep the ticker scheduled while prepared even when paused. If it stopped on pause,
        // resuming from a media notification could leave progress/crossfade checks dormant.
        try {
            if (player != null && prepared) {
                if (player.isPlaying()) {
                    latestPosition = player.getCurrentPosition();
                    maybeCrossfade();
                }
                updateState();
                handler.postDelayed(this, 250);
            }
        } catch (Exception ignored) {
            if (player != null && prepared) handler.postDelayed(this, 500);
        }
    }};

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Media playback", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Controls for local music playback");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
        audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        focusListener = change -> {
            if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                pausedForTransientFocusLoss = player != null && prepared && player.isPlaying();
                pause();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
                // A permanent focus loss must not restart playback later without user action.
                pausedForTransientFocusLoss = false;
                pause();
            } else if (change == AudioManager.AUDIOFOCUS_GAIN && pausedForTransientFocusLoss) {
                pausedForTransientFocusLoss = false;
                resume();
            }
        };
        session = new MediaSession(this, "LiquidFilesPlayback");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pausedForTransientFocusLoss = false; pause(); }
            @Override public void onStop() { pausedForTransientFocusLoss = false; stopSelf(); }
            @Override public void onSkipToNext() { skip(1); }
            @Override public void onSkipToPrevious() { skip(-1); }
            @Override public void onSeekTo(long pos) { if (prepared && player != null) try { player.seekTo((int)pos); latestPosition=(int)pos; updateNotification(); } catch (Exception ignored) {} }
        });
        session.setActive(true);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) { pausedForTransientFocusLoss = false; stopPlayback(); stopForeground(true); stopSelf(); return START_NOT_STICKY; }
        if (ACTION_PAUSE.equals(action)) { pausedForTransientFocusLoss = false; pause(); }
        else if (ACTION_PLAY.equals(action)) { pausedForTransientFocusLoss = false; resume(); }
        else if ("com.liquidfiles.offline.audio.NEXT".equals(action)) skip(1);
        else if ("com.liquidfiles.offline.audio.PREVIOUS".equals(action)) skip(-1);
        else if (ACTION_START.equals(action)) {
            String requested = intent == null ? null : intent.getStringExtra("path");
            int position = intent == null ? 0 : intent.getIntExtra("position", 0);
            repeatAll = intent != null && intent.getBooleanExtra("repeatAll", false);
            repeatOne = intent != null && intent.getBooleanExtra("repeatOne", false);
            shuffle = intent != null && intent.getBooleanExtra("shuffle", false);
            long sleepRemaining = intent == null ? 0L : intent.getLongExtra("sleepRemaining", 0L);
            playbackVolume = intent == null ? 1.0f : Math.max(0f, Math.min(1f, intent.getFloatExtra("volume", 1.0f)));
            if (sleepStopper != null) sleepHandler.removeCallbacks(sleepStopper);
            sleepStopper = null;
            if (sleepRemaining > 0L) setSleepTimer(sleepRemaining);
            ArrayList<String> incoming = intent == null ? null : intent.getStringArrayListExtra("queue");
            if (incoming != null && !incoming.isEmpty()) {
                queue = incoming;
                int suppliedIndex = Math.max(0, Math.min(queue.size() - 1, intent.getIntExtra("index", 0)));
                int resolvedIndex = requested == null ? -1 : queue.indexOf(requested);
                queueIndex = resolvedIndex >= 0 ? resolvedIndex : suppliedIndex;
            }
            if (requested != null && !requested.isEmpty() && !requested.equals(path)) load(requested, position);
            else if (requested != null && requested.equals(path) && player != null && prepared) {
                // A viewer handoff can carry a newly edited queue while the same track keeps playing.
                releaseNext();
                prepareNext();
                updateNotification();
            } else if (player != null && !prepared) { /* preparation is already in progress */ }
        }
        startForeground(NOTIFICATION_ID, notification());
        updateState();
        return START_NOT_STICKY;
    }

    private void load(String file, int position) {
        pausedForTransientFocusLoss = false;
        stopPlayback(); path = file; activePath = file; latestPosition = Math.max(0, position); prepared = false;
        try {
            player = new MediaPlayer();
            // Keep decoding alive while the screen is off during legitimate background playback.
            player.setWakeMode(getApplicationContext(), android.os.PowerManager.PARTIAL_WAKE_LOCK);
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(file);
            player.setOnPreparedListener(mp -> {
                // Ignore callbacks from a player replaced by a rapid skip/stop.
                if (player != mp) { try { mp.release(); } catch (Exception ignored) {} return; }
                prepared = true;
                boolean focusGranted = false;
                try {
                    int result;
                    if (Build.VERSION.SDK_INT >= 26) {
                        if (focusRequest == null) focusRequest = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setOnAudioFocusChangeListener(focusListener).setWillPauseWhenDucked(true).build();
                        result = audioManager.requestAudioFocus(focusRequest);
                    } else result = audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                    focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                } catch (Exception ignored) { }
                session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE, new File(path).getName()).putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, mp.getDuration()).build());
                if (latestPosition > 0 && latestPosition < mp.getDuration()-1500) mp.seekTo(latestPosition);
                try { mp.setVolume(playbackVolume, playbackVolume); } catch (Exception ignored) {}
                if (focusGranted) { try { mp.start(); } catch (Exception ignored) {} }
                updateState(); updateNotification();
                prepareNext();
                handler.removeCallbacks(progressTick); handler.post(progressTick);
            });
            player.setOnCompletionListener(mp -> {
                if (player != mp) return;
                latestPosition = 0;
                if (repeatOne || (repeatAll && queue.size() == 1)) {
                    try { mp.seekTo(0); mp.start(); updateState(); updateNotification(); }
                    catch (Exception ignored) { stopForeground(true); stopSelf(); }
                } else if (nextPrepared && nextPlayer != null) {
                    promoteNext();
                } else if (queue.size() > 1 && (repeatAll || shuffle || queueIndex < queue.size() - 1)) {
                    skip(1);
                } else {
                    stopForeground(true);
                    stopSelf();
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) { stopPlayback(); stopForeground(true); stopSelf(); }
                else { try { mp.release(); } catch (Exception ignored) {} }
                return true;
            });
            player.prepareAsync();
        } catch (Exception ex) { stopSelf(); }
        updateNotification();
    }

    /** Prepares the following local track before the current one ends. This reduces transition gaps. */
    private void prepareNext() {
        releaseNext();
        if (queue == null || queue.size() < 2 || repeatOne || path.isEmpty()) return;
        int n = computeNextIndex(1);
        if (n < 0 || n >= queue.size() || queue.get(n).equals(path)) return;
        try {
            MediaPlayer candidate = new MediaPlayer();
            nextPlayer = candidate;
            nextQueueIndex = n;
            candidate.setWakeMode(getApplicationContext(), android.os.PowerManager.PARTIAL_WAKE_LOCK);
            candidate.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            candidate.setVolume(0f, 0f);
            candidate.setDataSource(queue.get(n));
            candidate.setOnPreparedListener(mp -> { if (nextPlayer == mp) { nextPrepared = true; } else { try { mp.release(); } catch (Exception ignored) {} } });
            candidate.setOnErrorListener((mp, what, extra) -> { if (nextPlayer == mp) releaseNext(); return true; });
            candidate.prepareAsync();
        } catch (Exception ignored) { releaseNext(); }
    }

    private int computeNextIndex(int delta) {
        if (queue == null || queue.isEmpty()) return -1;
        if (shuffle && delta > 0 && queue.size() > 1) {
            int n = queueIndex;
            for (int tries = 0; tries < 8 && n == queueIndex; tries++) n = random.nextInt(queue.size());
            return n == queueIndex ? (queueIndex + 1) % queue.size() : n;
        }
        int n = queueIndex + delta;
        if (n >= queue.size()) return repeatAll ? 0 : -1;
        if (n < 0) return repeatAll ? queue.size() - 1 : -1;
        return n;
    }

    private void maybeCrossfade() {
        if (crossfadeActive || !nextPrepared || nextPlayer == null || player == null || !prepared || repeatOne) return;
        int remaining = player.getDuration() - player.getCurrentPosition();
        if (remaining <= 2500 && remaining > 0) {
            try {
                nextPlayer.setVolume(0f, 0f);
                nextPlayer.start();
                crossfadeActive = true;
                crossfadeStartedAt = SystemClock.elapsedRealtime();
                handler.post(crossfadeTick);
            } catch (Exception ignored) { releaseNext(); }
        }
    }

    private final Runnable crossfadeTick = new Runnable() { @Override public void run() {
        if (!crossfadeActive || player == null || nextPlayer == null) return;
        try {
            // Freeze the fade clock while playback is paused so a notification pause cannot
            // silently advance to the next track.
            if (!player.isPlaying()) {
                crossfadeStartedAt += 50L;
                handler.postDelayed(this, 50);
                return;
            }
        } catch (Exception ignored) { return; }
        float fraction = Math.min(1f, (SystemClock.elapsedRealtime() - crossfadeStartedAt) / 2500f);
        try {
            player.setVolume(1f - fraction, 1f - fraction);
            nextPlayer.setVolume(fraction, fraction);
        } catch (Exception ignored) {}
        if (fraction >= 1f) promoteNext(); else handler.postDelayed(this, 50);
    }};

    private void promoteNext() {
        if (nextPlayer == null || !nextPrepared) return;
        MediaPlayer old = player;
        player = nextPlayer; nextPlayer = null;
        prepared = true; nextPrepared = false; crossfadeActive = false;
        if (nextQueueIndex >= 0) queueIndex = nextQueueIndex;
        nextQueueIndex = -1;
        path = queue.get(queueIndex); activePath = path; latestPosition = 0;
        try { player.setVolume(1f, 1f); if (!player.isPlaying()) player.start(); } catch (Exception ignored) {}
        try { if (old != null) { old.setOnCompletionListener(null); old.release(); } } catch (Exception ignored) {}
        session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE, new File(path).getName()).putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, player.getDuration()).build());
        updateState(); updateNotification(); prepareNext();
    }

    private void releaseNext() {
        handler.removeCallbacks(crossfadeTick); crossfadeActive = false;
        try { if (nextPlayer != null) nextPlayer.release(); } catch (Exception ignored) {}
        nextPlayer = null; nextPrepared = false; nextQueueIndex = -1;
    }

    private void setSleepTimer(long delay) {
        if (sleepStopper != null) sleepHandler.removeCallbacks(sleepStopper);
        sleepStopper = null;
        if (delay <= 0L) return;
        sleepStopper = () -> {
            sleepStopper = null;
            pause();
        };
        sleepHandler.postDelayed(sleepStopper, delay);
    }

    private void skip(int delta) {
        if (queue == null || queue.isEmpty()) { updateNotification(); return; }
        int next = computeNextIndex(delta);
        if (next < 0) {
            if (delta < 0) next = queue.size() - 1;
            else { pause(); return; }
        }
        queueIndex = next; load(queue.get(queueIndex), 0);
        startForeground(NOTIFICATION_ID, notification());
    }

    private void resume() {
        try {
            if (player != null && prepared && !player.isPlaying()) player.start();
            // During a crossfade both players must pause/resume together; otherwise the hidden
            // next track can continue audibly while the current track is paused.
            if (crossfadeActive && nextPlayer != null && nextPrepared && !nextPlayer.isPlaying()) nextPlayer.start();
        } catch (Exception ignored) {}
        if (player != null && prepared) { handler.removeCallbacks(progressTick); handler.post(progressTick); }
        updateState(); updateNotification();
    }
    private void pause() {
        try {
            if (player != null && prepared && player.isPlaying()) player.pause();
            if (player != null && prepared) latestPosition=player.getCurrentPosition();
            if (crossfadeActive && nextPlayer != null && nextPrepared && nextPlayer.isPlaying()) nextPlayer.pause();
        } catch (Exception ignored) {}
        updateState(); updateNotification();
    }
    private void stopPlayback() {
        handler.removeCallbacks(progressTick);
        releaseNext();
        try {
            if (audioManager != null && Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
            else if (audioManager != null && focusListener != null) audioManager.abandonAudioFocus(focusListener);
        } catch (Exception ignored) { }
        try {
            if (player != null) {
                if (prepared) latestPosition = player.getCurrentPosition();
                player.setOnCompletionListener(null);
                player.setOnErrorListener(null);
                player.release();
            }
        } catch (Exception ignored) { }
        player = null;
        prepared = false;
        crossfadeActive = false;
    }

    private void updateState() {
        if (session == null) return;
        boolean playing = false; long pos = latestPosition;
        try { if (player != null && prepared) { playing=player.isPlaying(); pos=player.getCurrentPosition(); latestPosition=(int)pos; } } catch (Exception ignored) {}
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, playing ? 1f : 0f, SystemClock.elapsedRealtime()).build());
    }

    private PendingIntent action(String action, int code) {
        Intent i = new Intent(this, BackgroundAudioService.class).setAction(action);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }
    private Notification notification() {
        String name = path.isEmpty() ? "Local audio" : new File(path).getName();
        boolean playing=false; try { playing=player != null && prepared && player.isPlaying(); } catch (Exception ignored) {}
        ArrayList<String> viewerQueue = new ArrayList<>(queue);
        if (viewerQueue.isEmpty() && !path.isEmpty()) viewerQueue.add(path);
        Intent openViewer = new Intent(this, ViewerActivity.class)
            .putStringArrayListExtra("paths", viewerQueue)
            .putExtra("index", Math.max(0, Math.min(viewerQueue.size() - 1, queueIndex)))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 9, openViewer,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(name).setContentText("LiquidFiles · offline playback")
            .setContentIntent(contentIntent).setOngoing(playing).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1));
        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", action("com.liquidfiles.offline.audio.PREVIOUS", 10)).build());
        b.addAction(new Notification.Action.Builder(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "Pause" : "Play", action(playing ? ACTION_PAUSE : ACTION_PLAY, 11)).build());
        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", action("com.liquidfiles.offline.audio.NEXT", 12)).build());
        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action(ACTION_STOP, 13)).build());
        return b.build();
    }
    private void updateNotification() { try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification()); } catch (SecurityException ignored) {} }

    @Override public void onDestroy() {
        if (sleepStopper != null) sleepHandler.removeCallbacks(sleepStopper);
        stopPlayback();
        try {
            if (audioManager != null && Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
            else if (audioManager != null && focusListener != null) audioManager.abandonAudioFocus(focusListener);
        } catch (Exception ignored) {}
        if (session != null) { session.setActive(false); session.release(); session=null; }
        activePath=""; latestPosition=0; super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
