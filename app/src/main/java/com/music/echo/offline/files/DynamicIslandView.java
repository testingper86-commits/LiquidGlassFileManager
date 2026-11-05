package com.music.echo.offline.files;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
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
        {0xFF00E5FF,0xFF1677FF,0xFF843BFF},
        {0xFF00E5FF,0xFF1677FF,0xFF00BFA5},
        {0xFFFF4D36,0xFFFF2878,0xFFD12BFF},
        {0xFF1677FF,0xFF843BFF,0xFFD12BFF}
    };

    private final Activity host;
    private final SharedPreferences prefs;
    private final LinearLayout shell, pill, expanded, titleRow, actionRow;
    private final TextView lens, statusIcon, label, title, subtitle, action;
    private final ProgressBar progressBar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable longPress;
    private Runnable actionCallback;
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

        pill = new LinearLayout(host);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(13), 0, dp(13), 0);
        pill.setBackground(pillBackground());
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
        expanded.setBackground(cardBackground());
        expanded.setElevation(dp(16));
        LinearLayout.LayoutParams expandedLp = new LinearLayout.LayoutParams(dp(318), -2);
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
        subtitle = text("Ready", 11, 0xFF9AA6B5, false);
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

        addView(shell, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        setVisibility(isEnabledPreference() ? VISIBLE : GONE);
        applyPosition();
        render();
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
    private GradientDrawable pillBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xF5070A0F);
        d.setCornerRadius(dp(30));
        d.setStroke(dp(1), 0xFF303943);
        return d;
    }
    private GradientDrawable cardBackground() {
        int alpha = Math.max(220, Math.min(255, prefs.getInt("transparency", 245)));
        GradientDrawable d = new GradientDrawable();
        d.setColor((alpha << 24) | 0x00090D13);
        d.setCornerRadius(dp(25));
        d.setStroke(dp(1), 0xFF303943);
        return d;
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
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }
    private void applyPosition() {
        if (getWidth() <= 0 || shell.getMeasuredWidth() <= 0) {
            post(this::applyPosition);
            return;
        }
        int maxX = Math.max(0, getWidth() - shell.getMeasuredWidth());
        int maxY = Math.max(statusBarHeight() + dp(4), getHeight() - shell.getMeasuredHeight());
        float nx = prefs.getFloat("x", 0.5f);
        float ny = prefs.getFloat("y", Math.min(0.08f, (statusBarHeight() + dp(8)) / (float)Math.max(1, getHeight())));
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
    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        shell.animate().cancel();
        super.onDetachedFromWindow();
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
        if ((lower.contains("copy") || lower.contains("mov") || lower.contains("delet") || lower.contains("zip") || lower.contains("extract"))
                && !prefs.getBoolean("show_operations", true)) return;
        if ((lower.contains("video") || lower.contains("play")) && !prefs.getBoolean("show_playback", true)) return;
        if ((lower.contains("scan") || lower.contains("index")) && !prefs.getBoolean("show_scan", true)) return;
        boolean changed = !next.equals(activity);
        activity = next;
        progress = Math.max(-1, Math.min(100, percent));
        render();
        if (changed && isEnabledPreference() && prefs.getBoolean("auto_expand", true)
                && !activity.equals("ToxiFiles ready") && !activity.equals("Ready")) expand();
    }
    private void render() {
        String lower = activity.toLowerCase(java.util.Locale.ROOT);
        boolean active = !lower.equals("toxifiles ready") && !lower.equals("ready");
        label.setText(active ? compact(activity) : "ToxiFiles");
        statusIcon.setText(active ? "●" : "◆");
        statusIcon.setTextColor(active ? accent() : 0xFF8995A4);
        title.setText(activity);
        subtitle.setText(progress >= 0 ? "In progress · " + progress + "%" : active ? "Activity in progress" : "Your files, under control");
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
            actionCallback = null;
        } else {
            action.setText(titleText);
            action.setVisibility(VISIBLE);
        }
    }
    void setEnabledFromSettings() {
        setVisibility(isEnabledPreference() ? VISIBLE : GONE);
        if (!isEnabledPreference()) collapse();
    }
    void refreshStyle() {
        pill.setBackground(pillBackground());
        expanded.setBackground(cardBackground());
        action.setBackground(actionBackground());
        action.setTextColor(accent());
        progressBar.setProgressTintList(ColorStateList.valueOf(accent()));
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(accent()));
        pill.setMinimumHeight(dp(prefs.getInt("size", 0) == 0 ? 36 : 42));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)pill.getLayoutParams();
        lp.height = dp(prefs.getInt("size", 0) == 0 ? 36 : 42);
        pill.setLayoutParams(lp);
        setElevation(dp(6 + prefs.getInt("glow", 65) / 10));
        post(this::applyPosition);
    }
    static SharedPreferences preferences(Activity activity) {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }
}
