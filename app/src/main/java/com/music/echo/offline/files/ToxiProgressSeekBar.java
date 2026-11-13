package com.music.echo.offline.files;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatSeekBar;
/** Precision playback rail. Native SeekBar input, keyboard and accessibility semantics remain intact. */
public final class ToxiProgressSeekBar extends AppCompatSeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int activeColor;
    private int inactiveColor;
    private boolean customColors;
    private float density;
    public ToxiProgressSeekBar(Context context) { super(context); initialize(context); }
    public ToxiProgressSeekBar(Context context, AttributeSet attrs) { super(context, attrs); initialize(context); }
    public ToxiProgressSeekBar(Context context, AttributeSet attrs, int style) { super(context, attrs, style); initialize(context); }
    private void initialize(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        if (!customColors) {
            activeColor = ToxiVisualSystem.ACCENT;
            inactiveColor = ToxiVisualSystem.EDGE_STROKE;
        }
        setSplitTrack(false);
        setPadding(Math.max(getPaddingLeft(), (int)(ToxiThemeEngine.spacing(8f)*density)), Math.max(getPaddingTop(), (int)(ToxiThemeEngine.spacing(9f)*density)), Math.max(getPaddingRight(), (int)(ToxiThemeEngine.spacing(8f)*density)), Math.max(getPaddingBottom(), (int)(ToxiThemeEngine.spacing(9f)*density)));
        setProgressDrawable(null);
        setThumb(null);
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
        setContentDescription("Playback position");
        setMinimumHeight((int)(ToxiThemeEngine.spacing(48f)*density));
    }
    public void setProgressColors(int active, int inactive) {
        activeColor = active;
        inactiveColor = inactive;
        customColors = true;
        invalidate();
    }
    public void useThemeColors() {
        customColors = false;
        invalidate();
    }
    @Override protected synchronized void onDraw(Canvas canvas) {
        if (!customColors) {
            activeColor = ToxiVisualSystem.ACCENT;
            inactiveColor = ToxiVisualSystem.EDGE_STROKE;
        }
        float left=getPaddingLeft(), right=getWidth()-getPaddingRight();
        if(right<=left) return;
        float y=getHeight()/2f, radius=Math.max(1.5f*density, ToxiThemeEngine.spacing(2.2f)*density);
        float fraction=getMax()>0?Math.max(0f,Math.min(1f,getProgress()/(float)getMax())):0f;
        float end=left+(right-left)*fraction;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(inactiveColor);
        canvas.drawRoundRect(new RectF(left,y-radius,right,y+radius),radius,radius,paint);
        if(end>left){ paint.setColor(activeColor); canvas.drawRoundRect(new RectF(left,y-radius,end,y+radius),radius,radius,paint); canvas.drawCircle(end,y,ToxiThemeEngine.spacing(4.1f)*density,paint); }
    }
}
