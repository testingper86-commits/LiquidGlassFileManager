package com.music.echo.offline.files;


import com.music.echo.offline.R;

import android.app.Activity;
import androidx.activity.ComponentActivity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.media.PlaybackParams;
import android.media.audiofx.Equalizer;
import android.media.audiofx.BassBoost;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.EditText;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.LinearInterpolator;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Random;

/** Built-in viewer: images (pinch zoom), video, audio, PDF and text. Works without any other app installed. */
public class ViewerActivity extends ComponentActivity {
    ArrayList<String> paths;
    int idx;
    FrameLayout root, stage;
    TextView titleTv, counterTv, editImageBtn;
    Bitmap currentImageBitmap;
    ImageView prevBtn, nextBtn;
    LinearLayout bar;
    private final TaskCoordinator.Scope taskScope = new TaskCoordinator.Scope();
    final TaskCoordinator.ScopedExecutor io = taskScope.executor(TaskCoordinator.Lane.IO);
    final Handler h = new Handler(Looper.getMainLooper());
    MediaPlayer mp;
    MediaPlayer videoPlayer;
    Equalizer equalizer;
    BassBoost bassBoost;
    TextureView videoTexture;
    LinearLayout videoDock;
    Surface videoSurface;
    int videoWidth = 0, videoHeight = 0;
    boolean videoPrepared = false, videoResumeAfterSurface = false;
    boolean videoUserWantsPlayback = false, videoPipRequested = false;
    int videoPendingPosition = 0;
    PdfRenderer pdf;
    ParcelFileDescriptor pfd;
    volatile long pdfGeneration;
    Runnable ticker;
    Runnable videoTicker;
    boolean seekingVideo = false;
    volatile boolean destroyed;
    boolean audioRepeat = false, audioShuffle = false, videoLoop = false;
    int audioRepeatMode = 0; // 0 off, 1 repeat one, 2 repeat all
    int audioLoopStartMs = -1, audioLoopEndMs = -1;
    float audioSpeed = 1.0f, videoSpeed = 1.0f, audioVolume = 1.0f;
    final Handler sleepHandler = new Handler(Looper.getMainLooper());
    Runnable sleepStopper;
    long sleepDeadlineElapsed = 0L;
    final Random random = new Random();
    int topInset, bottomInset;
    boolean videoControlsLocked = false;
    final Handler levelOverlayHandler = new Handler(Looper.getMainLooper());
    Runnable hideLevelOverlays;
    boolean audioWasPlayingBeforeBackground = false, backgroundAudioHandoff = false;
    final Handler subtitleHandler = new Handler(Looper.getMainLooper());
    final java.util.ArrayList<Long> lyricTimes = new java.util.ArrayList<>();
    final java.util.ArrayList<String> lyricText = new java.util.ArrayList<>();
    Runnable lyricTicker;

