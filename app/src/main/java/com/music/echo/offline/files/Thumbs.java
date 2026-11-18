package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.content.ComponentCallbacks2;
import android.util.LruCache;
import android.widget.ImageView;

/** Async, cached thumbnails for images and videos. Recycled rows skip decoding entirely. */
final class Thumbs {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final Handler UI = new Handler(Looper.getMainLooper());

    static void trimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) CACHE.evictAll();
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) CACHE.trimToSize(CACHE.maxSize() / 4);
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) CACHE.trimToSize(CACHE.maxSize() / 2);
    }

    static void bind(final ImageView iv, final Entry e, final int px) {
        final String key = e.f.getAbsolutePath() + "|" + e.mod + "|" + Math.max(1, px);
        iv.setTag(key);
        boolean needsThumbnail = e.kind == FileKinds.IMAGE || e.kind == FileKinds.VIDEO;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageDrawable(ToxiIcons.symbol(FileKinds.glyph(e.kind), ToxiVisualSystem.TEXT_SECONDARY));
        if (!needsThumbnail) return;
        TaskCoordinator.tryExecute(TaskCoordinator.Lane.THUMBNAIL, () -> {
            if (!key.equals(iv.getTag())) return;
            final Bitmap t = e.kind == FileKinds.IMAGE ? image(e, px) : video(e, px);
            if (t == null) return;
            CACHE.put(key, t);
            UI.post(() -> { if (key.equals(iv.getTag())) { iv.setImageBitmap(t); } });
        });
    }

    private static Bitmap image(Entry e, int px) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            String path = e.f.getAbsolutePath();
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int target = Math.max(1, px);
            int sample = 1;
            while (sample <= (1 << 29)
                    && o.outWidth / (sample * 2) >= target
                    && o.outHeight / (sample * 2) >= target) {
                sample *= 2;
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            decode.inPreferredConfig = Bitmap.Config.RGB_565;
            decode.inDither = true;
            return BitmapFactory.decodeFile(path, decode);
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
            int target = Math.max(1, px);
            float sc = Math.min(1f, (float) target / Math.max(w, h));
            if (sc < 1f) {
                Bitmap scaled = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * sc)),
                        Math.max(1, Math.round(h * sc)), true);
                if (scaled != b && !b.isRecycled()) b.recycle();
                b = scaled;
            }
            return b;
        } catch (Throwable t) {
            return null;
        } finally {
            try { r.release(); } catch (Throwable ignored) { }
        }
    }
}
