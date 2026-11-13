package com.music.echo.offline.files;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import java.util.function.IntConsumer;

/** Five independent, theme-aware liquid-glass navigation orbs. */
final class PrismNavigationDock {
    private static final String[] NAMES = {"Home", "Files", "Storage", "Favorites", "Settings"};
    private static final int COUNT = NAMES.length;
    private final MainActivity activity;
    private final FrameLayout root;
    private final IntConsumer onTabSelected;
    private final OrbCanvas surface;
    private int selectedTab;
    private boolean imeVisible;
    private boolean built;

    PrismNavigationDock(MainActivity activity, FrameLayout root, IntConsumer onTabSelected) {
        if (activity == null || root == null || onTabSelected == null)
            throw new IllegalArgumentException("navigation dependencies must not be null");
        this.activity = activity;
        this.root = root;
        this.onTabSelected = onTabSelected;
        surface = new OrbCanvas(activity, this);
    }

    void build() {
        if (built) return;
        built = true;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1,
                NebulaHomeView.dp(activity, 92), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.leftMargin = NebulaHomeView.dp(activity, 8);
        lp.rightMargin = NebulaHomeView.dp(activity, 8);
        lp.bottomMargin = Math.max(ToxiVisualSystem.BOTTOM_INSET, NebulaHomeView.dp(activity, 8))
                + NebulaHomeView.dp(activity, 8);
        root.addView(surface, lp);
        surface.setVisibility(View.VISIBLE);
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    FrameLayout view() { return surface; }
    void setVisible(boolean visible) { surface.setVisibility(visible ? View.VISIBLE : View.GONE); }
    void setImeVisible(boolean visible) {
        imeVisible = visible;
        surface.animate().cancel();
        if (visible && !surface.isRail()) {
            surface.setTranslationY(surface.getHeight() + NebulaHomeView.dp(activity, 24));
            surface.setAlpha(0f);
            surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        } else {
            surface.setTranslationY(0f);
            surface.setAlpha(1f);
            surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }
    void applyBottomInset() {
        if (surface.getParent() != root || surface.getLayoutParams() == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        lp.bottomMargin = Math.max(ToxiVisualSystem.BOTTOM_INSET, NebulaHomeView.dp(activity, 8))
                + NebulaHomeView.dp(activity, 8);
        surface.setLayoutParams(lp);
        surface.invalidate();
    }
    void moveHighlight(int index) {
        selectedTab = Math.max(0, Math.min(COUNT - 1, index));
        surface.setSelectedOrb(selectedTab, !ToxiVisualSystem.booleanPreference("reduce_motion", false));
    }
    void recolor(int selectedIndex) {
        surface.invalidate();
        moveHighlight(selectedIndex);
    }

    private final class OrbCanvas extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF bounds = new RectF();
        private final float density;
        private int active = 0;
        private int pressed = -1;
        private float activePulse = 1f;
        private float activeFrom = 0f;
        private float activeTo = 0f;
        private float touchScale = 1f;
        private ValueAnimator animator;
        private final float[] centers = new float[COUNT];
        private float orbRadius;

        OrbCanvas(MainActivity context, PrismNavigationDock owner) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setClickable(true);
            setFocusable(true);
            setContentDescription("Liquid Orb navigation");
        }
        boolean isRail() { return false; }
        private float dp(float value) { return value * density; }
        void setSelectedOrb(int index, boolean animate) {
            if (index < 0 || index >= COUNT) return;
            if (animator != null) animator.cancel();
            float old = active;
            active = index;
            activeFrom = old;
            activeTo = index;
            if (animate && old != index) {
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(260L);
                animator.setInterpolator(new DecelerateInterpolator(1.45f));
                animator.addUpdateListener(a -> { activePulse = (float) a.getAnimatedValue(); invalidate(); });
                animator.start();
            } else {
                activePulse = 1f;
                invalidate();
            }
        }
        private float lerp(float a, float b, float t) { return a + (b - a) * t; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float pad = dp(8);
            float available = Math.max(1f, w - pad * 2);
            orbRadius = Math.min(dp(28), Math.min(dp(31), available / (COUNT * 2.15f)));
            float gap = (available - COUNT * orbRadius * 2f) / (COUNT - 1);
            if (gap < dp(4)) { orbRadius = Math.max(dp(21), (available - dp(16)) / (COUNT * 2.2f)); gap = (available - COUNT * orbRadius * 2f) / (COUNT - 1); }
            float cy = h * .48f;
            int accent = ToxiVisualSystem.ACCENT;
            int accentEnd = ToxiThemeSystem.ACCENT_ENDS[ToxiThemeSystem.normalize(ToxiVisualSystem.THEME_INDEX)];
            for (int i = 0; i < COUNT; i++) {
                float cx = pad + orbRadius + i * (orbRadius * 2f + gap);
                centers[i] = cx;
                float radius = orbRadius * (i == active ? 1.055f : 1f);
                drawOrb(c, cx, cy, radius, i == active, accent, accentEnd, i == pressed ? .94f : 1f);
                drawGlyph(c, cx, cy, radius * .48f, i, i == active ? Color.WHITE :
                        (ToxiVisualSystem.LIGHT_MODE ? 0xFF344052 : 0xFFE4E8F3));
                if (i == active) {
                    p.setShader(null);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(accent);
                    p.setAlpha(235);
                    c.drawCircle(cx, cy + radius + dp(7), dp(2.1f), p);
                    p.setAlpha(255);
                }
            }
        }
        private void drawOrb(Canvas c, float x, float y, float r, boolean selected, int accent, int accentEnd, float scale) {
            c.save(); c.scale(scale, scale, x, y);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(ToxiVisualSystem.LIGHT_MODE ? 26 : 92, 0, 0, 0));
            p.setShadowLayer(dp(selected ? 13 : 8), 0, dp(4), Color.argb(selected ? 95 : 54, 0, 0, 0));
            c.drawCircle(x, y + dp(1), r + dp(1), p); p.clearShadowLayer();
            int glassTop = ToxiVisualSystem.LIGHT_MODE ? 0xDDFEFFFF : 0xB72B3045;
            int glassMid = ToxiVisualSystem.LIGHT_MODE ? 0xA8E6ECF7 : 0x88343B56;
            int glassBottom = ToxiVisualSystem.LIGHT_MODE ? 0xB7B9C7DA : 0xA0121728;
            p.setShader(new LinearGradient(x - r, y - r, x + r, y + r,
                    new int[]{selected ? blend(accent, 0xAFFFFFFF, .30f) : glassTop,
                            selected ? blend(accentEnd, 0xBFFFFFFF, .20f) : glassMid, glassBottom}, null, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(selected ? 1.45f : 1f));
            p.setColor(selected ? 0xDFFFFFFF : (ToxiVisualSystem.LIGHT_MODE ? 0xBFFFFFFF : 0x80FFFFFF));
            c.drawCircle(x, y, r - dp(.6f), p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(x - r * .34f, y - r * .48f, r * 1.05f,
                    new int[]{Color.argb(selected ? 125 : 72, 255, 255, 255), 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r - dp(1.2f), p); p.setShader(null);
            p.setColor(Color.argb(ToxiVisualSystem.LIGHT_MODE ? 145 : 110, 255, 255, 255));
            p.setStrokeWidth(dp(1.2f)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(x - r * .72f, y - r * .72f, x + r * .72f, y + r * .72f, 205, 105, false, p);
            p.setStrokeCap(Paint.Cap.BUTT);
            if (selected) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2.2f)); p.setColor(Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent)));
                c.drawCircle(x, y, r + dp(2.3f), p); p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(x, y, r * 1.75f,
                        new int[]{Color.argb(48, Color.red(accent), Color.green(accent), Color.blue(accent)), 0x00000000}, null, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, r * 1.75f, p); p.setShader(null);
            }
            c.restore();
        }
        private int blend(int a, int b, float t) {
            return Color.rgb(Math.round(Color.red(a) * (1-t) + Color.red(b) * t),
                    Math.round(Color.green(a) * (1-t) + Color.green(b) * t),
                    Math.round(Color.blue(a) * (1-t) + Color.blue(b) * t));
        }
        private void drawGlyph(Canvas c, float x, float y, float s, int index, int color) {
            p.setShader(null); p.setColor(color); p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.8f)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path();
            switch (index) {
                case 0: // Home
                    path.moveTo(x-s, y); path.lineTo(x, y-s*.88f); path.lineTo(x+s, y);
                    path.moveTo(x-s*.68f, y-s*.12f); path.lineTo(x-s*.68f, y+s*.86f); path.lineTo(x+s*.68f, y+s*.86f); path.lineTo(x+s*.68f, y-s*.12f);
                    path.moveTo(x-s*.22f, y+s*.86f); path.lineTo(x-s*.22f, y+s*.18f); path.lineTo(x+s*.22f, y+s*.18f); path.lineTo(x+s*.22f, y+s*.86f); break;
                case 1: // Files
                    path.moveTo(x-s, y-s*.48f); path.lineTo(x-s*.18f, y-s*.48f); path.lineTo(x+s*.08f, y-s*.18f); path.lineTo(x+s, y-s*.18f); path.lineTo(x+s, y+s*.72f); path.lineTo(x-s, y+s*.72f); path.close(); break;
                case 2: // Storage
                    c.drawRoundRect(x-s, y-s*.72f, x+s, y+s*.72f, dp(3), dp(3), p);
                    c.drawLine(x-s*.65f, y-s*.18f, x+s*.65f, y-s*.18f, p);
                    c.drawLine(x-s*.65f, y+s*.25f, x+s*.65f, y+s*.25f, p);
                    c.drawCircle(x+s*.53f, y+s*.5f, dp(1), p); break;
                case 3: // Favorites
                    path.moveTo(x, y+s); path.cubicTo(x-s*.35f, y+s*.55f, x-s*1.12f, y-s*.04f, x-s*.78f, y-s*.62f);
                    path.cubicTo(x-s*.5f, y-s*1.08f, x-s*.08f, y-s*.72f, x, y-s*.38f);
                    path.cubicTo(x+s*.08f, y-s*.72f, x+s*.5f, y-s*1.08f, x+s*.78f, y-s*.62f);
                    path.cubicTo(x+s*1.12f, y-s*.04f, x+s*.35f, y+s*.55f, x, y+s); break;
                default: // Settings
                    c.drawCircle(x, y, s*.78f, p); c.drawCircle(x, y, s*.28f, p);
                    for (int a=0; a<8; a++) { double angle=Math.PI*a/4; float x1=x+(float)Math.cos(angle)*s*.9f, y1=y+(float)Math.sin(angle)*s*.9f; float x2=x+(float)Math.cos(angle)*s*1.16f, y2=y+(float)Math.sin(angle)*s*1.16f; c.drawLine(x1,y1,x2,y2,p); }
                    break;
            }
            if (index < 2 || index == 3) c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.BUTT); p.setStrokeJoin(Paint.Join.MITER);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            float x=event.getX(), y=event.getY();
            if (event.getAction()==MotionEvent.ACTION_DOWN) {
                pressed=hit(x,y); if (pressed<0) return false;
                invalidate(); return true;
            }
            if (event.getAction()==MotionEvent.ACTION_MOVE) { pressed=hit(x,y); invalidate(); return true; }
            if (event.getAction()==MotionEvent.ACTION_CANCEL) { pressed=-1; invalidate(); return true; }
            if (event.getAction()==MotionEvent.ACTION_UP) {
                int target=hit(x,y), previous=pressed; pressed=-1; invalidate();
                if (target>=0 && target==previous) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    setSelectedOrb(target, !ToxiVisualSystem.booleanPreference("reduce_motion", false));
                    if (!ToxiVisualSystem.booleanPreference("reduce_motion", false)) {
                        animate().cancel(); setScaleX(.985f); setScaleY(.985f);
                        animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
                    }
                    return performClickAndDispatch(target);
                }
                return true;
            }
            return true;
        }
        private boolean performClickAndDispatch(int index) { performClick(); dispatch(index); return true; }
        @Override public boolean performClick() { super.performClick(); return true; }
        private int hit(float x,float y) {
            if (orbRadius<=0 || Math.abs(y-getHeight()*.48f)>orbRadius*1.55f) return -1;
            for(int i=0;i<COUNT;i++) if(Math.abs(x-centers[i])<=orbRadius*1.22f) return i;
            return -1;
        }
    }
    private void dispatch(int index) { onTabSelected.accept(index); }
}
