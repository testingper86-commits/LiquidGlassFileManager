package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.Resources;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Lightweight animated gradient border. The center remains transparent. */
public final class GradientRingView extends View {
    private static final int[] DEFAULT_COLORS = {0xFF4285F4, 0xFF9B72CB, 0xFFD96570, 0xFFF9AB00, 0xFF4285F4};
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final ValueAnimator rotationAnimator;
    private final ValueAnimator pulseAnimator;
    private final ValueAnimator fadeAnimator;
    private SweepGradient gradient;
    private int[] colors = DEFAULT_COLORS;
    private float strokeWidth;
    private float glowRadius;
    private float cornerRadius;
    private float rotation;
    private float pulse = 1f;
    private boolean active;
    private boolean loopsRunning;
    private int speedMs = 2000;

    public GradientRingView(Context context) { this(context, null); }

    public GradientRingView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public GradientRingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        strokeWidth = 3f * density;
        glowRadius = 8f * density;
        cornerRadius = 12f * density;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GradientRingView, defStyleAttr, 0);
            strokeWidth = a.getDimension(R.styleable.GradientRingView_ringStrokeWidth, strokeWidth);
            glowRadius = a.getDimension(R.styleable.GradientRingView_ringGlowRadius, glowRadius);
            cornerRadius = a.getDimension(R.styleable.GradientRingView_ringCornerRadius, cornerRadius);
            speedMs = Math.max(250, a.getInt(R.styleable.GradientRingView_ringSpeed, speedMs));
            int colorRes = a.getResourceId(R.styleable.GradientRingView_ringColors, 0);
            if (colorRes != 0) {
                try { int[] loaded = getResources().getIntArray(colorRes); if (loaded.length >= 2) colors = closeColors(loaded); }
                catch (Resources.NotFoundException ignored) { }
            }
            a.recycle();
        }
        gradient = new SweepGradient(0f, 0f, colors, null);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(strokeWidth);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setShader(gradient);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(strokeWidth + glowRadius * 0.65f);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setShader(gradient);
        glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, glowRadius * 0.72f), BlurMaskFilter.Blur.NORMAL));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(speedMs);
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.addUpdateListener(a -> { rotation = (Float) a.getAnimatedValue(); invalidate(); });
        pulseAnimator = ValueAnimator.ofFloat(0.72f, 1f);
        pulseAnimator.setDuration(1350L);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.addUpdateListener(a -> { pulse = (Float) a.getAnimatedValue(); invalidate(); });
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeAnimator.setDuration(180L);
        fadeAnimator.addUpdateListener(a -> { setAlpha((Float) a.getAnimatedValue()); invalidate(); });
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!active) stopLoops();
            }
        });
        setAlpha(0f);
    }

    private static int[] closeColors(int[] source) {
        int[] out = new int[source.length + 1];
        System.arraycopy(source, 0, out, 0, source.length);
        out[out.length - 1] = source[0];
        return out;
    }

    public void setCornerRadius(float radius) { cornerRadius = Math.max(0f, radius); invalidate(); }
    public void setStrokeWidth(float width) {
        strokeWidth = Math.max(1f, width);
        ringPaint.setStrokeWidth(strokeWidth);
        glowPaint.setStrokeWidth(strokeWidth + glowRadius * 0.65f);
        invalidate();
    }
    public void setGlowRadius(float radius) {
        glowRadius = Math.max(0f, radius);
        glowPaint.setStrokeWidth(strokeWidth + glowRadius * 0.65f);
        glowPaint.setMaskFilter(glowRadius > 0f ? new BlurMaskFilter(Math.max(1f, glowRadius * 0.72f), BlurMaskFilter.Blur.NORMAL) : null);
        invalidate();
    }
    public void setSpeed(int durationMs) {
        speedMs = Math.max(250, durationMs);
        rotationAnimator.setDuration(speedMs);
    }
    public void setColors(int[] next) {
        if (next == null || next.length < 2) return;
        colors = closeColors(next);
        gradient = new SweepGradient(0f, 0f, colors, null);
        ringPaint.setShader(gradient);
        glowPaint.setShader(gradient);
        invalidate();
    }

    public void start() {
        active = true;
        if (getVisibility() != VISIBLE || !isAttachedToWindow()) return;
        startLoops();
        fadeAnimator.cancel();
        fadeAnimator.setFloatValues(getAlpha(), 1f);
        fadeAnimator.start();
    }

    public void stop() {
        active = false;
        fadeAnimator.cancel();
        if (!isAttachedToWindow() || getVisibility() != VISIBLE) { setAlpha(0f); stopLoops(); return; }
        fadeAnimator.setFloatValues(getAlpha(), 0f);
        fadeAnimator.start();
    }

    private void startLoops() {
        if (loopsRunning || !active || getVisibility() != VISIBLE || !isAttachedToWindow()) return;
        loopsRunning = true;
        if (!rotationAnimator.isStarted()) rotationAnimator.start();
        if (!pulseAnimator.isStarted()) pulseAnimator.start();
    }

    private void stopLoops() {
        loopsRunning = false;
        if (rotationAnimator.isStarted()) rotationAnimator.cancel();
        if (pulseAnimator.isStarted()) pulseAnimator.cancel();
    }

    private void syncVisibility() {
        if (active && getVisibility() == VISIBLE && isAttachedToWindow()) startLoops();
        else stopLoops();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); syncVisibility(); if (active) start(); }
    @Override protected void onDetachedFromWindow() {
        rotationAnimator.cancel(); pulseAnimator.cancel(); fadeAnimator.cancel();
        loopsRunning = false;
        super.onDetachedFromWindow();
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) { super.onVisibilityChanged(changedView, visibility); syncVisibility(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = strokeWidth * 0.5f + Math.min(glowRadius * 0.35f, Math.min(w, h) * 0.08f);
        bounds.set(inset, inset, Math.max(inset, w - inset), Math.max(inset, h - inset));
        gradient = new SweepGradient(w * 0.5f, h * 0.5f, colors, null);
        ringPaint.setShader(gradient);
        glowPaint.setShader(gradient);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getAlpha() <= 0f || bounds.isEmpty()) return;
        int save = canvas.save();
        canvas.rotate(rotation, getWidth() * 0.5f, getHeight() * 0.5f);
        glowPaint.setAlpha(Math.round(68f * pulse));
        ringPaint.setAlpha(Math.round(238f * pulse));
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, glowPaint);
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, ringPaint);
        canvas.restoreToCount(save);
    }
}
