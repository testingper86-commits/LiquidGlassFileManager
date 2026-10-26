package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One glass list row (used by every file list and the Home "recent" list). */
final class Rows {
    static final class H {
        FrameLayout root;
        LinearLayout row;
        ImageView icon, more, check;
        TextView name, sub;
        Drawable bgNormal, bgSel, ckOn, ckOff;
        View labelDot;
    }

    static View build(Context c) {
        H h = new H();
        h.root = new FrameLayout(c);
        h.root.setPadding(Ui.dp(16), Ui.dp(5), Ui.dp(16), Ui.dp(5));
        h.row = new LinearLayout(c);
        h.row.setOrientation(LinearLayout.HORIZONTAL);
        h.row.setGravity(Gravity.CENTER_VERTICAL);
        h.row.setPadding(Ui.dp(12), Ui.dp(11), Ui.dp(6), Ui.dp(11));
        h.bgNormal = Ui.glass(18, false);
        h.bgSel = Ui.accentPill(18);
        h.row.setBackground(h.bgNormal);

        FrameLayout iconWrap = new FrameLayout(c);
        h.icon = new ImageView(c);
        h.icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Ui.round(h.icon, 13);
        iconWrap.addView(h.icon, new FrameLayout.LayoutParams(-1, -1));
        h.labelDot = new View(c);
        h.labelDot.setVisibility(View.GONE);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(Ui.dp(13), Ui.dp(13), Gravity.BOTTOM | Gravity.END);
        iconWrap.addView(h.labelDot, dl);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46));
        il.rightMargin = Ui.dp(14);
        h.row.addView(iconWrap, il);

        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        h.name = Ui.tv(c, "", 15.5f, Ui.WHITE, true);
        h.name.setSingleLine(true);
        h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        h.sub = Ui.tv(c, "", 12, Ui.SUB, false);
        h.sub.setSingleLine(true);
        h.sub.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(h.name, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = Ui.dp(4);
        tx.addView(h.sub, sl);
        h.row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));

        h.more = new ImageView(c);
        h.more.setImageDrawable(Glyphs.get(Glyphs.MORE, Color.argb(210, 255, 255, 255)));
        h.more.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        h.more.setContentDescription("More options");
        h.row.addView(h.more, new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42)));

        h.check = new ImageView(c);
        h.check.setPadding(Ui.dp(5), Ui.dp(5), Ui.dp(5), Ui.dp(5));
        GradientDrawable on = new GradientDrawable();
        on.setShape(GradientDrawable.OVAL);
        on.setColor(Ui.accent);
        h.ckOn = on;
        GradientDrawable off = new GradientDrawable();
        off.setShape(GradientDrawable.OVAL);
        off.setColor(Color.TRANSPARENT);
        off.setStroke(Ui.dp(2), Color.argb(150, 255, 255, 255));
        h.ckOff = off;
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(28));
        cl.rightMargin = Ui.dp(12);
        h.check.setVisibility(View.GONE);
        h.row.addView(h.check, cl);

        h.root.addView(h.row, new FrameLayout.LayoutParams(-1, -2));
        h.root.setTag(h);
        return h.root;
    }

    static void bind(H h, Entry e, boolean selecting, boolean selected, String extra) {
        h.name.setText(e.name);
        String meta = e.dir ? "Folder" : Ui.size(e.len);
        h.sub.setText(meta + "  •  " + Ui.date(e.mod) + (extra != null && !extra.isEmpty() ? "  •  " + extra : ""));
        Thumbs.bind(h.icon, e, Ui.dp(46));
        h.row.setBackground(selected ? h.bgSel : h.bgNormal);
        if (selecting) {
            h.more.setVisibility(View.GONE);
            h.check.setVisibility(View.VISIBLE);
            h.check.setBackground(selected ? h.ckOn : h.ckOff);
            h.check.setImageDrawable(selected ? Glyphs.get(Glyphs.CHECK, 0xFF0B1020) : null);
            h.check.setContentDescription(selected ? "Selected" : "Not selected");
        } else {
            h.more.setVisibility(View.VISIBLE);
            h.check.setVisibility(View.GONE);
        }
    }

    static void setLabel(H h, int colorIndex) {
        if (colorIndex < 0) { h.labelDot.setVisibility(View.GONE); return; }
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Labels.COLORS[colorIndex % Labels.COLORS.length]);
        d.setStroke(Ui.dp(1), Color.argb(230, 255, 255, 255));
        h.labelDot.setBackground(d);
        h.labelDot.setVisibility(View.VISIBLE);
    }
}
