package com.music.echo.offline.files;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.SeekBar;

/** Precision playback rail. SeekBar touch, keyboard and accessibility semantics remain native. */
public final class ToxiProgressSeekBar extends SeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int activeColor = 0xFF8FE8C2;
    private int inactiveColor = 0x553C4A59;
    private float density;
    public ToxiProgressSeekBar(Context context) { super(context); initialize(context); }
    public ToxiProgressSeekBar(Context context, AttributeSet attrs) { super(context, attrs); initialize(context); }
    public ToxiProgressSeekBar(Context context, AttributeSet attrs, int style) { super(context, attrs, style); initialize(context); }
    private void initialize(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        activeColor = ToxiVisualSystem.ACCENT;
        inactiveColor = ToxiThemeSystem.EDGES[ToxiVisualSystem.THEME_INDEX];
        setSplitTrack(false);
        setPadding(Math.max(getPaddingLeft(), (int)(8*density)), Math.max(getPaddingTop(), (int)(9*density)), Math.max(getPaddingRight(), (int)(8*density)), Math.max(getPaddingBottom(), (int)(9*density)));
        setProgressDrawable(null);
        setThumb(null);
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
        setContentDescription("Playback position");
        setMinimumHeight((int)(32*density));
    }
    public void setProgressColors(int active, int inactive) { activeColor=active; inactiveColor=inactive; invalidate(); }
    @Override protected synchronized void onDraw(Canvas canvas) {
        float left=getPaddingLeft(), right=getWidth()-getPaddingRight();
        if(right<=left) return;
        float y=getHeight()/2f;
        float radius=Math.max(1.5f*density,2.2f*density);
        float fraction=getMax()>0?Math.max(0f,Math.min(1f,getProgress()/(float)getMax())):0f;
        float end=left+(right-left)*fraction;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(inactiveColor);
        canvas.drawRoundRect(new RectF(left,y-radius,right,y+radius),radius,radius,paint);
        if(end>left){
            paint.setColor(activeColor);
            canvas.drawRoundRect(new RectF(left,y-radius,end,y+radius),radius,radius,paint);
            canvas.drawCircle(end,y,4.1f*density,paint);
        }
    }
}
