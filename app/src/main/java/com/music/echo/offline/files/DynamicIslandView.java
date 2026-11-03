package com.music.echo.offline.files;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Environment;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Native, lifecycle-bound Liquid Glass activity island for ToxiFiles. */
final class DynamicIslandView extends View {
    private final MainActivity host;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private final int[] rgb = new int[] {
            Color.rgb(0, 245, 255), Color.rgb(35, 105, 255),
            Color.rgb(125, 65, 255), Color.rgb(210, 45, 255),
            Color.rgb(255, 45, 170), Color.rgb(255, 65, 75),
            Color.rgb(0, 245, 255)
    };
    private ValueAnimator animator;
    private float phase;
    private float rippleX, rippleY, rippleRadius;
    private int rippleAlpha;
    private boolean pressed;
    private boolean panelOpen;

    DynamicIslandView(MainActivity activity) {
        super(activity);
        host = activity;
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setClickable(true);
        setFocusable(true);
        setContentDescription("ToxiFiles Dynamic Island. Tap to open the control centre.");
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        label.setTextSize(dp(10));
        label.setLetterSpacing(0.12f);
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(Color.WHITE);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(dp(1.6f));
        fill.setColor(Color.argb(232, 10, 13, 25));
        highlight.setStyle(Paint.Style.STROKE);
        highlight.setStrokeWidth(dp(0.8f));
        highlight.setColor(Color.argb(90, 255, 255, 255));
        setOnClickListener(v -> openControlCentre());
        setOnLongClickListener(v -> { openControlCentre(); return true; });
    }

