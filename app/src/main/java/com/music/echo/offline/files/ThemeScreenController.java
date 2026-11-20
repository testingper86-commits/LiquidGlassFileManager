package com.music.echo.offline.files;

import com.music.echo.offline.R;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Five-theme Theme Studio with persistent live application. */
final class ThemeScreenController {
    private final MainActivity activity;
    ThemeScreenController(MainActivity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        this.activity = activity;
    }
    void open() {
        activity.themeScroll.setSaveEnabled(false);
        activity.themeScroll.scrollTo(0, 0);
        build();
        activity.showOverlay(activity.themeScroll);
    }

    private static int themePoster(int index) {
        switch (ToxiThemeSystem.normalize(index)) {
            case 0: return R.drawable.theme_poster_onepiece;
            case 1: return R.drawable.theme_poster_naruto;
            case 2: return R.drawable.theme_poster_demon;
            case 3: return R.drawable.theme_poster_dragonball;
            default: return R.drawable.theme_poster_aot;
        }
    }

    private static int themeCategoryArtwork(int themeIndex, int categoryIndex) {
        int theme = ToxiThemeSystem.normalize(themeIndex);
        switch (theme) {
            case 0:
                return new int[]{R.drawable.home_category_onepiece_images,
                        R.drawable.home_category_onepiece_videos, R.drawable.home_category_onepiece_audio,
                        R.drawable.home_category_onepiece_documents}[categoryIndex];
            case 1:
                return new int[]{R.drawable.home_category_naruto_images,
                        R.drawable.home_category_naruto_videos, R.drawable.home_category_naruto_audio,
                        R.drawable.home_category_naruto_documents}[categoryIndex];
            case 2:
                return new int[]{R.drawable.home_category_demon_images,
                        R.drawable.home_category_demon_videos, R.drawable.home_category_demon_audio,
                        R.drawable.home_category_demon_documents}[categoryIndex];
            case 3:
                return new int[]{R.drawable.home_category_dragonball_images,
                        R.drawable.home_category_dragonball_videos, R.drawable.home_category_dragonball_audio,
                        R.drawable.home_category_dragonball_documents}[categoryIndex];
            default:
                return new int[]{R.drawable.home_category_aot_images,
                        R.drawable.home_category_aot_videos, R.drawable.home_category_aot_audio,
                        R.drawable.home_category_aot_documents}[categoryIndex];
        }
    }

