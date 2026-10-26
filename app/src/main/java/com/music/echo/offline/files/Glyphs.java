package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Hand-drawn vector icons (24x24 grid) and the coloured file-type tiles. No image assets needed. */
final class Glyphs {
    static final int FOLDER = 0, IMAGE = 1, VIDEO = 2, AUDIO = 3, DOC = 4, ARCHIVE = 5, APK = 6, FILE = 7,
            HOME = 8, FILES = 9, STAR = 10, TUNE = 11, SEARCH = 12, MORE = 13, BACK = 14, NEXT = 15,
            CLOSE = 16, CHECK = 17, COPY = 18, CUT = 19, TRASH = 20, SHARE = 21, INFO = 22,
            FOLDER_PLUS = 23, SORT = 24, OPEN = 25, PLAY = 26, PAUSE = 27, PASTE = 28,
            PALETTE = 29, SPARKLE = 30, VIBRATE = 31, EYE = 32, SHIELD = 33, CLOCK = 34,
            LOCK = 35, WIFI = 36, DATABASE = 37, ANALYTICS = 38, DOWNLOAD = 39, CLOUD = 40,
            MUSIC = 41, SUN = 42, REFRESH = 43, SETTINGS = 44;

    static String label(int kind) {
        switch (kind) {
            case FOLDER: return "Folder"; case IMAGE: return "Image"; case VIDEO: return "Video";
            case AUDIO: return "Audio"; case DOC: return "Document"; case ARCHIVE: return "Archive";
            case APK: return "Android package"; case FILE: return "File"; case HOME: return "Home";
            case FILES: return "Files"; case STAR: return "Favorite"; case TUNE: return "Filter and sort";
            case SEARCH: return "Search"; case MORE: return "More options"; case BACK: return "Back";
            case NEXT: return "Next"; case CLOSE: return "Close"; case CHECK: return "Select";
            case COPY: return "Copy"; case CUT: return "Cut"; case TRASH: return "Delete";
            case SHARE: return "Share"; case INFO: return "Information"; case FOLDER_PLUS: return "New folder";
            case SORT: return "Sort"; case OPEN: return "Open"; case PLAY: return "Play";
            case PAUSE: return "Pause"; case PASTE: return "Paste"; case PALETTE: return "Theme";
            case SPARKLE: return "Appearance effects"; case VIBRATE: return "Haptic feedback";
            case EYE: return "Visibility"; case SHIELD: return "Security"; case CLOCK: return "Recent";
            case LOCK: return "Lock"; case WIFI: return "Wi-Fi transfer"; case DATABASE: return "Storage";
            case ANALYTICS: return "Analysis"; case DOWNLOAD: return "Download"; case CLOUD: return "Cloud";
            case MUSIC: return "Music"; case SUN: return "Light appearance"; case REFRESH: return "Refresh";
            case SETTINGS: return "Settings"; default: return "Action";
        }
    }

    static Drawable get(int kind, int color) { return new G(kind, color); }

    static Drawable tile(int fileKind) { return new Tile(fileKind); }

    static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static final class G extends Drawable {
        final int kind;
        final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint fs = new Paint(Paint.ANTI_ALIAS_FLAG);

        G(int kind, int color) {
            this.kind = kind;
            f.setStyle(Paint.Style.FILL);
            f.setColor(color);
            s.setStyle(Paint.Style.STROKE);
            s.setColor(color);
            s.setStrokeCap(Paint.Cap.ROUND);
            s.setStrokeJoin(Paint.Join.ROUND);
            s.setStrokeWidth(1.9f);
            fs.setStyle(Paint.Style.FILL_AND_STROKE);
            fs.setColor(color);
            fs.setStrokeJoin(Paint.Join.ROUND);
            fs.setStrokeWidth(1.5f);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.width() <= 0) return;
            c.save();
            c.translate(b.left, b.top);
            c.scale(b.width() / 24f, b.height() / 24f);
            paint(c);
            c.restore();
        }

        void poly(Canvas c, Paint p, boolean close, float... v) {
            Path pa = new Path();
            pa.moveTo(v[0], v[1]);
            for (int i = 2; i + 1 < v.length; i += 2) pa.lineTo(v[i], v[i + 1]);
            if (close) pa.close();
            c.drawPath(pa, p);
        }

