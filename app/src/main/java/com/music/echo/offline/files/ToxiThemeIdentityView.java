package com.music.echo.offline.files;

import com.music.echo.offline.R;
import android.content.Context;
import android.graphics.Color;
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

    ToxiThemeIdentityView(Context context, int heightDp, String label) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
        setMinimumHeight(ToxiVisualSystem.dp(heightDp));
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

    private static final class ViewOverlay extends View {
        ViewOverlay(Context context) { super(context); refresh(); }
        void refresh() {
            int canvas = ToxiVisualSystem.CANVAS;
            int opaque = Color.argb(238, Color.red(canvas), Color.green(canvas), Color.blue(canvas));
            int middle = Color.argb(168, Color.red(canvas), Color.green(canvas), Color.blue(canvas));
            setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{opaque, middle, Color.argb(22, Color.red(canvas),
                            Color.green(canvas), Color.blue(canvas))}));
            invalidate();
        }
    }

    static ToxiThemeIdentityView create(Context context, int heightDp, String label) {
        return new ToxiThemeIdentityView(context, heightDp, label);
    }
}
