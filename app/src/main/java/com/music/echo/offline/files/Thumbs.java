package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Async, cached thumbnails for images and videos. Recycled rows skip decoding entirely. */
final class Thumbs {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService EX = Executors.newFixedThreadPool(2);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    static void bind(final ImageView iv, final Entry e, final int px) {
        final String key = e.f.getAbsolutePath() + "|" + e.mod;
        iv.setTag(key);
        boolean needsThumbnail = e.kind == FileKinds.IMAGE || e.kind == FileKinds.VIDEO;
        if (needsThumbnail) { GlowRing.attach(iv); GlowRing.setActive(iv, true); }
        else GlowRing.detach(iv);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            GlowRing.setActive(iv, false);
            return;
        }
        iv.setImageDrawable(Glyphs.tile(e.kind));
        if (!needsThumbnail) return;
        EX.execute(() -> {
            if (!key.equals(iv.getTag())) return;
            final Bitmap t = e.kind == FileKinds.IMAGE ? image(e, px) : video(e, px);
            if (t == null) { UI.post(() -> { if (key.equals(iv.getTag())) GlowRing.setActive(iv, false); }); return; }
            CACHE.put(key, t);
            UI.post(() -> { if (key.equals(iv.getTag())) { iv.setImageBitmap(t); GlowRing.setActive(iv, false); } });
        });
    }

    private static Bitmap image(Entry e, int px) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(e.f.getAbsolutePath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            return BitmapFactory.decodeFile(e.f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap video(Entry e, int px) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(e.f.getAbsolutePath());
            Bitmap b = r.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (b == null) return null;
            int w = b.getWidth(), h = b.getHeight();
            float sc = (float) px / Math.min(w, h);
            if (sc < 1f) b = Bitmap.createScaledBitmap(b, Math.max(1, (int) (w * sc)), Math.max(1, (int) (h * sc)), true);
            return b;
        } catch (Throwable t) {
            return null;
        } finally {
            try { r.release(); } catch (Throwable ignored) { }
        }
    }
}
