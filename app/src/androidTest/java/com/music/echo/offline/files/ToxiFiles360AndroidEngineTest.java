package com.music.echo.offline.files;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ToxiFiles360AndroidEngineTest {
    @Test public void nativeActivityLaunchesWithoutCrashing() {
        try (ActivityScenario<ToxiFiles360Activity> scenario = ActivityScenario.launch(ToxiFiles360Activity.class)) {
            scenario.onActivity(activity -> assertNotNull(activity.findViewById(android.R.id.content)));
        }
    }

    @Test public void poiAndroidCreatesAndReopensWordAndSpreadsheet() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File dir=new File(context.getCacheDir(),"tox360-poi-smoke");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create test directory");
        File docx=new File(dir,"smoke.docx");
        ToxiFiles360Engine.writeWord(docx,"ToxiFiles 360 Android Word");
        assertTrue(ToxiFiles360Engine.extractText(docx).contains("ToxiFiles 360 Android Word"));
        File xlsx=new File(dir,"smoke.xlsx");
        ToxiFiles360Engine.SheetData sheet=new ToxiFiles360Engine.SheetData("Smoke");
        sheet.set(0,0,"Value");sheet.set(1,0,"42");sheet.set(2,0,"=SUM(A2:A2)");
        ToxiFiles360Engine.writeWorkbook(xlsx,Collections.singletonList(sheet));
        assertTrue(ToxiFiles360Engine.extractText(xlsx).contains("Value"));
    }

    @Test public void pptxSlideTextCanBeReadOfflineOnAndroid() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File dir=new File(context.getCacheDir(),"tox360-ppt-smoke");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create test directory");
        File pptx=new File(dir,"smoke.pptx");
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(pptx))){
            String xml="<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><p:cSld><p:spTree><a:t>ToxiFiles 360 offline slide</a:t></p:spTree></p:cSld></p:sld>";
            zip.putNextEntry(new java.util.zip.ZipEntry("ppt/slides/slide1.xml"));
            zip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
        }
        assertTrue(ToxiFiles360Engine.extractText(pptx).contains("ToxiFiles 360 offline slide"));
    }

    @Test public void bundledEnglishTesseractRecognizesTextWithNetworkDisabled() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root=ToxiFiles360Storage.root(context.getFilesDir());
        File tessdata=new File(root,"tessdata");
        if(!tessdata.exists()&&!tessdata.mkdirs())throw new IllegalStateException("Cannot create OCR data folder");
        File trained=new File(tessdata,"eng.traineddata");
        if(!trained.isFile()||trained.length()!=4113088L||!"7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2".equals(ToxiFiles360Storage.sha256(trained))){if(trained.exists())trained.delete();ToxiFiles360Storage.atomicWrite(context.getFilesDir(),trained,tmp->{
            try(FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buffer=new byte[32768];
                for(int part=0;part<8;part++)try(InputStream in=context.getAssets().open(String.format(java.util.Locale.ROOT,"toxifiles360/tessdata/eng.traineddata.part%02d",part))){int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
                out.flush();
            }
        });
        Bitmap image=Bitmap.createBitmap(1200,300,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(112f);paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
        canvas.drawText("TOXIFILES OFFLINE",32,190,paint);
        assertEquals(4113088L,trained.length());
        assertEquals("7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",ToxiFiles360Storage.sha256(trained));
        TessBaseAPI api=new TessBaseAPI();
        try{
            assertTrue("Bundled English model must initialize offline",api.init(root.getAbsolutePath(),"eng"));
            api.setImage(image);
            String recognized=api.getUTF8Text();
            assertNotNull(recognized);
            assertTrue("OCR did not recognize the offline smoke-test image: "+recognized,recognized.toUpperCase(java.util.Locale.ROOT).contains("TOXIFILES"));
        }finally{api.recycle();image.recycle();}
    }
}
