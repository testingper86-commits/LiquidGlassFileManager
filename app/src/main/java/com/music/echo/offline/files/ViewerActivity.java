package com.music.echo.offline.files;


import com.music.echo.offline.R;

import android.app.Activity;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Built-in viewer: images (pinch zoom), video, audio, PDF and text. Works without any other app installed. */
public class ViewerActivity extends Activity {
    ArrayList<String> paths;
    int idx;
    FrameLayout root, stage;
    TextView titleTv, counterTv, editImageBtn;
    Bitmap currentImageBitmap;
    ImageView prevBtn, nextBtn;
    LinearLayout bar;
    final ExecutorService io = Executors.newSingleThreadExecutor();
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
    Runnable ticker;
    Runnable videoTicker;
    boolean seekingVideo = false;
    boolean destroyed;
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
        super.onCreate(b);
        Ui.init(this);
        if (AppLock.enabled(this)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        paths = getIntent().getStringArrayListExtra("paths");
        idx = getIntent().getIntExtra("index", 0);
        if (paths == null || paths.isEmpty()) { finish(); return; }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05070C);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.glass(26, true));
        bar.setPadding(Ui.dp(4), 0, Ui.dp(4), 0);
        ImageView back = Ui.iconBtn(this, Glyphs.BACK, Ui.WHITE);
        back.setOnClickListener(v -> finish());
        titleTv = Ui.tv(this, "", 15, Ui.WHITE, true);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        counterTv = Ui.tv(this, "", 12, Ui.SUB, false);
        ImageView open = Ui.iconBtn(this, Glyphs.OPEN, Ui.WHITE);
        open.setOnClickListener(v -> FileOps.external(this, new File(paths.get(idx)), true));
        ImageView share = Ui.iconBtn(this, Glyphs.SHARE, Ui.WHITE);
        share.setOnClickListener(v -> { ArrayList<File> l = new ArrayList<>(); l.add(new File(paths.get(idx))); FileOps.share(this, l); });
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        bar.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.rightMargin = Ui.dp(6);
        bar.addView(counterTv, cl);
        editImageBtn = Ui.tv(this, "Edit", 13, Ui.WHITE, true);
        editImageBtn.setGravity(Gravity.CENTER);
        editImageBtn.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
        editImageBtn.setBackground(Ui.glass(18, true));
        editImageBtn.setOnClickListener(v -> openImageEditor());
        bar.addView(editImageBtn, new LinearLayout.LayoutParams(-2, Ui.dp(36)));
        bar.addView(open, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        bar.addView(share, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, Ui.dp(52), Gravity.TOP);
        bp.setMargins(Ui.dp(12), Ui.dp(8), Ui.dp(12), 0);
        root.addView(bar, bp);

        prevBtn = edge(Glyphs.BACK, Gravity.START | Gravity.CENTER_VERTICAL, -1);
        nextBtn = edge(Glyphs.NEXT, Gravity.END | Gravity.CENTER_VERTICAL, 1);

        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                topInset = in.top; bottomInset = in.bottom;
            } else { topInset = insets.getSystemWindowInsetTop(); bottomInset = insets.getSystemWindowInsetBottom(); }
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bar.getLayoutParams();
            if (p.topMargin != topInset + Ui.dp(8)) { p.topMargin = topInset + Ui.dp(8); bar.setLayoutParams(p); }
            return insets;
        });
        root.requestApplyInsets();
        show();
    }

    ImageView edge(int glyph, int gravity, final int delta) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(Glyphs.get(glyph, Ui.WHITE));
        v.setBackground(Ui.glass(24, true));
        v.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        v.setOnClickListener(x -> go(delta));
        Ui.press(v);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Ui.dp(46), Ui.dp(46), gravity);
        p.setMargins(Ui.dp(10), 0, Ui.dp(10), 0);
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
        GlowRing.setActive(stage, false);
        if (pdf != null) {
            final PdfRenderer r = pdf;
            final ParcelFileDescriptor f = pfd;
            io.execute(() -> { try { r.close(); } catch (Exception ignored) { } try { if (f != null) f.close(); } catch (Exception ignored) { } });
            pdf = null; pfd = null;
        }
        stage.removeAllViews();
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
        GlowRing.attach(stage);
        GlowRing.setActive(stage, true);
        final ZoomImage z = new ZoomImage(this);
        stage.addView(z, new FrameLayout.LayoutParams(-1, -1));
        final int my = idx;
        io.execute(() -> {
            final Bitmap bm = decode(f, 2600);
            h.post(() -> {
                if (destroyed || my != idx) return;
                GlowRing.setActive(stage, false);
                if (bm == null) Ui.toast(this, "Can't display this image");
                else { currentImageBitmap = bm; z.setImageBitmap(bm); }
            });
        });
    }

    // Built-in lightweight image editor: non-destructive preview, crop presets, rotate/flip,
    // grayscale and brightness; saving creates a new JPEG next to the original.
    void openImageEditor() {
        if (currentImageBitmap == null || currentImageBitmap.isRecycled()) { Ui.toast(this, "Image is still loading"); return; }
        final Bitmap original = currentImageBitmap;
        final Bitmap[] working = { original.copy(Bitmap.Config.ARGB_8888, true) };
        final ImageView preview = new ImageView(this);
        preview.setImageBitmap(working[0]);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(4));
        panel.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(230)));
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(tools, new LinearLayout.LayoutParams(-1, Ui.dp(48)));
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
            final int action=i; TextView btn=Ui.tv(this,labels[i],11,Ui.WHITE,true); btn.setGravity(Gravity.CENTER);
            btn.setPadding(Ui.dp(6),0,Ui.dp(6),0); btn.setBackground(Ui.glass(14,true));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,Ui.dp(38),1f); bp.setMargins(Ui.dp(3),0,Ui.dp(3),0); tools.addView(btn,bp);
            btn.setOnClickListener(v->{
                if(action==0){ rotation[0]=(rotation[0]+90)%360; render.run(); }
                else if(action==1){flip[0]=!flip[0]; render.run();}
                else if(action==2){ new AlertDialog.Builder(this).setTitle("Crop ratio").setSingleChoiceItems(new String[]{"Original","Square 1:1","Portrait 4:3","Wide 16:9"},crop[0],(d,which)->{crop[0]=which;d.dismiss();render.run();}).setNegativeButton("Cancel",null).show(); }
                else {gray[0]=!gray[0];render.run();}
            });
        }
        TextView brightLabel=Ui.tv(this,"Brightness",12,Ui.WHITE,true); panel.addView(brightLabel);
        SeekBar bright=new SeekBar(this); bright.setMax(200); bright.setProgress(100); panel.addView(bright,new LinearLayout.LayoutParams(-1,Ui.dp(36)));
        bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u){brightness[0]=(p-100)/100f;render.run();}} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}});
        new AlertDialog.Builder(this).setTitle("Image editor").setView(panel).setNegativeButton("Cancel",(d,w)->{if(working[0]!=null&&!working[0].isRecycled())working[0].recycle();}).setNeutralButton("Reset",(d,w)->{if(working[0]!=null&&!working[0].isRecycled())working[0].recycle();currentImageBitmap=original;show();}).setPositiveButton("Save copy",(d,w)->saveEditedImage(working[0])).show();
    }

    void saveEditedImage(Bitmap bitmap) {
        if(bitmap==null||bitmap.isRecycled()) return;
        File src=new File(paths.get(idx)); String name=src.getName(); int dot=name.lastIndexOf('.');
        String base=dot>0?name.substring(0,dot):name;
        File out=new File(src.getParentFile(),base+"_edited.jpg"); int n=2;
        while(out.exists()) out=new File(src.getParentFile(),base+"_edited"+(n++)+".jpg");
        try(FileOutputStream fos=new FileOutputStream(out)) {
            if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,fos)) throw new java.io.IOException("JPEG encoding failed");
            fos.flush(); Ui.toast(this,"Saved copy: "+out.getName());
        } catch(Exception ex) { Ui.toast(this,"Couldn't save beside original. Check folder write permission."); }
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
                Matrix m = new Matrix();
                m.postRotate(rot);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    static final class ZoomImage extends ImageView {
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
        GlowRing.attach(stage);
        GlowRing.setActive(stage, true);
        enterVideoImmersiveMode();
        // TextureView does not support background drawables on Android; put the black
        // fallback on its FrameLayout parent instead of the TextureView itself.
        stage.setBackgroundColor(Color.BLACK);
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
        dock.setPadding(Ui.dp(8), Ui.dp(7), Ui.dp(8), Ui.dp(7));
        dock.setBackground(Ui.glass(24, true));
        dock.setElevation(Ui.dp(10));

        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout primaryRow = new LinearLayout(this);
        primaryRow.setGravity(Gravity.CENTER);
        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setGravity(Gravity.CENTER);
        dock.addView(seekRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = Ui.dp(4);
        dock.addView(primaryRow, rowParams);
        LinearLayout.LayoutParams secondRowParams = new LinearLayout.LayoutParams(-1, -2);
        secondRowParams.topMargin = Ui.dp(4);
        dock.addView(secondaryRow, secondRowParams);

        TextView playbackToggle = videoChip("Play", true);
        TextView currentTimeTv = Ui.tv(this, "0:00", 11, Ui.WHITE, false);
        currentTimeTv.setGravity(Gravity.CENTER);
        currentTimeTv.setMinWidth(Ui.dp(36));
        TextView durationTv = Ui.tv(this, "0:00", 11, Ui.SUB, false);
        durationTv.setGravity(Gravity.CENTER);
        durationTv.setMinWidth(Ui.dp(36));
        SeekBar seekBar = new SquigglySeekBar(this);
        seekBar.setMax(1);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, Ui.dp(34), 1f);
        seekParams.leftMargin = Ui.dp(4);
        seekParams.rightMargin = Ui.dp(4);
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
                Ui.toast(this, "Playback speed: " + videoSpeed + "×");
            } catch (Exception ex) { Ui.toast(this, "Playback speed isn't supported for this video"); }
        });

        TextView repeat = videoChip(videoLoop ? "Repeat on" : "Repeat off", false);
        repeat.setContentDescription("Toggle video repeat");
        repeat.setOnClickListener(v -> {
            videoLoop = !videoLoop;
            repeat.setText(videoLoop ? "Repeat on" : "Repeat off");
            repeat.setBackground(videoLoop ? Ui.accentPill(18) : Ui.glass(18, false));
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
                } catch (Exception ex) { videoPipRequested = false; Ui.toast(this, "Picture-in-Picture is unavailable on this device"); }
            } else Ui.toast(this, "Picture-in-Picture requires Android 8 or newer");
        });

        TextView lock = videoChip("Lock", false);
        lock.setOnClickListener(v -> setVideoControlsLocked(!videoControlsLocked, dock, lock));
        addVideoChip(primaryRow, speed, true);
        addVideoChip(primaryRow, repeat, true);
        addVideoChip(primaryRow, rotate, true);
        addVideoChip(secondaryRow, pip, true);
        addVideoChip(secondaryRow, lock, true);

        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockParams.setMargins(Ui.dp(12), 0, Ui.dp(12), Ui.dp(14));
        stage.addView(dock, dockParams);

        // MX Player-style touch level indicators: left side controls screen brightness,
        // right side controls media volume. They appear during vertical swipe gestures.
        final LevelSlider brightnessSlider = new LevelSlider(this, "Brightness", 0.5f);
        final LevelSlider volumeSlider = new LevelSlider(this, "Volume", getMediaVolumeFraction());
        LinearLayout brightnessPanel = levelPanel(brightnessSlider, "☼");
        LinearLayout volumePanel = levelPanel(volumeSlider, "♪");
        FrameLayout.LayoutParams leftLevelLp = new FrameLayout.LayoutParams(Ui.dp(58), Ui.dp(210), Gravity.CENTER_VERTICAL | Gravity.LEFT);
        leftLevelLp.setMargins(Ui.dp(12), 0, 0, 0);
        FrameLayout.LayoutParams rightLevelLp = new FrameLayout.LayoutParams(Ui.dp(58), Ui.dp(210), Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        rightLevelLp.setMargins(0, 0, Ui.dp(12), 0);
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
                    Ui.toast(ViewerActivity.this, delta < 0 ? "−10 seconds" : "+10 seconds");
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
                            GlowRing.setActive(stage, false);
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
                                        int durationNow = active.getDuration();
                                        float progressFraction = durationNow > 0 ? Math.max(0f, Math.min(1f, active.getCurrentPosition() / (float) durationNow)) : 0f;
                                        playbackToggle.setText(active.isPlaying() ? "Pause" : "Play");
                                        h.postDelayed(this, 400);
                                    } catch (RuntimeException ex) {
                                        android.util.Log.w("LiquidFiles", "Video progress update stopped", ex);
                                    }
                                }
                            };
                            h.post(videoTicker);
                        } catch (RuntimeException ex) {
                            android.util.Log.e("LiquidFiles", "Video preparation callback failed", ex);
                            Ui.toast(ViewerActivity.this, "Video playback failed. Try Open with.");
                            releaseVideoPlayer(prepared);
                        }
                    });
                    player.setOnVideoSizeChangedListener((changed, videoW, videoH) -> {
                        videoWidth = videoW;
                        videoHeight = videoH;
                        fitVideoTexture();
                    });
                    player.setOnInfoListener((bufferingPlayer, what, extra) -> {
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) GlowRing.setActive(stage, true);
                        else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) GlowRing.setActive(stage, false);
                        return false;
                    });
                    player.setOnCompletionListener(completed -> { if (!videoLoop) advanceToNextVideo(); });
                    player.setOnErrorListener((failed, what, extra) -> {
                        android.util.Log.e("LiquidFiles", "MediaPlayer error what=" + what + " extra=" + extra);
                        Ui.toast(ViewerActivity.this, "This video can't be played here. Try Open with.");
                        releaseVideoPlayer(failed);
                        return true;
                    });
                    player.prepareAsync();
                } catch (Exception ex) {
                    android.util.Log.e("LiquidFiles", "Video surface setup failed", ex);
                    if (videoPlayer != null) releaseVideoPlayer(videoPlayer);
                    GlowRing.setActive(stage, false);
                    Ui.toast(ViewerActivity.this, "Unable to render this video. Try Open with.");
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
        if (videoPlayer == player) { videoPrepared = false; videoResumeAfterSurface = false; videoUserWantsPlayback = false; videoPendingPosition = 0; }
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
        TextView chip = Ui.tv(this, label, 12, Ui.WHITE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        chip.setBackground(highlighted ? Ui.accentPill(18) : Ui.glass(18, false));
        Ui.press(chip);
        return chip;
    }

    void addVideoChip(LinearLayout row, TextView chip, boolean weighted) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(weighted ? 0 : -2, -2, weighted ? 1f : 0f);
        p.setMargins(Ui.dp(3), 0, Ui.dp(3), 0);
        row.addView(chip, p);
    }

    void setVideoControlsLocked(boolean locked, LinearLayout dock, TextView lock) {
        videoControlsLocked = locked;
        dock.setVisibility(locked ? View.GONE : View.VISIBLE);
        bar.setVisibility(locked ? View.GONE : View.VISIBLE);
        if (prevBtn != null) prevBtn.setVisibility(locked ? View.GONE : (paths != null && paths.size() > 1 && idx > 0 ? View.VISIBLE : View.GONE));
        if (nextBtn != null) nextBtn.setVisibility(locked ? View.GONE : (paths != null && paths.size() > 1 && idx < paths.size() - 1 ? View.VISIBLE : View.GONE));
        lock.setText(locked ? "Unlock" : "Lock");
        Ui.toast(this, locked ? "Controls hidden. Tap the video to unlock." : "Controls unlocked");
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
        list.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        scroll.addView(list);
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setTitle("Playback queue · " + (idx + 1) + "/" + paths.size())
            .setView(scroll).setNegativeButton("Close", null).create();
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAllViews();
            for (int i = 0; i < paths.size(); i++) {
                final int position = i;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(4), Ui.dp(6));
                row.setBackground(Ui.glass(14, i == idx));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.setMargins(0, 0, 0, Ui.dp(6));
                TextView name = Ui.tv(this, (i == idx ? "▶  " : "") + new File(paths.get(i)).getName(), 12.5f, i == idx ? Ui.accent : Ui.WHITE, i == idx);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setPadding(Ui.dp(5), Ui.dp(8), Ui.dp(5), Ui.dp(8));
                name.setOnClickListener(view -> { idx = position; dialog.dismiss(); show(); });
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView up = Ui.tv(this, "↑", 15, Ui.WHITE, true);
                up.setGravity(Gravity.CENTER);
                up.setPadding(Ui.dp(9), Ui.dp(8), Ui.dp(9), Ui.dp(8));
                up.setOnClickListener(view -> { if (position > 0) { java.util.Collections.swap(paths, position, position - 1); if (idx == position) idx--; else if (idx == position - 1) idx++; rebuild[0].run(); } });
                row.addView(up);
                TextView down = Ui.tv(this, "↓", 15, Ui.WHITE, true);
                down.setGravity(Gravity.CENTER);
                down.setPadding(Ui.dp(9), Ui.dp(8), Ui.dp(9), Ui.dp(8));
                down.setOnClickListener(view -> { if (position < paths.size() - 1) { java.util.Collections.swap(paths, position, position + 1); if (idx == position) idx++; else if (idx == position + 1) idx--; rebuild[0].run(); } });
                row.addView(down);
                TextView remove = Ui.tv(this, "×", 16, Ui.SUB, true);
                remove.setGravity(Gravity.CENTER);
                remove.setPadding(Ui.dp(9), Ui.dp(8), Ui.dp(9), Ui.dp(8));
                remove.setOnClickListener(view -> {
                    if (paths.size() <= 1) { Ui.toast(this, "Keep at least one item in the queue"); return; }
                    boolean removingCurrent = position == idx;
                    paths.remove(position);
                    if (position < idx) idx--;
                    else if (removingCurrent) idx = Math.min(position, paths.size() - 1);
                    if (removingCurrent) { dialog.dismiss(); show(); }
                    else { dialog.setTitle("Playback queue · " + (idx + 1) + "/" + paths.size()); rebuild[0].run(); }
                });
                row.addView(remove);
                list.addView(row, rp);
            }
        };
        rebuild[0].run();
        dialog.show();
    }

    // ---------------- audio ----------------
    void showAudio(File f, Entry e) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.glass(30, true));
        card.setPadding(Ui.dp(22), Ui.dp(24), Ui.dp(22), Ui.dp(20));
        card.setElevation(Ui.dp(10));
        TextView nowPlaying = Ui.tv(this, "NOW PLAYING", 10.5f, Ui.accent, true);
        nowPlaying.setLetterSpacing(0.16f);
        nowPlaying.setPadding(0, 0, 0, Ui.dp(14));
        card.addView(nowPlaying);
        final ImageView art = new ImageView(this);
        art.setImageDrawable(Glyphs.tile(FileKinds.AUDIO));
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(Ui.glass(28, true));
        art.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        Ui.round(art, 28);
        card.addView(art, new LinearLayout.LayoutParams(Ui.dp(180), Ui.dp(180)));
        TextView nm = Ui.tv(this, f.getName(), 18, Ui.WHITE, true);
        nm.setMaxLines(2);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nm.setGravity(Gravity.CENTER);
        nm.setPadding(0, Ui.dp(18), 0, 0);
        card.addView(nm, new LinearLayout.LayoutParams(-1, -2));
        final TextView meta = Ui.tv(this, "", 13, Ui.SUB, false);
        meta.setGravity(Gravity.CENTER);
        meta.setPadding(0, Ui.dp(6), 0, Ui.dp(14));
        card.addView(meta, new LinearLayout.LayoutParams(-1, -2));
        final SeekBar sb = new SquigglySeekBar(this);
        card.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        final TextView time = Ui.tv(this, "0:00 / 0:00", 12, Ui.SUB, false);
        time.setContentDescription("Playback position and duration");
        time.setGravity(Gravity.CENTER);
        time.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
        card.addView(time, new LinearLayout.LayoutParams(-1, -2));

        // Keep playback options inside the built-in player; no separate player app is launched.
        LinearLayout modes = new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);
        modes.setPadding(0, Ui.dp(8), 0, Ui.dp(2));
        final TextView repeat = Ui.tv(this, repeatLabel(), 12.5f, Ui.WHITE, true);
        final TextView shuffle = Ui.tv(this, audioShuffle ? "⤨ Shuffle: On" : "⤨ Shuffle", 12.5f, Ui.WHITE, true);
        final TextView abRepeat = Ui.tv(this, "Set A", 12.5f, Ui.WHITE, true);
        repeat.setGravity(Gravity.CENTER);
        shuffle.setGravity(Gravity.CENTER); abRepeat.setGravity(Gravity.CENTER);
        repeat.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        shuffle.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        abRepeat.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        repeat.setBackground(audioRepeatMode != 0 ? Ui.accentPill(20) : Ui.glass(20, false));
        shuffle.setBackground(audioShuffle ? Ui.accentPill(20) : Ui.glass(20, false));
        abRepeat.setBackground(Ui.glass(20, false));
        abRepeat.setContentDescription("A-B loop is off. Tap to mark loop start A.");
        repeat.setContentDescription("Repeat mode " + repeatLabel());
        shuffle.setContentDescription("Shuffle playback " + (audioShuffle ? "on" : "off"));
        repeat.setOnClickListener(v -> {
            audioRepeatMode = (audioRepeatMode + 1) % 3;
            audioRepeat = audioRepeatMode == 1;
            repeat.setText(repeatLabel());
            repeat.setBackground(audioRepeatMode != 0 ? Ui.accentPill(20) : Ui.glass(20, false));
            repeat.setContentDescription("Repeat mode " + repeatLabel());
            Ui.tick(v);
        });
        shuffle.setOnClickListener(v -> {
            audioShuffle = !audioShuffle;
            shuffle.setText(audioShuffle ? "⤨ Shuffle: On" : "⤨ Shuffle");
            shuffle.setBackground(audioShuffle ? Ui.accentPill(20) : Ui.glass(20, false));
            shuffle.setContentDescription("Shuffle playback " + (audioShuffle ? "on" : "off"));
            Ui.tick(v);
        });
        abRepeat.setOnClickListener(v -> {
            if (mp == null) return;
            try {
                int position = mp.getCurrentPosition();
                if (audioLoopStartMs < 0) { audioLoopStartMs = position; audioLoopEndMs = -1; Ui.toast(this, "Loop start A set at " + fmt(position)); }
                else if (audioLoopEndMs < 0) {
                    if (position <= audioLoopStartMs + 500) { Ui.toast(this, "Set point B at least half a second after A"); return; }
                    audioLoopEndMs = position; Ui.toast(this, "A–B loop enabled");
                } else { audioLoopStartMs = -1; audioLoopEndMs = -1; Ui.toast(this, "A–B loop cleared"); }
                abRepeat.setText(audioLoopStartMs < 0 ? "Set A" : audioLoopEndMs < 0 ? "Set B" : "A–B On");
                abRepeat.setBackground(audioLoopEndMs >= 0 ? Ui.accentPill(20) : Ui.glass(20, false));
                abRepeat.setContentDescription(audioLoopStartMs < 0 ? "A-B loop off. Tap to mark point A" : audioLoopEndMs < 0 ? "Point A set. Tap to mark point B" : "A-B loop enabled. Tap to clear loop");
            } catch (Exception ignored) { Ui.toast(this, "A–B repeat is unavailable"); }
            Ui.tick(v);
        });
        Ui.press(repeat); Ui.press(shuffle); Ui.press(abRepeat);
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(-2, -2);
        modeLp.setMargins(Ui.dp(5), 0, Ui.dp(5), 0);
        modes.addView(repeat, modeLp);
        LinearLayout.LayoutParams shuffleLp = new LinearLayout.LayoutParams(-2, -2);
        shuffleLp.setMargins(Ui.dp(5), 0, Ui.dp(5), 0);
        modes.addView(shuffle, shuffleLp);
        LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(-2, -2);
        abLp.setMargins(Ui.dp(5), 0, Ui.dp(5), 0);
        modes.addView(abRepeat, abLp);
        card.addView(modes, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout extras = new LinearLayout(this);
        extras.setOrientation(LinearLayout.VERTICAL);
        extras.setGravity(Gravity.CENTER);
        extras.setPadding(0, Ui.dp(7), 0, Ui.dp(2));
        LinearLayout extrasTop = new LinearLayout(this);
        LinearLayout extrasBottom = new LinearLayout(this);
        extrasTop.setGravity(Gravity.CENTER);
        extrasBottom.setGravity(Gravity.CENTER);
        TextView speedBtn = Ui.tv(this, "1.0× Speed", 12, Ui.WHITE, true);
        TextView sleepBtn = Ui.tv(this, "Sleep timer", 12, Ui.WHITE, true);
        TextView lyricsBtn = Ui.tv(this, "Lyrics", 12, Ui.WHITE, true);
        TextView eqBtn = Ui.tv(this, "EQ / Bass", 12, Ui.WHITE, true);
        TextView queueBtn = Ui.tv(this, "Queue", 12, Ui.WHITE, true);
        TextView[] extraButtons = {speedBtn, sleepBtn, lyricsBtn, eqBtn, queueBtn};
        for (int i = 0; i < extraButtons.length; i++) {
            TextView b = extraButtons[i];
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(Ui.dp(42));
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setPadding(Ui.dp(7), Ui.dp(9), Ui.dp(7), Ui.dp(9));
            b.setBackground(Ui.glass(16, false));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, -2, i < 3 ? 1f : 1f);
            ep.setMargins(Ui.dp(3), 0, Ui.dp(3), Ui.dp(0));
            if (i >= 3) ep.width = Ui.dp(118);
            (i < 3 ? extrasTop : extrasBottom).addView(b, ep);
            Ui.press(b);
        }
        extras.addView(extrasTop, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams extrasBottomLp = new LinearLayout.LayoutParams(-1, -2);
        extrasBottomLp.topMargin = Ui.dp(6);
        extras.addView(extrasBottom, extrasBottomLp);
        // Balance the second row's two actions around the center.
        extrasBottom.setGravity(Gravity.CENTER);
        speedBtn.setOnClickListener(v -> {
            audioSpeed = audioSpeed >= 2.0f ? 0.75f : audioSpeed == 1.0f ? 1.25f : audioSpeed == 1.25f ? 1.5f : audioSpeed == 1.5f ? 2.0f : 1.0f;
            speedBtn.setText(audioSpeed + "×");
            try { if (Build.VERSION.SDK_INT >= 23 && mp != null) mp.setPlaybackParams(new PlaybackParams().setSpeed(audioSpeed)); else Ui.toast(this, "Playback speed requires Android 6 or newer"); }
            catch (Exception ex) { Ui.toast(this, "Speed change is not supported for this file"); }
        });
        sleepBtn.setOnClickListener(v -> {
            String[] choices = {"Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes"};
            new android.app.AlertDialog.Builder(this).setTitle("Sleep timer").setItems(choices, (d, which) -> {
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
                    Ui.toast(this, "Sleep timer set: " + choices[which]);
                } else { sleepDeadlineElapsed = 0L; Ui.toast(this, "Sleep timer off"); }
            }).show();
        });
        lyricsBtn.setOnClickListener(v -> showLocalLyrics(f));
        eqBtn.setOnClickListener(v -> showAudioEffects());
        queueBtn.setOnClickListener(v -> {
            if (paths == null || paths.isEmpty()) { Ui.toast(this, "No files in this playback list"); return; }
            showQueueEditor();
        });
        card.addView(extras, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout volumeHeader = new LinearLayout(this);
        volumeHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView volLabel = Ui.tv(this, "VOLUME", 10.5f, Ui.SUB, true);
        TextView volValue = Ui.tv(this, "100%", 10.5f, Ui.SUB, false);
        volumeHeader.addView(volLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        volumeHeader.addView(volValue, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams volumeHeaderLp = new LinearLayout.LayoutParams(-1, -2);
        volumeHeaderLp.topMargin = Ui.dp(7);
        card.addView(volumeHeader, volumeHeaderLp);
        SeekBar volume = new SeekBar(this);
        audioVolume = getPreferences(MODE_PRIVATE).getInt("audio_volume", 100) / 100f;
        volume.setMax(100); volume.setProgress(Math.round(audioVolume * 100));
        volValue.setText(Math.round(audioVolume * 100) + "%");
        volume.getProgressDrawable().setTint(Ui.accent); volume.getThumb().setTint(Ui.accent);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seek, int value, boolean fromUser) { volValue.setText(value + "%"); if (fromUser) { audioVolume = value / 100f; getPreferences(MODE_PRIVATE).edit().putInt("audio_volume", value).apply(); if (mp != null) try { mp.setVolume(audioVolume, audioVolume); } catch (Exception ignored) {} } }
            public void onStartTrackingTouch(SeekBar seek) {} public void onStopTrackingTouch(SeekBar seek) {}
        });
        card.addView(volume, new LinearLayout.LayoutParams(-1, Ui.dp(38)));

        LinearLayout ctl = new LinearLayout(this);
        ctl.setGravity(Gravity.CENTER);
        ImageView pv = Ui.iconBtn(this, Glyphs.BACK, Ui.WHITE);
        final PlayAnimationImageView pp = new PlayAnimationImageView(this);
        pp.setPlaying(true);
        ImageView nx = Ui.iconBtn(this, Glyphs.NEXT, Ui.WHITE);
        pp.setBackground(Ui.accentPill(30));
        pv.setContentDescription("Previous track");
        pp.setContentDescription("Play or pause");
        nx.setContentDescription("Next track");
        pv.setOnClickListener(v -> { if (mp != null) { try { if (mp.getCurrentPosition() > 3000) { mp.seekTo(0); return; } } catch (Exception ignored) {} } go(-1); });
        nx.setOnClickListener(v -> go(1));
        ctl.addView(pv, new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
        LinearLayout.LayoutParams ppl = new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64));
        ppl.setMargins(Ui.dp(18), 0, Ui.dp(18), 0);
        ctl.addView(pp, ppl);
        ctl.addView(nx, new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
        LinearLayout.LayoutParams cll = new LinearLayout.LayoutParams(-1, -2);
        cll.topMargin = Ui.dp(10);
        card.addView(ctl, cll);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        fp.setMargins(Ui.dp(22), 0, Ui.dp(22), 0);
        stage.addView(card, fp);
        GlowRing.attach(art);
        GlowRing.setActive(art, false);
        GlowRing.attach(pp);
        GlowRing.setActive(pp, false);

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
            final String fi = info.isEmpty() ? Ui.size(e.len) : info;
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
                GlowRing.setActive(art, true);
                GlowRing.setActive(pp, true);
                initAudioEffects(m.getAudioSessionId());
                pp.setPlaying(true);
                startTicker(sb, time);
            });
            mp.setOnCompletionListener(m -> {
                if (audioRepeatMode == 1) {
                    try { m.seekTo(0); m.start(); GlowRing.setActive(art, true); GlowRing.setActive(pp, true); pp.setPlaying(true); }
                    catch (Exception ignored) { GlowRing.setActive(art, false); GlowRing.setActive(pp, false); pp.setPlaying(false); }
                } else if (paths.size() > 1 && audioShuffle) {
                    go(1);
                } else if (idx < paths.size() - 1) go(1);
                else if (audioRepeatMode == 2 && paths.size() > 0) { idx = 0; show(); }
                else { GlowRing.setActive(art, false); GlowRing.setActive(pp, false); pp.setPlaying(false); }
            });
            mp.setOnErrorListener((m, w, x) -> { Ui.toast(this, "Can't play this audio file"); return true; });
            mp.prepareAsync();
        } catch (Exception ex) {
            Ui.toast(this, "Can't play this audio file");
        }
        pp.setOnClickListener(v -> {
            if (mp == null) return;
            try {
                if (mp.isPlaying()) { mp.pause(); GlowRing.setActive(art, false); GlowRing.setActive(pp, false); pp.setPlaying(false); }
                else { mp.start(); GlowRing.setActive(art, true); GlowRing.setActive(pp, true); pp.setPlaying(true); }
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
        if (audioRepeatMode == 1) return "↻ Repeat One";
        if (audioRepeatMode == 2) return "↻ Repeat All";
        return "↻ Repeat Off";
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
        panel.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), Ui.dp(8));
        TextView note = Ui.tv(this, "Audio effects depend on Android/device support and may reset when the track changes.", 12, Ui.SUB, false);
        panel.addView(note);
        final SeekBar bass = new SeekBar(this); bass.setMax(1000);
        panel.addView(Ui.tv(this, "Bass boost", 13, Ui.WHITE, true)); panel.addView(bass);
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
                    panel.addView(Ui.tv(this, center + " Hz", 12, Ui.SUB, false));
                    SeekBar seek = new SeekBar(this); seek.setMax(range[1] - range[0]);
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
        new android.app.AlertDialog.Builder(this).setTitle("Equalizer & bass").setView(panel).setPositiveButton("Done", null).show();
    }

    void showLocalLyrics(File audio) {
        String base = audio.getAbsolutePath();
        int dot = base.lastIndexOf('.');
        if (dot > base.lastIndexOf(File.separator)) base = base.substring(0, dot);
        File lrc = new File(base + ".lrc");
        File txt = new File(base + ".txt");
        File chosen = lrc.isFile() ? lrc : (txt.isFile() ? txt : null);
        if (chosen == null) { Ui.toast(this, "No local .lrc or matching .txt lyrics found"); return; }
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
                LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(12));
                TextView current = Ui.tv(this, "Lyrics", 18, Ui.WHITE, true); current.setGravity(Gravity.CENTER); panel.addView(current, new LinearLayout.LayoutParams(-1, -2));
                TextView all = Ui.tv(this, raw, 13, Ui.SUB, false); all.setTextIsSelectable(true);
                ScrollView scroll = new ScrollView(this); scroll.addView(all); panel.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(300)));
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle(chosen.getName()).setView(panel).setPositiveButton("Close", null).create();
                dialog.setOnDismissListener(d -> { if (lyricTicker != null) { subtitleHandler.removeCallbacks(lyricTicker); lyricTicker = null; } });
                dialog.show();
                if (!lyricTimes.isEmpty()) {
                    lyricTicker = new Runnable() { @Override public void run() {
                        if (!dialog.isShowing()) return;
                        int pos = 0; try { if (mp != null) pos = mp.getCurrentPosition(); } catch (Exception ignored) { }
                        String active = "♪  " + (lyricText.isEmpty() ? "" : lyricText.get(0));
                        for (int i = 0; i < lyricTimes.size(); i++) if (lyricTimes.get(i) <= pos) active = "♪  " + lyricText.get(i); else break;
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
        t.setTextColor(0xFFE6EAF5);
        t.setTextSize(13);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(18), Ui.dp(72) + topInset, Ui.dp(18), Ui.dp(40) + bottomInset);
        t.setText("Loading…");
        sv.addView(t, new FrameLayout.LayoutParams(-1, -2));
        stage.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        TextView edit = Ui.tv(this, "Edit text", 12, Ui.WHITE, true);
        edit.setGravity(Gravity.CENTER); edit.setPadding(Ui.dp(14), Ui.dp(9), Ui.dp(14), Ui.dp(9));
        edit.setBackground(Ui.glass(16, true));
        FrameLayout.LayoutParams editLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        editLp.setMargins(0, Ui.dp(16) + topInset, Ui.dp(16), 0);
        stage.addView(edit, editLp);
        edit.setOnClickListener(v -> openTextEditor(f));
        final int my = idx;
        io.execute(() -> {
            String s;
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[300 * 1024];
                int n = in.read(buf);
                s = new String(buf, 0, Math.max(n, 0), "UTF-8");
                if (f.length() > buf.length) s += "\n\n… file truncated (showing first 300 KB)";
            } catch (Exception ex) { s = "Can't read this file."; }
            final String fs = s;
            h.post(() -> { if (!destroyed && my == idx) t.setText(fs); });
        });
    }

    void openTextEditor(File file) {
        if (!file.isFile() || file.length() > 1024 * 1024) { Ui.toast(this, "For safety, the built-in editor supports text files up to 1 MB"); return; }
        io.execute(() -> {
            String content;
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()]; int n = in.read(bytes);
                content = new String(bytes, 0, Math.max(0, n), "UTF-8");
            } catch (Exception e) { h.post(() -> Ui.toast(this, "Couldn't read this text file")); return; }
            final String initial = content;
            h.post(() -> {
                EditText editor = new EditText(ViewerActivity.this);
                editor.setText(initial); editor.setTextSize(13); editor.setTypeface(Typeface.MONOSPACE);
                editor.setTextColor(Ui.WHITE); editor.setHintTextColor(Ui.SUB); editor.setGravity(Gravity.TOP | Gravity.LEFT);
                editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                editor.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
                editor.setBackground(Ui.glass(18, true));
                ScrollView wrap = new ScrollView(ViewerActivity.this); wrap.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), 0);
                wrap.addView(editor, new ScrollView.LayoutParams(-1, Ui.dp(380)));
                new AlertDialog.Builder(ViewerActivity.this).setTitle("Edit · " + file.getName()).setView(wrap)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (d, w) -> io.execute(() -> {
                        try (FileOutputStream out = new FileOutputStream(file, false)) {
                            out.write(editor.getText().toString().getBytes("UTF-8")); out.flush();
                            h.post(() -> { Ui.toast(ViewerActivity.this, "Text file saved"); show(); });
                        } catch (Exception ex) { h.post(() -> Ui.toast(ViewerActivity.this, "Couldn't save this file. Check write access.")); }
                    })).show();
            });
        });
    }

    // ---------------- pdf ----------------
    void showPdf(File f) {
        try {
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            pdf = new PdfRenderer(pfd);
        } catch (Exception ex) {
            Ui.toast(this, "Can't open this PDF here — try \"Open with\"");
            return;
        }
        final PdfRenderer r = pdf;
        final int count = r.getPageCount();
        final int w = getResources().getDisplayMetrics().widthPixels;
        final LruCache<Integer, Bitmap> cache = new LruCache<>(6);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setClipToPadding(false);
        lv.setPadding(0, Ui.dp(72) + topInset, 0, Ui.dp(24) + bottomInset);
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
                        Bitmap bm = renderPage(r, pos, w);
                        if (bm == null) return;
                        cache.put(pos, bm);
                        h.post(() -> {
                            Object tg = iv.getTag();
                            if (!destroyed && tg != null && ((Integer) tg) == pos) { iv.setImageBitmap(bm); iv.setMinimumHeight(0); }
                        });
                    });
                }
                return iv;
            }
        });
        stage.addView(lv, new FrameLayout.LayoutParams(-1, -1));
    }

    static Bitmap renderPage(PdfRenderer r, int pos, int w) {
        try {
            synchronized (r) {
                PdfRenderer.Page p = r.openPage(pos);
                float sc = (float) w / p.getWidth();
                Bitmap b = Bitmap.createBitmap(w, Math.max(1, (int) (p.getHeight() * sc)), Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.WHITE);
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                p.close();
                return b;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    float getMediaVolumeFraction() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0f : (float) am.getStreamVolume(AudioManager.STREAM_MUSIC) / max;
    }

    LinearLayout levelPanel(LevelSlider slider, String glyph) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(Ui.dp(8), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        panel.setBackground(Ui.glass(24, true));
        TextView icon = Ui.tv(this, glyph, 20, Ui.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        panel.addView(icon, new LinearLayout.LayoutParams(-1, Ui.dp(28)));
        panel.addView(slider, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView label = Ui.tv(this, slider.label, 9.5f, Ui.SUB, true);
        label.setGravity(Gravity.CENTER);
        panel.addView(label, new LinearLayout.LayoutParams(-1, Ui.dp(22)));
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
            float cx = getWidth() / 2f, top = Ui.dp(8), bottom = getHeight() - Ui.dp(8);
            paint.setColor(0x553FFFFF); paint.setStrokeWidth(Ui.dp(4)); paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx, top, cx, bottom, paint);
            float y = bottom - level * (bottom - top);
            paint.setColor(Ui.accent); canvas.drawLine(cx, y, cx, bottom, paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(cx, y, Ui.dp(7), paint);
            paint.setColor(0xFF18202D); canvas.drawCircle(cx, y, Ui.dp(3), paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                float top = Ui.dp(8), bottom = getHeight() - Ui.dp(8);
                setLevel(1f - Math.max(0f, Math.min(1f, (event.getY() - top) / Math.max(1f, bottom - top))));
                if (listener != null) listener.onChanged(level);
                return true;
            }
            return true;
        }
    }

    @Override public void onBackPressed() {
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
        io.shutdown();
        super.onDestroy();
    }

    private void setVideoPlayAnimation(TextView button, boolean playing) {
        // Keep playback feedback clear without a pulsing/radiant animation.
        button.clearAnimation();
        button.setScaleX(1f); button.setScaleY(1f);
        button.setText(playing ? "Ⅱ  Playing" : "▶  Play");
    }


    /** Stable, non-pulsing play/pause control; playback does not depend on any animation asset. */
    private static final class PlayAnimationImageView extends ImageView {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean playing = false;
        PlayAnimationImageView(android.content.Context context) { super(context); setContentDescription("Play or pause"); }
        void setPlaying(boolean value) { playing = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * .5f, cy = getHeight() * .5f, unit = Math.min(getWidth(), getHeight()) * .24f;
            paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.FILL);
            if (!playing) {
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(cx-unit*.55f, cy-unit); path.lineTo(cx+unit, cy); path.lineTo(cx-unit*.55f, cy+unit); path.close(); canvas.drawPath(path, paint);
            } else {
                canvas.drawRoundRect(cx-unit*.75f, cy-unit, cx-unit*.18f, cy+unit, unit*.12f, unit*.12f, paint);
                canvas.drawRoundRect(cx+unit*.18f, cy-unit, cx+unit*.75f, cy+unit, unit*.12f, unit*.12f, paint);
            }
        }
    }
}