    private static void setPreviewArtwork(MainActivity activity, ImageView target, int resourceId) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(activity.getResources(), resourceId, bounds);
        int targetPixels = ToxiVisualSystem.dp(44);
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / (sample * 2) >= targetPixels * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inDither = true;
        Bitmap bitmap = BitmapFactory.decodeResource(activity.getResources(), resourceId, options);
        if (bitmap != null) {
            BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), bitmap);
            drawable.setFilterBitmap(true);
            drawable.setAntiAlias(true);
            target.setImageDrawable(drawable);
        } else {
            target.setImageResource(resourceId);
        }
        target.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }
    void build() {
        activity.themeScroll.setSaveEnabled(false);
        int restoreScrollY = Math.max(0, activity.themeScroll.getScrollY());
        activity.themeScroll.setFocusable(true);
        activity.themeScroll.setFocusableInTouchMode(true);
        activity.themeScroll.removeAllViews();
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setFocusable(false);
        panel.setFocusableInTouchMode(false);
        panel.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        int backTarget = ToxiVisualSystem.dp(ToxiThemeEngine.touchTarget(48));
        back.setContentDescription("Back");
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setFocusable(true);
        back.setClickable(true);
        back.setMinimumWidth(backTarget);
        back.setMinimumHeight(backTarget);
        back.setOnClickListener(v -> activity.closeOverlay());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(backTarget, backTarget);
        backParams.gravity = Gravity.CENTER_VERTICAL;
        header.addView(back, backParams);
        TextView title = ToxiVisualSystem.text(activity, "Theme Studio", 23, ToxiVisualSystem.TEXT_PRIMARY, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.leftMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));
        header.addView(title, titleLp);
        panel.addView(header);
        title.setLetterSpacing(-0.025f);
        TextView subtitle = ToxiVisualSystem.text(activity, "Five anime worlds. One unified, adaptive filespace.", 13, ToxiVisualSystem.TEXT_SECONDARY, false);
        subtitle.setLineSpacing(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), 1.04f);
        subtitle.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
        panel.addView(subtitle);
        TextView label = ToxiVisualSystem.text(activity, "CHOOSE YOUR WORLD", 10.5f, ToxiVisualSystem.TEXT_SECONDARY, true);
        label.setLetterSpacing(0.14f);
        label.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, 0,
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        panel.addView(label);
        // Use one vertical scrolling container. Nested ScrollViews competed with
        // touch interception and prevented theme tiles from receiving drag/click input.
        LinearLayout cards = new LinearLayout(activity);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        cards.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)));
        for (int i = 0; i < ToxiVisualSystem.THEME_NAMES.length; i++) {
            final int themeIndex = i;
            boolean selected = i == ToxiVisualSystem.THEME_INDEX;
            FrameLayout tile = new FrameLayout(activity);
            tile.setClipChildren(true);
            tile.setClipToPadding(true);
            tile.setBackground(ToxiVisualSystem.surface(22, true));
            ToxiVisualSystem.clipRound(tile, 22f);

            // Keep one crisp poster only. A duplicate full-card copy created
            // a ghosted second character behind the contained poster.
            ImageView poster = new ImageView(activity);
            poster.setImageResource(themePoster(i));
            poster.setScaleType(ImageView.ScaleType.FIT_CENTER);\n            poster.setAdjustViewBounds(true);\n            poster.setCropToPadding(false);
            poster.setBackgroundColor(Color.TRANSPARENT);
            poster.setAlpha(selected ? 1f : 0.96f);
            poster.setContentDescription(ToxiVisualSystem.THEME_NAMES[i] + " full cinematic poster artwork");
            // Size the poster viewport from the source artwork's real aspect
            // ratio. A fixed-width box can make different portrait posters look
            // inconsistently zoomed or clipped on narrow and wide devices.
            android.graphics.drawable.Drawable posterDrawable = poster.getDrawable();
            int sourceWidth = posterDrawable == null ? 0 : posterDrawable.getIntrinsicWidth();
            int sourceHeight = posterDrawable == null ? 0 : posterDrawable.getIntrinsicHeight();
            float posterAspect = sourceWidth > 0 && sourceHeight > 0
                    ? (float) sourceWidth / (float) sourceHeight : 0.68f;
            int cardHeight = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(216));
            int maxPosterWidth = ToxiVisualSystem.dp(156);
            int posterWidth = Math.min(maxPosterWidth,
                    Math.max(ToxiVisualSystem.dp(112), Math.round(cardHeight * posterAspect)));
            FrameLayout.LayoutParams posterParams = new FrameLayout.LayoutParams(
                    posterWidth, -1, Gravity.END | Gravity.CENTER_VERTICAL);
            posterParams.rightMargin = ToxiVisualSystem.dp(5);
            tile.addView(poster, posterParams);

            // Restrict the dark readability gradient to the text side; never
            // wash out or duplicate the poster artwork.
            View shade = new View(activity);
            android.graphics.drawable.GradientDrawable shadeGradient =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[]{Color.argb(250, 5, 7, 12), Color.argb(238, 5, 7, 12),
                                    Color.argb(196, 5, 7, 12), Color.argb(0, 5, 7, 12)});
            shadeGradient.setCornerRadii(ToxiVisualSystem.profileCornerRadii(22f));
            shade.setBackground(shadeGradient);
            FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.72f),
                    -1, Gravity.START);
            tile.addView(shade, shadeParams);

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.BOTTOM);
            copy.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(150)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)));
            TextView themeName = ToxiVisualSystem.text(activity, ToxiVisualSystem.THEME_NAMES[i],
                    17, Color.WHITE, true);
            themeName.setMaxLines(2);
            themeName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(themeName);
            TextView description = ToxiVisualSystem.text(activity,
                    ToxiVisualSystem.themeDescription(i), 11,
                    Color.argb(235, 245, 248, 250), false);
            description.setMaxLines(2);
            description.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
            copy.addView(description, descriptionParams);

            // Four live category thumbnails make every theme's section artwork
            // visible in Theme Studio, not only after that theme is activated.
            LinearLayout previews = new LinearLayout(activity);
            previews.setOrientation(LinearLayout.HORIZONTAL);
            previews.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams previewsParams = new LinearLayout.LayoutParams(-1, -2);
            previewsParams.topMargin = ToxiVisualSystem.dp(10);
            String[] previewLabels = {"Images", "Videos", "Audio", "Documents"};
            for (int category = 0; category < 4; category++) {
                FrameLayout previewFrame = new FrameLayout(activity);
                previewFrame.setBackground(ToxiVisualSystem.surface(7, false));
                previewFrame.setClipToOutline(true);
                ImageView preview = new ImageView(activity);
                setPreviewArtwork(activity, preview, themeCategoryArtwork(i, category));
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                preview.setAlpha(1f);
                preview.setContentDescription(previewLabels[category] + " artwork");
                previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
                LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                        ToxiVisualSystem.dp(44), ToxiVisualSystem.dp(44));
                if (category > 0) previewLp.leftMargin = ToxiVisualSystem.dp(6);
                previews.addView(previewFrame, previewLp);
            }
            copy.addView(previews, previewsParams);
            tile.addView(copy, new FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM));

            TextView state = ToxiVisualSystem.text(activity,
                    selected ? "✓  ACTIVE THEME" : "TAP TO APPLY",
                    9.5f, selected ? ToxiVisualSystem.accentForeground() : Color.WHITE, true);
            state.setLetterSpacing(0.08f);
            state.setGravity(Gravity.CENTER);
            state.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)));
            state.setBackground(selected ? ToxiVisualSystem.accentSurface(14)
                    : ToxiVisualSystem.surface(14, false));
            FrameLayout.LayoutParams stateParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.END);
            stateParams.setMargins(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)),
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), 0);
            tile.addView(state, stateParams);
            tile.setContentDescription(ToxiVisualSystem.THEME_NAMES[i] + ". "
                    + ToxiVisualSystem.themeDescription(i) + (selected ? ". Active theme" : ". Double tap to apply"));
            tile.setFocusable(false);
            tile.setOnClickListener(v -> {
                ToxiVisualSystem.feedback(v);
                ToxiVisualSystem.applyTheme(themeIndex, true);
                activity.recolor();
                activity.themeScroll.post(this::build);
            });
            ToxiVisualSystem.interactive(tile);
            LinearLayout.LayoutParams tileParams = activity.lpm(-1,
                    ToxiVisualSystem.dp(ToxiThemeEngine.spacing(216)), 0, 0, 0, 12);
            cards.addView(tile, tileParams);
            NebulaHomeView.animateHomeCard(activity, tile, i);
        }
        panel.addView(cards, new LinearLayout.LayoutParams(-1, -2));
        activity.themeScroll.setFillViewport(false);
        // Keep the final card scrollable above the reserved bottom dock inset.
        activity.themeScroll.setClipToPadding(false);
        activity.themeScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        activity.themeScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        panel.setAlpha(1f);
        activity.themeScroll.post(() -> {
            if (activity.themeScroll.getChildCount() > 0) {
                int range = Math.max(0, activity.themeScroll.getChildAt(0).getHeight()
                        - activity.themeScroll.getHeight() + activity.themeScroll.getPaddingBottom());
                activity.themeScroll.scrollTo(0, Math.min(restoreScrollY, range));
            }
        });
    }
}
