package com.music.echo.offline.files;

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

/** Purpose-built file row for the new ToxiFiles navigation and content language. */
final class ToxiFileRow {
    static final class Holder {
        FrameLayout root;
        LinearLayout row;
        ImageView icon, more, check;
        TextView name, sub;
        Drawable bgNormal, bgSel, ckOn, ckOff;
        View labelDot;
    }
    private ToxiFileRow() { }
    static View create(Context context) {
        Holder h=new Holder();
        h.root=new FrameLayout(context);
        h.root.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)));
        h.row=new LinearLayout(context); h.row.setGravity(Gravity.CENTER_VERTICAL);
        h.row.setOrientation(LinearLayout.HORIZONTAL);
        h.row.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(13)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(11)));
        h.bgNormal=ToxiVisualSystem.surface(20,false); h.bgSel=ToxiVisualSystem.accentSurface(20); h.row.setBackground(h.bgNormal);
        h.row.setElevation(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1.5f)));
        FrameLayout iconFrame=new FrameLayout(context);
        h.icon=new ImageView(context); h.icon.setScaleType(ImageView.ScaleType.CENTER_CROP); ToxiVisualSystem.clipRound(h.icon,12);
        iconFrame.setBackground(ToxiVisualSystem.surface(15,true)); iconFrame.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(3)));
        iconFrame.addView(h.icon,new FrameLayout.LayoutParams(-1,-1));
        h.labelDot=new View(context); h.labelDot.setVisibility(View.GONE);
        FrameLayout.LayoutParams dotLp=new FrameLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),Gravity.BOTTOM|Gravity.END);
        iconFrame.addView(h.labelDot,dotLp);
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(50)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(50))); iconLp.rightMargin=ToxiVisualSystem.dp(ToxiThemeEngine.spacing(14));
        h.row.addView(iconFrame,iconLp);
        LinearLayout labels=new LinearLayout(context); labels.setOrientation(LinearLayout.VERTICAL);
        h.name=ToxiVisualSystem.text(context,"",15,ToxiVisualSystem.TEXT_PRIMARY,true); h.name.setSingleLine(true); h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        h.sub=ToxiVisualSystem.text(context,"",11.5f,ToxiVisualSystem.TEXT_SECONDARY,false); h.sub.setSingleLine(true); h.sub.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(h.name,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2);subLp.topMargin=ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5));labels.addView(h.sub,subLp);
        h.row.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        h.more=new ImageView(context);h.more.setImageDrawable(ToxiIcons.symbol(ToxiIcons.MORE,ToxiVisualSystem.TEXT_SECONDARY));
        h.more.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(10)));
        h.more.setContentDescription("More options");ToxiVisualSystem.interactive(h.more);h.row.addView(h.more,new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(40))));
        h.check=new ImageView(context);h.check.setPadding(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(5)));
        GradientDrawable on=new GradientDrawable();on.setShape(GradientDrawable.OVAL);on.setColor(ToxiVisualSystem.ACCENT);h.ckOn=on;
        GradientDrawable off=new GradientDrawable();off.setShape(GradientDrawable.OVAL);off.setColor(Color.TRANSPARENT);off.setStroke(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1.5f)),ToxiVisualSystem.TEXT_SECONDARY);h.ckOff=off;
        LinearLayout.LayoutParams checkLp=new LinearLayout.LayoutParams(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)),ToxiVisualSystem.dp(ToxiThemeEngine.spacing(28)));checkLp.rightMargin=ToxiVisualSystem.dp(ToxiThemeEngine.spacing(8));h.check.setVisibility(View.GONE);h.row.addView(h.check,checkLp);
        h.root.addView(h.row,new FrameLayout.LayoutParams(-1,-2));h.root.setTag(h);return h.root;
    }
    static Holder holder(View view) {
        if (!(view instanceof FrameLayout)) return null;
        Object tag = view.getTag();
        if (!(tag instanceof Holder)) return null;
        Holder holder = (Holder) tag;
        if (holder.root != view || holder.row == null || holder.icon == null
                || holder.more == null || holder.check == null || holder.name == null
                || holder.sub == null || holder.labelDot == null) return null;
        return holder;
    }

    static void bind(Holder h,Entry entry,boolean selecting,boolean selected,String extra){
        h.name.setText(entry.name);
        String meta=entry.dir?"Folder":ToxiVisualSystem.formatSize(entry.len);
        h.sub.setText(meta+"  ·  "+ToxiVisualSystem.formatDate(entry.mod)+(extra!=null&&!extra.isEmpty()?"  ·  "+extra:""));
        Thumbs.bind(h.icon,entry,ToxiVisualSystem.dp(48));
        h.bgNormal = ToxiVisualSystem.surface(18, false);
        h.bgSel = ToxiVisualSystem.accentSurface(18);
        GradientDrawable checked = new GradientDrawable();
        checked.setShape(GradientDrawable.OVAL);
        checked.setColor(ToxiVisualSystem.ACCENT);
        h.ckOn = checked;
        GradientDrawable unchecked = new GradientDrawable();
        unchecked.setShape(GradientDrawable.OVAL);
        unchecked.setColor(Color.TRANSPARENT);
        unchecked.setStroke(ToxiVisualSystem.dp(ToxiThemeEngine.spacing(1.5f)), ToxiVisualSystem.TEXT_SECONDARY);
        h.ckOff = unchecked;
        h.more.setImageDrawable(ToxiIcons.symbol(ToxiIcons.MORE, ToxiVisualSystem.TEXT_SECONDARY));
        h.row.setBackground(selected?h.bgSel:h.bgNormal);
        if(selecting){h.more.setVisibility(View.GONE);h.check.setVisibility(View.VISIBLE);h.check.setBackground(selected?h.ckOn:h.ckOff);h.check.setImageDrawable(selected?ToxiIcons.symbol(ToxiIcons.CHECK, ToxiVisualSystem.LIGHT_MODE ? 0xFFFFFFFF : 0xFF101820):null);h.check.setContentDescription(selected?"Selected":"Not selected");}
        else{h.more.setVisibility(View.VISIBLE);h.check.setVisibility(View.GONE);}
    }
    static void applyLabel(Holder h,int colorIndex){
        if(colorIndex<0){h.labelDot.setVisibility(View.GONE);return;}
        GradientDrawable dot=new GradientDrawable();dot.setShape(GradientDrawable.OVAL);dot.setColor(Labels.COLORS[colorIndex%Labels.COLORS.length]);dot.setStroke(ToxiVisualSystem.dp(1),ToxiVisualSystem.CANVAS);h.labelDot.setBackground(dot);h.labelDot.setVisibility(View.VISIBLE);
    }
}
