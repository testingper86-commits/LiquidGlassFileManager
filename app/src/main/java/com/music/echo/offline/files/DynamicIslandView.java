package com.music.echo.offline.files;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.ColorFilter;
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
import com.music.echo.offline.R;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Native, in-window Dynamic Island for ToxiFiles.
 *
 * The collapsed state is a theme-bound activity capsule with the original ToxiFiles aperture mark.
 * Active state uses compact activity information; tapping/long-pressing expands
 * into a detailed card with progress and a real host-provided action.
 */
final class DynamicIslandView extends FrameLayout {
    private static final String PREFS = "toxifiles_dynamic_island";

    private final Activity host;
    private final SharedPreferences prefs;
    private final LinearLayout shell, pill, expanded, titleRow, actionRow;
    private final ImageView lens, statusIcon;
    private final TextView label, title, subtitle, action;
    private final ProgressBar progressBar;
    private final IslandSurfaceDrawable pillSurface;
    private final IslandSurfaceDrawable cardSurface;
    private float statePhase;
    private boolean activeState;
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
        pillSurface = new IslandSurfaceDrawable(host, prefs, false);
        pill.setBackground(pillSurface);
        pill.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        pill.setElevation(dp(12));
        pill.setMinimumHeight(dp(prefs.getInt("size", 0) == 0 ? 36 : 42));

        lens = new ImageView(host);
        lens.setImageResource(R.drawable.ic_toxifiles_brand_mark);
        lens.setContentDescription("ToxiFiles activity mark");
        lens.setPadding(dp(2), dp(2), dp(2), dp(2));
        GradientDrawable lensBg = new GradientDrawable();
        lensBg.setShape(GradientDrawable.OVAL);
        lensBg.setColor(ToxiThemeSystem.SOFT_ACCENTS[ToxiVisualSystem.THEME_INDEX]);
        lensBg.setStroke(dp(1), ToxiThemeSystem.EDGES[ToxiVisualSystem.THEME_INDEX]);
        lens.setBackground(lensBg);
        LinearLayout.LayoutParams lensLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        lensLp.rightMargin = dp(8);
        pill.addView(lens, lensLp);

