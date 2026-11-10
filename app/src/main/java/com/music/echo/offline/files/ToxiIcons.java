package com.music.echo.offline.files;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Original geometric symbol language for every ToxiFiles navigation, action and file type. */
final class ToxiIcons {
    static final int FOLDER=0, IMAGE=1, VIDEO=2, AUDIO=3, DOC=4, ARCHIVE=5, APK=6, FILE=7,
        HOME=8, FILES=9, STAR=10, TUNE=11, SEARCH=12, MORE=13, BACK=14, NEXT=15,
        CLOSE=16, CHECK=17, COPY=18, CUT=19, TRASH=20, SHARE=21, INFO=22,
        FOLDER_PLUS=23, SORT=24, OPEN=25, PLAY=26, PAUSE=27, PASTE=28,
        PALETTE=29, SPARKLE=30, VIBRATE=31, EYE=32, SHIELD=33, CLOCK=34,
        LOCK=35, WIFI=36, DATABASE=37, ANALYTICS=38, DOWNLOAD=39, CLOUD=40,
        MUSIC=41, SUN=42, REFRESH=43, SETTINGS=44, PDF=45, TEXT=46, SHUFFLE=47, REPEAT=48, UP=49, DOWN=50, COLLAPSE=51, VOLUME=52;
    private ToxiIcons() { }
    static String accessibilityLabel(int id) {
        switch(id) {
            case FOLDER:return "Folder"; case IMAGE:return "Image"; case VIDEO:return "Video"; case AUDIO:return "Audio";
            case DOC:return "Document"; case PDF:return "PDF document"; case TEXT:return "Text file"; case ARCHIVE:return "Archive"; case APK:return "Android package"; case FILE:return "File";
            case HOME:return "Home"; case FILES:return "Files"; case STAR:return "Favorite"; case TUNE:return "Filter and sort";
            case SEARCH:return "Search"; case MORE:return "More options"; case BACK:return "Back"; case NEXT:return "Next";
            case CLOSE:return "Close"; case CHECK:return "Select"; case COPY:return "Copy"; case CUT:return "Cut";
            case TRASH:return "Delete"; case SHARE:return "Share"; case INFO:return "Information"; case FOLDER_PLUS:return "New folder";
            case SORT:return "Sort"; case OPEN:return "Open"; case PLAY:return "Play"; case PAUSE:return "Pause"; case PASTE:return "Paste";
            case PALETTE:return "Theme"; case SPARKLE:return "Appearance"; case VIBRATE:return "Haptic feedback"; case EYE:return "Visibility";
            case SHIELD:return "Security"; case CLOCK:return "Recent"; case LOCK:return "Lock"; case WIFI:return "Wi-Fi transfer";
            case DATABASE:return "Storage"; case ANALYTICS:return "Analysis"; case DOWNLOAD:return "Download"; case CLOUD:return "Cloud";
            case MUSIC:return "Music"; case SUN:return "Light appearance"; case REFRESH:return "Refresh"; case SETTINGS:return "Settings";
            case SHUFFLE:return "Shuffle playback"; case REPEAT:return "Repeat playback"; case UP:return "Move up"; case DOWN:return "Move down"; case COLLAPSE:return "Collapse"; case VOLUME:return "Media volume";
            default:return "Action";
        }
    }
    static Drawable symbol(int id, int color) { return new SymbolDrawable(id, color); }
    static Drawable fileTile(int fileKind) { return new FileTileDrawable(fileKind); }
    static int mix(int a,int b,float t) {
        return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t), (int)(Color.green(a)+(Color.green(b)-Color.green(a))*t), (int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
    }

