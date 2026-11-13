package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Builds the About/profile screen without coupling its content to MainActivity. */
final class AboutScreenBuilder {
    static int BACKGROUND = ToxiVisualSystem.CANVAS;
    static int ABOUT_SURFACE = ToxiVisualSystem.SURFACE_COLOR;
    static int ABOUT_SURFACE_ALT = ToxiVisualSystem.RAISED_SURFACE;
    static int ABOUT_TEXT = ToxiVisualSystem.TEXT_PRIMARY;
    static int ABOUT_MUTED = ToxiVisualSystem.TEXT_SECONDARY;
    static int ABOUT_CYAN = ToxiVisualSystem.ACCENT;

    private static void syncPalette() {
        BACKGROUND = ToxiVisualSystem.CANVAS;
        ABOUT_SURFACE = ToxiVisualSystem.SURFACE_COLOR;
        ABOUT_SURFACE_ALT = ToxiVisualSystem.RAISED_SURFACE;
        ABOUT_TEXT = ToxiVisualSystem.TEXT_PRIMARY;
        ABOUT_MUTED = ToxiVisualSystem.TEXT_SECONDARY;
        ABOUT_CYAN = ToxiVisualSystem.ACCENT;
    }

    private final Activity activity;
    private final LinearLayout about;
    private final Runnable onBack;
    private final String fallbackVersionName;

    private AboutScreenBuilder(Activity activity, LinearLayout about, Runnable onBack, String fallbackVersionName) {
        if (activity == null || about == null || onBack == null) throw new IllegalArgumentException("About screen dependencies are required");
        this.activity = activity;
        this.about = about;
        this.onBack = onBack;
        this.fallbackVersionName = fallbackVersionName == null ? "" : fallbackVersionName;
    }

    static void populate(Activity activity, LinearLayout about, Runnable onBack, String fallbackVersionName) {
        syncPalette();
        new AboutScreenBuilder(activity, about, onBack, fallbackVersionName).build();
    }

    private int dp(float value) {
        return ToxiVisualSystem.dp(ToxiThemeEngine.spacing(value));
    }

    private LinearLayout.LayoutParams lpm(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(ToxiVisualSystem.dp(left), ToxiVisualSystem.dp(top), ToxiVisualSystem.dp(right), ToxiVisualSystem.dp(bottom));
        return params;
    }

