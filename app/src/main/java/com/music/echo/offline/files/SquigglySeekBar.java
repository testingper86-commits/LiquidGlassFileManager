package com.music.echo.offline.files;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

/** Single-layer squiggly playback control. Native SeekBar input/accessibility is retained. */
public final class SquigglySeekBar extends SeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int played = 0xFFB9A4FF;
    private int unplayed = 0x557F7F8A;
    private float density;
    public SquigglySeekBar(Context c) { super(c); init(c); }
    public SquigglySeekBar(Context c, AttributeSet a) { super(c,a); init(c); }
    public SquigglySeekBar(Context c, AttributeSet a, int d) { super(c,a,d); init(c); }
    private void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        setSplitTrack(false);
        setPadding(getPaddingLeft(), Math.max(getPaddingTop(), (int)(8*density)), getPaddingRight(), Math.max(getPaddingBottom(), (int)(8*density)));
        setProgressDrawable(null);
        setThumb(null);
        setBackgroundTintList(ColorStateList.valueOf(0x00000000));
        setContentDescription("Squiggly playback progress");
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
    public void setSquiggleColors(int active, int inactive) { played=active; unplayed=inactive; invalidate(); }
    @Override protected synchronized void onDraw(Canvas canvas) {
        float left=getPaddingLeft()+4*density, right=getWidth()-getPaddingRight()-4*density;
        if (right<=left) return;
        float cy=getHeight()/2f;
        float fraction=getMax()>0 ? Math.max(0f,Math.min(1f,getProgress()/(float)getMax())) : 0f;
        float end=left+(right-left)*fraction;
        float amp=Math.min(3.5f*density,Math.max(1.6f*density,getHeight()*0.15f));
        float step=Math.max(1f,density);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeWidth(2.3f*density);
        path.reset(); path.moveTo(left,cy);
        for(float x=left+step;x<right;x+=step) path.lineTo(x,cy+(float)Math.sin((x-left)*Math.PI/(8*density))*amp);
        path.lineTo(right,cy); paint.setColor(unplayed); canvas.drawPath(path,paint);
        if(end>left) {
            path.reset(); path.moveTo(left,cy);
            for(float x=left+step;x<end;x+=step) path.lineTo(x,cy+(float)Math.sin((x-left)*Math.PI/(8*density))*amp);
            path.lineTo(end,cy); paint.setColor(played); canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(end,cy,3.2f*density,paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) { return super.onTouchEvent(event); }
}