    static void attach(Activity activity) {
        if (!(activity instanceof MainActivity) || activity.isFinishing()) return;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) content;
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child instanceof DynamicIslandView) parent.removeView(child);
        }
        DynamicIslandView island = new DynamicIslandView((MainActivity) activity);
        island.setTag("toxifiles_dynamic_island");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(island.dp(174), island.dp(38), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = island.dp(1);
        parent.addView(island, lp);
    }

    static void detach(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) content;
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child instanceof DynamicIslandView) parent.removeView(child);
        }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void startAnimation() {
        stopAnimation();
        if (host.isFinishing() || host.isDestroyed() || !Ui.bool("island_rgb", true) || Ui.bool("island_reduce_motion", false)) {
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(Math.max(2800L, Math.min(12000L, Ui.sp.getInt("island_speed", 6500))));
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> { phase = (Float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator.removeAllUpdateListeners();
            animator = null;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = dp(2);
        pill.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float radius = pill.height() / 2f;
        int transparency = Ui.sp.getInt("island_transparency", 88);
        int alpha = Math.max(125, Math.min(245, Math.round(255f * transparency / 100f)));
        fill.setColor(Color.argb(alpha, 9, 12, 24));
        canvas.drawRoundRect(pill, radius, radius, fill);

        if (Ui.bool("island_glow", true)) {
            Shader glow = new SweepGradient(pill.centerX(), pill.centerY(), rgb, null);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setRotate(phase, pill.centerX(), pill.centerY());
            glow.setLocalMatrix(matrix);
            edge.setShader(glow);
            edge.setStrokeWidth(dp(5));
            edge.setAlpha(62);
            canvas.drawRoundRect(pill, radius, radius, edge);
            edge.setStrokeWidth(dp(1.8f));
            edge.setAlpha(255);
            canvas.drawRoundRect(pill, radius, radius, edge);
            edge.setShader(null);
        } else {
            edge.setColor(Color.argb(180, 190, 205, 255));
            edge.setStrokeWidth(dp(1));
            canvas.drawRoundRect(pill, radius, radius, edge);
        }

        highlight.setShader(new LinearGradient(0, pill.top, 0, pill.centerY(),
                new int[]{Color.argb(75,255,255,255), Color.argb(0,255,255,255)}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(pill.left + dp(2), pill.top + dp(1), pill.right - dp(2), pill.centerY(), radius, radius, highlight);
        highlight.setShader(null);

        if (rippleAlpha > 0) {
            Paint ripple = new Paint(Paint.ANTI_ALIAS_FLAG);
            ripple.setColor(Color.argb(rippleAlpha, 100, 220, 255));
            canvas.save();
            canvas.clipRoundRect(pill, radius, radius);
            canvas.drawCircle(rippleX, rippleY, rippleRadius, ripple);
            canvas.restore();
            rippleRadius += dp(1.8f);
            rippleAlpha = Math.max(0, rippleAlpha - 9);
            postInvalidateOnAnimation();
        }

        String status = host.busyCount > 0 && host.busyTv != null && host.busyTv.getVisibility() == VISIBLE
                ? "ACTIVE  ·  " + Math.min(host.busyCount, 99)
                : "TOXIFILES";
        label.setTextSize(dp(10));
        canvas.drawText(status, pill.centerX(), pill.centerY() + dp(3.5f), label);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                rippleX = event.getX(); rippleY = event.getY();
                rippleRadius = dp(3); rippleAlpha = 80;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (pressed) {
                    pressed = false;
                    performClick();
                    if (Ui.bool("island_haptics", true)) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        openControlCentre();
        return true;
    }

    private TextView text(String value, int size, boolean strong) {
        TextView v = new TextView(host);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(Color.WHITE);
        v.setTypeface(Typeface.create("sans-serif", strong ? Typeface.BOLD : Typeface.NORMAL));
        v.setPadding(dp(14), dp(11), dp(14), dp(11));
        return v;
    }

    private void action(LinearLayout parent, String title, String detail, Runnable task) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(7), dp(12), dp(7));
        row.setBackground(round(Color.argb(34, 180, 205, 255), dp(12)));
        TextView t = text(title, 14, true);
        t.setPadding(0, 0, 0, 0);
        row.addView(t);
        if (detail != null) {
            TextView d = text(detail, 11, false);
            d.setTextColor(Color.rgb(178, 190, 215));
            d.setPadding(0, dp(3), 0, 0);
            row.addView(d);
        }
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            if (panelOpen) {
                panelOpen = false;
                if (host.isFinishing() || host.isDestroyed()) return;
                task.run();
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(6);
        parent.addView(row, p);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), Color.argb(38, 210, 225, 255));
        return d;
    }

    private void section(LinearLayout parent, String title, String subtitle, String[] labels, Runnable[] actions) {
        LinearLayout group = new LinearLayout(host);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView header = text(title + "   +", 14, true);
        header.setBackground(round(Color.argb(45, 115, 145, 210), dp(13)));
        group.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(8), dp(7), dp(8), dp(2));
        body.setVisibility(View.GONE);
        for (int i = 0; i < labels.length; i++) action(body, labels[i], i == 0 ? subtitle : null, actions[i]);
        group.addView(body);
        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            header.setText(title + (show ? "   −" : "   +"));
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(7);
        parent.addView(group, p);
    }

    private void openControlCentre() {
        if (panelOpen || host.isFinishing() || host.isDestroyed()) return;
        panelOpen = true;
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackground(round(Color.rgb(14, 18, 32), dp(22)));
        TextView heading = text("TOXIFILES  /  CONTROL CENTRE", 16, true);
        heading.setPadding(dp(4), dp(4), dp(4), dp(2));
        root.addView(heading);
        TextView current = text(host.busyCount > 0 && host.busyTv != null
                ? host.busyTv.getText().toString() : "Native file tools and live activity status", 11, false);
        current.setTextColor(Color.rgb(175, 190, 218));
        current.setPadding(dp(4), 0, dp(4), dp(10));
        root.addView(current);

        ScrollView scroll = new ScrollView(host);
        LinearLayout groups = new LinearLayout(host);
        groups.setOrientation(LinearLayout.VERTICAL);
        section(groups, "FILES", "Open the native file browser", new String[]{"Browse files", "Search files"}, new Runnable[]{
                () -> host.selectTab(1), () -> host.openSearch()
        });
        section(groups, "APK", "Browse APK files; tapping a file uses Android's install confirmation", new String[]{"Browse APK files"}, new Runnable[]{
                () -> openApkFiles()
        });
        section(groups, "STORAGE", "Analyze actual indexed storage", new String[]{"Storage analyzer", "Find duplicate files", "Rescan storage"}, new Runnable[]{
                () -> host.openStorageAnalyzer(), () -> host.openDuplicates(),
                () -> { Indexer.I.dirty = true; host.startIndex(); android.widget.Toast.makeText(host, "Storage scan started", android.widget.Toast.LENGTH_SHORT).show(); }
        });
        section(groups, "ARCHIVE", "ZIP extraction is built in; other formats can be listed", new String[]{"Browse archives"}, new Runnable[]{
                () -> host.openCategory(4)
        });
        section(groups, "TRANSFERS", "Encrypted local Wi-Fi transfer controls", new String[]{"Receive via Wi-Fi", "Cancel active operation"}, new Runnable[]{
                () -> host.receiveViaWifi(),
                () -> { FileOps.requestCancel(); LanTransfer.cancel(); android.widget.Toast.makeText(host, "Cancellation requested", android.widget.Toast.LENGTH_SHORT).show(); }
        });
        section(groups, "SETTINGS", "Tune the Island without changing the app theme", new String[]{"Island appearance settings"}, new Runnable[]{
                () -> openIslandSettings()
        });
        scroll.addView(groups);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(390)));

        AlertDialog dialog = new AlertDialog.Builder(host).setView(root).setNegativeButton("Close", (d, w) -> {}).create();
        dialog.setOnDismissListener(d -> panelOpen = false);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().setLayout(Math.min(host.getResources().getDisplayMetrics().widthPixels - dp(28), dp(420)), -2);
            }
        });
        dialog.show();
    }

    private void openApkFiles() {
        if (!Indexer.I.ready) {
            host.startIndex();
            android.widget.Toast.makeText(host, "Indexing files. Try again when the scan finishes.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        List<Entry> apks = new ArrayList<>();
        for (Entry entry : Indexer.I.all) if (!entry.dir && entry.kind == FileKinds.APK) apks.add(entry);
        host.listView.setList("APK Files", apks, false, false);
        host.showOverlay(host.listView);
    }

    private void openIslandSettings() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(8));
        addSwitch(box, "RGB gradient animation", "island_rgb", true);
        addSwitch(box, "Glowing border", "island_glow", true);
        addSwitch(box, "Liquid touch haptics", "island_haptics", true);
        addSwitch(box, "Reduce motion", "island_reduce_motion", false);
        TextView opacity = text("Glass opacity", 13, true);
        opacity.setPadding(0, dp(12), 0, dp(4));
        box.addView(opacity);
        SeekBar seek = new SeekBar(host);
        seek.setMax(100);
        seek.setProgress(Ui.sp.getInt("island_transparency", 88));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) Ui.sp.edit().putInt("island_transparency", Math.max(49, value)).apply();
                invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        box.addView(seek);
        TextView speed = text("Animation speed", 13, true);
        speed.setPadding(0, dp(12), 0, dp(4));
        box.addView(speed);
        String[] speeds = {"Slow", "Normal", "Fast"};
        int[] durations = {9500, 6500, 3600};
        android.widget.Spinner spinner = new android.widget.Spinner(host);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(host, android.R.layout.simple_spinner_dropdown_item, speeds);
        spinner.setAdapter(adapter);
        int current = Ui.sp.getInt("island_speed", 6500);
        spinner.setSelection(current >= 8000 ? 0 : current <= 4000 ? 2 : 1);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Ui.sp.edit().putInt("island_speed", durations[position]).apply();
                startAnimation();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        box.addView(spinner);
        new AlertDialog.Builder(host).setTitle("Dynamic Island settings").setView(box).setPositiveButton("Done", (d, w) -> invalidate()).show();
    }

    private void addSwitch(LinearLayout parent, String title, String key, boolean defaultValue) {
        Switch sw = new Switch(host);
        sw.setText(title);
        sw.setTextColor(Color.WHITE);
        sw.setChecked(Ui.bool(key, defaultValue));
        sw.setOnCheckedChangeListener((button, checked) -> {
            Ui.set(key, checked);
            if ("island_rgb".equals(key) || "island_reduce_motion".equals(key)) startAnimation();
            invalidate();
        });
        parent.addView(sw);
    }
}
