package com.music.echo.offline.files;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import java.util.WeakHashMap;

/** Attaches one non-interactive gradient ring to any existing view without changing layout size. */
public final class GlowRing {
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();
    private GlowRing() { }

    private static final class State {
        final View target;
        final GradientRingView ring;
        ViewGroup host;
        boolean active;
        final View.OnLayoutChangeListener layout = (v, l, t, r, b, ol, ot, or, ob) -> sync(this);
        final View.OnAttachStateChangeListener attach = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { sync(State.this); if (active) ring.start(); }
            @Override public void onViewDetachedFromWindow(View v) { removeOverlay(thisState()); ring.stop(); }
            private State thisState() { return State.this; }
        };
        State(View target, boolean active) {
            this.target = target;
            this.active = active;
            ring = new GradientRingView(target.getContext());
            ring.setAlpha(0f);
            target.addOnLayoutChangeListener(layout);
            target.addOnAttachStateChangeListener(attach);
            sync(this);
            if (active) ring.start();
        }
    }

    public static void attach(View target) { if (target != null) state(target, !Ui.bool("reduce_motion", true)); }
    public static void detach(View target) {
        if (target == null) return;
        State s = STATES.remove(target);
        if (s == null) return;
        target.removeOnLayoutChangeListener(s.layout);
        target.removeOnAttachStateChangeListener(s.attach);
        removeOverlay(s);
        s.ring.stop();
    }
    public static void setActive(View target, boolean active) {
        if (target == null) return;
        if (Ui.bool("reduce_motion", true)) {
            State s = state(target, false);
            s.active = false; s.ring.setAlpha(0f); s.ring.stop(); sync(s);
            return;
        }
        State s = state(target, active);
        s.active = active;
        sync(s);
        if (active) s.ring.start(); else s.ring.stop();
    }
    /** Attaches a ring that follows keyboard/input focus. */
    public static void watchFocus(View target) {
        if (target == null) return;
        attach(target);
        setActive(target, target.hasFocus());
        target.setOnFocusChangeListener((v, focused) -> setActive(v, focused));
    }

    private static State state(View target, boolean initialActive) {
        State s = STATES.get(target);
        if (s == null) { s = new State(target, initialActive); STATES.put(target, s); }
        return s;
    }
    private static void sync(State s) {
        if (s == null || !s.target.isAttachedToWindow()) { removeOverlay(s); return; }
        if (!(s.target.getParent() instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) s.target.getParent();
        if (s.host != host) {
            removeOverlay(s);
            s.host = host;
            host.getOverlay().add(s.ring);
        }
        View v = s.target;
        int left = v.getLeft() + Math.round(v.getTranslationX());
        int top = v.getTop() + Math.round(v.getTranslationY());
        s.ring.layout(left, top, left + v.getWidth(), top + v.getHeight());
        s.ring.setElevation(v.getElevation() + 1f);
        s.ring.setCornerRadius(cornerRadius(v));
    }
    private static float cornerRadius(View v) {
        try {
            if (v.getOutlineProvider() != null) {
                Outline o = new Outline();
                v.getOutlineProvider().getOutline(v, o);
                if (o.getRadius() >= 0f) return o.getRadius();
            }
        } catch (RuntimeException ignored) { }
        float d = v.getResources().getDisplayMetrics().density;
        return v.getHeight() > 0 && v.getHeight() <= 48f * d ? v.getHeight() * 0.5f : 12f * d;
    }
    private static void removeOverlay(State s) {
        if (s != null && s.host != null) {
            try { s.host.getOverlay().remove(s.ring); } catch (RuntimeException ignored) { }
            s.host = null;
        }
    }
}
