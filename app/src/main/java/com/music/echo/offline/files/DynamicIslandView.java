package com.music.echo.offline.files;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.os.Build;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Native, in-window Dynamic Island for ToxiFiles.
 *
 * The collapsed state is a black sensor-style pill with a subtle camera lens.
 * Active state uses compact activity information; tapping/long-pressing expands
 * into a detailed card with progress and a real host-provided action.
 */
final class DynamicIslandView extends FrameLayout {
    private static final String PREFS = "toxifiles_dynamic_island";
    private static final int[][] ACCENTS = {
        {0xFF00E5FF,0xFF1677FF,0xFF843BFF,0xFFD12BFF,0xFFFF2878,0xFFFF4D36},
        {0xFF00E5FF,0xFF1677FF,0xFF00BFA5,0xFF00E5FF,0xFF1677FF,0xFF843BFF},
        {0xFFFF4D36,0xFFFF2878,0xFFD12BFF,0xFFFF4D36,0xFFFF2878,0xFFD12BFF},
        {0xFF1677FF,0xFF843BFF,0xFFD12BFF,0xFF1677FF,0xFF843BFF,0xFFD12BFF}
    };

    private final Activity host;
    private final SharedPreferences prefs;
    private final LinearLayout shell, pill, expanded, titleRow, actionRow;
    private final TextView lens, statusIcon, label, title, subtitle, action;
    private final ProgressBar progressBar;
    private final GlowBackgroundDrawable pillGlow;
    private final GlowBackgroundDrawable cardGlow;
    private ValueAnimator glowAnimator;
    private float glowPhase;
    private boolean activeGlow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable longPress;
    private Runnable actionCallback;
    private Runnable autoHide;
    private String suppressedStatus;
    private int safeTopInset;
    private static final long COMPLETION_VISIBLE_MS = 2400L;
    private static final long PAUSED_VISIBLE_MS = 5200L;
    private float downX, downY, startX, startY;
    private boolean dragging, longPressed;
    private String activity = "ToxiFiles ready";
    private int progress = -1;

    DynamicIslandView(Activity activityHost) {
        super(activityHost);
        host = activityHost;
        prefs = preferences(host);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);

        shell = new LinearLayout(host);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        int islandWidth = Math.max(1, Math.min(dp(344),
                getResources().getDisplayMetrics().widthPixels - dp(24)));

