package com.music.echo.offline.files;

import com.music.echo.offline.R;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Live anime identity surface shared by Home, Files, Tools and Settings. */
final class ToxiThemeIdentityView extends FrameLayout {
    private final ImageView artwork;
    private final ViewOverlay overlay;
    private final TextView eyebrow, title, description;
    private final int fixedHeightPx;

    ToxiThemeIdentityView(Context context, int heightDp, String label) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
        fixedHeightPx = ToxiVisualSystem.dp(heightDp);
        setMinimumHeight(fixedHeightPx);
        setBackground(ToxiVisualSystem.surface(20, true));
        ToxiVisualSystem.clipRound(this, 20f);
        artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setAlpha(0.92f);
        addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        overlay = new ViewOverlay(context);
        addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(ToxiVisualSystem.dp(15), ToxiVisualSystem.dp(11),
                ToxiVisualSystem.dp(15), ToxiVisualSystem.dp(11));
        eyebrow = ToxiVisualSystem.text(context, label, 9, ToxiVisualSystem.ACCENT, true);
        eyebrow.setLetterSpacing(0.15f);
        copy.addView(eyebrow);
        title = ToxiVisualSystem.text(context, "", 16, ToxiVisualSystem.TEXT_PRIMARY, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = ToxiVisualSystem.dp(5);
        copy.addView(title, titleLp);
        description = ToxiVisualSystem.text(context, "", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, false);
        description.setMaxLines(2);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descriptionLp = new LinearLayout.LayoutParams(-1, -2);
        descriptionLp.topMargin = ToxiVisualSystem.dp(4);
        copy.addView(description, descriptionLp);
        addView(copy, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_VERTICAL));
        refresh();
    }

    /** Keep poster artwork inside its designed compact banner height in wrap-content scroll layouts. */
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int measuredHeight = resolveSize(fixedHeightPx, heightMeasureSpec);
        int childWidthSpec = MeasureSpec.makeMeasureSpec(Math.max(0, measuredWidth - getPaddingLeft() - getPaddingRight()), MeasureSpec.EXACTLY);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(0, measuredHeight - getPaddingTop() - getPaddingBottom()), MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec);
        }
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    void refresh() {
        int index = ToxiThemeSystem.normalize(ToxiVisualSystem.THEME_INDEX);
        int poster;
        switch (index) {
            case 0: poster = R.drawable.theme_poster_onepiece; break;
            case 1: poster = R.drawable.theme_poster_naruto; break;
            case 2: poster = R.drawable.theme_poster_demon; break;
            case 3: poster = R.drawable.theme_poster_dragonball; break;
            default: poster = R.drawable.theme_poster_aot; break;
        }
        artwork.setImageResource(poster);
        title.setText(ToxiVisualSystem.THEME_NAMES[index]);
        description.setText(ToxiVisualSystem.themeDescription(index));
        eyebrow.setTextColor(ToxiVisualSystem.signalTextColor());
        title.setTextColor(ToxiVisualSystem.TEXT_PRIMARY);
        description.setTextColor(ToxiVisualSystem.TEXT_SECONDARY);
        overlay.refresh();
        setContentDescription(ToxiVisualSystem.THEME_NAMES[index] + ". "
                + ToxiVisualSystem.themeDescription(index));
    }

    /** Draws an original, theme-owned emblem over the poster; no character or franchise logo is reused. */
    private static final class ViewOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        ViewOverlay(Context context) {
            super(context);
            setWillNotDraw(false);
            refresh();
        }

        void refresh() {
            int canvas = ToxiVisualSystem.CANVAS;
            int opaque = Color.argb(238, Color.red(canvas), Color.green(canvas), Color.blue(canvas));
            int middle = Color.argb(168, Color.red(canvas), Color.green(canvas), Color.blue(canvas));
            setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{opaque, middle, Color.argb(22, Color.red(canvas),
                            Color.green(canvas), Color.blue(canvas))}));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0f || h <= 0f) return;
            int save = canvas.save();
            canvas.translate(w * 0.77f, h * 0.5f);
            float unit = Math.min(h * 0.34f, w * 0.17f);
            canvas.scale(unit, unit);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(0.035f);
            paint.setColor(Color.argb(ToxiThemeEngine.highlightAlpha(178),
                    Color.red(ToxiVisualSystem.CHROME), Color.green(ToxiVisualSystem.CHROME),
                    Color.blue(ToxiVisualSystem.CHROME)));
            switch (ToxiThemeEngine.profile().motif) {
                case COMPASS: drawCompass(canvas); break;
                case SPIRAL: drawSpiral(canvas); break;
                case FLOW: drawFlow(canvas); break;
                case ORBIT: drawOrbit(canvas); break;
                case RAMPART: drawRampart(canvas); break;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(210, Color.red(ToxiVisualSystem.ACCENT),
                    Color.green(ToxiVisualSystem.ACCENT), Color.blue(ToxiVisualSystem.ACCENT)));
            canvas.drawCircle(0f, 0f, 0.055f, paint);
            canvas.restoreToCount(save);
        }

        private void drawCompass(Canvas canvas) {
            canvas.drawCircle(0f, 0f, 0.88f, paint);
            canvas.drawCircle(0f, 0f, 0.68f, paint);
            path.reset();
            path.moveTo(0f, -1.05f); path.lineTo(0.13f, -0.13f);
            path.lineTo(1.05f, 0f); path.lineTo(0.13f, 0.13f);
            path.lineTo(0f, 1.05f); path.lineTo(-0.13f, 0.13f);
            path.lineTo(-1.05f, 0f); path.lineTo(-0.13f, -0.13f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawLine(-1.12f, 0f, -0.94f, 0f, paint);
            canvas.drawLine(0.94f, 0f, 1.12f, 0f, paint);
            canvas.drawLine(0f, -1.12f, 0f, -0.94f, paint);
            canvas.drawLine(0f, 0.94f, 0f, 1.12f, paint);
        }

        private void drawSpiral(Canvas canvas) {
            canvas.drawCircle(0f, 0f, 0.92f, paint);
            path.reset();
            for (int i = 0; i <= 72; i++) {
                double angle = i * Math.PI * 2.0 / 24.0;
                float radius = 0.06f + i * 0.012f;
                float x = (float) Math.cos(angle) * radius;
                float y = (float) Math.sin(angle) * radius;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            canvas.drawPath(path, paint);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                float x = (float) Math.cos(angle) * 0.99f;
                float y = (float) Math.sin(angle) * 0.99f;
                canvas.drawCircle(x, y, 0.035f, paint);
            }
        }

        private void drawFlow(Canvas canvas) {
            for (int i = 0; i < 3; i++) {
                float offset = (i - 1) * 0.24f;
                path.reset();
                path.moveTo(-1.05f, offset - 0.34f);
                path.cubicTo(-0.38f, offset - 0.92f, 0.20f, offset + 0.92f, 1.05f, offset + 0.34f);
                canvas.drawPath(path, paint);
            }
            canvas.drawCircle(-0.82f, -0.53f, 0.06f, paint);
            canvas.drawCircle(0.82f, 0.53f, 0.06f, paint);
        }

        private void drawOrbit(Canvas canvas) {
            canvas.drawCircle(0f, 0f, 0.30f, paint);
            RectF oval = new RectF(-1.02f, -0.43f, 1.02f, 0.43f);
            canvas.drawOval(oval, paint);
            canvas.save();
            canvas.rotate(58f);
            canvas.drawOval(oval, paint);
            canvas.restore();
            canvas.save();
            canvas.rotate(-58f);
            canvas.drawOval(oval, paint);
            canvas.restore();
            canvas.drawCircle(0.91f, 0.14f, 0.085f, paint);
        }

        private void drawRampart(Canvas canvas) {
            path.reset();
            path.moveTo(-1.02f, 0.82f);
            path.lineTo(-1.02f, -0.42f);
            path.lineTo(-0.72f, -0.42f);
            path.lineTo(-0.72f, -0.78f);
            path.lineTo(-0.32f, -0.78f);
            path.lineTo(-0.32f, -0.42f);
            path.lineTo(0.10f, -0.42f);
            path.lineTo(0.10f, -0.78f);
            path.lineTo(0.50f, -0.78f);
            path.lineTo(0.50f, -0.42f);
            path.lineTo(1.02f, -0.42f);
            path.lineTo(1.02f, 0.82f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawLine(-0.70f, -0.12f, 0.70f, -0.12f, paint);
            canvas.drawLine(-0.70f, 0.30f, 0.70f, 0.30f, paint);
            canvas.drawLine(-0.40f, 0.82f, -0.40f, 0.30f, paint);
            canvas.drawLine(0.40f, 0.82f, 0.40f, 0.30f, paint);
        }
    }

    static ToxiThemeIdentityView create(Context context, int heightDp, String label) {
        return new ToxiThemeIdentityView(context, heightDp, label);
    }
}
