package com.music.echo.offline.files;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** In-window Dynamic Island. It is attached only to ToxiFiles Activity roots. */
final class DynamicIslandView extends FrameLayout {
    private static final String PREFS = "toxifiles_dynamic_island";
    private static final int[] RGB = {0xFF00E5FF,0xFF1677FF,0xFF843BFF,0xFFD12BFF,0xFFFF2878,0xFFFF4D36};
    private final Activity host;
    private final SharedPreferences prefs;
    private final TextView label, detail, action;
    private Runnable actionCallback;
    private final LinearLayout shell, expanded;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float downX, downY, startX, startY;
    private boolean dragging, longPressed;
    private String activity = "ToxiFiles ready";
    private int progress = -1;
    private Runnable longPress;

    DynamicIslandView(Activity activityHost) {
        super(activityHost);
        host = activityHost;
        prefs = host.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        setClipChildren(false); setClipToPadding(false); setClickable(false);
        shell = new LinearLayout(host); shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.CENTER); shell.setPadding(Ui.dp(15),Ui.dp(8),Ui.dp(15),Ui.dp(8));
        shell.setBackground(glass()); shell.setElevation(Ui.dp(12));
        label = Ui.tv(host, "●  ToxiFiles", 12, Color.WHITE, true); label.setGravity(Gravity.CENTER);
        shell.addView(label, new LinearLayout.LayoutParams(-2,-2));
        expanded = new LinearLayout(host); expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setGravity(Gravity.CENTER); expanded.setPadding(Ui.dp(12),Ui.dp(6),Ui.dp(12),Ui.dp(3));
        detail = Ui.tv(host, activity, 11, Color.WHITE, false); detail.setGravity(Gravity.CENTER);
        expanded.addView(detail, new LinearLayout.LayoutParams(-2,-2));
        TextView close = Ui.tv(host,"Collapse",10,0xFFDAE8FF,true); close.setGravity(Gravity.CENTER);
        close.setPadding(Ui.dp(10),Ui.dp(5),Ui.dp(10),Ui.dp(2)); close.setOnClickListener(v -> collapse());
        expanded.addView(close);
        action=Ui.tv(host,"",11,0xFF8FEAFF,true); action.setGravity(Gravity.CENTER); action.setPadding(Ui.dp(12),Ui.dp(6),Ui.dp(12),Ui.dp(4)); action.setVisibility(GONE); action.setOnClickListener(v->{if(actionCallback!=null)actionCallback.run();}); expanded.addView(action);
        shell.addView(expanded); expanded.setVisibility(GONE);
        addView(shell,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START));
        setVisibility(enabled()?VISIBLE:GONE);
        shell.setOnTouchListener((v,e)->handleTouch(e));
        applyPosition();
    }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&h>0)post(this::applyPosition);}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacksAndMessages(null);shell.animate().cancel();super.onDetachedFromWindow();}
    private boolean enabled(){return prefs.getBoolean("enabled",true);}
    private GradientDrawable glass(){
        int palette=prefs.getInt("palette",0);
        int[][] palettes={RGB,{0xFF00E5FF,0xFF1677FF,0xFF843BFF,0xFF1677FF,0xFF00E5FF,0xFF843BFF},{0xFFFF4D36,0xFFFF2878,0xFFD12BFF,0xFFFF4D36,0xFFFF2878,0xFFD12BFF},{0xFF1677FF,0xFF843BFF,0xFFD12BFF,0xFFFF2878,0xFF843BFF,0xFF1677FF}};
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,palettes[Math.max(0,Math.min(palette,palettes.length-1))]);
        d.setCornerRadius(Ui.dp(32));
        int alpha=Math.max(80,Math.min(255,prefs.getInt("transparency",225)));
        d.setAlpha(alpha);
        d.setStroke(Ui.dp(1),0xBFFFFFFF);
        return d;
    }
    private void applyPosition(){
        if(getWidth()==0||getHeight()==0){post(this::applyPosition);return;}
        int w=Math.max(Ui.dp(108),shell.getMeasuredWidth()), h=shell.getMeasuredHeight();
        float nx=prefs.getFloat("x",0.5f), ny=prefs.getFloat("y",0.08f);
        int x=Math.round(nx*Math.max(0,getWidth()-w)), y=Math.round(ny*Math.max(0,getHeight()-h));
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)shell.getLayoutParams();
        lp.gravity=Gravity.TOP|Gravity.START; lp.leftMargin=x; lp.topMargin=y; shell.setLayoutParams(lp);
    }
    private boolean handleTouch(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=e.getRawX(); downY=e.getRawY(); startX=shell.getX(); startY=shell.getY(); dragging=false; longPressed=false;
                longPress=()->{if(!dragging){longPressed=true;expand();if(prefs.getBoolean("haptics",true))performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);}};
                handler.postDelayed(longPress,ViewConfiguration.getLongPressTimeout()); return true;
            case MotionEvent.ACTION_MOVE:
                float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
                if(Math.abs(dx)+Math.abs(dy)>Ui.dp(7)){dragging=true;if(longPress!=null)handler.removeCallbacks(longPress);}
                if(dragging){float x=Math.max(0,Math.min(getWidth()-shell.getWidth(),startX+dx));float y=Math.max(0,Math.min(getHeight()-shell.getHeight(),startY+dy));FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)shell.getLayoutParams();lp.leftMargin=(int)x;lp.topMargin=(int)y;shell.setLayoutParams(lp);} return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(longPress!=null)handler.removeCallbacks(longPress);
                if(dragging){int maxX=Math.max(0,getWidth()-shell.getWidth());float x=shell.getX()<maxX/2f?0:maxX;FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)shell.getLayoutParams();lp.leftMargin=(int)x;lp.topMargin=Math.max(0,Math.min(getHeight()-shell.getHeight(),(int)shell.getY()));shell.setLayoutParams(lp);prefs.edit().putFloat("x",maxX==0?0:x/maxX).putFloat("y",getHeight()<=shell.getHeight()?0:shell.getY()/(getHeight()-shell.getHeight())).apply();}
                else if(!longPressed){toggleExpanded();if(prefs.getBoolean("haptics",true))performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);} return true;
        } return true;
    }
    private void toggleExpanded(){if(expanded.getVisibility()==VISIBLE)collapse();else expand();}
    private void expand(){if(!enabled())return;detail.setText(activity+(progress>=0?" · "+progress+"%":""));expanded.setVisibility(VISIBLE);if(!prefs.getBoolean("reduce_motion",false)) {shell.setScaleX(.94f);shell.setScaleY(.94f);shell.animate().scaleX(1f).scaleY(1f).setDuration(Math.max(90,prefs.getInt("speed",180))).start();}}
    private void collapse(){expanded.setVisibility(GONE);shell.animate().scaleX(1f).scaleY(1f).setDuration(Math.max(80,prefs.getInt("speed",180)/2)).start();}
    void update(String status,int percent){String next=status==null?"ToxiFiles active":status;boolean changed=!next.equals(activity);activity=next;String lower=activity.toLowerCase(java.util.Locale.ROOT);if((lower.contains("copy")||lower.contains("mov")||lower.contains("delet")||lower.contains("zip")||lower.contains("extract"))&&!prefs.getBoolean("show_operations",true))return;if((lower.contains("video")||lower.contains("play"))&&!prefs.getBoolean("show_playback",true))return;if((lower.contains("scan")||lower.contains("index"))&&!prefs.getBoolean("show_scan",true))return;progress=Math.max(-1,Math.min(100,percent));label.setText(activity.length()>23?activity.substring(0,21)+"…":"●  "+activity);detail.setText(activity+(progress>=0?" · "+progress+"%":""));if(changed&&enabled()&&prefs.getBoolean("auto_expand",true)&&!activity.equals("ToxiFiles ready"))expand();}
    void align(int alignment){float x=alignment==0?0f:alignment==1?0.5f:1f;prefs.edit().putFloat("x",x).apply();post(this::applyPosition);}
    void setAction(String title,Runnable callback){actionCallback=callback;action.setText(title==null?"":title);action.setVisibility(title==null?GONE:VISIBLE);}
    void setEnabledFromSettings(){setVisibility(enabled()?VISIBLE:GONE);if(!enabled())collapse();}
    void refreshStyle(){shell.setBackground(glass());shell.setElevation(Ui.dp(6+prefs.getInt("glow",65)/8));shell.setPadding(Ui.dp(15),Ui.dp(prefs.getInt("size",0)==0?8:11),Ui.dp(15),Ui.dp(prefs.getInt("size",0)==0?8:11));}
    static SharedPreferences preferences(Activity a){return a.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);}
}