        statusIcon = new ImageView(host);
        statusIcon.setImageDrawable(ToxiIcons.symbol(ToxiIcons.ANALYTICS, accent()));
        statusIcon.setContentDescription("Activity status");
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(15), dp(20));
        iconLp.rightMargin = dp(5);
        pill.addView(statusIcon, iconLp);

        label = text("ToxiFiles", 11, Color.WHITE, true);
        label.setSingleLine(true);
        label.setMaxEms(13);
        pill.addView(label, new LinearLayout.LayoutParams(-2, -2));
        pill.setOnTouchListener(this::handleTouch);
        pill.setOnClickListener(v -> toggleExpanded());
        ToxiVisualSystem.interactive(pill);
        shell.addView(pill, new LinearLayout.LayoutParams(dp(132), dp(prefs.getInt("size", 0) == 0 ? 36 : 42)));

        expanded = new LinearLayout(host);
        expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setPadding(dp(16), dp(13), dp(16), dp(12));
        cardSurface = new IslandSurfaceDrawable(host, prefs, true);
        expanded.setBackground(cardSurface);
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

        ImageView appMark = new ImageView(host);
        appMark.setImageResource(R.drawable.ic_toxifiles_brand_mark);
        appMark.setPadding(dp(5), dp(5), dp(5), dp(5));
        appMark.setBackground(ToxiVisualSystem.surface(10, true));
        appMark.setContentDescription("ToxiFiles brand symbol");
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

        ImageView close = new ImageView(host);
        close.setImageDrawable(ToxiIcons.symbol(ToxiIcons.COLLAPSE, ToxiVisualSystem.TEXT_PRIMARY));
        close.setContentDescription("Collapse activity panel");
        close.setPadding(dp(5), dp(5), dp(5), dp(5));
        close.setOnClickListener(v -> collapse());
        ToxiVisualSystem.interactive(close);
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
        ToxiVisualSystem.interactive(action);
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
        v.setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        return v;
    }
    private int accent() { return themedAccent(prefs); }
    private static int themedAccent(SharedPreferences preferences) {
        int treatment = Math.max(0, Math.min(3, preferences.getInt("palette", 0)));
        int theme = ToxiVisualSystem.THEME_INDEX;
        if (treatment == 1) return ToxiThemeSystem.ACCENT_ENDS[theme];
        if (treatment == 2) return ToxiThemeSystem.MUTED[theme];
        if (treatment == 3) return ToxiThemeSystem.EDGES[theme];
        return ToxiThemeSystem.ACCENTS[theme];
    }
    private GradientDrawable actionBackground() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{accent(), ToxiThemeSystem.ACCENT_ENDS[ToxiVisualSystem.THEME_INDEX]});
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), ToxiThemeSystem.EDGES[ToxiVisualSystem.THEME_INDEX]);
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
        if (getVisibility() == VISIBLE) refreshStateAccent();
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        shell.animate().cancel();
        clearStateAccent();
        super.onDetachedFromWindow();
    }

    private void refreshStateAccent() {
        clearStateAccent();
        setStatePhase(0f);
    }

    private void clearStateAccent() { }

    private void setStatePhase(float phase) {
        statePhase = phase;
        pillSurface.setState(activeState, phase);
        cardSurface.setState(activeState, phase);
    }

    private void updateGlowActivity(boolean active) {
        if (activeState == active) return;
        activeState = active;
        if (getVisibility() == VISIBLE) refreshStateAccent();
        else setStatePhase(0f);
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
        if (ToxiVisualSystem.booleanPreference("reduce_motion", true)) {
            shell.setScaleX(1f);
            shell.setScaleY(1f);
            shell.setAlpha(1f);
        } else {
            shell.setScaleX(0.94f);
            shell.setScaleY(0.94f);
            shell.setAlpha(0.92f);
            shell.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(Math.max(100, prefs.getInt("speed", 180)))
                .setInterpolator(new DecelerateInterpolator()).start();
        }
        post(this::applyPosition);
    }
    private void collapse() {
        expanded.setVisibility(GONE);
        if (ToxiVisualSystem.booleanPreference("reduce_motion", true)) {
            shell.setScaleX(1f);
            shell.setScaleY(1f);
            shell.setAlpha(1f);
        } else {
            shell.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(Math.max(80, prefs.getInt("speed", 180) / 2)).start();
        }
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
            refreshStateAccent();
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
        activeState = false;
        setStatePhase(0f);
        clearStateAccent();
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
        statusIcon.setImageDrawable(ToxiIcons.symbol(active ? ToxiIcons.ANALYTICS : ToxiIcons.CHECK, active ? accent() : ToxiVisualSystem.TEXT_SECONDARY));
        statusIcon.setContentDescription(active ? "Activity in progress" : "Activity complete");
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
        if (getVisibility() == VISIBLE) refreshStateAccent();
        else { clearStateAccent(); setStatePhase(0f); }
        post(this::applyPosition);
    }
    /** Theme-bound matte panel used by the compact and expanded island states. */
    private static final class IslandSurfaceDrawable extends Drawable {
        private final Activity host;
        private final SharedPreferences prefs;
        private final boolean card;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean active;

        IslandSurfaceDrawable(Activity host, SharedPreferences prefs, boolean card) {
            this.host = host;
            this.prefs = prefs;
            this.card = card;
            fill.setStyle(Paint.Style.FILL);
            edge.setStyle(Paint.Style.STROKE);
            accentLine.setStyle(Paint.Style.STROKE);
            accentLine.setStrokeCap(Paint.Cap.ROUND);
        }

        private float dp(float value) { return value * host.getResources().getDisplayMetrics().density; }
        void setState(boolean active, float ignoredPhase) { this.active = active; invalidateSelf(); }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            rect.set(bounds.left + dp(1), bounds.top + dp(1), bounds.right - dp(1), bounds.bottom - dp(1));
            int theme = ToxiVisualSystem.THEME_INDEX;
            int base = card ? ToxiThemeSystem.RAISED[theme] : Color.BLACK;
            int opacity = Math.max(80, Math.min(255, prefs.getInt("transparency", 225)));
            fill.setColor((opacity << 24) | (base & 0x00FFFFFF));
            fill.setShader(null);
            canvas.drawRoundRect(rect, dp(card ? 21 : 18), dp(card ? 21 : 18), fill);
            int[] spectrum = new int[]{0xFF00E5FF, 0xFF1677FF, 0xFF843BFF, 0xFFD12BFF, 0xFFFF2878, 0xFFFF4D36, 0xFF00E5FF};
            edge.setColor(Color.WHITE);
            edge.setShader(card ? null : new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, spectrum, null, Shader.TileMode.CLAMP));
            edge.setStrokeWidth(dp(card ? 1f : 1.25f));
            canvas.drawRoundRect(rect, dp(card ? 21 : 18), dp(card ? 21 : 18), edge);
            float intensity = Math.max(0, Math.min(100, prefs.getInt("glow", 65))) / 100f;
            accentLine.setColor(themedAccent(prefs));
            accentLine.setShader(card ? null : new LinearGradient(rect.left, rect.top, rect.right, rect.top, spectrum, null, Shader.TileMode.CLAMP));
            accentLine.setAlpha((int)((active ? 150 : 70) * intensity));
            accentLine.setStrokeWidth(dp(active ? 1.6f : 1f));
            canvas.drawLine(rect.left + dp(12), rect.top + dp(1), rect.right - dp(12), rect.top + dp(1), accentLine);
        }

        @Override public void getOutline(Outline outline) {
            Rect bounds = getBounds();
            outline.setRoundRect(bounds, dp(card ? 21f : 18f));
        }
        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); edge.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static SharedPreferences preferences(Activity activity) {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }
}