    private void build() {
        about.removeAllViews();
        about.setPadding(0, 0, 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), 0);
        ImageView back = ToxiVisualSystem.iconButton(activity, ToxiIcons.BACK, ABOUT_TEXT);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> onBack.run());
        header.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(48))));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12));
        titleParams.rightMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));
        TextView title = aboutText("Creator / ToxiFiles", 21, ABOUT_TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, titleParams);
        about.addView(header, lpm(-1, -2, 8, 4, 8, 4));
        about.addView(aboutDivider(), lpm(-1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1)), 16, 0, 16, 8));

        about.addView(aboutSectionHeading("01  /  CREATOR SIGNAL"), lpm(-1, -2, 18, 12, 18, 10));
        LinearLayout profile = new LinearLayout(activity);
        profile.setOrientation(LinearLayout.VERTICAL);
        profile.setGravity(Gravity.CENTER_HORIZONTAL);
        profile.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(22)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(24)));
        profile.setBackground(ToxiVisualSystem.surface(28, true));
        profile.setElevation(0f);
        FrameLayout avatarFrame = new FrameLayout(activity);
        GradientDrawable avatarHalo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{ToxiVisualSystem.ACCENT, ToxiThemeSystem.ACCENT_ENDS[ToxiVisualSystem.THEME_INDEX],
                        Color.argb(70, Color.red(ToxiVisualSystem.ACCENT), Color.green(ToxiVisualSystem.ACCENT), Color.blue(ToxiVisualSystem.ACCENT))});
        avatarHalo.setShape(GradientDrawable.OVAL);
        avatarHalo.setStroke(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), Color.argb(225, 255, 255, 255));
        avatarFrame.setBackground(avatarHalo);
        avatarFrame.setElevation(0f);
        avatarFrame.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)));
        ImageView avatar = new ImageView(activity);
        // Load the supplied creator portrait directly from the packaged drawable.
        // Avoid eager BitmapFactory decoding, which can leave only a blank background.
        avatar.setImageResource(R.drawable.dominators_profile);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackgroundColor(Color.TRANSPARENT);
        avatar.setContentDescription("Creator portrait");
        avatar.setClipToOutline(true);
        avatar.setAdjustViewBounds(false);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(
                ToxiVisualSystem.dp(ToxiThemeEngine.spacing(132)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(132)), Gravity.CENTER);
        avatarFrame.addView(avatar, avatarParams);
        profile.addView(avatarFrame, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(144)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(144))));
        TextView name = aboutText("CHANDU", 30, ABOUT_TEXT, true);
        name.setLetterSpacing(0.10f);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(15)), 0, 0);
        profile.addView(name);
        TextView role = aboutText("Corporate Professional · Coder · Gamer · Tech Enthusiast", 12.5f, ABOUT_MUTED, false);
        role.setGravity(Gravity.CENTER);
        role.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), 0, 0);
        profile.addView(role, new LinearLayout.LayoutParams(-1, -2));
        TextView tagline = aboutText("“Building ideas into experiences that matter.”", 14, ABOUT_TEXT, false);
        tagline.setGravity(Gravity.CENTER);
        tagline.setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), Typeface.ITALIC));
        tagline.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(15)), 0, 0);
        profile.addView(tagline, new LinearLayout.LayoutParams(-1, -2));
        about.addView(profile, lpm(-1, -2, 16, 0, 16, 8));

        about.addView(aboutSectionHeading("02  /  THE PERSON BEHIND THE BUILD"), lpm(-1, -2, 18, 14, 18, 8));
        LinearLayout origin = new LinearLayout(activity);
        origin.setOrientation(LinearLayout.VERTICAL);
        origin.setPadding(dp(15), dp(15), dp(15), dp(14));
        origin.setBackground(aboutSurface(21, true));
        TextView originTitle = aboutText("Corporate by schedule. Creator by instinct.", 16, ABOUT_TEXT, true);
        originTitle.setLineSpacing(dp(2), 1.02f);
        origin.addView(originTitle);
        TextView originCopy = aboutText("A practical problem-solver with a creative mindset — turning curiosity, code and technology into useful experiences.", 12.5f, ABOUT_MUTED, false);
        originCopy.setLineSpacing(dp(3), 1.04f);
        originCopy.setPadding(0, dp(7), 0, dp(13));
        origin.addView(originCopy);
        LinearLayout facts = new LinearLayout(activity);
        facts.setOrientation(LinearLayout.HORIZONTAL);
        String[] factNames = {"WORK", "CREATE", "EXPLORE"};
        String[] factValues = {"Professional", "Developer", "Technology"};
        for (int i = 0; i < factNames.length; i++) {
            LinearLayout fact = aboutFactTile(factNames[i], factValues[i]);
            LinearLayout.LayoutParams factParams = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) factParams.leftMargin = dp(6);
            facts.addView(fact, factParams);
        }
        origin.addView(facts);
        about.addView(origin, lpm(-1, -2, 16, 0, 16, 8));
        about.addView(aboutQuoteCard("“Corporate by schedule, coder by mindset.”"), lpm(-1, -2, 16, 0, 16, 8));

        about.addView(aboutSectionHeading("03  /  TIMELINE"), lpm(-1, -2, 18, 14, 18, 8));
        about.addView(aboutJourneyItem("2021", "The Beginning", false), lpm(-1, -2, 16, 0, 16, 5));
        about.addView(aboutJourneyItem("2022", "First Creation", false), lpm(-1, -2, 16, 0, 16, 5));
        about.addView(aboutJourneyItem("2024", "Growing Skills", false), lpm(-1, -2, 16, 0, 16, 5));
        about.addView(aboutJourneyItem("2026", "Building Bigger", true), lpm(-1, -2, 16, 0, 16, 8));

        addAboutGridSection("Skills & Expertise", new String[]{
                "Java", "Python", "SQL", "HTML & CSS",
                "JavaScript", "C / C++", "Android", "Software Architecture"
        });

        about.addView(aboutSectionHeading("04  /  BUILT & IMAGINED"), lpm(-1, -2, 18, 14, 18, 8));
        about.addView(aboutProjectRow("ToxiFiles", "Modern Android file management experience.", 0), lpm(-1, -2, 16, 0, 16, 8));
        about.addView(aboutProjectRow("NovaBoard", "Productivity and organization concept.", 1), lpm(-1, -2, 16, 0, 16, 8));
        about.addView(aboutProjectRow("CodeNest", "Mobile coding workspace concept.", 2), lpm(-1, -2, 16, 0, 16, 8));

        addAboutGridSection("Interests & Personality", new String[]{
                "Gamer", "Anime Watcher", "Coder", "Trader",
                "Corporate Guy", "Lazy Mind", "Overthinker", "God Believer"
        });
        about.addView(aboutQuoteCard("“A corporate soul, a gamer's heart, a coder's brain, and a mind that never switches off.”"), lpm(-1, -2, 16, 0, 16, 8));

        about.addView(aboutSectionHeading("05  /  CREATOR PRINCIPLES"), lpm(-1, -2, 18, 14, 18, 8));
        about.addView(aboutParagraphCard("Curiosity, continuous learning, practical problem-solving and building useful experiences."), lpm(-1, -2, 16, 0, 16, 8));

        about.addView(aboutSectionHeading("06  /  CONNECTIONS"), lpm(-1, -2, 18, 14, 18, 8));
        addAboutContactRow(
                aboutContactCard("GH", "GitHub", "testingper86-commits", "https://github.com/testingper86-commits"),
                aboutContactCard("IG", "Instagram", "@_chandu_rp_1", "https://www.instagram.com/_chandu_rp_1/"));
        addAboutContactRow(
                aboutContactCard("FB", "Facebook", "facebook.com/chand.p.319", "https://www.facebook.com/chand.p.319/"),
                aboutContactCard("TG", "Telegram", "t.me/Toxiiii2k23", "https://t.me/Toxiiii2k23"));
        LinearLayout emailRow = new LinearLayout(activity);
        emailRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emailCardParams = new LinearLayout.LayoutParams(0, -2, 1f);
        emailCardParams.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0);
        emailRow.addView(new View(activity), new LinearLayout.LayoutParams(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(82)), 0.5f));
        emailRow.addView(aboutContactCard("@", "Email", "testingper86@gmail.com", "mailto:testingper86@gmail.com"), emailCardParams);
        emailRow.addView(new View(activity), new LinearLayout.LayoutParams(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(82)), 0.5f));
        about.addView(emailRow, lpm(-1, -2, 12, 0, 12, 14));

        about.addView(aboutDivider(), lpm(-1, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1)), 24, 10, 24, 14));
        TextView thanks = aboutText("THANK YOU FOR VISITING", 11, ABOUT_CYAN, true);
        thanks.setLetterSpacing(0.17f);
        thanks.setGravity(Gravity.CENTER);
        about.addView(thanks, lpm(-1, -2, 16, 0, 16, 8));
        TextView signoff = aboutText("“Stay curious. Keep creating. Never stop improving.”", 13, ABOUT_TEXT, false);
        signoff.setGravity(Gravity.CENTER);
        signoff.setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), Typeface.ITALIC));
        about.addView(signoff, lpm(-1, -2, 16, 0, 16, 8));
        TextView copyright = aboutText("© 2026 CHANDU · PERSONAL PROFILE", 10.5f, ABOUT_MUTED, true);
        copyright.setLetterSpacing(0.08f);
        copyright.setGravity(Gravity.CENTER);
        about.addView(copyright, lpm(-1, -2, 16, 0, 16, 12));

    }

    TextView aboutText(String value, float size, int color, boolean bold) {
        TextView text = ToxiVisualSystem.text(activity, value, size, color, bold);
        text.setIncludeFontPadding(false);
        text.setTextColor(color);
        return text;
    }

    android.graphics.drawable.Drawable aboutSurface(int radius, boolean featured) {
        int top = featured ? ABOUT_SURFACE_ALT : ABOUT_SURFACE;
        int bottom = ABOUT_SURFACE;
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        surface.setCornerRadius(ToxiVisualSystem.dp(radius));
        surface.setStroke(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1)), featured
                ? Color.argb(125, Color.red(ABOUT_CYAN), Color.green(ABOUT_CYAN), Color.blue(ABOUT_CYAN))
                : ToxiVisualSystem.EDGE_STROKE);
        return surface;
    }

    android.graphics.drawable.Drawable aboutAvatarSurface() {
        android.graphics.drawable.GradientDrawable avatar = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{ToxiVisualSystem.ACCENT, ToxiThemeSystem.ACCENT_ENDS[ToxiVisualSystem.THEME_INDEX]});
        avatar.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        avatar.setStroke(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), Color.argb(180, Color.red(ABOUT_CYAN), Color.green(ABOUT_CYAN), Color.blue(ABOUT_CYAN)));
        return avatar;
    }

    View aboutDivider() {
        View divider = new View(activity);
        divider.setBackgroundColor(ToxiVisualSystem.EDGE_STROKE);
        return divider;
    }

    LinearLayout aboutSectionHeading(String value) {
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        View marker = new View(activity);
        android.graphics.drawable.GradientDrawable markerShape = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ABOUT_CYAN, ToxiThemeSystem.ACCENT_ENDS[ToxiVisualSystem.THEME_INDEX]});
        markerShape.setCornerRadius(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)));
        marker.setBackground(markerShape);
        heading.addView(marker, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(19))));
        TextView text = aboutText(value, 16, ABOUT_TEXT, true);
        text.setLetterSpacing(0.015f);
        text.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0, 0, 0);
        heading.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return heading;
    }


    private LinearLayout aboutFactTile(String label, String value) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(9), dp(10), dp(7), dp(10));
        tile.setBackground(aboutSurface(13, false));
        TextView eyebrow = aboutText(label, 8.5f, ABOUT_CYAN, true);
        eyebrow.setLetterSpacing(0.08f);
        tile.addView(eyebrow);
        TextView detail = aboutText(value, 10.5f, ABOUT_TEXT, true);
        detail.setPadding(0, dp(4), 0, 0);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(detail);
        return tile;
    }

    LinearLayout aboutParagraphCard(String value) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(15)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(15)));
        card.setBackground(aboutSurface(19, false));
        TextView text = aboutText(value, 13, ABOUT_MUTED, false);
        text.setLineSpacing(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 1.04f);
        card.addView(text);
        return card;
    }

    LinearLayout aboutQuoteCard(String value) {
        LinearLayout card = new LinearLayout(activity);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)));
        card.setBackground(aboutSurface(17, false));
        View accent = new View(activity);
        accent.setBackgroundColor(ABOUT_CYAN);
        card.addView(accent, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), -1));
        TextView quote = aboutText(value, 12.5f, ABOUT_TEXT, false);
        quote.setTypeface(Typeface.create(ToxiVisualSystem.fontFamily(), Typeface.ITALIC));
        quote.setLineSpacing(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), 1.03f);
        quote.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)), 0, 0, 0);
        card.addView(quote, new LinearLayout.LayoutParams(0, -2, 1f));
        return card;
    }

    LinearLayout aboutJourneyItem(String year, String milestone, boolean last) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.TOP);
        LinearLayout markerColumn = new LinearLayout(activity);
        markerColumn.setOrientation(LinearLayout.VERTICAL);
        markerColumn.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        markerColumn.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), 0, 0);
        TextView yearText = aboutText(year, 12, ABOUT_CYAN, true);
        yearText.setGravity(Gravity.CENTER);
        markerColumn.addView(yearText, new LinearLayout.LayoutParams(-1, -2));
        View dot = new View(activity);
        android.graphics.drawable.GradientDrawable dotShape = new android.graphics.drawable.GradientDrawable();
        dotShape.setColor(ABOUT_CYAN);
        dotShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setBackground(dotShape);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)));
        dotParams.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));
        markerColumn.addView(dot, dotParams);
        if (!last) {
            View line = new View(activity);
            line.setBackgroundColor(Color.argb(110, Color.red(ABOUT_CYAN), Color.green(ABOUT_CYAN), Color.blue(ABOUT_CYAN)));
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(26)));
            lineParams.topMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4));
            markerColumn.addView(line, lineParams);
        }
        row.addView(markerColumn, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(62)), -2));

        LinearLayout milestoneCard = new LinearLayout(activity);
        milestoneCard.setGravity(Gravity.CENTER_VERTICAL);
        milestoneCard.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(15)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(15)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)));
        milestoneCard.setBackground(aboutSurface(17, false));
        TextView milestoneText = aboutText(milestone, 13.5f, ABOUT_TEXT, true);
        milestoneCard.addView(milestoneText, new LinearLayout.LayoutParams(-1, -2));
        row.addView(milestoneCard, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    void addAboutGridSection(String title, String[] values) {
        about.addView(aboutSectionHeading(title), lpm(-1, -2, 18, 14, 18, 8));
        for (int i = 0; i < values.length; i += 2) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1f);
            first.rightMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5));
            row.addView(aboutGridTile(values[i]), first);
            if (i + 1 < values.length) {
                LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1f);
                second.leftMargin = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5));
                row.addView(aboutGridTile(values[i + 1]), second);
            } else {
                row.addView(new View(activity), new LinearLayout.LayoutParams(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(46)), 1f));
            }
            about.addView(row, lpm(-1, -2, 16, 0, 16, 7));
        }
    }

    LinearLayout aboutGridTile(String value) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
        tile.setMinimumHeight(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(50)));
        tile.setBackground(aboutSurface(14, false));
        View dot = new View(activity);
        android.graphics.drawable.GradientDrawable dotShape = new android.graphics.drawable.GradientDrawable();
        dotShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dotShape.setColor(ABOUT_CYAN);
        dot.setBackground(dotShape);
        tile.addView(dot, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6))));
        TextView label = aboutText(value, 12.2f, ABOUT_TEXT, false);
        label.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), 0, 0, 0);
        tile.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        return tile;
    }

    LinearLayout aboutProjectRow(String name, String description, int kind) {
        LinearLayout card = new LinearLayout(activity);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(12)));
        card.setBackground(aboutSurface(20, false));
        ImageView artwork = new ImageView(activity);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setImageDrawable(aboutProjectArtwork(kind));
        artwork.setBackground(aboutSurface(15, true));
        artwork.setClipToOutline(true);
        card.addView(artwork, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(78)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(78))));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), 0, 0, 0);
        TextView projectName = aboutText(name, 15, ABOUT_TEXT, true);
        copy.addView(projectName);
        TextView projectDescription = aboutText(description, 12.2f, ABOUT_MUTED, false);
        projectDescription.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(6)), 0, 0);
        projectDescription.setLineSpacing(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(2)), 1.02f);
        copy.addView(projectDescription);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return card;
    }

    android.graphics.drawable.Drawable aboutProjectArtwork(int kind) {
        int size = ToxiVisualSystem.dp(ToxiThemeEngine.spacing(96));
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int theme = ToxiVisualSystem.THEME_INDEX;
        int accent = ToxiVisualSystem.ACCENT;
        int accentEnd = ToxiThemeSystem.ACCENT_ENDS[theme];
        int chrome = ToxiVisualSystem.CHROME;
        int[][] palettes = {
                {ToxiThemeSystem.SOFT_ACCENTS[theme], ToxiVisualSystem.CANVAS},
                {ToxiVisualSystem.RAISED_SURFACE, ToxiThemeSystem.SOFT_ACCENTS[theme]},
                {ToxiVisualSystem.SURFACE_COLOR, ToxiVisualSystem.CANVAS}
        };
        paint.setShader(new android.graphics.LinearGradient(0, 0, size, size, palettes[kind][0], palettes[kind][1], Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, size, size), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(15)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(15)), paint);
        paint.setShader(null);
        paint.setColor(Color.argb(25, 255, 255, 255));
        paint.setStrokeWidth(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1)));
        for (int i = 1; i < 4; i++) {
            float offset = size * i / 4f;
            canvas.drawLine(offset, 0, offset, size, paint);
            canvas.drawLine(0, offset, size, offset, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (kind == 0) {
            paint.setColor(accent);
            android.graphics.Path folder = new android.graphics.Path();
            folder.moveTo(size * .17f, size * .36f);
            folder.lineTo(size * .41f, size * .36f);
            folder.lineTo(size * .50f, size * .45f);
            folder.lineTo(size * .83f, size * .45f);
            folder.lineTo(size * .78f, size * .72f);
            folder.lineTo(size * .20f, size * .72f);
            folder.close();
            canvas.drawPath(folder, paint);
            paint.setColor(ToxiVisualSystem.CANVAS);
            canvas.drawRoundRect(new RectF(size * .27f, size * .51f, size * .43f, size * .64f), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), paint);
            canvas.drawRoundRect(new RectF(size * .48f, size * .51f, size * .64f, size * .64f), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), paint);
        } else if (kind == 1) {
            paint.setColor(Color.argb(225, 244, 249, 255));
            canvas.drawRoundRect(new RectF(size * .16f, size * .19f, size * .84f, size * .80f), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), paint);
            int[] colors = {accent, accentEnd, chrome};
            for (int i = 0; i < 3; i++) {
                paint.setColor(colors[i]);
                float left = size * (.23f + i * .20f);
                canvas.drawRoundRect(new RectF(left, size * (.32f + (i % 2) * .08f), left + size * .15f, size * .65f), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), paint);
            }
        } else {
            paint.setColor(Color.rgb(0, 229, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            android.graphics.Path left = new android.graphics.Path();
            left.moveTo(size * .39f, size * .29f);
            left.lineTo(size * .23f, size * .50f);
            left.lineTo(size * .39f, size * .71f);
            canvas.drawPath(left, paint);
            android.graphics.Path right = new android.graphics.Path();
            right.moveTo(size * .61f, size * .29f);
            right.lineTo(size * .77f, size * .50f);
            right.lineTo(size * .61f, size * .71f);
            canvas.drawPath(right, paint);
            paint.setColor(accentEnd);
            canvas.drawLine(size * .55f, size * .25f, size * .45f, size * .75f, paint);
        }
        android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(activity.getResources(), bitmap);
        drawable.setFilterBitmap(true);
        return drawable;
    }

    void addAboutContactRow(View first, View second) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, -2, 1f);
        firstParams.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), 0);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, -2, 1f);
        secondParams.setMargins(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0);
        row.addView(first, firstParams);
        row.addView(second, secondParams);
        about.addView(row, lpm(-1, -2, 12, 0, 12, 8));
    }

    LinearLayout aboutContactCard(String monogram, String label, String detail, String url) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(78)));
        card.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(9)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)));
        card.setBackground(aboutSurface(17, false));

        TextView mark = aboutText(monogram, 13, ABOUT_CYAN, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(aboutAvatarSurface());
        card.addView(mark, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(38)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(38))));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0, 0, 0);
        TextView title = aboutText(label, 12.5f, ABOUT_TEXT, true);
        copy.addView(title);
        TextView value = aboutText(detail, 10, ABOUT_MUTED, false);
        value.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(4)), 0, 0);
        value.setMaxLines(2);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(value, new LinearLayout.LayoutParams(-1, -2));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        card.setContentDescription(label + ": " + detail);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            ToxiVisualSystem.feedback(v);
            openExternalUrl(url);
        });
        ToxiVisualSystem.interactive(card);
        return card;
    }

    void openExternalUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(i);
        } catch (Exception e) {
            ToxiVisualSystem.toast(activity, "Could not open link");
        }
    }

    View aboutDetailRow(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)));
        TextView left = ToxiVisualSystem.text(activity, label, 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 0.9f));
        TextView right = ToxiVisualSystem.text(activity, value, 12, ToxiVisualSystem.TEXT_PRIMARY, true);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1.1f));
        return row;
    }

    String versionAndBuild() {
        try {
            android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return "Version " + info.versionName + "  \u00B7  Build " + code;
        } catch (Exception e) { return fallbackVersionName; }
    }

    LinearLayout aboutCard(int icon, String title, String detail, String url) {
        LinearLayout card = new LinearLayout(activity);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(ToxiVisualSystem.surface(18, false));
        card.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)));
        ImageView iv = new ImageView(activity);
        iv.setImageDrawable(ToxiIcons.symbol(icon, ToxiVisualSystem.ACCENT));
        iv.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(7)));
        iv.setBackground(ToxiVisualSystem.surface(10, true));
        iv.setContentDescription(title);
        card.addView(iv, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(36)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(36))));
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)), 0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8)), 0);
        texts.addView(ToxiVisualSystem.text(activity, title, 14, ToxiVisualSystem.TEXT_PRIMARY, true));
        TextView d = ToxiVisualSystem.text(activity, detail, 12, ToxiVisualSystem.TEXT_SECONDARY, false);
        d.setPadding(0, ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)), 0, 0);
        texts.addView(d);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (url != null) {
            ImageView ext = ToxiVisualSystem.iconButton(activity, ToxiIcons.OPEN, ToxiVisualSystem.TEXT_SECONDARY);
            ext.setContentDescription("Open " + title);
            card.addView(ext, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(32)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(38))));
            View.OnClickListener open = v -> {
                try { activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
                catch (Exception e) { ToxiVisualSystem.toast(activity, "Could not open project link"); }
            };
            card.setOnClickListener(open);
            ext.setOnClickListener(open);
        }
        return card;
    }

    LinearLayout aboutTextCard(String text) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ToxiVisualSystem.surface(18, false));
        card.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(16)), ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14)));
        card.addView(ToxiVisualSystem.text(activity, text, 13, ToxiVisualSystem.TEXT_SECONDARY, false));
        return card;
    }


}
