package com.music.echo.offline.files;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Lifecycle-safe tap, hold and swipe feedback that never consumes native click dispatch. */
final class ToxiPowerTouchEngine {
    private static final long CHARGE_DELAY_MS = 420L;
    private static final float SWIPE_DISTANCE_DP = 18f;
    private ToxiPowerTouchEngine() { }

    static void attach(View target, int themeIndex) {
        if (target == null) return;
        float threshold = ToxiThemeEngine.spacing(SWIPE_DISTANCE_DP) * ToxiThemeEngine.profile().motionScale
                * target.getResources().getDisplayMetrics().density;
        TouchFeedback feedback = new TouchFeedback(target, threshold);
        target.setOnTouchListener(feedback);
        target.addOnAttachStateChangeListener(feedback);
    }

    private static boolean reducedMotion() {
        return ToxiVisualSystem.booleanPreference("reduce_motion", false);
    }

    private static final class TouchFeedback implements View.OnTouchListener, View.OnAttachStateChangeListener {
        private final View target;
        private final float threshold;
        private final Runnable charge = this::showCharge;
        private float downX, downY, lastX, lastY;
        private int pointerId = MotionEvent.INVALID_POINTER_ID;
        private boolean moved, charged, valid;
        private ValueAnimator animator;
        private EffectDrawable drawable;

        TouchFeedback(View target, float threshold) {
            this.target = target;
            this.threshold = threshold;
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelCharge();
                    pointerId = event.getPointerId(0);
                    downX = lastX = event.getX(0);
                    downY = lastY = event.getY(0);
                    moved = charged = false;
                    valid = true;
                    target.postDelayed(charge, ToxiThemeEngine.motionDuration(CHARGE_DELAY_MS));
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    invalidateGesture();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!valid) break;
                    int index = event.findPointerIndex(pointerId);
                    if (index < 0) {
                        invalidateGesture();
                        break;
                    }
                    lastX = event.getX(index);
                    lastY = event.getY(index);
                    if (Math.hypot(lastX - downX, lastY - downY) > threshold) {
                        moved = true;
                        cancelCharge();
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerId(event.getActionIndex()) == pointerId) invalidateGesture();
                    break;
                case MotionEvent.ACTION_UP:
                    cancelCharge();
                    if (valid && !reducedMotion()) {
                        if (moved) animate(new EffectDrawable(downX, downY, lastX, lastY, false), ToxiThemeEngine.motionDuration(260L));
                        else if (!charged) animate(new EffectDrawable(downX, downY, downX, downY, false), ToxiThemeEngine.motionDuration(220L));
                    }
                    resetGesture();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    invalidateGesture();
                    resetGesture();
                    break;
                default:
                    break;
            }
            // Native View handling retains pressed state, clicks, long-clicks and accessibility actions.
            return false;
        }

        private void showCharge() {
            if (valid && !moved && target.isAttachedToWindow() && target.isPressed() && !reducedMotion()) {
                charged = true;
                animate(new EffectDrawable(downX, downY, downX, downY, true), ToxiThemeEngine.motionDuration(360L));
            }
        }

        private void cancelCharge() { target.removeCallbacks(charge); }
        private void invalidateGesture() { valid = false; cancelCharge(); }
        private void resetGesture() { pointerId = MotionEvent.INVALID_POINTER_ID; valid = false; }

        private void animate(EffectDrawable next, long duration) {
            if (!target.isAttachedToWindow() || target.getWidth() <= 0 || target.getHeight() <= 0) return;
            stopAnimation();
            drawable = next;
            next.setBounds(0, 0, target.getWidth(), target.getHeight());
            target.getOverlay().add(next);
            ValueAnimator running = ValueAnimator.ofFloat(0f, 1f);
            animator = running;
            running.setDuration(duration);
            running.setInterpolator(new DecelerateInterpolator());
            running.addUpdateListener(value -> {
                if (animator != running) return;
                next.setProgress((Float) value.getAnimatedValue());
                target.invalidate();
            });
            running.addListener(new AnimatorListenerAdapter() {
                private void removeEffect() {
                    target.getOverlay().remove(next);
                    if (animator == running) {
                        animator = null;
                        drawable = null;
                    }
                }
                @Override public void onAnimationEnd(Animator animation) { removeEffect(); }
                @Override public void onAnimationCancel(Animator animation) { removeEffect(); }
            });
            running.start();
        }

        private void stopAnimation() {
            ValueAnimator running = animator;
            animator = null;
            if (running != null) running.cancel();
            if (drawable != null) {
                target.getOverlay().remove(drawable);
                drawable = null;
            }
        }

        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) {
            cancelCharge();
            resetGesture();
            stopAnimation();
        }
    }

    private static final class EffectDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float x1, y1, x2, y2;
        private final boolean charge;
        private float progress;

        EffectDrawable(float x1, float y1, float x2, float y2, boolean charge) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.charge = charge;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setProgress(float value) { progress = value; invalidateSelf(); }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int save = canvas.save();
            paint.setColor(ToxiVisualSystem.ACCENT);
            paint.setAlpha(Math.round(150f * (1f - progress)));
            if (charge) {
                paint.setStrokeWidth(Math.max(1f, bounds.width() * .012f * ToxiThemeEngine.profile().iconStroke / 2f));
                RectF ring = new RectF(x1 - 14f - progress * 8f, y1 - 14f - progress * 8f,
                        x1 + ToxiThemeEngine.spacing(14f) + progress * ToxiThemeEngine.spacing(8f), y1 + 14f + progress * 8f);
                canvas.drawArc(ring, -90f, progress * 350f, false, paint);
                paint.setAlpha(Math.round(55f * (1f - progress)));
                canvas.drawCircle(x1, y1, 14f + progress * 8f, paint);
            } else {
                paint.setStrokeWidth(Math.max(1f, ToxiThemeEngine.profile().iconStroke * (1f - progress)));
                canvas.drawCircle(x1, y1, ToxiThemeEngine.spacing(4f) + progress * ToxiThemeEngine.spacing(25f), paint);
                if (Math.hypot(x2 - x1, y2 - y1) > 8f) {
                    paint.setAlpha(Math.round(115f * (1f - progress)));
                    paint.setStrokeWidth(Math.max(1f, 3f * (1f - progress)));
                    Path trail = new Path();
                    trail.moveTo(x1, y1);
                    trail.quadTo((x1 + x2) * .5f, (y1 + y2) * .5f - ToxiThemeEngine.spacing(8f), x2, y2);
                    canvas.drawPath(trail, paint);
                }
            }
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}