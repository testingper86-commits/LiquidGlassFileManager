package com.music.echo.offline.files;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.ParcelFileDescriptor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ToxiFiles360Activity extends Activity {
    private static final int REQ_IMPORT=3601, REQ_CAMERA=3602, REQ_EXPORT=3603;
    private static final String[] SECTIONS={"Word Documents","Excel Spreadsheets","PowerPoint","PDF Workspace","Notes","Scanner & OCR","Offline Converter","Smart Collections","Version History","Document Inspector"};
    private static final String[] SECTION_ICONS={"word","excel","powerpoint","pdf","notes","scanner","converter","collections","history","inspector"};
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"toxifiles360-io");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private LinearLayout root,body;
    private File currentFile,pendingExport,scanFile;
    private boolean pendingImageImport,editorDirty,spreadsheetDirty,slideDirty;
    private final Set<String> editedWorkspacePaths=new HashSet<>();
    private String activeSection="Home";
    private EditText editor,slideEditor;
    private ArrayList<ToxiFiles360Engine.SheetData> sheets;
    private int sheetIndex,slideIndex;
    private ToxiFiles360Engine.PresentationData presentation;
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        Ui.init(this);
        ToxiFiles360Storage.documents(getFilesDir());
        ToxiFiles360Storage.scans(getFilesDir());
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.bg);
        setContentView(root);
        renderHome();
    }
    @Override public void onBackPressed(){if("Editor".equals(activeSection)&&(editorDirty||spreadsheetDirty||slideDirty)){new AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save your changes before leaving this document?").setNegativeButton("Keep editing",null).setNeutralButton("Discard",(d,w)->{editorDirty=false;spreadsheetDirty=false;slideDirty=false;renderHome();}).setPositiveButton("Save",(d,w)->{if(editorDirty)saveCurrent();else if(spreadsheetDirty)saveSpreadsheet();else if(slideDirty)saveSlideText();}).show();return;}if(!"Home".equals(activeSection)){renderHome();return;}super.onBackPressed();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private void renderHome(){
        activeSection="Home";root.removeAllViews();addHeader("ToxiFiles 360","OFFLINE OFFICE WORKSPACE",true);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(14),Ui.dp(8),Ui.dp(14),Ui.dp(28));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView intro=Ui.tv(this,"Your private productivity workspace",18,Ui.WHITE,true);intro.setPadding(Ui.dp(4),Ui.dp(8),Ui.dp(4),Ui.dp(12));body.addView(intro);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        addAction(actions,"New","new",v->createDocument());addAction(actions,"Import","import",v->importDocument());addAction(actions,"Search","search",v->searchDocuments());
        body.addView(actions,new LinearLayout.LayoutParams(-1,Ui.dp(76)));
        TextView label=Ui.tv(this,"WORKSPACES",11,Ui.accent,true);label.setLetterSpacing(.12f);label.setPadding(Ui.dp(4),Ui.dp(20),0,Ui.dp(10));body.addView(label);
        for(int r=0;r<(SECTIONS.length+1)/2;r++){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            for(int c=0;c<2;c++){int i=r*2+c;if(i>=SECTIONS.length){row.addView(new View(this),new LinearLayout.LayoutParams(0,Ui.dp(1),1));continue;}
                final int index=i;LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(Ui.dp(12),Ui.dp(12),Ui.dp(12),Ui.dp(12));card.setBackground(Ui.glass(18,true));card.setElevation(Ui.dp(2));
                ImageView icon=icon(SECTION_ICONS[i]);card.addView(icon,new LinearLayout.LayoutParams(Ui.dp(42),Ui.dp(42)));
                TextView name=Ui.tv(this,SECTIONS[i],14,Ui.WHITE,true);name.setPadding(0,Ui.dp(8),0,0);card.addView(name);
                TextView desc=Ui.tv(this,sectionDescription(i),11.5f,Ui.SUB,false);desc.setPadding(0,Ui.dp(3),0,0);card.addView(desc);
                card.setFocusable(true);card.setOnClickListener(v->openSection(SECTIONS[index]));Ui.press(card);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(Ui.dp(c==0?0:6),Ui.dp(5),Ui.dp(c==1?0:6),Ui.dp(5));row.addView(card,lp);
            }
            body.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        TextView note=Ui.tv(this,"Files stay on this device. Imported documents are copied into the private ToxiFiles 360 workspace; originals are not overwritten.",12,Ui.SUB,false);
        note.setPadding(Ui.dp(12),Ui.dp(14),Ui.dp(12),Ui.dp(14));note.setBackground(Ui.glass(16,false));body.addView(note,new LinearLayout.LayoutParams(-1,-2));
        addRecentFiles();
    }
    private String sectionDescription(int i){switch(i){case 0:return "Create and edit supported documents";case 1:return "Worksheets, cells and formulas";case 2:return "Slides and presentation text";case 3:return "Read and create PDF files";case 4:return "Fast offline notes";case 5:return "Camera capture and offline OCR";case 6:return "Convert supported local formats";case 7:return "Favorites and smart filters";case 8:return "Restore earlier saved versions";default:return "File details, hashes and compatibility";}}
    private void addHeader(String title,String subtitle,boolean home){
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);h.setPadding(Ui.dp(12),Ui.dp(8),Ui.dp(12),Ui.dp(8));h.setBackground(Ui.glass(18,true));
        if(!home){TextView back=Ui.tv(this,"‹",32,Ui.WHITE,true);back.setGravity(Gravity.CENTER);h.addView(back,new LinearLayout.LayoutParams(Ui.dp(44),Ui.dp(48)));back.setOnClickListener(v->onBackPressed());}
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(Ui.tv(this,title,19,Ui.WHITE,true));TextView sub=Ui.tv(this,subtitle,9.5f,Ui.accent,true);sub.setLetterSpacing(.13f);labels.addView(sub);
        h.addView(labels,new LinearLayout.LayoutParams(0,-2,1));root.addView(h,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addAction(LinearLayout row,String label,String asset,View.OnClickListener click){
        LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER);item.setOrientation(LinearLayout.VERTICAL);ImageView ic=icon(asset);item.addView(ic,new LinearLayout.LayoutParams(Ui.dp(28),Ui.dp(28)));TextView tx=Ui.tv(this,label,11,Ui.WHITE,true);item.addView(tx);item.setOnClickListener(click);Ui.press(item);row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }
    private ImageView icon(String name){
        ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try(InputStream in=getAssets().open("toxifiles360/icons/"+name+".png")){v.setImageBitmap(BitmapFactory.decodeStream(in));}catch(Exception ignored){v.setImageDrawable(Glyphs.get(Glyphs.INFO,Ui.accent));}
        v.setContentDescription(name);return v;
    }
    private void openSection(String section){
        activeSection=section;root.removeAllViews();addHeader(section,"TOXIFILES 360  •  LOCAL ONLY",false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(14),Ui.dp(10),Ui.dp(14),Ui.dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        if(section.equals("Scanner & OCR")){addAction(actions,"Camera","camera",v->captureScan());addAction(actions,"Choose image","import",v->importImage());}
        else if(section.equals("Offline Converter")){addAction(actions,"Import","import",v->importDocument());addAction(actions,"Convert","convert",v->chooseConversion());}
        else if(section.equals("PDF Workspace")){addAction(actions,"Import","import",v->importDocument());addAction(actions,"Annotate","annotate",v->chooseAnnotationFile());addAction(actions,"Search","search",v->searchDocuments());}
        else if(section.equals("Smart Collections")){addAction(actions,"Favorites","collections",v->showFavorites());addAction(actions,"Recent","history",v->showRecent());}
        else if(section.equals("Version History")){addAction(actions,"Choose file","search",v->chooseHistoryFile());}
        else if(section.equals("Document Inspector")){addAction(actions,"Inspect file","inspector",v->chooseInspectorFile());}
        else {addAction(actions,"New","new",v->createDocumentFor(section));addAction(actions,"Import","import",v->importDocument());addAction(actions,"Search","search",v->searchDocuments());}
        body.addView(actions,new LinearLayout.LayoutParams(-1,Ui.dp(76)));
        if(section.equals("Scanner & OCR")){TextView t=Ui.tv(this,"Camera scans are stored locally. OCR uses the bundled English Tesseract model and runs on this device.",13,Ui.SUB,false);t.setPadding(Ui.dp(12),Ui.dp(12),Ui.dp(12),Ui.dp(12));body.addView(t);listScans();}
        else if(section.equals("Document Inspector")){TextView t=Ui.tv(this,"Select a workspace document to inspect its format, size, modified time and SHA-256 digest.",13,Ui.SUB,false);t.setPadding(Ui.dp(12),Ui.dp(12),Ui.dp(12),Ui.dp(12));body.addView(t);listFiles(null);}
        else if(section.equals("Version History"))listFiles(null);
        else if(section.equals("Smart Collections")){showCollectionFilters();}
        else if(section.equals("Offline Converter"))listFiles(null);
        else if(section.equals("PDF Workspace"))listFiles("pdf");
        else if(section.equals("Notes"))listFiles("txt");
        else if(section.equals("Word Documents"))listFilesFor("Word");
        else if(section.equals("Excel Spreadsheets"))listFilesFor("Excel");
        else if(section.equals("PowerPoint"))listFilesFor("PowerPoint");
        else listFiles(null);
    }
    private void listFilesFor(String category){listFiles(category);}
    private void listScans(){File[] scans=ToxiFiles360Storage.scans(getFilesDir()).listFiles();if(scans==null||scans.length==0){addTextButton("No scans captured yet.",()->captureScan());return;}Arrays.sort(scans,(a,b)->Long.compare(a.lastModified(),b.lastModified()));addIconTextButton("Create a multi-page PDF from all scans","scan-pdf",this::createPdfFromScans);for(File f:scans)if(f.isFile())addFileRow(f);}
    private void listFiles(String filter){
        File[] files=ToxiFiles360Storage.documents(getFilesDir()).listFiles();if(files==null)files=new File[0];Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        int count=0;for(File f:files){if(!f.isFile()||f.getName().startsWith("."))continue;ToxiFiles360Engine.FormatInfo info=ToxiFiles360Engine.format(f.getName());String cat=info==null?"Other":info.category;
            if(filter!=null&&!filter.equalsIgnoreCase(cat)&&!filter.equalsIgnoreCase(ToxiFiles360Engine.extension(f.getName())))continue;
            addFileRow(f);count++;
        }
        if(count==0){TextView empty=Ui.tv(this,"No files here yet. Import a document or create a new one.",13,Ui.SUB,false);empty.setPadding(Ui.dp(12),Ui.dp(24),Ui.dp(12),Ui.dp(24));body.addView(empty);}
    }
    private void addFileRow(File f){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(10),Ui.dp(8),Ui.dp(8),Ui.dp(8));row.setBackground(Ui.glass(14,false));
        String ext=ToxiFiles360Engine.extension(f.getName());ImageView ic=icon(ext.isEmpty()?"txt":ext);row.addView(ic,new LinearLayout.LayoutParams(Ui.dp(42),Ui.dp(42)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView name=Ui.tv(this,f.getName(),13,Ui.WHITE,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(name);
        ToxiFiles360Engine.FormatInfo info=ToxiFiles360Engine.format(f.getName());String detail=(info==null?"Unknown":info.level.name().replace('_',' '))+"  •  "+Ui.size(f.length());text.addView(Ui.tv(this,detail,10.5f,Ui.SUB,false));
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView more=Ui.tv(this,"⋮",24,Ui.WHITE,true);more.setPadding(Ui.dp(8),0,Ui.dp(8),0);row.addView(more);more.setOnClickListener(v->fileMenu(f));
        row.setOnClickListener(v->openFile(f));Ui.press(row);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(6);body.addView(row,lp);
    }
    private void fileMenu(File f){boolean pdf="pdf".equals(ToxiFiles360Engine.extension(f.getName()));String[] options=pdf?new String[]{"Open","Annotate PDF copy","Export copy","Add to Favorites","Inspect"}:new String[]{"Open","Export copy","Add to Favorites","Inspect"};new AlertDialog.Builder(this).setTitle(f.getName()).setItems(options,(d,w)->{if(w==0)openFile(f);else if(pdf&&w==1)annotatePdfDialog(f);else{int k=pdf?w-1:w;if(k==1)exportFile(f);else if(k==2)toggleFavorite(f);else inspect(f);}}).show();}
    private void addRecentFiles(){TextView label=Ui.tv(this,"WORKSPACE FILES",11,Ui.accent,true);label.setPadding(Ui.dp(4),Ui.dp(20),0,Ui.dp(8));body.addView(label);File[] fs=ToxiFiles360Storage.documents(getFilesDir()).listFiles();if(fs!=null){Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));int n=0;for(File f:fs)if(f.isFile()&&n++<5)addFileRow(f);}}
    private void importDocument(){pendingImageImport=false;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_IMPORT);}
    private void importImage(){pendingImageImport=true;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_IMPORT);}
    private String displayName(Uri uri){String n=null;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())n=c.getString(0);}catch(Exception ignored){}return ToxiFiles360Storage.safeName(n==null?"imported-document":n);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        if(request==REQ_IMPORT&&data!=null&&data.getData()!=null){Uri uri=data.getData();boolean imageImport=pendingImageImport;pendingImageImport=false;runIo(()->{File target=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),displayName(uri));try(InputStream in=getContentResolver().openInputStream(uri)){ToxiFiles360Storage.copy(in,target,ToxiFiles360Storage.MAX_IMPORT_BYTES);}return target;},f->{currentFile=f;if(imageImport){runIo(()->ocr(f),text->runIo(()->{File out=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),stripExt(f.getName())+"_OCR.txt");writeTextAtomically(out,text);return out;},out->{currentFile=out;editedWorkspacePaths.add(out.getAbsolutePath());openEditor(out);Ui.toast(this,"Offline OCR complete");}));}else{openSection(activeSection);Ui.toast(this,"Imported into private workspace");}});}
        else if(request==REQ_CAMERA&&scanFile!=null&&scanFile.isFile()){File f=scanFile;runIo(()->ocr(f),text->runIo(()->{File out=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),stripExt(f.getName())+"_OCR.txt");writeTextAtomically(out,text);return out;},out->{currentFile=out;editedWorkspacePaths.add(out.getAbsolutePath());openEditor(out);Ui.toast(this,"Offline OCR complete");}));}
        else if(request==REQ_EXPORT&&data!=null&&data.getData()!=null&&pendingExport!=null){Uri uri=data.getData();File source=pendingExport;runIo(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("Destination unavailable");byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();}return true;},ok->Ui.toast(this,"Exported copy"));pendingExport=null;}
    }
    private void createDocument(){showCreateDialog(null);}
    private void createDocumentFor(String section){showCreateDialog(section);}
    private void showCreateDialog(String section){
        String[] labels={"Word document (.docx)","Spreadsheet (.xlsx)","Presentation (.pptx)","Note (.txt)","CSV sheet (.csv)","PDF (.pdf)","Rich text (.rtf)"};
        String[] exts={"docx","xlsx","pptx","txt","csv","pdf","rtf"};
        int[] allowed=section==null?new int[]{0,1,2,3,4,5,6}:section.equals("Word Documents")?new int[]{0,3,6}:section.equals("Excel Spreadsheets")?new int[]{1,4}:section.equals("PowerPoint")?new int[]{2}:section.equals("PDF Workspace")?new int[]{5}:section.equals("Notes")?new int[]{3}:new int[]{3};
        String[] shown=new String[allowed.length];for(int i=0;i<allowed.length;i++)shown[i]=labels[allowed[i]];
        new AlertDialog.Builder(this).setTitle("Create locally").setItems(shown,(d,w)->{int k=allowed[w];createFile(exts[k]);}).show();
    }
    private void createFile(String ext){
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Document name");name.setText("Untitled");
        new AlertDialog.Builder(this).setTitle("New "+ext.toUpperCase(Locale.ROOT)).setView(name).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{
            String n=ToxiFiles360Storage.safeName(name.getText().toString());if(!n.toLowerCase(Locale.ROOT).endsWith("."+ext))n+="."+ext;
            File out=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),n);
            runIo(()->{ToxiFiles360Storage.atomicWrite(getFilesDir(),out,tmp->createLocal(tmp,ext));return out;},f->{currentFile=f;editedWorkspacePaths.add(f.getAbsolutePath());openFile(f);});
        }).show();
    }
    private void createLocal(File f,String ext)throws Exception{
        if(ext.equals("docx"))ToxiFiles360Engine.writeWord(f,"");
        else if(ext.equals("xlsx")){ToxiFiles360Engine.SheetData s=new ToxiFiles360Engine.SheetData("Sheet1");s.set(0,0,"");ToxiFiles360Engine.writeWorkbook(f,Collections.singletonList(s));}
        else if(ext.equals("pptx")){try(InputStream in=getAssets().open("toxifiles360/templates/blank-presentation.pptx")){ToxiFiles360Storage.copy(in,f,10L*1024*1024);}}
        else if(ext.equals("pdf"))createPdf(f,"");
        else if(ext.equals("rtf"))writeText(f,ToxiFiles360Engine.exportRtf(""));
        else writeText(f,"");
    }
    private void openFile(File f){
        if(!f.isFile())return;String ext=ToxiFiles360Engine.extension(f.getName());
        if(ext.equals("pdf")){ArrayList<String> paths=new ArrayList<>();paths.add(f.getAbsolutePath());Intent i=new Intent(this,ViewerActivity.class);i.putStringArrayListExtra("paths",paths);i.putExtra("index",0);startActivity(i);return;}
        if(ext.equals("jpg")||ext.equals("jpeg")||ext.equals("png")||ext.equals("webp")){new AlertDialog.Builder(this).setTitle(f.getName()).setMessage("Run offline English OCR on this image?").setNegativeButton("Cancel",null).setPositiveButton("Recognize",(d,w)->runIo(()->ocr(f),text->runIo(()->{File out=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),stripExt(f.getName())+"_OCR.txt");writeTextAtomically(out,text);return out;},out->{currentFile=out;editedWorkspacePaths.add(out.getAbsolutePath());openEditor(out);}))).show();return;}
        openEditor(f);
    }
    private void openEditor(File f){
        currentFile=f;activeSection="Editor";editorDirty=false;spreadsheetDirty=false;root.removeAllViews();addHeader(f.getName(),"LOCAL EDITOR  •  "+ToxiFiles360Engine.extension(f.getName()).toUpperCase(Locale.ROOT),false);
        String ext=ToxiFiles360Engine.extension(f.getName());ToxiFiles360Engine.FormatInfo info=ToxiFiles360Engine.format(f.getName());
        if(ext.equals("xlsx")||ext.equals("xls")||ext.equals("xlsm")||ext.equals("xltx")||ext.equals("xlt")||ext.equals("xltm")||ext.equals("csv")||ext.equals("tsv")){openSpreadsheet(f);return;}
        if(ext.equals("pptx")||ext.equals("ppsx")||ext.equals("potx")){openSlides(f);return;}
        boolean canEdit=ToxiFiles360Engine.editable(f.getName());
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setPadding(Ui.dp(8),Ui.dp(6),Ui.dp(8),Ui.dp(6));
        if(canEdit&&ext.equals("docx")){addSmall(controls,"B",()->applyStyle(Typeface.BOLD));addSmall(controls,"I",()->applyStyle(Typeface.ITALIC));addSmall(controls,"U",this::applyUnderline);addSmall(controls,"H1",()->applyHeading(1.6f));addSmall(controls,"H2",()->applyHeading(1.3f));addSmall(controls,"•",this::insertBullet);addSmall(controls,"Find",this::findReplace);}
        if(canEdit){addSmall(controls,"Save",this::saveCurrent);addSmall(controls,"PDF",this::createPdfFromEditor);addSmall(controls,"Export",()->exportFile(currentFile));addSmall(controls,"Convert",this::chooseConversion);}
        else{addSmall(controls,"Export",()->exportFile(currentFile));addSmall(controls,"Inspect",()->inspect(currentFile));}
        root.addView(controls,new LinearLayout.LayoutParams(-1,Ui.dp(52)));
        ScrollView scroll=new ScrollView(this);editor=new EditText(this);editor.setTextSize(15);editor.setTextColor(Ui.WHITE);editor.setHintTextColor(Ui.SUB);editor.setGravity(Gravity.TOP|Gravity.START);editor.setPadding(Ui.dp(14),Ui.dp(12),Ui.dp(14),Ui.dp(24));editor.setBackground(Ui.glass(16,false));editor.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));editor.setTextIsSelectable(true);editor.setEnabled(false);editor.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(editor.isEnabled())editorDirty=true;}public void afterTextChanged(android.text.Editable e){}});
        runIo(()->ToxiFiles360Engine.extractText(f),text->{editor.setText(text);editor.setEnabled(canEdit);});
        scroll.addView(editor,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(info!=null){TextView level=Ui.tv(this,"Compatibility: "+info.level+" — "+info.details,11,Ui.SUB,false);level.setPadding(Ui.dp(12),Ui.dp(8),Ui.dp(12),Ui.dp(10));root.addView(level);}
    }
    private void applyStyle(int style){if(editor==null)return;int a=editor.getSelectionStart(),b=editor.getSelectionEnd();if(a<0||b<=a){Ui.toast(this,"Select text first");return;}Spannable s=editor.getText();s.setSpan(new StyleSpan(style),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);editorDirty=true;}
    private void applyUnderline(){if(editor==null)return;int a=editor.getSelectionStart(),b=editor.getSelectionEnd();if(a<0||b<=a){Ui.toast(this,"Select text first");return;}editor.getText().setSpan(new android.text.style.UnderlineSpan(),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);editorDirty=true;}
    private void applyHeading(float scale){if(editor==null)return;int a=editor.getSelectionStart(),b=editor.getSelectionEnd();if(a<0||b<=a){Ui.toast(this,"Select heading text first");return;}editor.getText().setSpan(new StyleSpan(Typeface.BOLD),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);editor.getText().setSpan(new android.text.style.RelativeSizeSpan(scale),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);editorDirty=true;}
    private void insertBullet(){if(editor==null)return;int p=Math.max(0,editor.getSelectionStart());Editable text=editor.getText();int line=p;while(line>0&&text.charAt(line-1)!='\n')line--;text.insert(line,"• ");editor.setSelection(Math.min(text.length(),p+2));editorDirty=true;}
    private void findReplace(){if(editor==null)return;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText find=new EditText(this);find.setSingleLine(true);find.setHint("Find text");EditText replace=new EditText(this);replace.setSingleLine(true);replace.setHint("Replace with");box.addView(find);box.addView(replace);new AlertDialog.Builder(this).setTitle("Find and replace").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Replace all",(d,w)->{String q=find.getText().toString();if(q.isEmpty())return;String before=editor.getText().toString();String after=before.replace(q,replace.getText().toString());if(!before.equals(after)){editor.setText(after);editorDirty=true;}}).show();}
    private void saveCurrent(){
        if(currentFile==null||editor==null)return;File source=currentFile;String ext=ToxiFiles360Engine.extension(source.getName());CharSequence value=new SpannableString(editor.getText());
        if(!ToxiFiles360Engine.editable(source.getName())){Ui.toast(this,"This format is view-only");return;}
        File target=editedWorkspacePaths.contains(source.getAbsolutePath())?source:ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),stripExt(source.getName())+"_edited."+((ext.equals("xls")||ext.equals("csv")||ext.equals("tsv"))?"xlsx":ext));
        final File out=target;runIo(()->{ToxiFiles360Storage.atomicWrite(getFilesDir(),out,tmp->{String e=ToxiFiles360Engine.extension(out.getName());if(e.equals("docx"))ToxiFiles360Engine.writeWord(tmp,value);else if(e.equals("xlsx")){List<ToxiFiles360Engine.SheetData> data=ToxiFiles360Engine.readSheets(source);ToxiFiles360Engine.SheetData sh=data.isEmpty()?new ToxiFiles360Engine.SheetData("Sheet1"):data.get(0);sh.cells.clear();for(ArrayList<String> row:ToxiFiles360Engine.parseDelimited(value.toString(),'\t'))sh.cells.add(row);ToxiFiles360Engine.writeWorkbook(tmp,data);}else if(e.equals("csv")||e.equals("tsv"))writeText(tmp,value.toString());else if(e.equals("rtf"))writeText(tmp,ToxiFiles360Engine.exportRtf(value.toString()));else writeText(tmp,value.toString());});return out;},f->{currentFile=f;editedWorkspacePaths.add(f.getAbsolutePath());editorDirty=false;Ui.toast(this,"Saved safely");openEditor(f);});
    }
    private void openSpreadsheet(File f){
        runIo(()->ToxiFiles360Engine.readSheets(f),data->{sheets=data;spreadsheetDirty=false;if(sheets.isEmpty())sheets.add(new ToxiFiles360Engine.SheetData("Sheet1"));sheetIndex=0;renderSpreadsheet();});
    }
    private void renderSpreadsheet(){
        root.removeAllViews();addHeader(currentFile.getName(),"SPREADSHEET GRID  •  FORMULAS ON SAVE",false);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);Spinner selector=new Spinner(this);ArrayList<String> names=new ArrayList<>();for(ToxiFiles360Engine.SheetData s:sheets)names.add(s.name);
        selector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));selector.setSelection(Math.min(sheetIndex,names.size()-1));selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(pos!=sheetIndex){sheetIndex=pos;renderSpreadsheet();}}});
        toolbar.addView(selector,new LinearLayout.LayoutParams(0,Ui.dp(46),1));addSmall(toolbar,"+ Sheet",this::addSheet);addSmall(toolbar,"Save",this::saveSpreadsheet);root.addView(toolbar);
        ToxiFiles360Engine.FormatInfo workbookInfo=ToxiFiles360Engine.format(currentFile.getName());String workbookNotice=(workbookInfo==null?"":workbookInfo.details);if(Arrays.asList("xlsm","xltm").contains(ToxiFiles360Engine.extension(currentFile.getName())))workbookNotice+=" Macro content is never executed or copied; saving creates a macro-free XLSX copy.";else if(Arrays.asList("xls","xlt").contains(ToxiFiles360Engine.extension(currentFile.getName())))workbookNotice+=" Saving creates an XLSX copy; unsupported legacy formatting and embedded objects may not survive.";TextView formula=Ui.tv(this,"Enter values or formulas beginning with = (for example =SUM(A1:A4)). Apache POI evaluates supported formulas on save. "+workbookNotice,11,Ui.SUB,false);formula.setPadding(Ui.dp(10),Ui.dp(8),Ui.dp(10),Ui.dp(8));root.addView(formula);
        HorizontalScrollView horizontal=new HorizontalScrollView(this);GridLayout grid=new GridLayout(this);grid.setColumnCount(8);ToxiFiles360Engine.SheetData sheet=sheets.get(sheetIndex);
        for(int r=0;r<24;r++)for(int c=0;c<8;c++){final int rr=r,cc=c;EditText cell=new EditText(this);cell.setSingleLine(true);cell.setTextSize(12);cell.setTextColor(Ui.WHITE);cell.setPadding(Ui.dp(5),0,Ui.dp(5),0);cell.setBackground(Ui.glass(5,false));cell.setText(sheet.get(r,c));cell.setSelectAllOnFocus(true);cell.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){sheet.set(rr,cc,s.toString());spreadsheetDirty=true;}public void afterTextChanged(android.text.Editable e){}});
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(r),GridLayout.spec(c));lp.width=Ui.dp(94);lp.height=Ui.dp(40);lp.setMargins(Ui.dp(1),Ui.dp(1),Ui.dp(1),Ui.dp(1));grid.addView(cell,lp);}
        horizontal.addView(grid);ScrollView vertical=new ScrollView(this);vertical.addView(horizontal);root.addView(vertical,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void addSheet(){EditText e=new EditText(this);e.setSingleLine(true);e.setHint("Sheet name");new AlertDialog.Builder(this).setTitle("Add worksheet").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Add",(d,w)->{sheets.add(new ToxiFiles360Engine.SheetData(e.getText().toString()));sheetIndex=sheets.size()-1;spreadsheetDirty=true;renderSpreadsheet();}).show();}
    private void saveSpreadsheet(){File src=currentFile;ArrayList<ToxiFiles360Engine.SheetData> snapshot=copySheets(sheets);File out=editedWorkspacePaths.contains(src.getAbsolutePath())?src:ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),stripExt(src.getName())+"_edited.xlsx");runIo(()->{ToxiFiles360Storage.atomicWrite(getFilesDir(),out,tmp->ToxiFiles360Engine.writeWorkbook(tmp,snapshot));return out;},f->{currentFile=f;editedWorkspacePaths.add(f.getAbsolutePath());spreadsheetDirty=false;Ui.toast(this,"Workbook saved safely");});}
     private ArrayList<ToxiFiles360Engine.SheetData> copySheets(List<ToxiFiles360Engine.SheetData> source){ArrayList<ToxiFiles360Engine.SheetData> copy=new ArrayList<>();if(source!=null)for(ToxiFiles360Engine.SheetData sheet:source){ToxiFiles360Engine.SheetData next=new ToxiFiles360Engine.SheetData(sheet.name);for(ArrayList<String> row:sheet.cells)next.cells.add(new ArrayList<>(row));copy.add(next);}return copy;}
    private void openSlides(File f){runIo(()->ToxiFiles360Engine.readPresentation(f),data->{presentation=data;slideIndex=0;slideDirty=false;renderSlides();});}
    private void renderSlides(){
        root.removeAllViews();addHeader(currentFile.getName(),"SLIDE TEXT EDITOR  •  ORIGINAL LAYOUT PRESERVED",false);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        addSmall(bar,"‹",()->{if(slideDirty){new AlertDialog.Builder(this).setMessage("Save slide changes before moving?").setNegativeButton("Stay",null).setNeutralButton("Discard",(d,w)->{slideDirty=false;if(slideIndex>0){slideIndex--;renderSlides();}}).setPositiveButton("Save",(d,w)->saveSlideText()).show();}else if(slideIndex>0){slideIndex--;renderSlides();}});
        addSmall(bar,(slideIndex+1)+" / "+presentation.slides.size(),()->{});
        addSmall(bar,"›",()->{if(slideDirty){new AlertDialog.Builder(this).setMessage("Save slide changes before moving?").setNegativeButton("Stay",null).setNeutralButton("Discard",(d,w)->{slideDirty=false;if(slideIndex+1<presentation.slides.size()){slideIndex++;renderSlides();}}).setPositiveButton("Save",(d,w)->saveSlideText()).show();}else if(slideIndex+1<presentation.slides.size()){slideIndex++;renderSlides();}});
        addSmall(bar,"Save copy",this::saveSlideText);root.addView(bar);
        ScrollView scroll=new ScrollView(this);slideEditor=new EditText(this);slideEditor.setText(presentation.slides.get(slideIndex));slideEditor.setTextSize(16);slideEditor.setTextColor(Ui.WHITE);slideEditor.setHintTextColor(Ui.SUB);slideEditor.setGravity(Gravity.TOP|Gravity.START);slideEditor.setMinLines(12);slideEditor.setPadding(Ui.dp(18),Ui.dp(18),Ui.dp(18),Ui.dp(18));slideEditor.setBackground(Ui.glass(18,true));slideEditor.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){slideDirty=true;}public void afterTextChanged(Editable e){}});
        scroll.addView(slideEditor,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView level=Ui.tv(this,"Text in the selected slide can be edited. Existing theme, shapes, images and relationships are retained; new text is placed in the first existing text run. Advanced layout and animation editing are not supported.",11,Ui.SUB,false);level.setPadding(Ui.dp(12),Ui.dp(8),Ui.dp(12),Ui.dp(10));root.addView(level);
    }
    private void saveSlideText(){if(currentFile==null||slideEditor==null||presentation==null)return;File src=currentFile;int index=slideIndex;String value=slideEditor.getText().toString();File out=editedWorkspacePaths.contains(src.getAbsolutePath())?src:ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),stripExt(src.getName())+"_edited.pptx");runIo(()->{ToxiFiles360Storage.atomicWrite(getFilesDir(),out,tmp->ToxiFiles360Engine.writePresentationText(src,tmp,index,value));return out;},f->{currentFile=f;editedWorkspacePaths.add(f.getAbsolutePath());slideDirty=false;openSlides(f);Ui.toast(this,"Presentation text saved to a copy");});}
    private void createPdf(File out,String text)throws Exception{
        PdfDocument pdf=new PdfDocument();android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);paint.setTextSize(12);paint.setColor(Color.BLACK);int width=612,height=792,margin=42,lineH=18;String[] lines=(text==null?"":text).split("\\n",-1);int pageNo=1,y=margin;
        PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(width,height,pageNo).create());android.graphics.Canvas canvas=page.getCanvas();
        for(String line:lines){String remaining=line;do{int chars=paint.breakText(remaining,true,width-2*margin,null);if(chars<=0)chars=Math.min(remaining.length(),1);canvas.drawText(remaining.substring(0,chars),margin,y,paint);remaining=remaining.substring(chars);y+=lineH;if(y>height-margin){pdf.finishPage(page);page=pdf.startPage(new PdfDocument.PageInfo.Builder(width,height,++pageNo).create());canvas=page.getCanvas();y=margin;}}while(!remaining.isEmpty());}
        pdf.finishPage(page);try(OutputStream os=new FileOutputStream(out)){pdf.writeTo(os);}finally{pdf.close();}
    }
    private void createPdfFromEditor(){if(editor==null)return;String content=editor.getText().toString();File out=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),"Note-export.pdf");runIo(()->{ToxiFiles360Storage.atomicWrite(getFilesDir(),out,tmp->createPdf(tmp,content));return out;},f->Ui.toast(this,"PDF created"));}
    private void annotatePdfDialog(File source){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText page=new EditText(this);page.setSingleLine(true);page.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);page.setHint("Page number (starting at 1)");page.setText("1");EditText note=new EditText(this);note.setHint("Annotation text");note.setMinLines(2);box.addView(page);box.addView(note);new AlertDialog.Builder(this).setTitle("Annotate a PDF copy").setMessage("Adds a visible note to one page. The exported PDF is flattened; the original is preserved.").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Create annotated copy",(d,w)->{int n;try{n=Integer.parseInt(page.getText().toString().trim())-1;}catch(Exception e){Ui.toast(this,"Enter a valid page number");return;}String text=note.getText().toString().trim();if(text.isEmpty()){Ui.toast(this,"Enter annotation text");return;}if(text.length()>420){Ui.toast(this,"Annotation is limited to 420 characters so it fits on the page");return;}File out=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),stripExt(source.getName())+"_annotated.pdf");runIo(()->{ToxiFiles360Storage.atomicWrite(getFilesDir(),out,tmp->annotatePdf(source,tmp,n,text));return out;},f->{Ui.toast(this,"Annotated PDF copy created");openFile(f);});}).show();}
    private void annotatePdf(File source,File output,int selectedPage,String note)throws Exception{if(source.length()>100L*1024*1024)throw new IOException("PDF exceeds the 100 MB annotation limit");try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd);PdfDocument result=new PdfDocument()){int count=renderer.getPageCount();if(count<1||count>100)throw new IOException("PDF must contain between 1 and 100 pages for annotation");if(selectedPage<0||selectedPage>=count)throw new IOException("Page number is outside this PDF");android.graphics.Paint notePaint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);notePaint.setColor(Color.BLACK);notePaint.setTextSize(10f);for(int i=0;i<count;i++)try(PdfRenderer.Page page=renderer.openPage(i)){int width=page.getWidth(),height=page.getHeight();if(width<1||height<1||(long)width*height>12000000L)throw new IOException("PDF page dimensions exceed the safe rendering limit");float scale=Math.min(2f,(float)Math.sqrt(6000000d/((double)width*height)));int bw=Math.max(1,Math.round(width*scale)),bh=Math.max(1,Math.round(height*scale));Bitmap bitmap=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);try{page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);PdfDocument.Page dest=result.startPage(new PdfDocument.PageInfo.Builder(width,height,i+1).create());android.graphics.Canvas canvas=dest.getCanvas();canvas.drawBitmap(bitmap,null,new Rect(0,0,width,height),null);if(i==selectedPage){float boxH=Math.min(110f,Math.max(58f,16f+(note.length()/75f)*13f));android.graphics.Paint bg=new android.graphics.Paint();bg.setColor(Color.argb(232,255,250,210));canvas.drawRect(0,height-boxH,width,height,bg);String remaining=note;float y=height-boxH+18f;while(!remaining.isEmpty()&&y<height-8f){int chars=notePaint.breakText(remaining,true,width-24f,null);if(chars<=0)break;canvas.drawText(remaining.substring(0,chars),12f,y,notePaint);remaining=remaining.substring(chars);y+=13f;}}result.finishPage(dest);}finally{bitmap.recycle();}}try(OutputStream os=new FileOutputStream(output)){result.writeTo(os);os.flush();}}}
    private void createPdfFromScans(){File[] all=ToxiFiles360Storage.scans(getFilesDir()).listFiles();ArrayList<File> images=new ArrayList<>();if(all!=null)for(File f:all)if(f.isFile()&&Arrays.asList("jpg","jpeg","png","webp").contains(ToxiFiles360Engine.extension(f.getName())))images.add(f);images.sort(Comparator.comparingLong(File::lastModified));if(images.isEmpty()){Ui.toast(this,"No scan images found");return;}if(images.size()>50){Ui.toast(this,"Create PDFs from at most 50 scans at a time");return;}File out=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),"Scans-"+System.currentTimeMillis()+".pdf");runIo(()->{ToxiFiles360Storage.atomicWrite(getFilesDir(),out,tmp->{PdfDocument pdf=new PdfDocument();try{android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG|android.graphics.Paint.FILTER_BITMAP_FLAG);int pageNo=0;for(File image:images){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(image.getAbsolutePath(),bounds);if(bounds.outWidth<1||bounds.outHeight<1)continue;int sample=1;while(bounds.outWidth/sample>1800||bounds.outHeight/sample>1800)sample*=2;BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=sample;opt.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap bitmap=BitmapFactory.decodeFile(image.getAbsolutePath(),opt);if(bitmap==null)continue;try{PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(612,792,++pageNo).create());float factor=Math.min(564f/bitmap.getWidth(),744f/bitmap.getHeight());float w=bitmap.getWidth()*factor,h=bitmap.getHeight()*factor;float left=(612f-w)/2f,top=(792f-h)/2f;page.getCanvas().drawBitmap(bitmap,null,new RectF(left,top,left+w,top+h),paint);pdf.finishPage(page);}finally{bitmap.recycle();}}if(pageNo==0)throw new IOException("No readable scan images found");try(OutputStream os=new FileOutputStream(tmp)){pdf.writeTo(os);}}finally{pdf.close();}});return out;},f->{Ui.toast(this,"Multi-page scan PDF created");openFile(f);});}

    private void captureScan(){
        try{File dir=ToxiFiles360Storage.scans(getFilesDir());scanFile=new File(dir,"scan-"+System.currentTimeMillis()+".jpg");Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",scanFile);Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);camera.putExtra(MediaStore.EXTRA_OUTPUT,uri);camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(camera,REQ_CAMERA);}catch(ActivityNotFoundException e){Ui.toast(this,"No camera app is available");}catch(Exception e){Ui.toast(this,"Camera could not be opened");}
    }
    private String ocr(File image)throws Exception{
        File root=ToxiFiles360Storage.root(getFilesDir());File tess=new File(root,"tessdata");if(!tess.exists()&&!tess.mkdirs())throw new IOException("Cannot create OCR data folder");File trained=new File(tess,"eng.traineddata");
        if(!trained.isFile()||trained.length()!=4113088L||!"7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2".equals(ToxiFiles360Storage.sha256(trained))){if(trained.exists())trained.delete();ToxiFiles360Storage.atomicWrite(getFilesDir(),trained,tmp->{try(OutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[32768];for(int part=0;part<8;part++){String asset=String.format(Locale.ROOT,"toxifiles360/tessdata/eng.traineddata.part%02d",part);try(InputStream in=getAssets().open(asset)){int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}out.flush();}});}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(image.getAbsolutePath(),bounds);int sample=1;while(bounds.outWidth/sample>2400||bounds.outHeight/sample>2400)sample*=2;BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=sample;opt.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap bitmap=BitmapFactory.decodeFile(image.getAbsolutePath(),opt);if(bitmap==null)throw new IOException("Image could not be decoded");
        TessBaseAPI api=new TessBaseAPI();try{if(!api.init(root.getAbsolutePath(),"eng"))throw new IOException("Offline English OCR model could not initialize");api.setImage(bitmap);String result=api.getUTF8Text();return result==null?"":result.trim();}finally{api.recycle();bitmap.recycle();}
    }
    private void chooseConversion(){if(currentFile==null){Ui.toast(this,"Open a document first");return;}String ext=ToxiFiles360Engine.extension(currentFile.getName());ToxiFiles360Engine.FormatInfo info=ToxiFiles360Engine.format(currentFile.getName());if(ext.equals("pdf")||info==null||info.level==ToxiFiles360Engine.Level.UNSUPPORTED){Ui.toast(this,"No verified converter is available for this format");return;}boolean tabular="Excel".equals(info.category)&&Arrays.asList("xlsx","xls","csv","tsv").contains(ext);String[] opts=tabular?new String[]{"CSV","XLSX","TXT"}:"PowerPoint".equals(info.category)?new String[]{"TXT","PDF"}:"Excel".equals(info.category)?new String[]{"TXT"}:new String[]{"TXT","DOCX","PDF"};new AlertDialog.Builder(this).setTitle("Convert a copy").setItems(opts,(d,w)->convertTo(opts[w].toLowerCase(Locale.ROOT))).show();}
    private void convertTo(String targetExt){
        File src=currentFile;File out=ToxiFiles360Storage.unique(ToxiFiles360Storage.documents(getFilesDir()),stripExt(src.getName())+"_converted."+targetExt);
        runIo(()->{String text=ToxiFiles360Engine.extractText(src);ToxiFiles360Storage.atomicWrite(getFilesDir(),out,tmp->{if(targetExt.equals("txt"))writeText(tmp,text);else if(targetExt.equals("docx"))ToxiFiles360Engine.writeWord(tmp,text);else if(targetExt.equals("pdf"))createPdf(tmp,text);else if(targetExt.equals("csv")){List<ToxiFiles360Engine.SheetData> data=ToxiFiles360Engine.readSheets(src);if(data.isEmpty())throw new IOException("No worksheet data to convert");writeText(tmp,ToxiFiles360Engine.toDelimited(data.get(0).cells,','));}else if(targetExt.equals("xlsx")){List<ToxiFiles360Engine.SheetData> data=ToxiFiles360Engine.readSheets(src);ToxiFiles360Engine.writeWorkbook(tmp,data);}});return out;},f->Ui.toast(this,"Converted copy saved"));
    }
    private void searchDocuments(){
        EditText q=new EditText(this);q.setSingleLine(true);q.setHint("Search filenames and readable text");new AlertDialog.Builder(this).setTitle("Search workspace").setView(q).setNegativeButton("Cancel",null).setPositiveButton("Search",(d,w)->{
            String term=q.getText().toString().trim().toLowerCase(Locale.ROOT);if(term.isEmpty())return;runIo(()->{ArrayList<File> found=new ArrayList<>();File[] fs=ToxiFiles360Storage.documents(getFilesDir()).listFiles();if(fs!=null)for(File f:fs)if(f.isFile()){if(f.getName().toLowerCase(Locale.ROOT).contains(term)){found.add(f);continue;}try{if(f.length()<3*1024*1024&&ToxiFiles360Engine.extractText(f).toLowerCase(Locale.ROOT).contains(term))found.add(f);}catch(Exception ignored){}}return found;},found->{openSection("Smart Collections");for(File f:found)addFileRow(f);if(found.isEmpty())Ui.toast(this,"No matches");});
        }).show();
    }
    private void showCollectionFilters(){
        addTextButton("Favorites",()->showFavorites());addTextButton("Recently modified",()->showRecent());addTextButton("Spreadsheets",()->openSection("Excel Spreadsheets"));addTextButton("Presentations",()->openSection("PowerPoint"));addTextButton("PDF documents",()->openSection("PDF Workspace"));addTextButton("Large files over 10 MB",()->{File[] fs=ToxiFiles360Storage.documents(getFilesDir()).listFiles();if(fs!=null)for(File f:fs)if(f.isFile()&&f.length()>10L*1024*1024)addFileRow(f);});
    }
    private void showFavorites(){Set<String> fav=getSharedPreferences("toxifiles360",MODE_PRIVATE).getStringSet("favorites",Collections.emptySet());if(body!=null){body.removeAllViews();}File[] fs=ToxiFiles360Storage.documents(getFilesDir()).listFiles();int n=0;if(fs!=null)for(File f:fs)if(f.isFile()&&fav.contains(f.getAbsolutePath())){addFileRow(f);n++;}if(n==0)addTextButton("No favorites yet. Use a file menu to add one.",()->{});}
    private void showRecent(){body.removeAllViews();File[] fs=ToxiFiles360Storage.documents(getFilesDir()).listFiles();if(fs!=null){Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));int n=0;for(File f:fs)if(f.isFile()&&n++<20)addFileRow(f);}}
    private void toggleFavorite(File f){Set<String> old=new HashSet<>(getSharedPreferences("toxifiles360",MODE_PRIVATE).getStringSet("favorites",Collections.emptySet()));if(!old.add(f.getAbsolutePath()))old.remove(f.getAbsolutePath());getSharedPreferences("toxifiles360",MODE_PRIVATE).edit().putStringSet("favorites",old).apply();Ui.toast(this,old.contains(f.getAbsolutePath())?"Added to Favorites":"Removed from Favorites");}
    private void chooseHistoryFile(){File[] fs=ToxiFiles360Storage.documents(getFilesDir()).listFiles();showFilePicker(fs,"Version history",f->showHistory(f));}
    private void showHistory(File f){List<File> versions=ToxiFiles360Storage.history(getFilesDir(),f);if(versions.isEmpty()){Ui.toast(this,"No saved versions yet");return;}String[] names=new String[versions.size()];for(int i=0;i<names.length;i++)names[i]=versions.get(i).getName();new AlertDialog.Builder(this).setTitle("Restore version").setItems(names,(d,w)->new AlertDialog.Builder(this).setMessage("Restore this earlier copy? The current version will also be preserved.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(dd,ww)->runIo(()->{ToxiFiles360Storage.restore(getFilesDir(),versions.get(w),f);return true;},ok->Ui.toast(this,"Version restored"))).show()).show();}
    private void chooseAnnotationFile(){File[] fs=ToxiFiles360Storage.documents(getFilesDir()).listFiles();ArrayList<File> pdfs=new ArrayList<>();if(fs!=null)for(File f:fs)if(f.isFile()&&"pdf".equals(ToxiFiles360Engine.extension(f.getName())))pdfs.add(f);showFilePicker(pdfs.toArray(new File[0]),"Annotate PDF",this::annotatePdfDialog);}private void chooseInspectorFile(){showFilePicker(ToxiFiles360Storage.documents(getFilesDir()).listFiles(),"Inspect document",this::inspect);}
    private void showFilePicker(File[] fs,String title,Consumer<File> selected){if(fs==null)fs=new File[0];ArrayList<File> files=new ArrayList<>();for(File f:fs)if(f.isFile())files.add(f);String[] names=new String[files.size()];for(int i=0;i<names.length;i++)names[i]=files.get(i).getName();new AlertDialog.Builder(this).setTitle(title).setItems(names,(d,w)->selected.accept(files.get(w))).show();}
    private void inspect(File f){runIo(()->{ToxiFiles360Engine.FormatInfo info=ToxiFiles360Engine.format(f.getName());String hash=ToxiFiles360Storage.sha256(f);return "Name: "+f.getName()+"\nFormat: "+(info==null?"Unknown":info.label)+"\nCompatibility: "+(info==null?"UNSUPPORTED":info.level)+"\nDetails: "+(info==null?"No registered handler":info.details)+"\nSize: "+f.length()+" bytes\nModified: "+new Date(f.lastModified())+"\nSHA-256: "+hash;},text->new AlertDialog.Builder(this).setTitle("Document Inspector").setMessage(text).setPositiveButton("Close",null).show());}
    private void exportFile(File f){if(f==null||!f.isFile())return;pendingExport=f;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime(f));i.putExtra(Intent.EXTRA_TITLE,f.getName());startActivityForResult(i,REQ_EXPORT);}
    private String mime(File f){String e=ToxiFiles360Engine.extension(f.getName());if(e.equals("pdf"))return "application/pdf";if(e.equals("docx"))return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";if(e.equals("xlsx"))return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";if(e.equals("pptx"))return "application/vnd.openxmlformats-officedocument.presentationml.presentation";if(e.equals("csv"))return "text/csv";if(e.equals("txt"))return "text/plain";return "application/octet-stream";}
    private void addTextButton(String label,Runnable action){TextView t=Ui.tv(this,label,13,Ui.WHITE,true);t.setPadding(Ui.dp(12),Ui.dp(14),Ui.dp(12),Ui.dp(14));t.setBackground(Ui.glass(14,false));t.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(6);body.addView(t,lp);}
    private void addIconTextButton(String label,String asset,Runnable action){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(12),Ui.dp(10),Ui.dp(12),Ui.dp(10));row.setBackground(Ui.glass(14,false));ImageView ic=icon(asset);row.addView(ic,new LinearLayout.LayoutParams(Ui.dp(26),Ui.dp(26)));TextView tx=Ui.tv(this,label,13,Ui.WHITE,true);tx.setPadding(Ui.dp(10),0,0,0);row.addView(tx);row.setOnClickListener(v->action.run());Ui.press(row);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(6);body.addView(row,lp);}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);return b;}
    private void addSmall(LinearLayout row,String text,Runnable action){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER);item.setPadding(Ui.dp(7),Ui.dp(4),Ui.dp(7),Ui.dp(4));item.setBackground(Ui.glass(10,true));
        String asset=null;
        if(text.equals("Save"))asset="save";else if(text.equals("PDF"))asset="pdf";else if(text.equals("Export"))asset="export";else if(text.equals("Convert"))asset="convert";else if(text.equals("B"))asset="bold";else if(text.equals("I"))asset="italic";else if(text.equals("U"))asset="underline";else if(text.equals("H1")||text.equals("H2"))asset="heading";else if(text.equals("•"))asset="bullet";else if(text.equals("Find"))asset="find-replace";else if(text.equals("Save copy"))asset="slide-edit";else if(text.equals("+ Sheet"))asset="add-sheet";else if(text.equals("Delete"))asset="delete";else if(text.equals("Duplicate"))asset="duplicate";else if(text.equals("Inspect"))asset="inspector";
        if(asset!=null){ImageView ic=icon(asset);item.addView(ic,new LinearLayout.LayoutParams(Ui.dp(18),Ui.dp(18)));}
        TextView label=Ui.tv(this,text,12,Ui.WHITE,true);if(asset!=null)label.setPadding(Ui.dp(4),0,0,0);item.addView(label);
        item.setOnClickListener(v->action.run());Ui.press(item);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,Ui.dp(42));lp.setMargins(Ui.dp(3),Ui.dp(3),Ui.dp(3),Ui.dp(3));row.addView(item,lp);
    }
    private void writeText(File f,String text)throws IOException{try(OutputStream out=new FileOutputStream(f)){out.write((text==null?"":text).getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();}}
    private void writeTextAtomically(File f,String text)throws Exception{ToxiFiles360Storage.atomicWrite(getFilesDir(),f,tmp->writeText(tmp,text));}
    private String stripExt(String n){int i=n.lastIndexOf('.');return i>0?n.substring(0,i):n;}
    private <T> void runIo(Callable<T> task,Consumer<T> success){
        io.execute(()->{try{T result=task.call();main.post(()->{if(!isFinishing()&&!isDestroyed())success.accept(result);});}catch(Throwable e){main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("ToxiFiles 360").setMessage(safeMessage(e)).setPositiveButton("OK",null).show();});}});
    }
    private String safeMessage(Throwable e){String m=e.getMessage();if(m==null||m.trim().isEmpty())m="The document could not be processed.";return m.length()>240?m.substring(0,240):m;}
}
