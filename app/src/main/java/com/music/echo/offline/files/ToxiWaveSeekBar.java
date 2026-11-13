package com.music.echo.offline.files;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import androidx.appcompat.widget.AppCompatSeekBar;
/** Single-layer squiggly playback control. Native SeekBar input and accessibility are retained. */
public final class ToxiWaveSeekBar extends AppCompatSeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int played, unplayed;
    private boolean customColors;
    private float density, phase;
    private ValueAnimator waveAnimator;
    public ToxiWaveSeekBar(Context c) { super(c); init(c); }
    public ToxiWaveSeekBar(Context c, AttributeSet a) { super(c,a); init(c); }
    public ToxiWaveSeekBar(Context c, AttributeSet a, int d) { super(c,a,d); init(c); }
    private void init(Context c) {
        density=c.getResources().getDisplayMetrics().density;
        updateThemeColors();
        setSplitTrack(false);
        setPadding(Math.max(getPaddingLeft(), (int)(ToxiThemeEngine.spacing(8f)*density)),
                Math.max(getPaddingTop(), (int)(ToxiThemeEngine.spacing(12f)*density)),
                Math.max(getPaddingRight(), (int)(8*density)),
                Math.max(getPaddingBottom(), (int)(12*density)));
        setMinimumHeight((int)(ToxiThemeEngine.spacing(48f)*density));
        setProgressDrawable(null); setThumb(null);
        setBackgroundTintList(ColorStateList.valueOf(0x00000000));
        setContentDescription("Squiggly playback progress");
        setLayerType(LAYER_TYPE_SOFTWARE,null);
    }
    public void setSquiggleColors(int active,int inactive) {
        played=active;
        unplayed=inactive;
        customColors=true;
        invalidate();
    }
    public void useThemeColors() {
        customColors=false;
        updateThemeColors();
        invalidate();
    }
    private void updateThemeColors() {
        if (customColors) return;
        played=ToxiVisualSystem.ACCENT;
        unplayed=ToxiVisualSystem.EDGE_STROKE;
    }
    @Override protected synchronized void onDraw(Canvas canvas) {
        updateThemeColors();
        float left=getPaddingLeft()+ToxiThemeEngine.spacing(4f)*density,right=getWidth()-getPaddingRight()-ToxiThemeEngine.spacing(4f)*density;
        if(right<=left)return;
        float cy=getHeight()/2f;
        float fraction=getMax()>0?Math.max(0f,Math.min(1f,getProgress()/(float)getMax())):0f;
        float end=left+(right-left)*fraction;
        float amp=Math.min(ToxiThemeEngine.spacing(3.5f)*density,Math.max(ToxiThemeEngine.spacing(1.6f)*density,getHeight()*0.15f)),step=Math.max(1f,density);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeWidth(ToxiThemeEngine.profile().iconStroke*density);
        path.reset(); path.moveTo(left,cy);
        for(float x=left+step;x<right;x+=step)path.lineTo(x,cy+(float)Math.sin((x-left)*Math.PI/(8*density)+phase)*amp);
        path.lineTo(right,cy); paint.setColor(unplayed); canvas.drawPath(path,paint);
        if(end>left){
            path.reset(); path.moveTo(left,cy);
            for(float x=left+step;x<end;x+=step)path.lineTo(x,cy+(float)Math.sin((x-left)*Math.PI/(8*density)+phase)*amp);
            path.lineTo(end,cy); paint.setColor(played); canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(end,cy,ToxiThemeEngine.spacing(3.2f)*density,paint);
        }
    }
    public void setPlaying(boolean playing) {
        if (ToxiVisualSystem.booleanPreference("reduce_motion", false)) playing = false;
        if(playing){
            if(waveAnimator!=null&&waveAnimator.isRunning())return;
            waveAnimator=ValueAnimator.ofFloat(0f,(float)(Math.PI*2)); waveAnimator.setDuration(ToxiThemeEngine.motionDuration(1100L));
            waveAnimator.setRepeatCount(ValueAnimator.INFINITE); waveAnimator.setInterpolator(new LinearInterpolator());
            waveAnimator.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();invalidate();}); waveAnimator.start();
        }else{
            if(waveAnimator!=null){waveAnimator.cancel();waveAnimator=null;}
            phase=0f;invalidate();
        }
    }
    @Override protected void onDetachedFromWindow(){
        if(waveAnimator!=null){waveAnimator.cancel();waveAnimator=null;}
        super.onDetachedFromWindow();
    }
    @Override public boolean onTouchEvent(MotionEvent event){return super.onTouchEvent(event);}
}