        pill = new LinearLayout(host);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(13), 0, dp(13), 0);
        pillGlow = new GlowBackgroundDrawable(host, prefs, false);
        pill.setBackground(pillGlow);
        pill.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        pill.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        pill.setElevation(dp(12));
        pill.setMinimumHeight(dp(prefs.getInt("size", 0) == 0 ? 36 : 42));

        lens = text("●", 9, 0xFF637080, true);
        lens.setGravity(Gravity.CENTER);
        GradientDrawable lensBg = new GradientDrawable();
        lensBg.setShape(GradientDrawable.OVAL);
        lensBg.setColor(0xFF101923);
        lensBg.setStroke(dp(1), 0xFF35404B);
        lens.setBackground(lensBg);
        LinearLayout.LayoutParams lensLp = new LinearLayout.LayoutParams(dp(15), dp(15));
        lensLp.rightMargin = dp(8);
        pill.addView(lens, lensLp);

        statusIcon = text("◆", 10, accent(), true);
        statusIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(14), dp(20));
        iconLp.rightMargin = dp(5);
        pill.addView(statusIcon, iconLp);

        label = text("ToxiFiles", 11, Color.WHITE, true);
        label.setSingleLine(true);
        label.setMaxEms(13);
        pill.addView(label, new LinearLayout.LayoutParams(-2, -2));
        pill.setOnTouchListener(this::handleTouch);
        pill.setOnClickListener(v -> toggleExpanded());
        shell.addView(pill, new LinearLayout.LayoutParams(dp(132), dp(prefs.getInt("size", 0) == 0 ? 36 : 42)));

        expanded = new LinearLayout(host);
        expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setPadding(dp(16), dp(13), dp(16), dp(12));
        cardGlow = new GlowBackgroundDrawable(host, prefs, true);
        expanded.setBackground(cardGlow);
        expanded.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        expanded.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        expanded.setElevation(dp(16));
        LinearLayout.LayoutParams expandedLp = new LinearLayout.LayoutParams(islandWidth, -2);
        expandedLp.topMargin = dp(8);
        shell.addView(expanded, expandedLp);
        expanded.setVisibility(GONE);

        titleRow = new LinearLayout(host);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        expanded.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView appMark = text("T", 15, Color.WHITE, true);
        appMark.setGravity(Gravity.CENTER);
        GradientDrawable markBg = new GradientDrawable();
        markBg.setShape(GradientDrawable.OVAL);
        markBg.setColor(0xFF202833);
        markBg.setStroke(dp(1), 0xFF536171);
        appMark.setBackground(markBg);
        titleRow.addView(appMark, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout textColumn = new LinearLayout(host);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(10), 0, dp(6), 0);
        titleRow.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1f));
        title = text("ToxiFiles", 13, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle = text("Ready", 11, 0xFF9AA6B5, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textColumn.addView(title);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(2);
        textColumn.addView(subtitle, subLp);

        TextView close = text("⌃", 18, 0xFFB8C4D2, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> collapse());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(30), dp(30)));

        progressBar = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(accent()));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF303944));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(3));
        progressLp.topMargin = dp(13);
        expanded.addView(progressBar, progressLp);

        actionRow = new LinearLayout(host);
        actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(10);
        expanded.addView(actionRow, rowLp);

        action = text("Action", 11, accent(), true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(13), dp(7), dp(13), dp(7));
        action.setBackground(actionBackground());
        action.setOnClickListener(v -> {
            Runnable callback = actionCallback;
            if (callback != null) callback.run();
        });
        actionRow.addView(action, new LinearLayout.LayoutParams(-2, -2));
        actionRow.setVisibility(GONE);

        addView(shell, new FrameLayout.LayoutParams(islandWidth, -2, Gravity.TOP | Gravity.START));
        setVisibility(GONE);
        applyPosition();
        render();
        refreshOutlineShadows();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(host);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setIncludeFontPadding(false);
        return v;
    }
    private int accent() {
        int palette = Math.max(0, Math.min(ACCENTS.length - 1, prefs.getInt("palette", 0)));
        return ACCENTS[palette][0];
    }
    private GradientDrawable actionBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF1A222D);
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), accent());
        return d;
    }
    private boolean isEnabledPreference() { return prefs.getBoolean("enabled", true); }

    private int statusBarHeight() {
        if (safeTopInset > 0) return safeTopInset;
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    void setSafeTopInset(int inset) {
        safeTopInset = Math.max(0, inset);
        if (getWidth() > 0) post(this::applyPosition);
    }
    private void applyPosition() {
        if (getWidth() <= 0 || shell.getMeasuredWidth() <= 0) {
            post(this::applyPosition);
            return;
        }
        int maxX = Math.max(0, getWidth() - shell.getMeasuredWidth());
        int maxY = Math.max(statusBarHeight() + dp(4), getHeight() - shell.getMeasuredHeight());
        float nx = prefs.getFloat("x", 0.5f);
        float ny = prefs.getFloat("y", (statusBarHeight() + dp(6)) / (float)Math.max(1, getHeight()));
        int x = Math.round(Math.max(0f, Math.min(1f, nx)) * maxX);
        int y = Math.max(statusBarHeight() + dp(4), Math.min(maxY, Math.round(ny * getHeight())));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)shell.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = x;
        lp.topMargin = y;
        shell.setLayoutParams(lp);
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) post(this::applyPosition);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) startGlowAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        shell.animate().cancel();
        stopGlowAnimation();
        super.onDetachedFromWindow();
    }

    private void startGlowAnimation() {
        stopGlowAnimation();
        if (prefs.getInt("glow", 65) <= 0 || !isEnabledPreference()) {
            setGlowPhase(0f);
            return;
        }
        glowAnimator = ValueAnimator.ofFloat(0f, 1f);
        glowAnimator.setDuration(activeGlow ? 2600L : 4200L);
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        glowAnimator.setInterpolator(new LinearInterpolator());
        glowAnimator.addUpdateListener(animation -> {
            float phase = (Float) animation.getAnimatedValue();
            setGlowPhase(phase);
        });
        glowAnimator.start();
    }

    private void stopGlowAnimation() {
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator.removeAllUpdateListeners();
            glowAnimator = null;
        }
    }

    private void setGlowPhase(float phase) {
        glowPhase = phase;
        pillGlow.setGlow(activeGlow, phase);
        cardGlow.setGlow(activeGlow, phase);
    }

    private void updateGlowActivity(boolean active) {
        if (activeGlow == active) return;
        activeGlow = active;
        if (getVisibility() == VISIBLE) startGlowAnimation();
        else setGlowPhase(0f);
    }

    private boolean handleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                startX = shell.getX();
                startY = shell.getY();
                dragging = false;
                longPressed = false;
                longPress = () -> {
                    if (!dragging) {
                        longPressed = true;
                        expand();
                        haptic(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                };
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                if (Math.abs(dx) + Math.abs(dy) > ViewConfiguration.get(host).getScaledTouchSlop()) {
                    dragging = true;
                    if (longPress != null) handler.removeCallbacks(longPress);
                }
                if (dragging) {
                    float x = Math.max(0, Math.min(getWidth() - shell.getWidth(), startX + dx));
                    float y = Math.max(statusBarHeight() + dp(4), Math.min(getHeight() - shell.getHeight(), startY + dy));
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)shell.getLayoutParams();
                    lp.leftMargin = (int)x;
                    lp.topMargin = (int)y;
                    shell.setLayoutParams(lp);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (longPress != null) handler.removeCallbacks(longPress);
                if (dragging) {
                    int maxX = Math.max(0, getWidth() - shell.getWidth());
                    float raw = maxX == 0 ? 0f : shell.getX() / maxX;
                    float snapped = raw < 0.25f ? 0f : raw > 0.75f ? 1f : 0.5f;
                    int x = Math.round(snapped * maxX);
                    int y = Math.max(statusBarHeight() + dp(4), Math.min(getHeight() - shell.getHeight(), (int)shell.getY()));
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)shell.getLayoutParams();
                    lp.leftMargin = x;
                    lp.topMargin = y;
                    shell.setLayoutParams(lp);
                    prefs.edit().putFloat("x", snapped).putFloat("y", y / (float)Math.max(1, getHeight())).apply();
                    haptic(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                } else if (!longPressed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    toggleExpanded();
                    haptic(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                }
                return true;
        }
        return true;
    }
    private void haptic(int constant) {
        if (prefs.getBoolean("haptics", true)) performHapticFeedback(constant);
    }

    private void toggleExpanded() {
        if (expanded.getVisibility() == VISIBLE) collapse();
        else expand();
    }
    private void expand() {
        if (!isEnabledPreference() || expanded.getVisibility() == VISIBLE) return;
        expanded.setVisibility(VISIBLE);
        render();
        shell.setScaleX(0.94f);
        shell.setScaleY(0.94f);
        shell.setAlpha(0.92f);
        shell.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(Math.max(100, prefs.getInt("speed", 180)))
            .setInterpolator(new DecelerateInterpolator()).start();
        post(this::applyPosition);
    }
    private void collapse() {
        expanded.setVisibility(GONE);
        shell.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(Math.max(80, prefs.getInt("speed", 180) / 2)).start();
        post(this::applyPosition);
    }

    void update(String status, int percent) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(() -> update(status, percent));
            return;
        }
        String next = status == null || status.trim().isEmpty() ? "ToxiFiles active" : status.trim();
        String lower = next.toLowerCase(java.util.Locale.ROOT);
        if (next.equals(suppressedStatus)) {
            activity = next;
            progress = Math.max(-1, Math.min(100, percent));
            return;
        }
        suppressedStatus = null;
        if (!isEnabledPreference()) {
            hideNow();
            return;
        }
        if ((lower.contains("copy") || lower.contains("mov") || lower.contains("delet") || lower.contains("zip") || lower.contains("extract"))
                && !prefs.getBoolean("show_operations", true)) return;
        if ((lower.contains("video") || lower.contains("play")) && !prefs.getBoolean("show_playback", true)) return;
        if ((lower.contains("scan") || lower.contains("index")) && !prefs.getBoolean("show_scan", true)) return;

        boolean changed = !next.equals(activity);
        boolean terminal = isTerminalStatus(lower);
        boolean pausedPlayback = lower.contains("video paused");
        activity = next;
        progress = Math.max(-1, Math.min(100, percent));

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            startGlowAnimation();
        }
        updateGlowActivity(!terminal && !pausedPlayback);
        render();

        if (terminal) {
            if (lower.contains("finish") || lower.contains("complete") || lower.contains("success")) {
                progress = 100;
                render();
            }
            scheduleAutoHide(COMPLETION_VISIBLE_MS, changed);
        } else if (pausedPlayback) {
            scheduleAutoHide(PAUSED_VISIBLE_MS, changed);
        } else {
            cancelAutoHide();
            if (changed && prefs.getBoolean("auto_expand", true)) expand();
        }
    }

    private boolean isTerminalStatus(String lower) {
        return lower.equals("ready") || lower.equals("toxifiles ready")
                || lower.contains("finished") || lower.contains("complete")
                || lower.contains("cancelled") || lower.contains("canceled")
                || lower.contains("failed") || lower.contains("error")
                || lower.contains("success");
    }

    private void scheduleAutoHide(long delay, boolean reset) {
        if (autoHide != null && !reset) return;
        cancelAutoHide();
        autoHide = this::hideNow;
        handler.postDelayed(autoHide, delay);
    }

    private void cancelAutoHide() {
        if (autoHide != null) {
            handler.removeCallbacks(autoHide);
            autoHide = null;
        }
    }

    private void hideNow() {
        cancelAutoHide();
        collapse();
        actionCallback = null;
        action.setVisibility(GONE);
        actionRow.setVisibility(GONE);
        activeGlow = false;
        setGlowPhase(0f);
        stopGlowAnimation();
        setVisibility(GONE);
        suppressedStatus = activity;
        activity = "ToxiFiles ready";
        progress = -1;
    }

    private void render() {
        String lower = activity.toLowerCase(java.util.Locale.ROOT);
        boolean active = !isTerminalStatus(lower);
        String compactStatus = compact(activity);
        if (progress >= 0 && active) {
            if (lower.contains("video")) compactStatus = "Video " + progress + "%";
            else {
                int space = activity.indexOf(' ');
                String task = space > 0 ? activity.substring(0, space) : activity;
                if (task.length() > 8) task = task.substring(0, 8);
                compactStatus = task + " " + progress + "%";
            }
        }
        label.setText(active ? compactStatus : "ToxiFiles");
        statusIcon.setText(active ? "●" : "◆");
        statusIcon.setTextColor(active ? accent() : 0xFF8995A4);
        title.setText(activity);
        java.util.regex.Matcher items = java.util.regex.Pattern.compile("([0-9]+)[ ]+of[ ]+([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(activity);
        if (lower.contains("video")) {
            subtitle.setText(progress >= 0 ? "Video playback · " + progress + "%" : "Video playback");
        } else if (items.find()) {
            subtitle.setText(items.group(1) + " of " + items.group(2) + " items"
                    + (progress >= 0 ? " · " + progress + "%" : ""));
        } else if (progress >= 0) {
            subtitle.setText("Task progress · " + progress + "%");
        } else {
            subtitle.setText(active ? "Working on your files" : "Your files, under control");
        }
        progressBar.setIndeterminate(progress < 0 && active);
        progressBar.setProgress(progress < 0 ? 0 : progress);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(accent()));
        progressBar.setProgressTintList(ColorStateList.valueOf(accent()));
        if (expanded.getVisibility() == VISIBLE) post(this::applyPosition);
    }
    private String compact(String value) {
        if (value.length() <= 13) return value;
        return value.substring(0, 12) + "…";
    }
    void align(int alignment) {
        float x = alignment == 0 ? 0f : alignment == 1 ? 0.5f : 1f;
        prefs.edit().putFloat("x", x).apply();
        post(this::applyPosition);
    }
    void setAction(String titleText, Runnable callback) {
        actionCallback = callback;
        if (titleText == null || titleText.trim().isEmpty() || callback == null) {
            action.setVisibility(GONE);
            actionRow.setVisibility(GONE);
            actionCallback = null;
        } else {
            action.setText(titleText);
            action.setVisibility(VISIBLE);
            actionRow.setVisibility(VISIBLE);
        }
    }
    void setEnabledFromSettings() {
        if (!isEnabledPreference()) hideNow();
    }
    private void refreshOutlineShadows() {
        if (Build.VERSION.SDK_INT >= 28) {
            int shadow = prefs.getInt("glow", 65) > 0 ? accent() : 0x55000000;
            pill.setOutlineAmbientShadowColor(shadow);
            pill.setOutlineSpotShadowColor(shadow);
            expanded.setOutlineAmbientShadowColor(shadow);
            expanded.setOutlineSpotShadowColor(shadow);
        }
    }

    void refreshStyle() {
        String lower = activity.toLowerCase(java.util.Locale.ROOT);
        if ((lower.contains("copy") || lower.contains("mov") || lower.contains("delet") || lower.contains("zip") || lower.contains("extract"))
                && !prefs.getBoolean("show_operations", true)
                || (lower.contains("video") || lower.contains("play")) && !prefs.getBoolean("show_playback", true)
                || (lower.contains("scan") || lower.contains("index")) && !prefs.getBoolean("show_scan", true)) {
            hideNow();
        }
        action.setBackground(actionBackground());
        action.setTextColor(accent());
        progressBar.setProgressTintList(ColorStateList.valueOf(accent()));
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(accent()));
        pill.setMinimumHeight(dp(prefs.getInt("size", 0) == 0 ? 36 : 42));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)pill.getLayoutParams();
        lp.height = dp(prefs.getInt("size", 0) == 0 ? 36 : 42);
        pill.setLayoutParams(lp);
        shell.setElevation(dp(2));
        pill.setElevation(dp(10 + prefs.getInt("glow", 65) / 12));
        expanded.setElevation(dp(12 + prefs.getInt("glow", 65) / 10));
        refreshOutlineShadows();
        if (getVisibility() == VISIBLE) startGlowAnimation();
        else { stopGlowAnimation(); setGlowPhase(0f); }
        post(this::applyPosition);
    }
    /**
     * Black glass surface with a continuously blended six-colour RGB perimeter.
     * A soft coloured halo breathes around the edge; intensity is controlled by
     * the existing Glow intensity preference (0 disables it).
     */
    private static final class GlowBackgroundDrawable extends Drawable {
        private final Activity host;
        private final SharedPreferences prefs;
        private final boolean card;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float phase;
        private boolean active;

        GlowBackgroundDrawable(Activity host, SharedPreferences prefs, boolean card) {
            this.host = host;
            this.prefs = prefs;
            this.card = card;
            fill.setStyle(Paint.Style.FILL);
            halo.setStyle(Paint.Style.STROKE);
            edge.setStyle(Paint.Style.STROKE);
        }

        private float dp(float value) {
            return value * host.getResources().getDisplayMetrics().density;
        }

        void setGlow(boolean active, float phase) {
            this.active = active;
            this.phase = phase;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            if (b.isEmpty()) return;
            float intensity = Math.max(0, Math.min(100, prefs.getInt("glow", 65))) / 100f;
            float pulse = 0.78f + 0.22f * (float)Math.sin(phase * Math.PI);
            float strength = intensity * pulse * (active ? 1.22f : 0.82f);
            float radius = card ? dp(25) : dp(30);
            rect.set(b.left + dp(2), b.top + dp(2), b.right - dp(2), b.bottom - dp(2));

            fill.setColor(card ? ((Math.max(80, Math.min(255, prefs.getInt("transparency", 225))) << 24) | 0x00090D13) : 0xF5070A0F);
            fill.setShader(null);
            fill.clearShadowLayer();
            canvas.drawRoundRect(rect, radius, radius, fill);

            int palette = Math.max(0, Math.min(ACCENTS.length - 1, prefs.getInt("palette", 0)));
            int[] colors = ACCENTS[palette];
            int[] sweepColors = new int[colors.length + 1];
            System.arraycopy(colors, 0, sweepColors, 0, colors.length);
            sweepColors[sweepColors.length - 1] = colors[0];
            SweepGradient gradient = new SweepGradient(rect.centerX(), rect.centerY(), sweepColors, null);
            Matrix rotation = new Matrix();
            rotation.setRotate(phase * 360f, rect.centerX(), rect.centerY());
            gradient.setLocalMatrix(rotation);

            float edgeWidth = dp(1.35f + intensity * 0.9f);
            if (intensity > 0f) {
                halo.setShader(null);
                halo.setStrokeWidth(dp(2.2f));
                halo.setColor(Color.WHITE);
                halo.setAlpha((int)(Math.min(255, 36f + 86f * strength)));
                int haloColor = colors[Math.min(colors.length - 1, (int)(phase * colors.length))];
                halo.setShadowLayer(dp(3f + 13f * strength), 0f, 0f,
                        Color.argb((int)(45f + 130f * strength), Color.red(haloColor), Color.green(haloColor), Color.blue(haloColor)));
                canvas.drawRoundRect(rect, radius, radius, halo);
                halo.clearShadowLayer();
                halo.setAlpha(255);
            }

            edge.setShader(intensity > 0f ? gradient : null);
            edge.setColor(intensity > 0f ? Color.WHITE : 0xFF303943);
            edge.setStrokeWidth(edgeWidth);
            edge.clearShadowLayer();
            canvas.drawRoundRect(rect, radius, radius, edge);
            edge.setShader(null);
        }

        @Override public void getOutline(Outline outline) {
            RectF b = getBounds().isEmpty() ? new RectF() : new RectF(getBounds());
            outline.setRoundRect(Math.round(b.left), Math.round(b.top), Math.round(b.right),
                    Math.round(b.bottom), dp(card ? 25f : 30f));
        }

        @Override public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
            edge.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static SharedPreferences preferences(Activity activity) {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }
}