    private static final class SymbolDrawable extends Drawable {
        final int id; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        SymbolDrawable(int id,int color) { this.id=id; p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.9f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }
        void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,p);}
        void circle(Canvas c,float x,float y,float r){c.drawCircle(x,y,r,p);}
        void rect(Canvas c,float l,float t,float r,float b,float rad){c.drawRoundRect(new RectF(l,t,r,b),rad,rad,p);}
        void path(Canvas c,float... xy){Path q=new Path();q.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)q.lineTo(xy[i],xy[i+1]);c.drawPath(q,p);}
        void arc(Canvas c,float l,float t,float r,float b,float start,float sweep){c.drawArc(new RectF(l,t,r,b),start,sweep,false,p);}
        @Override public void draw(Canvas canvas) {
            Rect b=getBounds(); if(b.isEmpty()) return; int save=canvas.save(); canvas.translate(b.left,b.top); canvas.scale(b.width()/24f,b.height()/24f);
            switch(id) {
                case FOLDER: case FILES: case FOLDER_PLUS:
                    path(canvas,3,7,3,5.5f,4.5f,4,9,4,11,6,20,6,21,7,21,18,20,20,4,20,3,19,3,7);
                    if(id==FILES){path(canvas,5,4,5,3,18,3,20,5);}
                    if(id==FOLDER_PLUS){line(canvas,12,10,12,17);line(canvas,8.5f,13.5f,15.5f,13.5f);} break;
                case IMAGE: rect(canvas,3,4,21,20,2); circle(canvas,8,9,1.6f); path(canvas,4.5f,17,9.5f,12,13,15,16,11,20,16); break;
                case VIDEO: rect(canvas,2.5f,5,21.5f,19,3); path(canvas,10,8.5f,16,12,10,15.5f,10,8.5f); break;
                case AUDIO: path(canvas,9,17,9,5,19,3,19,15); circle(canvas,6.5f,17,2.5f); circle(canvas,16.5f,15,2.5f); break;
                case VOLUME: path(canvas,3,9,7,9,13,4,13,20,7,15,3,15,3,9); path(canvas,16,8,18,12,16,16); path(canvas,19,5,22,12,19,19); break;
                case DOC: path(canvas,6,3,15,3,20,8,20,21,6,21,6,3); path(canvas,15,3,15,8,20,8); line(canvas,9,12,17,12); line(canvas,9,16,17,16); break;
                case PDF: path(canvas,6,3,15,3,20,8,20,21,6,21,6,3); path(canvas,15,3,15,8,20,8); line(canvas,8,12,17,12); line(canvas,8,15,17,15); line(canvas,8,18,14,18); break;
                case TEXT: path(canvas,6,3,15,3,20,8,20,21,6,21,6,3); path(canvas,15,3,15,8,20,8); line(canvas,9,12,17,12); line(canvas,9,15,17,15); line(canvas,9,18,15,18); break;
                case SHUFFLE: path(canvas,3,6,8,6,16,18,21,18); path(canvas,17,14,21,18,17,22); path(canvas,3,18,8,18,16,6,21,6); path(canvas,17,2,21,6,17,10); break;
                case REPEAT: path(canvas,20,8,20,4,16,4); path(canvas,20,4,16,8); arc(canvas,4,4,20,20,205,245); path(canvas,4,16,4,20,8,20); path(canvas,4,20,8,16); break;
                case UP: path(canvas,5,15,12,8,19,15); line(canvas,12,8,12,21); break;
                case DOWN: path(canvas,5,9,12,16,19,9); line(canvas,12,3,12,16); break;
                case COLLAPSE: path(canvas,5,15,12,8,19,15); break;
                case ARCHIVE: rect(canvas,4,6,20,20,2); rect(canvas,3,4,21,8,1); line(canvas,12,8,12,17); path(canvas,9,14,12,17,15,14); break;
                case APK: path(canvas,5,10,7,6,17,6,19,10,19,18,5,18,5,10); line(canvas,7,4,9,7); line(canvas,17,4,15,7); circle(canvas,9,11,0.6f); circle(canvas,15,11,0.6f); line(canvas,8,19,8,21); line(canvas,16,19,16,21); break;
                case FILE: path(canvas,5,3,15,3,20,8,20,21,5,21,5,3); path(canvas,15,3,15,8,20,8); break;
                case HOME: path(canvas,2.5f,11,12,3,21.5f,11); path(canvas,5,10,5,21,19,21,19,10); path(canvas,10,21,10,15,14,15,14,21); break;
                case STAR: path(canvas,12,2.5f,14.8f,8.5f,21,9.3f,16.5f,13.7f,17.6f,20,12,17,6.4f,20,7.5f,13.7f,3,9.3f,9.2f,8.5f,12,2.5f); break;
                case TUNE: line(canvas,4,6,20,6);line(canvas,4,12,20,12);line(canvas,4,18,20,18);circle(canvas,9,6,2);circle(canvas,15,12,2);circle(canvas,7,18,2); break;
                case SEARCH: circle(canvas,10.5f,10.5f,6.5f);line(canvas,15.5f,15.5f,21,21);break;
                case MORE: p.setStyle(Paint.Style.FILL);circle(canvas,5,12,1.4f);circle(canvas,12,12,1.4f);circle(canvas,19,12,1.4f);p.setStyle(Paint.Style.STROKE);break;
                case BACK: path(canvas,14.5f,5,7.5f,12,14.5f,19);line(canvas,8,12,21,12);break;
                case NEXT: path(canvas,9.5f,5,16.5f,12,9.5f,19);line(canvas,3,12,16,12);break;
                case CLOSE: line(canvas,6,6,18,18);line(canvas,18,6,6,18);break;
                case CHECK: path(canvas,4,12.5f,9.5f,18,20,6);break;
                case COPY: rect(canvas,8,7,20,20,1.8f);path(canvas,15,7,15,4,4,4,4,17,8,17);break;
                case CUT: circle(canvas,6.5f,17,2.5f);circle(canvas,6.5f,7,2.5f);path(canvas,9,8,20,19);path(canvas,9,16,20,5);break;
                case TRASH: path(canvas,4,7,20,7);line(canvas,9,4,15,4);path(canvas,6,7,7,21,17,21,18,7);line(canvas,10,10,10,18);line(canvas,14,10,14,18);break;
                case SHARE: circle(canvas,18,5,2.5f);circle(canvas,6,12,2.5f);circle(canvas,18,19,2.5f);line(canvas,8.2f,10.8f,15.8f,6.2f);line(canvas,8.2f,13.2f,15.8f,17.8f);break;
                case INFO: circle(canvas,12,12,9);line(canvas,12,11,12,17);p.setStyle(Paint.Style.FILL);circle(canvas,12,7.5f,0.9f);p.setStyle(Paint.Style.STROKE);break;
                case SORT: path(canvas,7,5,4,8,10,8);line(canvas,7,5,7,19);path(canvas,17,19,14,16,20,16);line(canvas,17,19,17,5);break;
                case OPEN: path(canvas,13,4,20,4,20,11);line(canvas,20,4,10,14);path(canvas,18,14,18,20,4,20,4,6,10,6);break;
                case PLAY: p.setStyle(Paint.Style.FILL);path(canvas,7,4,19,12,7,20,7,4);p.setStyle(Paint.Style.STROKE);break;
                case PAUSE: rect(canvas,6,4,10,20,1);rect(canvas,14,4,18,20,1);break;
                case PASTE: path(canvas,8,5,8,3,16,3,16,5,19,5,19,21,5,21,5,5,8,5);rect(canvas,9,5,15,8,1);break;
                case PALETTE: path(canvas,12,3,20,7,21,14,17,20,10,21,4,17,3,10,7,5,12,3);circle(canvas,8,10,1);circle(canvas,13,7,1);circle(canvas,17,11,1);circle(canvas,11,16,1);break;
                case SPARKLE: path(canvas,12,2,14,9,21,12,14,14,12,22,10,14,3,12,10,9,12,2);path(canvas,19,2,20,5,23,6,20,7,19,10,18,7,15,6,18,5,19,2);break;
                case VIBRATE: rect(canvas,7,3,17,21,2);line(canvas,10,6,14,6);path(canvas,3,8,1.5f,12,3,16);path(canvas,21,8,22.5f,12,21,16);break;
                case EYE: path(canvas,2,12,6,7,12,5,18,7,22,12,18,17,12,19,6,17,2,12);circle(canvas,12,12,3);break;
                case SHIELD: path(canvas,12,2,20,5,19,14,16,19,12,22,8,19,5,14,4,5,12,2);path(canvas,8,12,11,15,16,9);break;
                case CLOCK: circle(canvas,12,12,9);path(canvas,12,6,12,12,17,15);break;
                case LOCK: rect(canvas,4,10,20,21,2);arc(canvas,7,3,17,15,180,180);circle(canvas,12,15,1);line(canvas,12,16,12,18);break;
                case WIFI: arc(canvas,2,3,22,23,225,90);arc(canvas,6,7,18,21,225,90);arc(canvas,9,11,15,19,225,90);p.setStyle(Paint.Style.FILL);circle(canvas,12,20,1.2f);p.setStyle(Paint.Style.STROKE);break;
                case DATABASE: arc(canvas,4,3,20,10,0,360);path(canvas,4,6,4,18,12,21,20,18,20,6);arc(canvas,4,10,20,17,0,180);break;
                case ANALYTICS: line(canvas,4,20,4,4);line(canvas,4,20,21,20);rect(canvas,7,12,10,19,0.8f);rect(canvas,13,8,16,19,0.8f);rect(canvas,19,4,21,19,0.8f);break;
                case DOWNLOAD: path(canvas,12,3,12,16);path(canvas,7,11,12,16,17,11);path(canvas,4,17,4,21,20,21,20,17);break;
                case CLOUD: path(canvas,5,18,3,15,3,12,5,9,9,8,11,4,16,4,19,8,21,10,21,15,18,18,5,18);break;
                case MUSIC: path(canvas,10,17,10,5,20,3,20,15);circle(canvas,7,18,2.5f);circle(canvas,17,16,2.5f);break;
                case SUN: circle(canvas,12,12,4);line(canvas,12,2,12,5);line(canvas,12,19,12,22);line(canvas,2,12,5,12);line(canvas,19,12,22,12);line(canvas,5,5,7,7);line(canvas,17,17,19,19);line(canvas,19,5,17,7);line(canvas,7,17,5,19);break;
                case REFRESH: arc(canvas,4,4,20,20,205,250);path(canvas,4,5,4,11,10,10);path(canvas,20,19,20,13,14,14);break;
                case SETTINGS: circle(canvas,12,12,3);circle(canvas,12,12,8);line(canvas,12,1.5f,12,4);line(canvas,12,20,12,22.5f);line(canvas,1.5f,12,4,12);line(canvas,20,12,22.5f,12);line(canvas,4.5f,4.5f,6.3f,6.3f);line(canvas,17.7f,17.7f,19.5f,19.5f);line(canvas,19.5f,4.5f,17.7f,6.3f);line(canvas,6.3f,17.7f,4.5f,19.5f);break;
                default: path(canvas,5,3,15,3,20,8,20,21,5,21,5,3);path(canvas,15,3,15,8,20,8);break;
            }
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    private static final class FileTileDrawable extends Drawable {
        final int kind, color; final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), edge=new Paint(Paint.ANTI_ALIAS_FLAG), sheen=new Paint(Paint.ANTI_ALIAS_FLAG); final SymbolDrawable glyph;
        FileTileDrawable(int kind){this.kind=kind;color=FileKinds.color(kind);glyph=new SymbolDrawable(FileKinds.glyph(kind),0xFFF4F7FA);edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(1f);edge.setColor(0x66FFFFFF);}
        @Override protected void onBoundsChange(Rect b){fill.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{mix(color,0xFFFFFFFF,.18f),color,mix(color,0xFF000000,.2f)},null,Shader.TileMode.CLAMP));}
        @Override public void draw(Canvas c){Rect b=getBounds();RectF r=new RectF(b);float rad=b.width()*.22f;c.drawRoundRect(r,rad,rad,fill);RectF top=new RectF(b.left+1,b.top+1,b.right-1,b.top+b.height()*.42f);sheen.setShader(new LinearGradient(0,top.top,0,top.bottom,new int[]{0x55FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP));c.drawRoundRect(top,rad*.8f,rad*.8f,sheen);c.drawRoundRect(r,rad,rad,edge);int inset=(int)(b.width()*.22f);glyph.setBounds(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);glyph.draw(c);}
        @Override public void setAlpha(int a){fill.setAlpha(a);glyph.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){fill.setColorFilter(f);glyph.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
