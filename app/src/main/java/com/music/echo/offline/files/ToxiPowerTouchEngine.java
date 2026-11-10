package com.music.echo.offline.files;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Lightweight theme-colored tap, charge and swipe feedback shared by interactive controls. */
final class ToxiPowerTouchEngine {
    private static final long CHARGE_DELAY_MS = 420L;
    private static final float SWIPE_DISTANCE_DP = 18f;
    private ToxiPowerTouchEngine() { }

    static void attach(View target, int themeIndex) {
        if (target == null) return;
        final int color = ToxiThemeSystem.ACCENTS[ToxiThemeSystem.normalize(themeIndex)];
        final float density = target.getResources().getDisplayMetrics().density;
        final float swipeThreshold = SWIPE_DISTANCE_DP * density;
        target.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, lastX, lastY;
            boolean moved, charged;
            final Runnable charge = () -> {
                if (!moved && target.isPressed() && !reducedMotion()) {
                    charged = true;
                    animate(target, new EffectDrawable(color, downX, downY, downX, downY, true), 360L);
                }
            };
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = lastX = event.getX();
                        downY = lastY = event.getY();
                        moved = false;
                        charged = false;
                        target.removeCallbacks(charge);
                        target.postDelayed(charge, CHARGE_DELAY_MS);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        lastX = event.getX();
                        lastY = event.getY();
                        if (Math.hypot(lastX - downX, lastY - downY) > swipeThreshold) {
                            moved = true;
                            target.removeCallbacks(charge);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        target.removeCallbacks(charge);
                        if (!reducedMotion()) {
                            if (moved) animate(target, new EffectDrawable(color, downX, downY, lastX, lastY, false), 260L);
                            else if (!charged) animate(target, new EffectDrawable(color, downX, downY, downX, downY, false), 220L);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        target.removeCallbacks(charge);
                        break;
                    default: break;
                }
                return false;
            }
        });
    }

    private static boolean reducedMotion() {
        return ToxiVisualSystem.booleanPreference("reduce_motion", false);
    }

    private static void animate(View target, EffectDrawable drawable, long duration) {
        if (target.getWidth() <= 0 || target.getHeight() <= 0) return;
        drawable.setBounds(0, 0, target.getWidth(), target.getHeight());
        target.getOverlay().add(drawable);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            drawable.setProgress((Float) value.getAnimatedValue());
            target.invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                target.getOverlay().remove(drawable);
            }
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                target.getOverlay().remove(drawable);
            }
        });
        animator.start();
    }

    private static final class EffectDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final float x1, y1, x2, y2;
        private final boolean charge;
        private float progress;
        EffectDrawable(int color, float x1, float y1, float x2, float y2, boolean charge) {
            this.color=color; this.x1=x1; this.y1=y1; this.x2=x2; this.y2=y2; this.charge=charge;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        void setProgress(float value) { progress=value; invalidateSelf(); }
        @Override public void draw(Canvas canvas) {
            Rect b=getBounds();
            int save=canvas.save();
            paint.setColor(color);
            paint.setAlpha(Math.round(150f * (1f-progress)));
            if (charge) {
                paint.setStrokeWidth(Math.max(1f, b.width() * .012f));
                RectF ring=new RectF(x1-14f-progress*8f,y1-14f-progress*8f,x1+14f+progress*8f,y1+14f+progress*8f);
                canvas.drawArc(ring,-90f,progress*350f,false,paint);
                paint.setAlpha(Math.round(55f*(1f-progress)));
                canvas.drawCircle(x1,y1,14f+progress*8f,paint);
            } else {
                float radius=(4f+progress*25f);
                paint.setStrokeWidth(Math.max(1f, 2.2f*(1f-progress)));
                canvas.drawCircle(x1,y1,radius,paint);
                if (Math.hypot(x2-x1,y2-y1)>8f) {
                    paint.setAlpha(Math.round(115f*(1f-progress)));
                    paint.setStrokeWidth(Math.max(1f,3f*(1f-progress)));
                    Path trail=new Path(); trail.moveTo(x1,y1); trail.quadTo((x1+x2)*.5f,(y1+y2)*.5f-8f,x2,y2);
                    canvas.drawPath(trail,paint);
                }
            }
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}