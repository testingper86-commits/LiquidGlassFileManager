package com.music.echo.offline.files;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

/** Brief touch-triggered radiant halo. Never animates while the icon is idle. */
public final class TouchGradientRingDrawable extends Drawable {
    private final Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint middle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float rotation = 0f;
    private float intensity = 0f;
    private ValueAnimator animator;
    private final int[] colors = {
        Color.rgb(40, 235, 255), Color.rgb(45, 125, 255),
        Color.rgb(119, 82, 255), Color.rgb(235, 65, 255),
        Color.rgb(255, 92, 150), Color.rgb(255, 145, 72),
        Color.rgb(255, 213, 112), Color.rgb(90, 245, 225),
        Color.rgb(40, 235, 255)
    };

    public TouchGradientRingDrawable(android.content.Context context) {
        density = context.getResources().getDisplayMetrics().density;
        outer.setStyle(Paint.Style.STROKE); outer.setStrokeCap(Paint.Cap.ROUND);
        outer.setStrokeWidth(8.0f * density);
        middle.setStyle(Paint.Style.STROKE); middle.setStrokeCap(Paint.Cap.ROUND);
        middle.setStrokeWidth(4.2f * density);
        core.setStyle(Paint.Style.STROKE); core.setStrokeCap(Paint.Cap.ROUND);
        core.setStrokeWidth(1.55f * density);
    }

    public void playTouch() {
        if (animator != null) animator.cancel();
        intensity = 0f;
        rotation = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(980L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            rotation = 360f * f;
            if (f < 0.18f) intensity = f / 0.18f;
            else if (f < 0.60f) intensity = 1f;
            else intensity = Math.max(0f, (1f - f) / 0.40f);
            invalidateSelf();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                intensity = 0f;
                invalidateSelf();
            }
        });
        animator.start();
    }

    @Override public void draw(Canvas canvas) {
        if (intensity <= 0.005f) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        float inset = Math.max(3.0f * density, Math.min(b.width(), b.height()) * 0.10f);
        RectF r = new RectF(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        if (r.width() <= 0 || r.height() <= 0) return;
        float cx = r.centerX(), cy = r.centerY();
        SweepGradient shader = new SweepGradient(cx, cy, colors, null);
        outer.setShader(shader); middle.setShader(shader); core.setShader(shader);
        outer.setAlpha((int)(42 * intensity));
        middle.setAlpha((int)(112 * intensity));
        core.setAlpha((int)(245 * intensity));
        outer.setMaskFilter(new android.graphics.BlurMaskFilter(4.2f * density, android.graphics.BlurMaskFilter.Blur.NORMAL));
        float radius = Math.min(r.width(), r.height()) * 0.43f;
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        canvas.drawCircle(cx, cy, radius, outer);
        canvas.drawCircle(cx, cy, radius, middle);
        canvas.drawCircle(cx, cy, radius, core);
        canvas.restore();
    }

    @Override protected void onBoundsChange(Rect bounds) { super.onBoundsChange(bounds); }
    @Override public void setAlpha(int alpha) { outer.setAlpha(alpha); middle.setAlpha(alpha); core.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { outer.setColorFilter(filter); middle.setColorFilter(filter); core.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
