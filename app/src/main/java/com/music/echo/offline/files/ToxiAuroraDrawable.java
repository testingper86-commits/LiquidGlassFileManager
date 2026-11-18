package com.music.echo.offline.files;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Cached, theme-reactive aurora light field for secondary app surfaces. */
final class ToxiAuroraDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private int cachedWidth = -1, cachedHeight = -1, cachedAccent, cachedAccentEnd, alpha = 255;
    private boolean cachedLightMode;
    private Shader primary, secondary, tertiary;

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int width = bounds.width(), height = bounds.height();
        if (width <= 0 || height <= 0) return;
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.drawColor(ToxiVisualSystem.CANVAS);
        ensureShaders(width, height);
        paint.setAlpha(alpha);
        paint.setShader(primary);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(secondary);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(tertiary);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setAlpha(255);
        canvas.restore();
    }

    private void ensureShaders(int width, int height) {
        int accent = ToxiVisualSystem.ACCENT;
        int accentEnd = ToxiThemeSystem.ACCENT_ENDS[ToxiVisualSystem.THEME_INDEX];
        boolean light = ToxiVisualSystem.LIGHT_MODE;
        if (width == cachedWidth && height == cachedHeight && accent == cachedAccent
                && accentEnd == cachedAccentEnd && light == cachedLightMode) return;
        cachedWidth = width;
        cachedHeight = height;
        cachedAccent = accent;
        cachedAccentEnd = accentEnd;
        cachedLightMode = light;
        float radius = Math.max(width, height) * 0.76f;
        primary = new RadialGradient(width * 0.18f, height * 0.22f, radius,
                new int[]{withAlpha(accent, light ? 24 : 42), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP);
        secondary = new RadialGradient(width * 0.86f, height * 0.58f, radius * 0.82f,
                new int[]{withAlpha(accentEnd, light ? 17 : 30), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP);
        int blend = ToxiIcons.mix(accent, accentEnd, 0.5f);
        tertiary = new RadialGradient(width * 0.52f, height * 0.94f, radius * 0.58f,
                new int[]{withAlpha(blend, light ? 10 : 18), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override public void setAlpha(int value) {
        alpha = Math.max(0, Math.min(255, value));
        invalidateSelf();
    }

    @Override public void setColorFilter(android.graphics.ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