    @Override protected void onCreate(Bundle b) {
        boolean lightAppearance = getSharedPreferences("lg_settings", MODE_PRIVATE).getBoolean("light", false);
        setTheme(lightAppearance ? R.style.AppTheme_Light : R.style.AppTheme);
        super.onCreate(b);
        ToxiBackNavigation.bind(this, this::handleBackNavigation);
        ToxiVisualSystem.initialize(this);
        if (AppLock.enabled(this)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        paths = getIntent().getStringArrayListExtra("paths");
        idx = getIntent().getIntExtra("index", 0);
        if (paths == null || paths.isEmpty()) { finish(); return; }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        root = new FrameLayout(this);
        root.setBackground(ToxiVisualSystem.auroraBackground());
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(ToxiVisualSystem.surface(26, true));
        ToxiVisualSystem.revealChildren(bar);
        bar.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0);
        ImageView back = ToxiVisualSystem.iconButton(this, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setOnClickListener(v -> finish());
        titleTv = ToxiVisualSystem.text(this, "", 15, ToxiVisualSystem.TEXT_PRIMARY, true);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        counterTv = ToxiVisualSystem.text(this, "", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        ImageView open = ToxiVisualSystem.iconButton(this, ToxiIcons.OPEN, ToxiVisualSystem.TEXT_PRIMARY);
        open.setOnClickListener(v -> FileOps.external(this, new File(paths.get(idx)), true));
        ImageView share = ToxiVisualSystem.iconButton(this, ToxiIcons.SHARE, ToxiVisualSystem.TEXT_PRIMARY);
        share.setOnClickListener(v -> { ArrayList<File> l = new ArrayList<>(); l.add(new File(paths.get(idx))); FileOps.share(this, l); });
        bar.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48)), ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48))));
        bar.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.rightMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6));
        bar.addView(counterTv, cl);
        editImageBtn = ToxiVisualSystem.text(this, "Edit", 13, ToxiVisualSystem.TEXT_PRIMARY, true);
        editImageBtn.setGravity(Gravity.CENTER);
        editImageBtn.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0);
        editImageBtn.setBackground(ToxiVisualSystem.surface(18, true));
        editImageBtn.setOnClickListener(v -> openImageEditor());
        ToxiVisualSystem.interactive(editImageBtn);
        bar.addView(editImageBtn, new LinearLayout.LayoutParams(-2, ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(36))));
        bar.addView(open, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48)), ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48))));
        bar.addView(share, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48)), ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48))));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(52)), Gravity.TOP);
        bp.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0);
        root.addView(bar, bp);

        prevBtn = edge(ToxiIcons.BACK, Gravity.START | Gravity.CENTER_VERTICAL, -1);
        nextBtn = edge(ToxiIcons.NEXT, Gravity.END | Gravity.CENTER_VERTICAL, 1);

        setContentView(root);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) lightFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(ToxiVisualSystem.LIGHT_MODE ? (flags | lightFlags) : (flags & ~lightFlags));
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                topInset = in.top; bottomInset = in.bottom;
            } else { topInset = insets.getSystemWindowInsetTop(); bottomInset = insets.getSystemWindowInsetBottom(); }
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bar.getLayoutParams();
            if (p.topMargin != topInset + ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8))) { p.topMargin = topInset + ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)); bar.setLayoutParams(p); }
            return insets;
        });
        root.requestApplyInsets();
        show();
    }

    ImageView edge(int glyph, int gravity, final int delta) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(ToxiIcons.symbol(glyph, ToxiVisualSystem.TEXT_PRIMARY));
        v.setBackground(ToxiVisualSystem.surface(24, true));
        v.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        v.setOnClickListener(x -> go(delta));
        ToxiVisualSystem.interactive(v);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(46)), ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(46)), gravity);
        p.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0);
        root.addView(v, p);
        return v;
    }

    void go(int d) {
        if (paths == null || paths.isEmpty()) return;
        int n;
        if (audioShuffle && paths.size() > 1) {
            n = idx;
            for (int tries = 0; tries < 8 && n == idx; tries++) n = random.nextInt(paths.size());
            if (n == idx) n = (idx + 1) % paths.size();
        } else {
            n = idx + d;
            if (n < 0 || n >= paths.size()) return;
        }
        idx = n;
        show();
    }

    void cleanup() {
        if (sleepStopper != null) { sleepHandler.removeCallbacks(sleepStopper); sleepStopper = null; }
        if (ticker != null) { h.removeCallbacks(ticker); ticker = null; }
        if (videoTicker != null) { h.removeCallbacks(videoTicker); videoTicker = null; }
        if (lyricTicker != null) { subtitleHandler.removeCallbacks(lyricTicker); lyricTicker = null; }
        lyricTimes.clear(); lyricText.clear();
        videoControlsLocked = false;
        if (mp != null) { releaseAudioEffects(); try { mp.release(); } catch (Exception ignored) { } mp = null; }
        if (videoPlayer != null) { try { videoPlayer.setOnPreparedListener(null); videoPlayer.setOnCompletionListener(null); videoPlayer.setOnErrorListener(null); videoPlayer.setOnVideoSizeChangedListener(null); videoPlayer.setSurface(null); videoPlayer.release(); } catch (Exception ignored) { } videoPlayer = null; }
        videoPrepared = false; videoResumeAfterSurface = false; videoUserWantsPlayback = false; videoPipRequested = false; videoPendingPosition = 0;
        if (videoSurface != null) { try { videoSurface.release(); } catch (Exception ignored) { } videoSurface = null; }
        videoTexture = null;
        videoDock = null;
        videoWidth = videoHeight = 0;
        currentImageBitmap = null;
        if (stage != null) ToxiMotion.setActive(stage, false);
        ++pdfGeneration;
        if (pdf != null || pfd != null) {
            final PdfRenderer renderer = pdf;
            final ParcelFileDescriptor descriptor = pfd;
            pdf = null;
            pfd = null;
            closePdfResourcesAsync(renderer, descriptor);
        }
        if (stage != null) stage.removeAllViews();
    }

    void show() {
        cleanup();
        File f = new File(paths.get(idx));
        Entry e = new Entry(f);
        titleTv.setText(f.getName());
        counterTv.setText(paths.size() > 1 ? (idx + 1) + "/" + paths.size() : "");
        boolean multi = paths.size() > 1 && (e.kind == FileKinds.IMAGE || e.kind == FileKinds.VIDEO);
        prevBtn.setVisibility(multi && idx > 0 ? View.VISIBLE : View.GONE);
        nextBtn.setVisibility(multi && idx < paths.size() - 1 ? View.VISIBLE : View.GONE);
        if (editImageBtn != null) editImageBtn.setVisibility(e.kind == FileKinds.IMAGE ? View.VISIBLE : View.GONE);
        switch (e.kind) {
            case FileKinds.IMAGE: showImage(f); break;
            case FileKinds.VIDEO: showVideo(f); break;
            case FileKinds.AUDIO: showAudio(f, e); break;
            case FileKinds.PDF: showPdf(f); break;
            default: showText(f);
        }
        bar.bringToFront();
        prevBtn.bringToFront();
        nextBtn.bringToFront();
    }

    // ---------------- image ----------------
    void showImage(final File f) {
        ToxiMotion.attach(stage);
        ToxiMotion.setActive(stage, true);
        final ZoomImage z = new ZoomImage(this);
        stage.addView(z, new FrameLayout.LayoutParams(-1, -1));
        final int my = idx;
        io.execute(() -> {
            final Bitmap bm = decode(f, 2600);
            h.post(() -> {
                if (destroyed || my != idx) { if (bm != null && !bm.isRecycled()) bm.recycle(); return; }
                ToxiMotion.setActive(stage, false);
                if (bm == null) ToxiVisualSystem.toast(this, "Can't display this image");
                else {
                    currentImageBitmap = bm;
                    z.setImageBitmap(bm);
                }
            });
        });
    }

    // Built-in lightweight image editor: non-destructive preview, crop presets, rotate/flip,
    // grayscale and brightness; saving creates a new JPEG next to the original.
    void openImageEditor() {
        if (currentImageBitmap == null || currentImageBitmap.isRecycled()) { ToxiVisualSystem.toast(this, "Image is still loading"); return; }
        final Bitmap original = currentImageBitmap;
        final Bitmap[] working = { original.copy(Bitmap.Config.ARGB_8888, true) };
        final ImageView preview = new ImageView(this);
        preview.setImageBitmap(working[0]);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)));
        panel.addView(preview, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(230))));
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(tools, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48))));
        final float[] brightness = {0f};
        final boolean[] gray = {false};
        final int[] crop = {0}; // 0 original, 1 square, 2 4:3, 3 16:9
        final int[] rotation = {0};
        final boolean[] flip = {false};
        final Runnable render = () -> {
            Bitmap b = original.copy(Bitmap.Config.ARGB_8888, true);
            if (rotation[0] != 0) { Matrix rm=new Matrix(); rm.postRotate(rotation[0]); Bitmap rb=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),rm,true); b.recycle(); b=rb; }
            if (crop[0] != 0) {
                float ratio = crop[0] == 1 ? 1f : crop[0] == 2 ? 4f/3f : 16f/9f;
                int w=b.getWidth(), h=b.getHeight(); float ar=(float)w/h;
                int x=0,y=0,cw=w,ch=h;
                if (ar > ratio) { cw=Math.max(1,(int)(h*ratio)); x=(w-cw)/2; }
                else { ch=Math.max(1,(int)(w/ratio)); y=(h-ch)/2; }
                Bitmap c=Bitmap.createBitmap(b,x,y,cw,ch); if(c!=b) b.recycle(); b=c;
            }
            Matrix matrix = new Matrix(); matrix.postRotate(0); if(flip[0]) matrix.postScale(-1,1);
            if(flip[0]) { Bitmap c=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true); b.recycle(); b=c; }
            if (brightness[0] != 0f || gray[0]) {
                Bitmap out=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(out); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                ColorMatrix cm=new ColorMatrix(); if(gray[0]) cm.setSaturation(0f);
                float v=brightness[0]*255f;
                ColorMatrix offset=new ColorMatrix(new float[]{1,0,0,0,v, 0,1,0,0,v, 0,0,1,0,v, 0,0,0,1,0});
                if(gray[0]) { cm.postConcat(offset); paint.setColorFilter(new ColorMatrixColorFilter(cm)); }
                else paint.setColorFilter(new ColorMatrixColorFilter(offset));
                canvas.drawBitmap(b,0,0,paint); b.recycle(); b=out;
            }
            if (working[0] != null && working[0] != original && !working[0].isRecycled()) working[0].recycle();
            working[0]=b; preview.setImageBitmap(b);
        };
        String[] labels={"Rotate 90°","Flip","Crop","B&W"};
        for(int i=0;i<labels.length;i++) {
            final int action=i; TextView btn=ToxiVisualSystem.text(this,labels[i],11,ToxiVisualSystem.TEXT_PRIMARY,true); btn.setGravity(Gravity.CENTER);
            btn.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)),0,ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)),0); btn.setBackground(ToxiVisualSystem.surface(14,true));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,ToxiVisualSystem.dp(ToxiThemeEngine.spacing(38)),1f); bp.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)),0,ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)),0); tools.addView(btn,bp);
            btn.setOnClickListener(v->{
                if(action==0){ rotation[0]=(rotation[0]+90)%360; render.run(); }
                else if(action==1){flip[0]=!flip[0]; render.run();}
                else if(action==2){ ToxiVisualSystem.dialogBuilder(this).setTitle("Crop ratio").setSingleChoiceItems(new String[]{"Original","Square 1:1","Portrait 4:3","Wide 16:9"},crop[0],(d,which)->{crop[0]=which;d.dismiss();render.run();}).setNegativeButton("Cancel",null).show(); }
                else {gray[0]=!gray[0];render.run();}
            });
        }
        TextView brightLabel=ToxiVisualSystem.text(this,"Brightness",12,ToxiVisualSystem.TEXT_PRIMARY,true); panel.addView(brightLabel);
        SeekBar bright=new SeekBar(this); ToxiVisualSystem.styleSeekBar(bright); bright.setMax(200); bright.setProgress(100); panel.addView(bright,new LinearLayout.LayoutParams(-1,ToxiVisualSystem.dp(ToxiThemeEngine.spacing(36))));
        bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u){brightness[0]=(p-100)/100f;render.run();}} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}});
        ToxiVisualSystem.dialogBuilder(this).setTitle("Image editor").setView(panel).setNegativeButton("Cancel",(d,w)->{if(working[0]!=null&&!working[0].isRecycled())working[0].recycle();}).setNeutralButton("Reset",(d,w)->{if(working[0]!=null&&!working[0].isRecycled())working[0].recycle();currentImageBitmap=original;show();}).setPositiveButton("Save copy",(d,w)->saveEditedImage(working[0])).show();
    }

    void saveEditedImage(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || paths == null || idx < 0 || idx >= paths.size()) return;
        final File src = new File(paths.get(idx));
        final File parent = src.getParentFile();
        if (parent == null || !parent.isDirectory()) { ToxiVisualSystem.toast(this, "The original folder is unavailable"); return; }
        final String name = src.getName();
        final int dot = name.lastIndexOf('.');
        final String base = dot > 0 ? name.substring(0, dot) : name;
        io.execute(() -> {
            File pending = null;
            String savedName = null;
            try {
                pending = new File(parent, ".toxifiles-image-" + java.util.UUID.randomUUID() + ".part");
                if (!pending.createNewFile()) throw new java.io.IOException("Unable to reserve temporary image output");
                try (FileOutputStream fos = new FileOutputStream(pending, false)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos)) throw new java.io.IOException("JPEG encoding failed");
                    fos.flush();
                    fos.getFD().sync();
                }
                for (int suffix = 1; suffix < 10000; suffix++) {
                    File candidate = new File(parent, base + "_edited" + (suffix == 1 ? "" : suffix) + ".jpg");
                    try { java.nio.file.Files.move(pending.toPath(), candidate.toPath()); savedName = candidate.getName(); pending = null; break; }
                    catch (java.nio.file.FileAlreadyExistsException collision) { /* Try the next unique name without replacing user data. */ }
                }
                if (savedName == null) throw new java.io.IOException("Unable to reserve a unique edited-image filename");
            } catch (Exception ex) {
                if (pending != null) pending.delete();
            }
            final String result = savedName;
            h.post(() -> { if (!destroyed) ToxiVisualSystem.toast(this, result == null ? "Couldn't save beside original. Check folder write permission." : "Saved copy: " + result); });
        });
    }

    static Bitmap decode(File f, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > max) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b == null) return null;
            int rot = 0;
            try {
                int ori = new ExifInterface(f.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            } catch (Exception ignored) { }
            if (rot != 0) {
                Bitmap original = b;
                Matrix m = new Matrix();
                m.postRotate(rot);
                b = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), m, true);
                if (b != original && !original.isRecycled()) original.recycle();
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    static final class ZoomImage extends androidx.appcompat.widget.AppCompatImageView {
        final Matrix m = new Matrix();
        final ScaleGestureDetector sg;
        final GestureDetector gd;
        float minS = 1f;

        ZoomImage(android.content.Context c) {
            super(c);
            setScaleType(ScaleType.MATRIX);
            sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    float cur = scale();
                    float ns = Math.max(minS, Math.min(cur * d.getScaleFactor(), minS * 8f));
                    float f = ns / cur;
                    m.postScale(f, f, d.getFocusX(), d.getFocusY());
                    fix();
                    setImageMatrix(m);
                    return true;
                }
            });
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                    m.postTranslate(-dx, -dy);
                    fix();
                    setImageMatrix(m);
                    return true;
                }
                @Override public boolean onDoubleTap(MotionEvent e) {
                    float cur = scale();
                    float target = cur > minS * 1.5f ? minS : minS * 3f;
                    float f = target / cur;
                    m.postScale(f, f, e.getX(), e.getY());
                    fix();
                    setImageMatrix(m);
                    return true;
                }
            });
        }

        float scale() { float[] v = new float[9]; m.getValues(v); return v[Matrix.MSCALE_X]; }

        void fit() {
            Drawable d = getDrawable();
            if (d == null || getWidth() == 0) return;
            float dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
            float s = Math.min(getWidth() / dw, getHeight() / dh);
            minS = s;
            m.reset();
            m.postScale(s, s);
            m.postTranslate((getWidth() - dw * s) / 2f, (getHeight() - dh * s) / 2f);
            setImageMatrix(m);
        }

        void fix() {
            Drawable d = getDrawable();
            if (d == null) return;
            RectF r = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            m.mapRect(r);
            float dx = 0, dy = 0;
            if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
            else if (r.left > 0) dx = -r.left;
            else if (r.right < getWidth()) dx = getWidth() - r.right;
            if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
            else if (r.top > 0) dy = -r.top;
            else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
            m.postTranslate(dx, dy);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); fit(); }

        @Override public void setImageBitmap(Bitmap b) { super.setImageBitmap(b); fit(); }

        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            sg.onTouchEvent(e);
            if (!sg.isInProgress()) gd.onTouchEvent(e);
            return true;
        }
    }

    void advanceToNextVideo() {
        if (destroyed || paths == null || idx >= paths.size() - 1) return;
        for (int next = idx + 1; next < paths.size(); next++) {
            if (new Entry(new File(paths.get(next))).kind == FileKinds.VIDEO) {
                idx = next;
                show();
                return;
            }
        }
        // No later video in the current queue; leave the completed video stopped.
    }

    // ---------------- video ----------------
    // TextureView keeps video frames in the normal view composition layer. This avoids
    // SurfaceView/Z-order rendering failures where audio plays but the video stays black.
    void showVideo(File f) {
        ToxiMotion.attach(stage);
        ToxiMotion.setActive(stage, true);
        enterVideoImmersiveMode();
        // TextureView does not support background drawables on Android; put the black
        // fallback on its FrameLayout parent instead of the TextureView itself.
        stage.setBackground(ToxiVisualSystem.auroraBackground());
        final TextureView texture = new TextureView(this);
        videoTexture = texture;
        // Keep the video texture opaque; some vendor renderers show audio-only playback
        // when MediaPlayer is attached to a translucent TextureView.
        texture.setOpaque(true);
        texture.setKeepScreenOn(true);
        FrameLayout.LayoutParams videoLp = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
        stage.addView(texture, videoLp);

        LinearLayout dock = new LinearLayout(this);
        videoDock = dock;
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)));
        dock.setBackground(ToxiVisualSystem.surface(24, true));
        dock.setElevation(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));

        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout primaryRow = new LinearLayout(this);
        primaryRow.setGravity(Gravity.CENTER);
        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setGravity(Gravity.CENTER);
        dock.addView(seekRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
        dock.addView(primaryRow, rowParams);
        LinearLayout.LayoutParams secondRowParams = new LinearLayout.LayoutParams(-1, -2);
        secondRowParams.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
        dock.addView(secondaryRow, secondRowParams);

        TextView playbackToggle = videoChip("Play", true);
        TextView currentTimeTv = ToxiVisualSystem.text(this, "0:00", 11, ToxiVisualSystem.TEXT_PRIMARY, false);
        currentTimeTv.setGravity(Gravity.CENTER);
        currentTimeTv.setMinWidth(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(36)));
        TextView durationTv = ToxiVisualSystem.text(this, "0:00", 11, ToxiVisualSystem.TEXT_SECONDARY, false);
        durationTv.setGravity(Gravity.CENTER);
        durationTv.setMinWidth(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(36)));
        ToxiWaveSeekBar seekBar = new ToxiWaveSeekBar(this);
        seekBar.setSquiggleColors(ToxiVisualSystem.signalTextColor(), ToxiVisualSystem.EDGE_STROKE);
        seekBar.setMax(1);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(34)), 1f);
        seekParams.leftMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
        seekParams.rightMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
        seekRow.addView(playbackToggle, new LinearLayout.LayoutParams(-2, -2));
        seekRow.addView(currentTimeTv, new LinearLayout.LayoutParams(-2, -2));
        seekRow.addView(seekBar, seekParams);
        seekRow.addView(durationTv, new LinearLayout.LayoutParams(-2, -2));
        playbackToggle.setOnClickListener(v -> {
            MediaPlayer player = videoPlayer;
            if (player == null) return;
            try {
                if (player.isPlaying()) { player.pause(); videoUserWantsPlayback = false; setVideoPlayAnimation(playbackToggle, false); }
                else { player.start(); videoUserWantsPlayback = true; setVideoPlayAnimation(playbackToggle, true); }
            } catch (IllegalStateException ignored) { }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { seekingVideo = true; }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTimeTv.setText(formatVideoTime(progress));
                    int duration = bar.getMax();
                        }
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                MediaPlayer player = videoPlayer;
                if (player != null) try { player.seekTo(bar.getProgress()); } catch (IllegalStateException ignored) { }
                seekingVideo = false;
            }
        });

        TextView speed = videoChip("Speed 1.0×", true);
        speed.setContentDescription("Change video playback speed");
        speed.setOnClickListener(v -> {
            if (videoSpeed == 1.0f) videoSpeed = 1.25f;
            else if (videoSpeed == 1.25f) videoSpeed = 1.5f;
            else if (videoSpeed == 1.5f) videoSpeed = 2.0f;
            else if (videoSpeed == 2.0f) videoSpeed = 0.75f;
            else videoSpeed = 1.0f;
            speed.setText("Speed " + videoSpeed + "×");
            try {
                if (videoPlayer != null) videoPlayer.setPlaybackParams(new PlaybackParams().setSpeed(videoSpeed));
                ToxiVisualSystem.toast(this, "Playback speed: " + videoSpeed + "×");
            } catch (Exception ex) { ToxiVisualSystem.toast(this, "Playback speed isn't supported for this video"); }
        });

        TextView repeat = videoChip(videoLoop ? "Repeat on" : "Repeat off", false);
        repeat.setContentDescription("Toggle video repeat");
        repeat.setOnClickListener(v -> {
            videoLoop = !videoLoop;
            repeat.setText(videoLoop ? "Repeat on" : "Repeat off");
            repeat.setBackground(videoLoop ? ToxiVisualSystem.accentSurface(18) : ToxiVisualSystem.surface(18, false));
            try { if (videoPlayer != null) videoPlayer.setLooping(videoLoop); } catch (Exception ignored) { }
        });

        TextView rotate = videoChip("Rotate", false);
        rotate.setOnClickListener(v -> {
            int next = getRequestedOrientation() == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            setRequestedOrientation(next);
        });

        TextView pip = videoChip("PiP", false);
        pip.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    android.app.PictureInPictureParams params = new android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(new android.util.Rational(16, 9)).build();
                    // Set this before requesting PiP: onPause may run before Android's PiP-mode callback, and must not stop the player during that transition.
                    videoPipRequested = true;
                    if (enterPictureInPictureMode(params)) {
                        dock.setVisibility(View.GONE);
                        if (bar != null) bar.setVisibility(View.GONE);
                        if (prevBtn != null) prevBtn.setVisibility(View.GONE);
                        if (nextBtn != null) nextBtn.setVisibility(View.GONE);
                    } else {
                        videoPipRequested = false;
                    }
                } catch (Exception ex) { videoPipRequested = false; ToxiVisualSystem.toast(this, "Picture-in-Picture is unavailable on this device"); }
            } else ToxiVisualSystem.toast(this, "Picture-in-Picture requires Android 8 or newer");
        });

        TextView lock = videoChip("Lock", false);
        lock.setOnClickListener(v -> setVideoControlsLocked(!videoControlsLocked, dock, lock));
        addVideoChip(primaryRow, speed, true);
        addVideoChip(primaryRow, repeat, true);
        addVideoChip(primaryRow, rotate, true);
        addVideoChip(secondaryRow, pip, true);
        addVideoChip(secondaryRow, lock, true);

        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockParams.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)));
        stage.addView(dock, dockParams);

        // MX Player-style touch level indicators: left side controls screen brightness,
        // right side controls media volume. They appear during vertical swipe gestures.
        final LevelSlider brightnessSlider = new LevelSlider(this, "Brightness", 0.5f);
        final LevelSlider volumeSlider = new LevelSlider(this, "Volume", getMediaVolumeFraction());
        LinearLayout brightnessPanel = levelPanel(brightnessSlider, ToxiIcons.SUN);
        LinearLayout volumePanel = levelPanel(volumeSlider, ToxiIcons.VOLUME);
        FrameLayout.LayoutParams leftLevelLp = new FrameLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(58)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(210)), Gravity.CENTER_VERTICAL | Gravity.LEFT);
        leftLevelLp.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0, 0, 0);
        FrameLayout.LayoutParams rightLevelLp = new FrameLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(58)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(210)), Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        rightLevelLp.setMargins(0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0);
        brightnessPanel.setVisibility(View.GONE);
        volumePanel.setVisibility(View.GONE);
        stage.addView(brightnessPanel, leftLevelLp);
        stage.addView(volumePanel, rightLevelLp);
        brightnessSlider.setOnLevelChanged(level -> {
            android.view.WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.screenBrightness = Math.max(0.05f, Math.min(1f, level));
            getWindow().setAttributes(attrs);
        });
        volumeSlider.setOnLevelChanged(level -> {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level * max), 0);
        });

        final GestureDetector videoGestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!videoControlsLocked && videoTexture != null) {
                    if (dock.getVisibility() == View.VISIBLE) {
                        dock.setVisibility(View.GONE); bar.setVisibility(View.GONE);
                        if (prevBtn != null) prevBtn.setVisibility(View.GONE);
                        if (nextBtn != null) nextBtn.setVisibility(View.GONE);
                    } else {
                        dock.setVisibility(View.VISIBLE); bar.setVisibility(View.VISIBLE);
                        if (prevBtn != null) prevBtn.setVisibility(paths != null && paths.size() > 1 && idx > 0 ? View.VISIBLE : View.GONE);
                        if (nextBtn != null) nextBtn.setVisibility(paths != null && paths.size() > 1 && idx < paths.size() - 1 ? View.VISIBLE : View.GONE);
                    }
                }
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) {
                MediaPlayer player = videoPlayer;
                if (player == null || videoControlsLocked) return true;
                try {
                    int delta = event.getX() < texture.getWidth() / 2f ? -10000 : 10000;
                    int target = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + delta));
                    player.seekTo(target);
                    ToxiVisualSystem.toast(ViewerActivity.this, delta < 0 ? "−10 seconds" : "+10 seconds");
                } catch (IllegalStateException ignored) { }
                return true;
            }
            @Override public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                if (first == null || videoPlayer == null || videoControlsLocked || Math.abs(dy) < Math.abs(dx)) return false;
                float delta = -dy / Math.max(1f, texture.getHeight());
                if (first.getX() < texture.getWidth() / 2f) {
                    android.view.WindowManager.LayoutParams attrs = getWindow().getAttributes();
                    float currentBrightness = attrs.screenBrightness < 0 ? 0.5f : attrs.screenBrightness;
                    float next = Math.max(0.05f, Math.min(1f, currentBrightness + delta));
                    attrs.screenBrightness = next;
                    getWindow().setAttributes(attrs);
                    brightnessSlider.setLevel(next);
                    brightnessPanel.setVisibility(View.VISIBLE);
                    volumePanel.setVisibility(View.GONE);
                } else {
                    float next = Math.max(0f, Math.min(1f, getMediaVolumeFraction() + delta));
                    volumeSlider.setLevel(next);
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(next * max), 0);
                    volumePanel.setVisibility(View.VISIBLE);
                    brightnessPanel.setVisibility(View.GONE);
                }
                if (hideLevelOverlays != null) levelOverlayHandler.removeCallbacks(hideLevelOverlays);
                hideLevelOverlays = () -> { brightnessPanel.setVisibility(View.GONE); volumePanel.setVisibility(View.GONE); };
                levelOverlayHandler.postDelayed(hideLevelOverlays, 1800L);
                return true;
            }
        });
        texture.setOnTouchListener((view, event) -> {
            if (videoControlsLocked && event.getAction() == MotionEvent.ACTION_UP) {
                setVideoControlsLocked(false, dock, lock);
                return true;
            }
            return videoGestures.onTouchEvent(event);
        });

        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                try {
                    if (destroyed || videoTexture != texture) return;
                    // TextureView can lose and recreate its SurfaceTexture when the Activity
                    // moves between fullscreen, PiP and foreground. Reattach the prepared
                    // player instead of leaving audio running against a dead video surface.
                    if (videoPlayer != null && videoPrepared) {
                        if (videoSurface != null) {
                            try { videoSurface.release(); } catch (RuntimeException ignored) { }
                            videoSurface = null;
                        }
                        Surface replacement = new Surface(surfaceTexture);
                        videoSurface = replacement;
                        videoPlayer.setSurface(replacement);
                        if (videoPendingPosition > 0) {
                            try { videoPlayer.seekTo(videoPendingPosition); } catch (RuntimeException ignored) { }
                            videoPendingPosition = 0;
                        }
                        if (videoResumeAfterSurface && videoUserWantsPlayback) {
                            try { videoPlayer.start(); } catch (RuntimeException ignored) { }
                        }
                        videoResumeAfterSurface = false;
                        fitVideoTexture();
                        return;
                    }
                    MediaPlayer player = new MediaPlayer();
                    videoPlayer = player;
                    // Set the data source before attaching the TextureView surface. This order
                    // is more compatible with Android media implementations across vendors.
                    player.setDataSource(f.getAbsolutePath());
                    Surface output = new Surface(surfaceTexture);
                    videoSurface = output;
                    player.setSurface(output);
                    player.setOnPreparedListener(prepared -> {
                        if (destroyed || videoPlayer != prepared) return;
                        try {
                            try { prepared.setPlaybackParams(new PlaybackParams().setSpeed(videoSpeed)); } catch (Exception ignored) { }
                            try { prepared.setLooping(videoLoop); } catch (Exception ignored) { }
                            videoPrepared = true;
                            ToxiMotion.setActive(stage, false);
                            videoUserWantsPlayback = true;
                            int duration = prepared.getDuration();
                            seekBar.setMax(Math.max(1, duration));
                            durationTv.setText(formatVideoTime(duration));
                            int saved = getPreferences(MODE_PRIVATE).getInt("video_pos_" + f.getAbsolutePath(), 0);
                            int initial = saved > 0 && saved < duration - 3000 ? saved : 0;
                            if (initial > 0) prepared.seekTo(initial);
                            videoWidth = prepared.getVideoWidth();
                            videoHeight = prepared.getVideoHeight();
                            fitVideoTexture();
                            prepared.start();
                            setVideoPlayAnimation(playbackToggle, true);
                            if (videoTicker != null) h.removeCallbacks(videoTicker);
                            videoTicker = new Runnable() {
                                @Override public void run() {
                                    MediaPlayer active = videoPlayer;
                                    if (destroyed || active == null) return;
                                    try {
                                        if (!seekingVideo) {
                                            int position = active.getCurrentPosition();
                                            seekBar.setProgress(position);
                                            currentTimeTv.setText(formatVideoTime(position));
                                        }
                                        seekBar.setPlaying(active.isPlaying());
                                        int durationNow = active.getDuration();
                                        int positionNow = active.getCurrentPosition();
                                        boolean playingNow = active.isPlaying();
                                        playbackToggle.setText(playingNow ? "Pause" : "Play");
                                        h.postDelayed(this, 400);
                                    } catch (RuntimeException ex) {
                                        android.util.Log.w("LiquidFiles", "Video progress update stopped", ex);
                                    }
                                }
                            };
                            h.post(videoTicker);
                        } catch (RuntimeException ex) {
                            android.util.Log.e("LiquidFiles", "Video preparation callback failed", ex);
                            ToxiVisualSystem.toast(ViewerActivity.this, "Video playback failed. Try Open with.");
                            releaseVideoPlayer(prepared);
                        }
                    });
                    player.setOnVideoSizeChangedListener((changed, videoW, videoH) -> {
                        videoWidth = videoW;
                        videoHeight = videoH;
                        fitVideoTexture();
                    });
                    player.setOnInfoListener((bufferingPlayer, what, extra) -> {
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) ToxiMotion.setActive(stage, true);
                        else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) ToxiMotion.setActive(stage, false);
                        return false;
                    });
                    player.setOnCompletionListener(completed -> { if (!videoLoop) advanceToNextVideo(); });
                    player.setOnErrorListener((failed, what, extra) -> {
                        android.util.Log.e("LiquidFiles", "MediaPlayer error what=" + what + " extra=" + extra);
                        ToxiVisualSystem.toast(ViewerActivity.this, "This video can't be played here. Try Open with.");
                        releaseVideoPlayer(failed);
                        return true;
                    });
                    player.prepareAsync();
                } catch (Exception ex) {
                    android.util.Log.e("LiquidFiles", "Video surface setup failed", ex);
                    if (videoPlayer != null) releaseVideoPlayer(videoPlayer);
                    ToxiMotion.setActive(stage, false);
                    ToxiVisualSystem.toast(ViewerActivity.this, "Unable to render this video. Try Open with.");
                }
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { fitVideoTexture(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                MediaPlayer active = videoPlayer;
                if (active != null && videoPrepared) {
                    try {
                        videoResumeAfterSurface = active.isPlaying() || (videoPipRequested && videoUserWantsPlayback);
                        videoPendingPosition = active.getCurrentPosition();
                        if (paths != null && idx >= 0 && idx < paths.size()) {
                            getPreferences(MODE_PRIVATE).edit().putInt("video_pos_" + paths.get(idx), videoPendingPosition).apply();
                        }
                        active.pause();
                        active.setSurface(null);
                    } catch (RuntimeException ex) {
                        android.util.Log.w("LiquidFiles", "Detaching video surface failed", ex);
                    }
                }
                if (videoSurface != null) {
                    try { videoSurface.release(); } catch (RuntimeException ignored) { }
                    videoSurface = null;
                }
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
        });
    }

    // Preserve the source video's aspect ratio inside the available TextureView bounds.
    // TextureView initially stretches the buffer to its bounds; this matrix corrects that
    // scaling and letterboxes instead of distorting the picture in landscape.
    private void fitVideoTexture() {
        TextureView texture = videoTexture;
        if (texture == null || videoWidth <= 0 || videoHeight <= 0) return;
        int viewWidth = texture.getWidth();
        int viewHeight = texture.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;
        float scaleX = (float) viewWidth / (float) videoWidth;
        float scaleY = (float) viewHeight / (float) videoHeight;
        float fit = Math.min(scaleX, scaleY);
        float correctedX = fit / scaleX;
        float correctedY = fit / scaleY;
        Matrix matrix = new Matrix();
        matrix.setScale(correctedX, correctedY, viewWidth / 2f, viewHeight / 2f);
        texture.setTransform(matrix);
    }

    private void releaseVideoPlayer(MediaPlayer player) {
        if (player == null) return;
        if (videoPlayer == player) {
            videoPrepared = false; videoResumeAfterSurface = false; videoUserWantsPlayback = false; videoPendingPosition = 0;
        }
        if (videoTicker != null) { h.removeCallbacks(videoTicker); videoTicker = null; }
        if (videoPlayer == player) videoPlayer = null;
        try { player.setOnPreparedListener(null); player.setOnCompletionListener(null); player.setOnErrorListener(null); } catch (RuntimeException ignored) { }
        try { player.release(); } catch (RuntimeException ignored) { }
        if (videoSurface != null) {
            try { videoSurface.release(); } catch (RuntimeException ignored) { }
            videoSurface = null;
        }
    }

    String formatVideoTime(int milliseconds) {
        int totalSeconds = Math.max(0, milliseconds) / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d", totalSeconds / 60, seconds);
    }

    TextView videoChip(String label, boolean highlighted) {
        TextView chip = ToxiVisualSystem.text(this, label, 12, ToxiVisualSystem.TEXT_PRIMARY, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        chip.setBackground(highlighted ? ToxiVisualSystem.accentSurface(18) : ToxiVisualSystem.surface(18, false));
        ToxiVisualSystem.interactive(chip);
        return chip;
    }

    void addVideoChip(LinearLayout row, TextView chip, boolean weighted) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(weighted ? 0 : -2, -2, weighted ? 1f : 0f);
        p.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0);
        row.addView(chip, p);
    }

    void setVideoControlsLocked(boolean locked, LinearLayout dock, TextView lock) {
        videoControlsLocked = locked;
        dock.setVisibility(locked ? View.GONE : View.VISIBLE);
        bar.setVisibility(locked ? View.GONE : View.VISIBLE);
        if (prevBtn != null) prevBtn.setVisibility(locked ? View.GONE : (paths != null && paths.size() > 1 && idx > 0 ? View.VISIBLE : View.GONE));
        if (nextBtn != null) nextBtn.setVisibility(locked ? View.GONE : (paths != null && paths.size() > 1 && idx < paths.size() - 1 ? View.VISIBLE : View.GONE));
        lock.setText(locked ? "Unlock" : "Lock");
        ToxiVisualSystem.toast(this, locked ? "Controls hidden. Tap the video to unlock." : "Controls unlocked");
    }

    void enterVideoImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // Editable queue for the built-in player: select, reorder, or remove entries.
    void showQueueEditor() {
        if (paths == null || paths.isEmpty()) return;
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        scroll.addView(list);
        final android.app.AlertDialog dialog = ToxiVisualSystem.dialogBuilder(this)
            .setTitle("Playback queue · " + (idx + 1) + "/" + paths.size())
            .setView(scroll).setNegativeButton("Close", null).create();
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAllViews();
            for (int i = 0; i < paths.size(); i++) {
                final int position = i;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)));
                row.setBackground(ToxiVisualSystem.surface(14, i == idx));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.setMargins(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)));
                ImageView currentMark = new ImageView(this);
                currentMark.setImageDrawable(ToxiIcons.symbol(ToxiIcons.PLAY, ToxiVisualSystem.ACCENT));
                currentMark.setContentDescription(i == idx ? "Currently playing" : "Queued track");
                if (i != idx) currentMark.setVisibility(View.INVISIBLE);
                row.addView(currentMark, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20))));
                TextView name = ToxiVisualSystem.text(this, new File(paths.get(i)).getName(), 12.5f, i == idx ? ToxiVisualSystem.ACCENT : ToxiVisualSystem.TEXT_PRIMARY, i == idx);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
                name.setOnClickListener(view -> { idx = position; dialog.dismiss(); show(); });
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                ImageView up = queueAction(ToxiIcons.UP, "Move track up");
                up.setOnClickListener(view -> { if (position > 0) { java.util.Collections.swap(paths, position, position - 1); if (idx == position) idx--; else if (idx == position - 1) idx++; rebuild[0].run(); } });
                row.addView(up, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40))));
                ImageView down = queueAction(ToxiIcons.DOWN, "Move track down");
                down.setOnClickListener(view -> { if (position < paths.size() - 1) { java.util.Collections.swap(paths, position, position + 1); if (idx == position) idx++; else if (idx == position + 1) idx--; rebuild[0].run(); } });
                row.addView(down, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40))));
                ImageView remove = queueAction(ToxiIcons.CLOSE, "Remove track from queue");
                remove.setOnClickListener(view -> {
                    if (paths.size() <= 1) { ToxiVisualSystem.toast(this, "Keep at least one item in the queue"); return; }
                    boolean removingCurrent = position == idx;
                    paths.remove(position);
                    if (position < idx) idx--;
                    else if (removingCurrent) idx = Math.min(position, paths.size() - 1);
                    if (removingCurrent) { dialog.dismiss(); show(); }
                    else { dialog.setTitle("Playback queue · " + (idx + 1) + "/" + paths.size()); rebuild[0].run(); }
                });
                row.addView(remove, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40))));
                list.addView(row, rp);
            }
        };
        rebuild[0].run();
        dialog.show();
    }

    private void setCompoundIcon(TextView target, int symbol) {
        android.graphics.drawable.Drawable icon = ToxiIcons.symbol(symbol, ToxiVisualSystem.TEXT_PRIMARY);
        int size = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18));
        icon.setBounds(0, 0, size, size);
        target.setCompoundDrawablesRelative(icon, null, null, null);
        target.setCompoundDrawablePadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)));
        target.setGravity(Gravity.CENTER);
    }

    private ImageView queueAction(int symbol, String description) {
        ImageView view = new ImageView(this);
        view.setImageDrawable(ToxiIcons.symbol(symbol, ToxiVisualSystem.TEXT_PRIMARY));
        view.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)));
        view.setBackground(ToxiVisualSystem.surface(10, false));
        view.setContentDescription(description);
        ToxiVisualSystem.interactive(view);
        return view;
    }

    // ---------------- audio ----------------
    void showAudio(File f, Entry e) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(ToxiVisualSystem.surface(30, true));
        card.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)));
        card.setElevation(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        TextView nowPlaying = ToxiVisualSystem.text(this, "NOW PLAYING", 10.5f, ToxiVisualSystem.ACCENT, true);
        nowPlaying.setLetterSpacing(0.16f);
        nowPlaying.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)));
        card.addView(nowPlaying);
        final ImageView art = new ImageView(this);
        art.setImageDrawable(ToxiIcons.fileTile(FileKinds.AUDIO));
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(ToxiVisualSystem.surface(28, true));
        art.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        ToxiVisualSystem.clipRound(art, 28);
        card.addView(art, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(180)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(180))));
        TextView nm = ToxiVisualSystem.text(this, f.getName(), 18, ToxiVisualSystem.TEXT_PRIMARY, true);
        nm.setMaxLines(2);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nm.setGravity(Gravity.CENTER);
        nm.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), 0, 0);
        card.addView(nm, new LinearLayout.LayoutParams(-1, -2));
        final TextView meta = ToxiVisualSystem.text(this, "", 13, ToxiVisualSystem.TEXT_SECONDARY, false);
        meta.setGravity(Gravity.CENTER);
        meta.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)));
        card.addView(meta, new LinearLayout.LayoutParams(-1, -2));
        final ToxiWaveSeekBar sb = new ToxiWaveSeekBar(this);
        sb.setSquiggleColors(ToxiVisualSystem.ACCENT, ToxiVisualSystem.EDGE_STROKE);
        card.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        final TextView time = ToxiVisualSystem.text(this, "0:00 / 0:00", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        time.setContentDescription("Playback position and duration");
        time.setGravity(Gravity.CENTER);
        time.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)));
        card.addView(time, new LinearLayout.LayoutParams(-1, -2));

        // Keep playback options inside the built-in player; no separate player app is launched.
        LinearLayout modes = new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);
        modes.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)));
        final TextView repeat = ToxiVisualSystem.text(this, repeatLabel(), 12.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        final TextView shuffle = ToxiVisualSystem.text(this, audioShuffle ? "Shuffle: On" : "Shuffle", 12.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        setCompoundIcon(repeat, ToxiIcons.REPEAT);
        setCompoundIcon(shuffle, ToxiIcons.SHUFFLE);
        final TextView abRepeat = ToxiVisualSystem.text(this, "Set A", 12.5f, ToxiVisualSystem.TEXT_PRIMARY, true);
        setCompoundIcon(abRepeat, ToxiIcons.CLOCK);
        repeat.setGravity(Gravity.CENTER);
        shuffle.setGravity(Gravity.CENTER); abRepeat.setGravity(Gravity.CENTER);
        repeat.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        shuffle.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        abRepeat.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        repeat.setBackground(audioRepeatMode != 0 ? ToxiVisualSystem.accentSurface(20) : ToxiVisualSystem.surface(20, false));
        shuffle.setBackground(audioShuffle ? ToxiVisualSystem.accentSurface(20) : ToxiVisualSystem.surface(20, false));
        abRepeat.setBackground(ToxiVisualSystem.surface(20, false));
        abRepeat.setContentDescription("A-B loop is off. Tap to mark loop start A.");
        repeat.setContentDescription("Repeat mode " + repeatLabel());
        shuffle.setContentDescription("Shuffle playback " + (audioShuffle ? "on" : "off"));
        repeat.setOnClickListener(v -> {
            audioRepeatMode = (audioRepeatMode + 1) % 3;
            audioRepeat = audioRepeatMode == 1;
            repeat.setText(repeatLabel());
            repeat.setBackground(audioRepeatMode != 0 ? ToxiVisualSystem.accentSurface(20) : ToxiVisualSystem.surface(20, false));
            repeat.setContentDescription("Repeat mode " + repeatLabel());
            ToxiVisualSystem.feedback(v);
        });
        shuffle.setOnClickListener(v -> {
            audioShuffle = !audioShuffle;
            shuffle.setText(audioShuffle ? "Shuffle: On" : "Shuffle");
            shuffle.setBackground(audioShuffle ? ToxiVisualSystem.accentSurface(20) : ToxiVisualSystem.surface(20, false));
            shuffle.setContentDescription("Shuffle playback " + (audioShuffle ? "on" : "off"));
            ToxiVisualSystem.feedback(v);
        });
        abRepeat.setOnClickListener(v -> {
            if (mp == null) return;
            try {
                int position = mp.getCurrentPosition();
                if (audioLoopStartMs < 0) { audioLoopStartMs = position; audioLoopEndMs = -1; ToxiVisualSystem.toast(this, "Loop start A set at " + fmt(position)); }
                else if (audioLoopEndMs < 0) {
                    if (position <= audioLoopStartMs + 500) { ToxiVisualSystem.toast(this, "Set point B at least half a second after A"); return; }
                    audioLoopEndMs = position; ToxiVisualSystem.toast(this, "A–B loop enabled");
                } else { audioLoopStartMs = -1; audioLoopEndMs = -1; ToxiVisualSystem.toast(this, "A–B loop cleared"); }
                abRepeat.setText(audioLoopStartMs < 0 ? "Set A" : audioLoopEndMs < 0 ? "Set B" : "A–B On");
                abRepeat.setBackground(audioLoopEndMs >= 0 ? ToxiVisualSystem.accentSurface(20) : ToxiVisualSystem.surface(20, false));
                abRepeat.setContentDescription(audioLoopStartMs < 0 ? "A-B loop off. Tap to mark point A" : audioLoopEndMs < 0 ? "Point A set. Tap to mark point B" : "A-B loop enabled. Tap to clear loop");
            } catch (Exception ignored) { ToxiVisualSystem.toast(this, "A–B repeat is unavailable"); }
            ToxiVisualSystem.feedback(v);
        });
        ToxiVisualSystem.interactive(repeat); ToxiVisualSystem.interactive(shuffle); ToxiVisualSystem.interactive(abRepeat);
        TextView[] modeActions = {repeat, shuffle, abRepeat};
        for (TextView modeAction : modeActions) {
            modeAction.setSingleLine(true);
            modeAction.setEllipsize(android.text.TextUtils.TruncateAt.END);
            modeAction.setMinWidth(0);
            LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(0, -2, 1f);
            modeLp.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0);
            modes.addView(modeAction, modeLp);
        }
        card.addView(modes, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout extras = new LinearLayout(this);
        extras.setOrientation(LinearLayout.VERTICAL);
        extras.setGravity(Gravity.CENTER);
        extras.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)));
        LinearLayout extrasTop = new LinearLayout(this);
        LinearLayout extrasBottom = new LinearLayout(this);
        extrasTop.setGravity(Gravity.CENTER);
        extrasBottom.setGravity(Gravity.CENTER);
        TextView speedBtn = ToxiVisualSystem.text(this, "1.0× Speed", 12, ToxiVisualSystem.TEXT_PRIMARY, true);
        TextView sleepBtn = ToxiVisualSystem.text(this, "Sleep timer", 12, ToxiVisualSystem.TEXT_PRIMARY, true);
        TextView lyricsBtn = ToxiVisualSystem.text(this, "Lyrics", 12, ToxiVisualSystem.TEXT_PRIMARY, true);
        TextView eqBtn = ToxiVisualSystem.text(this, "EQ / Bass", 12, ToxiVisualSystem.TEXT_PRIMARY, true);
        TextView queueBtn = ToxiVisualSystem.text(this, "Queue", 12, ToxiVisualSystem.TEXT_PRIMARY, true);
        TextView[] extraButtons = {speedBtn, sleepBtn, lyricsBtn, eqBtn, queueBtn};
        for (int i = 0; i < extraButtons.length; i++) {
            TextView b = extraButtons[i];
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(42)));
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)));
            b.setBackground(ToxiVisualSystem.surface(16, false));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, -2, i < 3 ? 1f : 1f);
            ep.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(0)));
            if (i >= 3) ep.width = 0;
            (i < 3 ? extrasTop : extrasBottom).addView(b, ep);
            ToxiVisualSystem.interactive(b);
        }
        extras.addView(extrasTop, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams extrasBottomLp = new LinearLayout.LayoutParams(-1, -2);
        extrasBottomLp.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6));
        extras.addView(extrasBottom, extrasBottomLp);
        // Balance the second row's two actions around the center.
        extrasBottom.setGravity(Gravity.CENTER);
        speedBtn.setOnClickListener(v -> {
            audioSpeed = audioSpeed >= 2.0f ? 0.75f : audioSpeed == 1.0f ? 1.25f : audioSpeed == 1.25f ? 1.5f : audioSpeed == 1.5f ? 2.0f : 1.0f;
            speedBtn.setText(audioSpeed + "×");
            try { if (Build.VERSION.SDK_INT >= 23 && mp != null) mp.setPlaybackParams(new PlaybackParams().setSpeed(audioSpeed)); else ToxiVisualSystem.toast(this, "Playback speed requires Android 6 or newer"); }
            catch (Exception ex) { ToxiVisualSystem.toast(this, "Speed change is not supported for this file"); }
        });
        sleepBtn.setOnClickListener(v -> {
            String[] choices = {"Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes"};
            ToxiVisualSystem.dialogBuilder(this).setTitle("Sleep timer").setItems(choices, (d, which) -> {
                if (sleepStopper != null) sleepHandler.removeCallbacks(sleepStopper);
                sleepStopper = null;
                if (which > 0) {
                    long delay = which * 15L * 60L * 1000L;
                    sleepDeadlineElapsed = android.os.SystemClock.elapsedRealtime() + delay;
                    sleepStopper = () -> {
                        try { if (mp != null && mp.isPlaying()) mp.pause(); } catch (Exception ignored) {}
                        try { if (!BackgroundAudioService.activePath.isEmpty()) startService(new Intent(this, BackgroundAudioService.class).setAction(BackgroundAudioService.ACTION_PAUSE)); } catch (Exception ignored) {}
                        sleepDeadlineElapsed = 0L;
                    };
                    sleepHandler.postDelayed(sleepStopper, delay);
                    ToxiVisualSystem.toast(this, "Sleep timer set: " + choices[which]);
                } else { sleepDeadlineElapsed = 0L; ToxiVisualSystem.toast(this, "Sleep timer off"); }
            }).show();
        });
        lyricsBtn.setOnClickListener(v -> showLocalLyrics(f));
        eqBtn.setOnClickListener(v -> showAudioEffects());
        queueBtn.setOnClickListener(v -> {
            if (paths == null || paths.isEmpty()) { ToxiVisualSystem.toast(this, "No files in this playback list"); return; }
            showQueueEditor();
        });
        card.addView(extras, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout volumeHeader = new LinearLayout(this);
        volumeHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView volLabel = ToxiVisualSystem.text(this, "VOLUME", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        TextView volValue = ToxiVisualSystem.text(this, "100%", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, false);
        volumeHeader.addView(volLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        volumeHeader.addView(volValue, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams volumeHeaderLp = new LinearLayout.LayoutParams(-1, -2);
        volumeHeaderLp.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7));
        card.addView(volumeHeader, volumeHeaderLp);
        ToxiProgressSeekBar volume = new ToxiProgressSeekBar(this);
        volume.setProgressColors(ToxiVisualSystem.signalTextColor(), ToxiVisualSystem.EDGE_STROKE);
        audioVolume = getPreferences(MODE_PRIVATE).getInt("audio_volume", 100) / 100f;
        volume.setMax(100); volume.setProgress(Math.round(audioVolume * 100));
        volValue.setText(Math.round(audioVolume * 100) + "%");
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek, int value, boolean fromUser) { volValue.setText(value + "%"); if (fromUser) { audioVolume = value / 100f; getPreferences(MODE_PRIVATE).edit().putInt("audio_volume", value).apply(); if (mp != null) try { mp.setVolume(audioVolume, audioVolume); } catch (Exception ignored) {} } }
            public void onStartTrackingTouch(SeekBar seek) {} public void onStopTrackingTouch(SeekBar seek) {}
        });
        card.addView(volume, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(38))));

        LinearLayout ctl = new LinearLayout(this);
        ctl.setGravity(Gravity.CENTER);
        ImageView pv = ToxiVisualSystem.iconButton(this, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        final PlayAnimationImageView pp = new PlayAnimationImageView(this);
        pp.setPlaying(true);
        ImageView nx = ToxiVisualSystem.iconButton(this, ToxiIcons.NEXT, ToxiVisualSystem.TEXT_PRIMARY);
        pp.setBackground(ToxiVisualSystem.accentSurface(30));
        pv.setContentDescription("Previous track");
        pp.setContentDescription("Play or pause");
        nx.setContentDescription("Next track");
        pv.setOnClickListener(v -> { if (mp != null) { try { if (mp.getCurrentPosition() > 3000) { mp.seekTo(0); return; } } catch (Exception ignored) {} } go(-1); });
        nx.setOnClickListener(v -> go(1));
        ctl.addView(pv, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(52)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(52))));
        LinearLayout.LayoutParams ppl = new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(64)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(64)));
        ppl.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), 0);
        ctl.addView(pp, ppl);
        ctl.addView(nx, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(52)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(52))));
        LinearLayout.LayoutParams cll = new LinearLayout.LayoutParams(-1, -2);
        cll.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10));
        card.addView(ctl, cll);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        fp.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), 0);
        stage.addView(card, fp);
        ToxiMotion.attach(art);
        ToxiMotion.setActive(art, false);
        ToxiMotion.attach(pp);
        ToxiMotion.setActive(pp, false);

        final int my = idx;
        io.execute(() -> {
            String info = "";
            Bitmap cover = null;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(f.getAbsolutePath());
                String ar = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String al = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                info = (ar != null ? ar : "") + (ar != null && al != null ? " • " : "") + (al != null ? al : "");
                byte[] pic = r.getEmbeddedPicture();
                if (pic != null) cover = BitmapFactory.decodeByteArray(pic, 0, pic.length);
            } catch (Throwable ignored) { } finally { try { r.release(); } catch (Throwable ignored) { } }
            final String fi = info.isEmpty() ? ToxiVisualSystem.formatSize(e.len) : info;
            final Bitmap fc = cover;
            h.post(() -> { if (destroyed || my != idx) return; meta.setText(fi); if (fc != null) art.setImageBitmap(fc); });
        });

        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnPreparedListener(m -> {
                sb.setMax(m.getDuration());
                    int savedPosition = getPreferences(MODE_PRIVATE).getInt("audio_pos_" + f.getAbsolutePath(), 0);
                if (savedPosition > 0 && savedPosition < m.getDuration() - 3000) { try { m.seekTo(savedPosition); } catch (Exception ignored) {} }
                try { m.setVolume(audioVolume, audioVolume); } catch (Exception ignored) {}
                try { if (Build.VERSION.SDK_INT >= 23) m.setPlaybackParams(new PlaybackParams().setSpeed(audioSpeed)); } catch (Exception ignored) {}
                m.start();
                ToxiMotion.setActive(art, true);
                ToxiMotion.setActive(pp, true);
                initAudioEffects(m.getAudioSessionId());
                pp.setPlaying(true);
                startTicker(sb, time);
            });
            mp.setOnCompletionListener(m -> {
                if (audioRepeatMode == 1) {
                    try { m.seekTo(0); m.start(); ToxiMotion.setActive(art, true); ToxiMotion.setActive(pp, true); pp.setPlaying(true); }
                    catch (Exception ignored) { ToxiMotion.setActive(art, false); ToxiMotion.setActive(pp, false); pp.setPlaying(false); }
                } else if (paths.size() > 1 && audioShuffle) {
                    go(1);
                } else if (idx < paths.size() - 1) go(1);
                else if (audioRepeatMode == 2 && paths.size() > 0) { idx = 0; show(); }
                else { ToxiMotion.setActive(art, false); ToxiMotion.setActive(pp, false); pp.setPlaying(false); }
            });
            mp.setOnErrorListener((m, w, x) -> { ToxiVisualSystem.toast(this, "Can't play this audio file"); return true; });
            mp.prepareAsync();
        } catch (Exception ex) {
            ToxiVisualSystem.toast(this, "Can't play this audio file");
        }
        pp.setOnClickListener(v -> {
            if (mp == null) return;
            try {
                if (mp.isPlaying()) { mp.pause(); ToxiMotion.setActive(art, false); ToxiMotion.setActive(pp, false); pp.setPlaying(false); }
                else { mp.start(); ToxiMotion.setActive(art, true); ToxiMotion.setActive(pp, true); pp.setPlaying(true); }
            } catch (Exception ignored) { }
        });
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) {
                    int duration = s.getMax();
                            if (mp != null) try { mp.seekTo(p); } catch (Exception ignored) { }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
    }

    String repeatLabel() {
        if (audioRepeatMode == 1) return "Repeat One";
        if (audioRepeatMode == 2) return "Repeat All";
        return "Repeat Off";
    }

    void releaseAudioEffects() {
        try { if (equalizer != null) equalizer.release(); } catch (Exception ignored) { }
        try { if (bassBoost != null) bassBoost.release(); } catch (Exception ignored) { }
        equalizer = null; bassBoost = null;
    }

    void initAudioEffects(int sessionId) {
        releaseAudioEffects();
        try { equalizer = new Equalizer(0, sessionId); equalizer.setEnabled(true); } catch (Exception ignored) { equalizer = null; }
        try { bassBoost = new BassBoost(0, sessionId); bassBoost.setEnabled(true); } catch (Exception ignored) { bassBoost = null; }
    }

    void showAudioEffects() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(20)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        TextView note = ToxiVisualSystem.text(this, "Audio effects depend on Android/device support and may reset when the track changes.", 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        panel.addView(note);
        final SeekBar bass = new SeekBar(this); ToxiVisualSystem.styleSeekBar(bass); bass.setMax(1000);
        panel.addView(ToxiVisualSystem.text(this, "Bass boost", 13, ToxiVisualSystem.TEXT_PRIMARY, true)); panel.addView(bass);
        try { bass.setProgress(bassBoost == null ? 0 : bassBoost.getRoundedStrength()); } catch (Exception ignored) { }
        bass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean fromUser) { if (fromUser && bassBoost != null) try { bassBoost.setStrength((short)value); } catch (Exception ignored) { } }
            public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
        });
        final java.util.ArrayList<SeekBar> bands = new java.util.ArrayList<>();
        if (equalizer != null) {
            try {
                short count = equalizer.getNumberOfBands();
                short[] range = equalizer.getBandLevelRange();
                for (short i = 0; i < count; i++) {
                    final short band = i;
                    int center = equalizer.getCenterFreq(band) / 1000;
                    panel.addView(ToxiVisualSystem.text(this, center + " Hz", 12, ToxiVisualSystem.TEXT_SECONDARY, false));
                    SeekBar seek = new SeekBar(this); ToxiVisualSystem.styleSeekBar(seek); seek.setMax(range[1] - range[0]);
                    seek.setProgress(equalizer.getBandLevel(band) - range[0]);
                    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        public void onProgressChanged(SeekBar b, int value, boolean fromUser) { if (fromUser && equalizer != null) try { equalizer.setBandLevel(band, (short)(value + range[0])); } catch (Exception ignored) { } }
                        public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
                    });
                    bands.add(seek); panel.addView(seek);
                }
            } catch (Exception ignored) { }
        }
        if (equalizer == null && bassBoost == null) note.setText("This device or audio output does not expose equalizer/bass effects for this track.");
        ToxiVisualSystem.dialogBuilder(this).setTitle("Equalizer & bass").setView(panel).setPositiveButton("Done", null).show();
    }

    void showLocalLyrics(File audio) {
        String base = audio.getAbsolutePath();
        int dot = base.lastIndexOf('.');
        if (dot > base.lastIndexOf(File.separator)) base = base.substring(0, dot);
        File lrc = new File(base + ".lrc");
        File txt = new File(base + ".txt");
        File chosen = lrc.isFile() ? lrc : (txt.isFile() ? txt : null);
        if (chosen == null) { ToxiVisualSystem.toast(this, "No local .lrc or matching .txt lyrics found"); return; }
        io.execute(() -> {
            java.util.ArrayList<Long> times = new java.util.ArrayList<>();
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            StringBuilder plain = new StringBuilder();
            java.util.regex.Pattern stamp = java.util.regex.Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new FileInputStream(chosen), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null && plain.length() < 200000) {
                    plain.append(line).append('\n');
                    java.util.regex.Matcher matcher = stamp.matcher(line); java.util.ArrayList<Long> rowTimes = new java.util.ArrayList<>();
                    while (matcher.find()) {
                        long min = Long.parseLong(matcher.group(1)), sec = Long.parseLong(matcher.group(2));
                        String fraction = matcher.group(3); long ms = 0;
                        if (fraction != null) { if (fraction.length() == 1) ms = Long.parseLong(fraction) * 100; else if (fraction.length() == 2) ms = Long.parseLong(fraction) * 10; else ms = Long.parseLong(fraction.substring(0, 3)); }
                        rowTimes.add((min * 60 + sec) * 1000 + ms);
                    }
                    String lyric = line.replaceAll("\\[[^\\]]+\\]", "").trim();
                    if (!rowTimes.isEmpty() && !lyric.isEmpty()) for (Long t : rowTimes) { times.add(t); lines.add(lyric); }
                }
            } catch (Exception ignored) { }
            final String raw = plain.toString();
            final java.util.ArrayList<Long> ft = times; final java.util.ArrayList<String> fl = lines;
            h.post(() -> {
                if (isFinishing()) return;
                lyricTimes.clear(); lyricTimes.addAll(ft); lyricText.clear(); lyricText.addAll(fl);
                LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
                TextView current = ToxiVisualSystem.text(this, "Lyrics", 18, ToxiVisualSystem.TEXT_PRIMARY, true); current.setGravity(Gravity.CENTER); panel.addView(current, new LinearLayout.LayoutParams(-1, -2));
                TextView all = ToxiVisualSystem.text(this, raw, 13, ToxiVisualSystem.TEXT_SECONDARY, false); all.setTextIsSelectable(true);
                ScrollView scroll = new ScrollView(this); scroll.addView(all); panel.addView(scroll, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(300))));
                android.app.AlertDialog dialog = ToxiVisualSystem.dialogBuilder(this).setTitle(chosen.getName()).setView(panel).setPositiveButton("Close", null).create();
                dialog.setOnDismissListener(d -> { if (lyricTicker != null) { subtitleHandler.removeCallbacks(lyricTicker); lyricTicker = null; } });
                dialog.show();
                if (!lyricTimes.isEmpty()) {
                    lyricTicker = new Runnable() { @Override public void run() {
                        if (!dialog.isShowing()) return;
                        int pos = 0; try { if (mp != null) pos = mp.getCurrentPosition(); } catch (Exception ignored) { }
                        String active = lyricText.isEmpty() ? "" : lyricText.get(0);
                        for (int i = 0; i < lyricTimes.size(); i++) if (lyricTimes.get(i) <= pos) active = lyricText.get(i); else break;
                        current.setText(active); subtitleHandler.postDelayed(this, 300);
                    }};
                    subtitleHandler.post(lyricTicker);
                }
            });
        });
    }

    static String fmt(int ms) { int s = ms / 1000; return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60); }

    void startTicker(final SeekBar sb, final TextView time) {
        ticker = new Runnable() {
            @Override public void run() {
                if (mp == null || destroyed) return;
                try {
                    int p = mp.getCurrentPosition();
                    if (audioLoopStartMs >= 0 && audioLoopEndMs > audioLoopStartMs && p >= audioLoopEndMs && mp.isPlaying()) {
                        mp.seekTo(audioLoopStartMs); p = audioLoopStartMs;
                    }
                    sb.setProgress(p);
                    if (sb instanceof ToxiWaveSeekBar) ((ToxiWaveSeekBar) sb).setPlaying(mp.isPlaying());
                    int duration = mp.getDuration();
                            time.setText(fmt(p) + " / " + fmt(duration));
                    getPreferences(MODE_PRIVATE).edit().putInt("audio_pos_" + paths.get(idx), p).apply();
                } catch (Exception ignored) { }
                h.postDelayed(this, 400);
            }
        };
        h.post(ticker);
    }

    // ---------------- text ----------------
    void showText(final File f) {
        final ScrollView sv = new ScrollView(this);
        final TextView t = new TextView(this);
        t.setTextColor(ToxiVisualSystem.TEXT_PRIMARY);
        t.setTextSize(13);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(72)) + topInset, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(18)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)) + bottomInset);
        t.setText("Loading…");
        sv.addView(t, new FrameLayout.LayoutParams(-1, -2));
        stage.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        TextView edit = ToxiVisualSystem.text(this, "Edit text", 12, ToxiVisualSystem.TEXT_PRIMARY, true);
        edit.setGravity(Gravity.CENTER); edit.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)));
        edit.setBackground(ToxiVisualSystem.surface(16, true));
        FrameLayout.LayoutParams editLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        editLp.setMargins(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)) + topInset, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), 0);
        stage.addView(edit, editLp);
        edit.setOnClickListener(v -> openTextEditor(f));
        final int my = idx;
        io.execute(() -> {
            String s;
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[300 * 1024];
                int total = 0;
                while (total < buf.length) {
                    int n = in.read(buf, total, buf.length - total);
                    if (n < 0) break;
                    if (n == 0) continue;
                    total += n;
                }
                s = new String(buf, 0, total, "UTF-8");
                if (f.length() > total) s += "\n\n… file truncated (showing first 300 KB)";
            } catch (Exception ex) { s = "Can't read this file."; }
            final String fs = s;
            h.post(() -> { if (!destroyed && my == idx) t.setText(fs); });
        });
    }

    void openTextEditor(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            ToxiVisualSystem.toast(this, "This text file is unavailable");
            return;
        }
        Intent advanced = new Intent(this, ToxiToolsActivity.class);
        advanced.putExtra(ToxiToolsActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        startActivity(advanced);
    }

    // ---------------- pdf ----------------
    private static void closePdfResources(PdfRenderer renderer, ParcelFileDescriptor descriptor) {
        if (renderer != null) {
            synchronized (renderer) {
                try { renderer.close(); } catch (Exception ignored) { }
            }
        }
        if (descriptor != null) {
            try { descriptor.close(); } catch (Exception ignored) { }
        }
    }

    private void closePdfResourcesAsync(PdfRenderer renderer, ParcelFileDescriptor descriptor) {
        if (renderer == null && descriptor == null) return;
        try {
            io.execute(() -> closePdfResources(renderer, descriptor));
        } catch (RuntimeException rejected) {
            closePdfResources(renderer, descriptor);
        }
    }

    void showPdf(File f) {
        final long token = ++pdfGeneration;
        io.execute(() -> {
            if (destroyed || token != pdfGeneration) return;
            ParcelFileDescriptor openedDescriptor = null;
            PdfRenderer openedRenderer = null;
            int pageCount = 0;
            String failure = null;
            try {
                if (f == null || !f.isFile() || !f.canRead() || f.length() <= 0L) {
                    failure = "This PDF is missing, empty, or unreadable";
                } else {
                    openedDescriptor = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                    openedRenderer = new PdfRenderer(openedDescriptor);
                    pageCount = openedRenderer.getPageCount();
                    if (pageCount <= 0) failure = "This PDF contains no readable pages";
                }
            } catch (Exception openFailure) {
                failure = "Can't open this PDF here — try \"Open with\"";
            }
            if (failure != null) {
                closePdfResources(openedRenderer, openedDescriptor);
                final String message = failure;
                h.post(() -> {
                    if (!destroyed && token == pdfGeneration) ToxiVisualSystem.toast(this, message);
                });
                return;
            }
            final PdfRenderer renderer = openedRenderer;
            final ParcelFileDescriptor descriptor = openedDescriptor;
            final int count = pageCount;
            if (destroyed || token != pdfGeneration) {
                closePdfResources(renderer, descriptor);
                return;
            }
            boolean posted = h.post(() -> {
                if (destroyed || token != pdfGeneration) {
                    closePdfResourcesAsync(renderer, descriptor);
                    return;
                }
                pdf = renderer;
                pfd = descriptor;
                showPdfPages(renderer, count, token);
            });
            if (!posted) closePdfResources(renderer, descriptor);
        });
    }

    private void showPdfPages(PdfRenderer r, int count, long token) {
        final int w = Math.max(1, Math.min(2400, stage.getWidth() > 0 ? stage.getWidth() : getResources().getDisplayMetrics().widthPixels));
        final int cacheBudgetBytes = (int)Math.max(4L * 1024L * 1024L, Runtime.getRuntime().maxMemory() / 8L);
        final LruCache<Integer, Bitmap> cache = new LruCache<Integer, Bitmap>(cacheBudgetBytes) {
            @Override protected int sizeOf(Integer key, Bitmap value) {
                return Math.max(1, value.getByteCount());
            }
        };
        final boolean[] pageErrorShown = {false};
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setClipToPadding(false);
        lv.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(72)) + topInset, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)) + bottomInset);
        lv.setVerticalScrollBarEnabled(false);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return count; }
            @Override public Object getItem(int i) { return null; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(final int pos, View cv, ViewGroup parent) {
                final ImageView iv;
                if (cv == null) {
                    iv = new ImageView(ViewerActivity.this);
                    iv.setAdjustViewBounds(true);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setBackgroundColor(Color.WHITE);
                    AbsListView.LayoutParams lp = new AbsListView.LayoutParams(-1, -2);
                    iv.setLayoutParams(lp);
                } else iv = (ImageView) cv;
                iv.setTag(pos);
                Bitmap b = cache.get(pos);
                if (b != null) { iv.setImageBitmap(b); iv.setMinimumHeight(0); }
                else {
                    iv.setImageDrawable(null);
                    iv.setMinimumHeight((int) (w * 1.35f));
                    io.execute(() -> {
                        if (destroyed || token != pdfGeneration) return;
                        Bitmap bm = renderPage(r, pos, w);
                        if (bm == null) {
                            h.post(() -> {
                                if (!destroyed && token == pdfGeneration && !pageErrorShown[0]) {
                                    pageErrorShown[0] = true;
                                    ToxiVisualSystem.toast(ViewerActivity.this, "One or more PDF pages could not be rendered");
                                }
                            });
                            return;
                        }
                        if (destroyed || token != pdfGeneration) {
                            bm.recycle();
                            return;
                        }
                        cache.put(pos, bm);
                        h.post(() -> {
                            Object tg = iv.getTag();
                            if (!destroyed && token == pdfGeneration && tg != null && ((Integer) tg) == pos) {
                                iv.setImageBitmap(bm);
                                iv.setMinimumHeight(0);
                            }
                        });
                    });
                }
                return iv;
            }
        });
        stage.addView(lv, new FrameLayout.LayoutParams(-1, -1));
    }

    static Bitmap renderPage(PdfRenderer r, int pos, int w) {
        PdfRenderer.Page page = null;
        Bitmap bitmap = null;
        try {
            synchronized (r) {
                if (pos < 0 || pos >= r.getPageCount()) return null;
                page = r.openPage(pos);
                int pageWidth = Math.max(1, page.getWidth());
                int pageHeight = Math.max(1, page.getHeight());
                double pagePixels = (double) pageWidth * (double) pageHeight;
                double scale = Math.min((double) Math.max(1, w) / pageWidth, Math.sqrt(16_000_000.0 / pagePixels));
                int targetWidth = Math.max(1, (int) Math.ceil(pageWidth * scale));
                int targetHeight = Math.max(1, (int) Math.ceil(pageHeight * scale));
                bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap result = bitmap;
                bitmap = null;
                return result;
            }
        } catch (Exception | OutOfMemoryError ex) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            return null;
        } finally {
            if (page != null) try { page.close(); } catch (Exception ignored) { }
        }
    }

    float getMediaVolumeFraction() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0f : (float) am.getStreamVolume(AudioManager.STREAM_MUSIC) / max;
    }

    LinearLayout levelPanel(LevelSlider slider, int symbol) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        panel.setBackground(ToxiVisualSystem.surface(24, true));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(ToxiIcons.symbol(symbol, ToxiVisualSystem.TEXT_PRIMARY));
        icon.setContentDescription(ToxiIcons.accessibilityLabel(symbol));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAdjustViewBounds(false);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(icon, iconLp);
        panel.addView(slider, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView label = ToxiVisualSystem.text(this, slider.label, 9.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        label.setGravity(Gravity.CENTER);
        panel.addView(label, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22))));
        return panel;
    }

    interface LevelChanged { void onChanged(float level); }

    final class LevelSlider extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String label;
        float level;
        LevelChanged listener;
        LevelSlider(android.content.Context context, String label, float initial) {
            super(context); this.label = label; this.level = Math.max(0f, Math.min(1f, initial));
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        void setOnLevelChanged(LevelChanged listener) { this.listener = listener; }
        void setLevel(float value) { level = Math.max(0f, Math.min(1f, value)); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, top = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), bottom = getHeight() - ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));
            paint.setColor(Color.argb(85, Color.red(ToxiVisualSystem.ACCENT), Color.green(ToxiVisualSystem.ACCENT), Color.blue(ToxiVisualSystem.ACCENT))); paint.setStrokeWidth(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4))); paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx, top, cx, bottom, paint);
            float y = bottom - level * (bottom - top);
            paint.setColor(ToxiVisualSystem.ACCENT); canvas.drawLine(cx, y, cx, bottom, paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(cx, y, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), paint);
            paint.setColor(ToxiVisualSystem.CANVAS); canvas.drawCircle(cx, y, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                float top = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), bottom = getHeight() - ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));
                setLevel(1f - Math.max(0f, Math.min(1f, (event.getY() - top) / Math.max(1f, bottom - top))));
                if (listener != null) listener.onChanged(level);
                return true;
            }
            return true;
        }
    }

    private void handleBackNavigation() {
        // Back from the viewer must return to the file list, never leave a hidden player alive.
        try {
            if (videoPlayer != null) {
                int pos = videoPlayer.getCurrentPosition();
                if (paths != null && idx >= 0 && idx < paths.size()) {
                    getPreferences(MODE_PRIVATE).edit().putInt("video_pos_" + paths.get(idx), pos).apply();
                }
                if (videoPlayer.isPlaying()) videoPlayer.pause();
                videoUserWantsPlayback = false;
                videoPipRequested = false;
            }
            if (mp != null && mp.isPlaying()) mp.pause();
        } catch (Exception ex) {
            android.util.Log.w("LiquidFiles", "Playback cleanup on Back failed", ex);
        }
        finish();
    }

    @Override protected void onPause() {
        super.onPause();
        try { audioWasPlayingBeforeBackground = mp != null && mp.isPlaying(); if (mp != null && mp.isPlaying()) mp.pause(); } catch (Exception ignored) { }
        try { if (videoPlayer != null && paths != null && idx >= 0 && idx < paths.size()) { getPreferences(MODE_PRIVATE).edit().putInt("video_pos_" + paths.get(idx), videoPlayer.getCurrentPosition()).apply(); if (videoPlayer.isPlaying() && !(Build.VERSION.SDK_INT >= 26 && (isInPictureInPictureMode() || videoPipRequested))) { videoPlayer.pause(); videoUserWantsPlayback = false; } } } catch (Exception ignored) { }
    }

    @Override protected void onStop() {
        super.onStop();
        // Video is allowed to continue only while PiP is actually active. If this Activity
        // is stopped in ordinary background mode, pause and persist its position to avoid
        // audio-only playback/ghost playback after returning to the app.
        try {
            if (videoPlayer != null && !(Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode())) {
                // Only Android's actual PiP state may keep video playing. A stale
                // videoPipRequested flag must never allow audio-only playback after dismissal.
                if (videoPlayer.isPlaying()) videoPlayer.pause();
                videoUserWantsPlayback = false;
                videoPipRequested = false;
                if (paths != null && idx >= 0 && idx < paths.size()) {
                    getPreferences(MODE_PRIVATE).edit().putInt("video_pos_" + paths.get(idx), videoPlayer.getCurrentPosition()).apply();
                }
            }
        } catch (RuntimeException ex) { android.util.Log.w("LiquidFiles", "Stopping background video failed", ex); }
        try {
            if (audioWasPlayingBeforeBackground && mp != null && paths != null && idx >= 0 && idx < paths.size()) {
                int pos = mp.getCurrentPosition();
                getPreferences(MODE_PRIVATE).edit().putInt("audio_pos_" + paths.get(idx), pos).apply();
                Intent service = new Intent(this, BackgroundAudioService.class).setAction(BackgroundAudioService.ACTION_START)
                    .putExtra("path", paths.get(idx)).putExtra("position", pos).putExtra("queue", new java.util.ArrayList<>(paths)).putExtra("index", idx).putExtra("repeatAll", audioRepeatMode == 2).putExtra("repeatOne", audioRepeatMode == 1).putExtra("shuffle", audioShuffle).putExtra("sleepRemaining", Math.max(0L, sleepDeadlineElapsed - android.os.SystemClock.elapsedRealtime())).putExtra("volume", audioVolume);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
                backgroundAudioHandoff = true;
            }
        } catch (Exception ex) { android.util.Log.w("LiquidFiles", "Background audio handoff failed", ex); }
    }

    @Override public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) videoPipRequested = true;
        else videoPipRequested = false;
        if (videoDock != null) videoDock.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        if (isInPictureInPictureMode) {
            if (bar != null) bar.setVisibility(View.GONE);
            if (prevBtn != null) prevBtn.setVisibility(View.GONE);
            if (nextBtn != null) nextBtn.setVisibility(View.GONE);
        } else {
            fitVideoTexture();
            if (bar != null) bar.setVisibility(View.VISIBLE);
            if (prevBtn != null) prevBtn.setVisibility(paths != null && paths.size() > 1 && idx > 0 ? View.VISIBLE : View.GONE);
            if (nextBtn != null) nextBtn.setVisibility(paths != null && paths.size() > 1 && idx < paths.size() - 1 ? View.VISIBLE : View.GONE);
            // Some OEM builds dismiss PiP without delivering a reliable onStop callback.
            // Delay briefly so reopening the PiP window is not mistaken for dismissal.
            levelOverlayHandler.postDelayed(() -> {
                try {
                    if (!destroyed && Build.VERSION.SDK_INT >= 26
                            && !isInPictureInPictureMode() && !hasWindowFocus()
                            && videoPlayer != null && videoPlayer.isPlaying()) {
                        int pos = videoPlayer.getCurrentPosition();
                        if (paths != null && idx >= 0 && idx < paths.size()) {
                            getPreferences(MODE_PRIVATE).edit().putInt("video_pos_" + paths.get(idx), pos).apply();
                        }
                        videoPlayer.pause();
                        videoUserWantsPlayback = false;
                        videoPipRequested = false;
                        android.util.Log.i("LiquidFiles", "Paused video after PiP dismissal");
                    }
                } catch (Exception ex) {
                    android.util.Log.w("LiquidFiles", "PiP dismissal cleanup failed", ex);
                }
            }, 350);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (AppLock.enabled(this) && !MainActivity.unlocked) { finish(); return; }
        if (backgroundAudioHandoff) {
            try {
                String servicePath = BackgroundAudioService.activePath;
                int servicePosition = BackgroundAudioService.latestPosition;
                startService(new Intent(this, BackgroundAudioService.class).setAction(BackgroundAudioService.ACTION_STOP));
                int serviceIndex = paths == null ? -1 : paths.indexOf(servicePath);
                if (serviceIndex >= 0 && serviceIndex != idx) {
                    idx = serviceIndex;
                    getPreferences(MODE_PRIVATE).edit().putInt("audio_pos_" + servicePath, servicePosition).apply();
                    show();
                } else if (mp != null && paths != null && idx >= 0 && idx < paths.size() && paths.get(idx).equals(servicePath)) {
                    if (servicePosition > 0) mp.seekTo(servicePosition);
                    if (audioWasPlayingBeforeBackground) mp.start();
                }
            } catch (Exception ex) { android.util.Log.w("LiquidFiles", "Returning from background playback failed", ex); }
            backgroundAudioHandoff = false;
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && videoTexture != null && !destroyed) enterVideoImmersiveMode();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        try { } catch (Exception ignored) {}
        if (sleepStopper != null) sleepHandler.removeCallbacks(sleepStopper);
        if (hideLevelOverlays != null) levelOverlayHandler.removeCallbacks(hideLevelOverlays);
        cleanup();
        taskScope.close(false);
        super.onDestroy();
    }

    private void setVideoPlayAnimation(TextView button, boolean playing) {
        // Keep playback feedback clear without a pulsing/radiant animation.
        button.clearAnimation();
        button.setScaleX(1f); button.setScaleY(1f);
        button.setText(playing ? "Playing" : "Play");
        setCompoundIcon(button, playing ? ToxiIcons.PAUSE : ToxiIcons.PLAY);
    }


    /** Stable, non-pulsing play/pause control; playback does not depend on any animation asset. */
    private static final class PlayAnimationImageView extends androidx.appcompat.widget.AppCompatImageView {
        private boolean playing = false;
        PlayAnimationImageView(android.content.Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            setContentDescription("Play or pause");
            setImageDrawable(ToxiIcons.symbol(ToxiIcons.PLAY, ToxiVisualSystem.TEXT_PRIMARY));
        }
        void setPlaying(boolean value) {
            playing = value;
            setImageDrawable(ToxiIcons.symbol(playing ? ToxiIcons.PAUSE : ToxiIcons.PLAY, ToxiVisualSystem.TEXT_PRIMARY));
            setContentDescription(playing ? "Pause playback" : "Start playback");
        }
    }
}