        void paint(Canvas c) {
            switch (kind) {
                case FOLDER:
                    poly(c, fs, true, 3, 6, 9, 6, 11, 8.5f, 21, 8.5f, 21, 19, 3, 19);
                    break;
                case IMAGE:
                    c.drawRoundRect(3.5f, 4.5f, 20.5f, 19.5f, 3f, 3f, s);
                    poly(c, fs, true, 6, 17, 10, 11.5f, 13.5f, 15.5f, 15.5f, 13, 19, 17);
                    c.drawCircle(16.5f, 8.5f, 1.8f, f);
                    break;
                case VIDEO:
                    poly(c, fs, true, 8.5f, 5.5f, 19, 12, 8.5f, 18.5f);
                    break;
                case AUDIO:
                    c.drawCircle(8.5f, 17f, 3f, f);
                    s.setStrokeWidth(2.2f);
                    c.drawLine(11f, 17f, 11f, 5.5f, s);
                    s.setStrokeWidth(1.9f);
                    poly(c, fs, true, 11, 5.5f, 18, 8, 18, 11.5f, 11, 9.5f);
                    break;
                case DOC:
                    poly(c, s, true, 6.5f, 3.5f, 14, 3.5f, 18.5f, 8, 18.5f, 20.5f, 6.5f, 20.5f);
                    poly(c, s, false, 14, 3.5f, 14, 8, 18.5f, 8);
                    c.drawLine(9f, 12.5f, 16f, 12.5f, s);
                    c.drawLine(9f, 15.5f, 16f, 15.5f, s);
                    c.drawLine(9f, 18.5f, 13f, 18.5f, s);
                    break;
                case ARCHIVE:
                    c.drawRoundRect(5.5f, 3.5f, 18.5f, 20.5f, 2.5f, 2.5f, s);
                    for (int i = 0; i < 5; i++) {
                        float y = 5f + i * 2.3f;
                        boolean even = i % 2 == 0;
                        c.drawRect(even ? 10.2f : 12f, y, even ? 12f : 13.8f, y + 1.5f, f);
                    }
                    s.setStrokeWidth(1.4f);
                    c.drawRoundRect(10.3f, 15.5f, 13.7f, 19f, 1f, 1f, s);
                    s.setStrokeWidth(1.9f);
                    break;
                case APK:
                    c.drawArc(new RectF(5.5f, 8.5f, 18.5f, 21.5f), 180, 180, true, f);
                    c.drawRoundRect(5.5f, 15f, 18.5f, 19.5f, 1.5f, 1.5f, f);
                    s.setStrokeWidth(1.5f);
                    c.drawLine(8.5f, 6.2f, 9.9f, 8.9f, s);
                    c.drawLine(15.5f, 6.2f, 14.1f, 8.9f, s);
                    s.setStrokeWidth(1.9f);
                    Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
                    eye.setColor(Color.argb(170, 0, 0, 0));
                    c.drawCircle(9.6f, 12.3f, 1f, eye);
                    c.drawCircle(14.4f, 12.3f, 1f, eye);
                    break;
                case FILE:
                    poly(c, s, true, 6.5f, 3.5f, 14, 3.5f, 18.5f, 8, 18.5f, 20.5f, 6.5f, 20.5f);
                    poly(c, s, false, 14, 3.5f, 14, 8, 18.5f, 8);
                    break;
                case HOME:
                    poly(c, s, false, 3.5f, 11.5f, 12, 4, 20.5f, 11.5f);
                    poly(c, s, false, 6, 10, 6, 20, 10.2f, 20, 10.2f, 14.5f, 13.8f, 14.5f, 13.8f, 20, 18, 20, 18, 10);
                    break;
                case FILES:
                    c.drawRoundRect(4f, 4f, 10.5f, 10.5f, 2f, 2f, s);
                    c.drawRoundRect(13.5f, 4f, 20f, 10.5f, 2f, 2f, s);
                    c.drawRoundRect(4f, 13.5f, 10.5f, 20f, 2f, 2f, s);
                    c.drawRoundRect(13.5f, 13.5f, 20f, 20f, 2f, 2f, s);
                    break;
                case STAR: {
                    Path st = new Path();
                    for (int i = 0; i < 10; i++) {
                        double a = Math.toRadians(-90 + i * 36);
                        float r = (i % 2 == 0) ? 9f : 4.2f;
                        float x = (float) (12 + r * Math.cos(a));
                        float y = (float) (12.8 + r * Math.sin(a));
                        if (i == 0) st.moveTo(x, y); else st.lineTo(x, y);
                    }
                    st.close();
                    c.drawPath(st, fs);
                    break;
                }
                case SETTINGS: {
                    c.drawCircle(12f, 12f, 3.3f, s);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians(i * 45);
                        float x1 = (float) (12 + 5.3 * Math.cos(a));
                        float y1 = (float) (12 + 5.3 * Math.sin(a));
                        float x2 = (float) (12 + 8.2 * Math.cos(a));
                        float y2 = (float) (12 + 8.2 * Math.sin(a));
                        c.drawLine(x1, y1, x2, y2, s);
                    }
                    c.drawCircle(12f, 12f, 8.4f, s);
                    break;
                }
                case TUNE:
                    c.drawLine(4f, 7f, 20f, 7f, s);
                    c.drawLine(4f, 12f, 20f, 12f, s);
                    c.drawLine(4f, 17f, 20f, 17f, s);
                    c.drawCircle(9f, 7f, 2.3f, f);
                    c.drawCircle(15.5f, 12f, 2.3f, f);
                    c.drawCircle(8f, 17f, 2.3f, f);
                    break;
                case SEARCH:
                    c.drawCircle(10.5f, 10.5f, 5.6f, s);
                    c.drawLine(14.8f, 14.8f, 20f, 20f, s);
                    break;
                case MORE:
                    c.drawCircle(12f, 5.5f, 1.9f, f);
                    c.drawCircle(12f, 12f, 1.9f, f);
                    c.drawCircle(12f, 18.5f, 1.9f, f);
                    break;
                case BACK:
                    s.setStrokeWidth(2.3f);
                    poly(c, s, false, 14.5f, 5.5f, 8, 12, 14.5f, 18.5f);
                    break;
                case NEXT:
                    s.setStrokeWidth(2.3f);
                    poly(c, s, false, 9.5f, 5.5f, 16, 12, 9.5f, 18.5f);
                    break;
                case CLOSE:
                    s.setStrokeWidth(2.2f);
                    c.drawLine(6f, 6f, 18f, 18f, s);
                    c.drawLine(18f, 6f, 6f, 18f, s);
                    break;
                case CHECK:
                    s.setStrokeWidth(2.6f);
                    poly(c, s, false, 5.5f, 12.5f, 10, 17, 18.5f, 7.5f);
                    break;
                case COPY:
                    c.drawRoundRect(8.5f, 8.5f, 19.5f, 19.5f, 2.2f, 2.2f, s);
                    poly(c, s, false, 5.5f, 15.5f, 5.5f, 5.5f, 15.5f, 5.5f, 15.5f, 8);
                    break;
                case CUT:
                    c.drawCircle(7f, 17.5f, 2.6f, s);
                    c.drawCircle(17f, 17.5f, 2.6f, s);
                    c.drawLine(8.8f, 15.6f, 16f, 4.5f, s);
                    c.drawLine(15.2f, 15.6f, 8f, 4.5f, s);
                    break;
                case TRASH:
                    c.drawLine(4.5f, 7f, 19.5f, 7f, s);
                    poly(c, s, false, 9.5f, 7, 9.5f, 4.5f, 14.5f, 4.5f, 14.5f, 7);
                    poly(c, s, false, 6.5f, 7, 7.5f, 20, 16.5f, 20, 17.5f, 7);
                    c.drawLine(10.3f, 10.5f, 10.3f, 16.5f, s);
                    c.drawLine(13.7f, 10.5f, 13.7f, 16.5f, s);
                    break;
                case SHARE:
                    c.drawCircle(6.5f, 12f, 2.5f, f);
                    c.drawCircle(17.5f, 6f, 2.5f, f);
                    c.drawCircle(17.5f, 18f, 2.5f, f);
                    c.drawLine(8.6f, 10.8f, 15.4f, 7.2f, s);
                    c.drawLine(8.6f, 13.2f, 15.4f, 16.8f, s);
                    break;
                case INFO:
                    c.drawCircle(12f, 12f, 8.6f, s);
                    c.drawCircle(12f, 8f, 1.15f, f);
                    c.drawLine(12f, 11.2f, 12f, 16.4f, s);
                    break;
                case FOLDER_PLUS:
                    poly(c, s, true, 3, 6, 9, 6, 11, 8.5f, 21, 8.5f, 21, 19, 3, 19);
                    c.drawLine(12f, 11.8f, 12f, 16.6f, s);
                    c.drawLine(9.6f, 14.2f, 14.4f, 14.2f, s);
                    break;
                case SORT:
                    c.drawLine(4f, 7f, 20f, 7f, s);
                    c.drawLine(4f, 12f, 15f, 12f, s);
                    c.drawLine(4f, 17f, 10f, 17f, s);
                    break;
                case OPEN:
                    poly(c, s, false, 14, 4.5f, 19.5f, 4.5f, 19.5f, 10);
                    c.drawLine(19.5f, 4.5f, 11f, 13f, s);
                    poly(c, s, false, 17, 14, 17, 19, 5, 19, 5, 7, 10, 7);
                    break;
                case PLAY:
                    poly(c, fs, true, 7.5f, 4.5f, 19.5f, 12, 7.5f, 19.5f);
                    break;
                case PAUSE:
                    c.drawRoundRect(6.5f, 5f, 10.5f, 19f, 1.2f, 1.2f, f);
                    c.drawRoundRect(13.5f, 5f, 17.5f, 19f, 1.2f, 1.2f, f);
                    break;
                case PASTE:
                    c.drawRoundRect(5.5f, 5f, 18.5f, 20.5f, 2.2f, 2.2f, s);
                    c.drawRoundRect(9f, 3f, 15f, 7f, 1.5f, 1.5f, f);
                    c.drawLine(9f, 12f, 15f, 12f, s);
                    c.drawLine(9f, 15.5f, 15f, 15.5f, s);
                    break;
                case PALETTE:
                    c.drawCircle(12f, 12f, 8.5f, s);
                    c.drawCircle(8f, 9f, 1.35f, f); c.drawCircle(12f, 6.9f, 1.35f, f);
                    c.drawCircle(16f, 9.3f, 1.35f, f); c.drawCircle(8.5f, 14f, 1.35f, f);
                    c.drawRoundRect(13f, 14f, 19f, 20f, 2f, 2f, f);
                    break;
                case SPARKLE:
                    poly(c, fs, true, 12, 2.5f, 14.2f, 9.4f, 21, 12, 14.2f, 14.2f, 12, 21, 9.8f, 14.2f, 3, 12, 9.8f, 9.8f);
                    c.drawLine(5f, 3.5f, 5f, 8f, s); c.drawLine(2.8f, 5.8f, 7.2f, 5.8f, s);
                    c.drawLine(19f, 16f, 19f, 21f, s); c.drawLine(16.5f, 18.5f, 21.5f, 18.5f, s);
                    break;
                case VIBRATE:
                    c.drawRoundRect(7f, 4f, 17f, 20f, 2.5f, 2.5f, s);
                    c.drawLine(10f, 7f, 14f, 7f, s); c.drawLine(10f, 17f, 14f, 17f, s);
                    c.drawLine(3.5f, 7f, 2.5f, 9f, s); c.drawLine(2.5f, 9f, 3.5f, 11f, s);
                    c.drawLine(20.5f, 7f, 21.5f, 9f, s); c.drawLine(21.5f, 9f, 20.5f, 11f, s);
                    c.drawLine(3.5f, 14f, 2.5f, 16f, s); c.drawLine(2.5f, 16f, 3.5f, 18f, s);
                    c.drawLine(20.5f, 14f, 21.5f, 16f, s); c.drawLine(21.5f, 16f, 20.5f, 18f, s);
                    break;
                case EYE:
                    c.drawOval(new RectF(2.5f, 6f, 21.5f, 18f), s); c.drawCircle(12f, 12f, 3.5f, s); c.drawCircle(12f, 12f, 1.25f, f);
                    break;
                case SHIELD:
                    poly(c, s, true, 12, 2.5f, 20, 5.5f, 19.2f, 13.2f, 16f, 18f, 12, 21.5f, 8f, 18f, 4.8f, 13.2f, 4f, 5.5f);
                    poly(c, s, false, 8.3f, 12.1f, 10.7f, 14.4f, 15.8f, 9.2f);
                    break;
                case CLOCK:
                    c.drawCircle(12f, 12f, 8.5f, s); c.drawLine(12f, 6.8f, 12f, 12f, s); c.drawLine(12f, 12f, 16.2f, 14.3f, s);
                    break;
                case LOCK:
                    c.drawRoundRect(4.5f, 10f, 19.5f, 21f, 2.4f, 2.4f, s);
                    c.drawArc(new RectF(7.2f, 3f, 16.8f, 15f), 180f, 180f, false, s);
                    c.drawCircle(12f, 14.5f, 1.2f, f); c.drawLine(12f, 15.5f, 12f, 18f, s);
                    break;
                case WIFI:
                    c.drawArc(new RectF(3f, 4f, 21f, 20f), 220f, 100f, false, s);
                    c.drawArc(new RectF(6.5f, 8f, 17.5f, 19f), 220f, 100f, false, s);
                    c.drawArc(new RectF(9.5f, 12f, 14.5f, 19f), 220f, 100f, false, s); c.drawCircle(12f, 19f, 1.3f, f);
                    break;
                case DATABASE:
                    c.drawOval(new RectF(4f, 3f, 20f, 9f), s); c.drawLine(4f, 6f, 4f, 18f, s); c.drawLine(20f, 6f, 20f, 18f, s);
                    c.drawArc(new RectF(4f, 9f, 20f, 15f), 0f, 180f, false, s); c.drawArc(new RectF(4f, 15f, 20f, 21f), 0f, 180f, false, s); c.drawArc(new RectF(4f, 15f, 20f, 21f), 180f, 180f, false, s);
                    break;
                case ANALYTICS:
                    c.drawLine(4f, 20f, 20f, 20f, s); c.drawLine(4f, 20f, 4f, 4f, s);
                    c.drawRoundRect(7f, 12f, 10f, 18f, 1f, 1f, f); c.drawRoundRect(12f, 8f, 15f, 18f, 1f, 1f, f); c.drawRoundRect(17f, 5f, 20f, 18f, 1f, 1f, f);
                    break;
                case DOWNLOAD:
                    c.drawLine(12f, 3.5f, 12f, 15f, s); poly(c, s, false, 7f, 10.5f, 12f, 15.5f, 17f, 10.5f);
                    c.drawLine(4f, 20f, 20f, 20f, s); c.drawLine(4f, 16f, 4f, 20f, s); c.drawLine(20f, 16f, 20f, 20f, s);
                    break;
                case CLOUD:
                    c.drawRoundRect(4f, 10f, 20f, 19f, 4.5f, 4.5f, s); c.drawCircle(9f, 10f, 4f, s); c.drawCircle(14f, 8f, 5f, s); c.drawCircle(18f, 12f, 3f, s);
                    break;
                case MUSIC:
                    c.drawLine(10f, 5f, 10f, 17f, s); c.drawLine(10f, 5f, 19f, 3.5f, s); c.drawLine(19f, 3.5f, 19f, 15f, s);
                    c.drawCircle(6.8f, 17.5f, 3f, f); c.drawCircle(15.8f, 15.5f, 3f, f);
                    break;
                case SUN:
                    c.drawCircle(12f, 12f, 4f, s); c.drawLine(12f, 2.5f, 12f, 5f, s); c.drawLine(12f, 19f, 12f, 21.5f, s);
                    c.drawLine(2.5f, 12f, 5f, 12f, s); c.drawLine(19f, 12f, 21.5f, 12f, s);
                    c.drawLine(5.3f, 5.3f, 7.1f, 7.1f, s); c.drawLine(16.9f, 16.9f, 18.7f, 18.7f, s); c.drawLine(16.9f, 7.1f, 18.7f, 5.3f, s); c.drawLine(5.3f, 18.7f, 7.1f, 16.9f, s);
                    break;
                case REFRESH:
                    c.drawArc(new RectF(4f, 4f, 20f, 20f), 40f, 280f, false, s); poly(c, fs, true, 16f, 3.5f, 20.5f, 4.5f, 19f, 9f);
                    break;
                default:
                    break;
            }
        }

        @Override public void setAlpha(int a) { f.setAlpha(a); s.setAlpha(a); fs.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { f.setColorFilter(cf); s.setColorFilter(cf); fs.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Rounded gradient tile with a white glyph, used for every file/folder icon. */
    static final class Tile extends Drawable {
        final int col;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Drawable g;
        final RectF r = new RectF();
        final RectF shine = new RectF();

        Tile(int kind) {
            col = FileKinds.color(kind);
            g = new G(FileKinds.glyph(kind), Color.WHITE);
            st.setStyle(Paint.Style.STROKE);
            st.setColor(Color.argb(128, 255, 255, 255));
            gloss.setShader(new LinearGradient(0, 0, 0, 1,
                    new int[]{Color.argb(116, 255, 255, 255), Color.argb(18, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                    new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override protected void onBoundsChange(Rect b) {
            p.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom, mix(col, 0xFFFFFFFF, 0.30f), col, Shader.TileMode.CLAMP));
            int in = (int) (b.width() * 0.2f);
            g.setBounds(b.left + in, b.top + in, b.right - in, b.bottom - in);
            st.setStrokeWidth(Math.max(1f, b.width() * 0.025f));
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            r.set(b);
            float rad = b.width() * 0.28f;
            c.drawRoundRect(r, rad, rad, p);
            // A compact top specular sheen and crisp outer rim make the file tiles read as glass.
            shine.set(b.left + b.width() * 0.035f, b.top + b.height() * 0.035f,
                    b.right - b.width() * 0.035f, b.top + b.height() * 0.49f);
            gloss.setShader(new LinearGradient(shine.left, shine.top, shine.left, shine.bottom,
                    new int[]{Color.argb(112, 255, 255, 255), Color.argb(22, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(shine, rad * 0.9f, rad * 0.9f, gloss);
            c.drawRoundRect(r, rad, rad, st);
            g.draw(c);
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
