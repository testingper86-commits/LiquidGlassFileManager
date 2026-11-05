package com.music.echo.offline.files;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;



import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class OfficeDocumentActivity extends Activity {
    private static final int REQ_IMPORT=4101, REQ_EXPORT=4102;
    static final String EXTRA_FILE_PATH = "office_document_path";
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"office-document-io");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private LinearLayout root;
    private File currentFile,pendingExport;
    private boolean editorDirty,spreadsheetDirty,slideDirty;
    private final Set<String> editedWorkspacePaths=new HashSet<>();
    private EditText editor,slideEditor;
    private ArrayList<OfficeDocumentEngine.SheetData> sheets;
    private int sheetIndex,slideIndex;
    private OfficeDocumentEngine.PresentationData presentation;
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());

@Override protected void onCreate(Bundle state){
        super.onCreate(state);
        Ui.init(this);
        OfficeDocumentStorage.documents(getFilesDir());
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.bg);
        setContentView(root);
        handleIncomingIntent(getIntent());
    }
@Override public void onBackPressed(){
        if(editorDirty||spreadsheetDirty||slideDirty){
            new AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save your changes before leaving this document?")
                .setNegativeButton("Keep editing",null).setNeutralButton("Discard",(d,w)->finish())
                .setPositiveButton("Save",(d,w)->{if(editorDirty)saveCurrent();else if(spreadsheetDirty)saveSpreadsheet();else if(slideDirty)saveSlideText();}).show();
            return;
        }
        super.onBackPressed();
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private void addHeader(String title,String subtitle,boolean home){
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);h.setPadding(Ui.dp(12),Ui.dp(8),Ui.dp(12),Ui.dp(8));h.setBackground(Ui.glass(18,true));
        if(!home){TextView back=Ui.tv(this,"‹",32,Ui.WHITE,true);back.setGravity(Gravity.CENTER);h.addView(back,new LinearLayout.LayoutParams(Ui.dp(44),Ui.dp(48)));back.setOnClickListener(v->onBackPressed());}
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(Ui.tv(this,title,19,Ui.WHITE,true));TextView sub=Ui.tv(this,subtitle,9.5f,Ui.accent,true);sub.setLetterSpacing(.13f);labels.addView(sub);
        h.addView(labels,new LinearLayout.LayoutParams(0,-2,1));root.addView(h,new LinearLayout.LayoutParams(-1,-2));
    }
private ImageView icon(String name){
        ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageDrawable(Glyphs.get(Glyphs.DOC,Ui.accent));v.setContentDescription(name);return v;
    }
private void importDocument(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_IMPORT);}
    private String displayName(Uri uri){String n=null;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())n=c.getString(0);}catch(Exception ignored){}if((n==null||n.trim().isEmpty())&&uri!=null)n=uri.getLastPathSegment();
        if((n==null||!n.contains("."))&&uri!=null){String type=getContentResolver().getType(uri);if(type!=null){if(type.equals("application/pdf"))n="imported.pdf";else if(type.equals("application/msword"))n="imported.doc";else if(type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))n="imported.docx";else if(type.equals("application/vnd.ms-excel"))n="imported.xls";else if(type.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))n="imported.xlsx";else if(type.equals("application/vnd.ms-powerpoint"))n="imported.ppt";else if(type.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation"))n="imported.pptx";else if(type.equals("text/csv"))n="imported.csv";else if(type.equals("text/tab-separated-values"))n="imported.tsv";}}
        return OfficeDocumentStorage.safeName(n==null?"imported-document":n);}
@Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        Uri uri=data.getData();
        if(request==REQ_IMPORT&&uri!=null){
            runIo(()->copyIncoming(uri),this::openFile);
        }else if(request==REQ_EXPORT&&uri!=null&&pendingExport!=null){
            File source=pendingExport;pendingExport=null;
            runIo(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new IOException("Destination is unavailable");byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.flush();
            }return Boolean.TRUE;},ok->Ui.toast(this,"Exported document"));
        }
    }
private void openFile(File f){
        if(f==null||!f.isFile()){Ui.toast(this,"The selected document is unavailable");finish();return;}
        String ext=OfficeDocumentEngine.extension(f.getName());
        if(!isSupportedOfficeFile(ext)){Ui.toast(this,"Unsupported Office or PDF file type");finish();return;}
        currentFile=f;
        if("pdf".equals(ext)){openPdfWorkspace(f);return;}
        openEditor(f);
    }
private void openEditor(File f){
        currentFile=f;editorDirty=false;spreadsheetDirty=false;slideDirty=false;root.removeAllViews();
        String ext=OfficeDocumentEngine.extension(f.getName());OfficeDocumentEngine.FormatInfo info=OfficeDocumentEngine.format(f.getName());
        if("xlsx".equals(ext)||"xls".equals(ext)||"csv".equals(ext)||"tsv".equals(ext)){openSpreadsheet(f);return;}
        if("pptx".equals(ext)){openSlides(f);return;}
        addHeader(f.getName(),"ppt".equals(ext)?"POWERPOINT DOCUMENT  •  READ ONLY":"WORD DOCUMENT  •  OFFLINE",false);
        boolean canEdit=OfficeDocumentEngine.editable(f.getName());
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setPadding(Ui.dp(8),Ui.dp(6),Ui.dp(8),Ui.dp(6));
        if(canEdit){addSmall(controls,"B",()->applyStyle(Typeface.BOLD));addSmall(controls,"I",()->applyStyle(Typeface.ITALIC));addSmall(controls,"U",this::applyUnderline);addSmall(controls,"H1",()->applyHeading(1.6f));addSmall(controls,"H2",()->applyHeading(1.3f));addSmall(controls,"•",this::insertBullet);addSmall(controls,"Find",this::findReplace);addSmall(controls,"Save",this::saveCurrent);}
        addSmall(controls,"Export",()->exportFile(currentFile));root.addView(controls,new LinearLayout.LayoutParams(-1,Ui.dp(52)));
        ScrollView scroll=new ScrollView(this);editor=new EditText(this);editor.setTextSize(15);editor.setTextColor(Ui.WHITE);editor.setHintTextColor(Ui.SUB);editor.setGravity(Gravity.TOP|Gravity.START);editor.setPadding(Ui.dp(14),Ui.dp(12),Ui.dp(14),Ui.dp(24));editor.setBackground(Ui.glass(16,false));editor.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));editor.setTextIsSelectable(true);editor.setEnabled(false);
        editor.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(editor.isEnabled())editorDirty=true;}public void afterTextChanged(android.text.Editable e){}});
        runIo(()->OfficeDocumentEngine.extractText(f),text->{editor.setText(text);editor.setEnabled(canEdit);});
        scroll.addView(editor,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(info!=null){TextView level=Ui.tv(this,"Compatibility: "+info.level+" — "+info.details,11,Ui.SUB,false);level.setPadding(Ui.dp(12),Ui.dp(8),Ui.dp(12),Ui.dp(10));root.addView(level);}
    }
    private void applyStyle(int style){if(editor==null)return;int a=editor.getSelectionStart(),b=editor.getSelectionEnd();if(a<0||b<=a){Ui.toast(this,"Select text first");return;}Spannable s=editor.getText();s.setSpan(new StyleSpan(style),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);editorDirty=true;}
    private void applyUnderline(){if(editor==null)return;int a=editor.getSelectionStart(),b=editor.getSelectionEnd();if(a<0||b<=a){Ui.toast(this,"Select text first");return;}editor.getText().setSpan(new android.text.style.UnderlineSpan(),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);editorDirty=true;}
    private void applyHeading(float scale){if(editor==null)return;int a=editor.getSelectionStart(),b=editor.getSelectionEnd();if(a<0||b<=a){Ui.toast(this,"Select heading text first");return;}editor.getText().setSpan(new StyleSpan(Typeface.BOLD),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);editor.getText().setSpan(new android.text.style.RelativeSizeSpan(scale),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);editorDirty=true;}
    private void insertBullet(){if(editor==null)return;int p=Math.max(0,editor.getSelectionStart());Editable text=editor.getText();int line=p;while(line>0&&text.charAt(line-1)!='\n')line--;text.insert(line,"• ");editor.setSelection(Math.min(text.length(),p+2));editorDirty=true;}
    private void findReplace(){if(editor==null)return;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText find=new EditText(this);find.setSingleLine(true);find.setHint("Find text");EditText replace=new EditText(this);replace.setSingleLine(true);replace.setHint("Replace with");box.addView(find);box.addView(replace);new AlertDialog.Builder(this).setTitle("Find and replace").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Replace all",(d,w)->{String q=find.getText().toString();if(q.isEmpty())return;String before=editor.getText().toString();String after=before.replace(q,replace.getText().toString());if(!before.equals(after)){editor.setText(after);editorDirty=true;}}).show();}
private void saveCurrent(){
        if(currentFile==null||editor==null)return;
        if(!"docx".equals(OfficeDocumentEngine.extension(currentFile.getName()))){Ui.toast(this,"This Word format is read-only; export a copy instead");return;}
        File source=currentFile;CharSequence value=new SpannableString(editor.getText());
        File target=editedWorkspacePaths.contains(source.getAbsolutePath())?source:OfficeDocumentStorage.unique(OfficeDocumentStorage.documents(getFilesDir()),stripExt(source.getName())+"_edited.docx");
        runIo(()->{OfficeDocumentStorage.atomicWrite(getFilesDir(),target,tmp->OfficeDocumentEngine.writeWord(tmp,value));return target;},f->{currentFile=f;editedWorkspacePaths.add(f.getAbsolutePath());editorDirty=false;Ui.toast(this,"Word document saved");openEditor(f);});
    }
    private void openSpreadsheet(File f){
        runIo(()->OfficeDocumentEngine.readSheets(f),data->{sheets=data;spreadsheetDirty=false;if(sheets.isEmpty())sheets.add(new OfficeDocumentEngine.SheetData("Sheet1"));sheetIndex=0;renderSpreadsheet();});
    }
    private void renderSpreadsheet(){
        root.removeAllViews();addHeader(currentFile.getName(),"SPREADSHEET GRID  •  FORMULAS ON SAVE",false);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);Spinner selector=new Spinner(this);ArrayList<String> names=new ArrayList<>();for(OfficeDocumentEngine.SheetData s:sheets)names.add(s.name);
        selector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));selector.setSelection(Math.min(sheetIndex,names.size()-1));selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(pos!=sheetIndex){sheetIndex=pos;renderSpreadsheet();}}});
        toolbar.addView(selector,new LinearLayout.LayoutParams(0,Ui.dp(46),1));addSmall(toolbar,"+ Sheet",this::addSheet);addSmall(toolbar,"Save",this::saveSpreadsheet);root.addView(toolbar);
        OfficeDocumentEngine.FormatInfo workbookInfo=OfficeDocumentEngine.format(currentFile.getName());String workbookNotice=(workbookInfo==null?"":workbookInfo.details);if(Arrays.asList("xlsm","xltm").contains(OfficeDocumentEngine.extension(currentFile.getName())))workbookNotice+=" Macro content is never executed or copied; saving creates a macro-free XLSX copy.";else if(Arrays.asList("xls","xlt").contains(OfficeDocumentEngine.extension(currentFile.getName())))workbookNotice+=" Saving creates an XLSX copy; unsupported legacy formatting and embedded objects may not survive.";TextView formula=Ui.tv(this,"Enter values or formulas beginning with = (for example =SUM(A1:A4)). Apache POI evaluates supported formulas on save. "+workbookNotice,11,Ui.SUB,false);formula.setPadding(Ui.dp(10),Ui.dp(8),Ui.dp(10),Ui.dp(8));root.addView(formula);
        HorizontalScrollView horizontal=new HorizontalScrollView(this);GridLayout grid=new GridLayout(this);grid.setColumnCount(8);OfficeDocumentEngine.SheetData sheet=sheets.get(sheetIndex);
        for(int r=0;r<24;r++)for(int c=0;c<8;c++){final int rr=r,cc=c;EditText cell=new EditText(this);cell.setSingleLine(true);cell.setTextSize(12);cell.setTextColor(Ui.WHITE);cell.setPadding(Ui.dp(5),0,Ui.dp(5),0);cell.setBackground(Ui.glass(5,false));cell.setText(sheet.get(r,c));cell.setSelectAllOnFocus(true);cell.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){sheet.set(rr,cc,s.toString());spreadsheetDirty=true;}public void afterTextChanged(android.text.Editable e){}});
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(r),GridLayout.spec(c));lp.width=Ui.dp(94);lp.height=Ui.dp(40);lp.setMargins(Ui.dp(1),Ui.dp(1),Ui.dp(1),Ui.dp(1));grid.addView(cell,lp);}
        horizontal.addView(grid);ScrollView vertical=new ScrollView(this);vertical.addView(horizontal);root.addView(vertical,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void addSheet(){EditText e=new EditText(this);e.setSingleLine(true);e.setHint("Sheet name");new AlertDialog.Builder(this).setTitle("Add worksheet").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Add",(d,w)->{sheets.add(new OfficeDocumentEngine.SheetData(e.getText().toString()));sheetIndex=sheets.size()-1;spreadsheetDirty=true;renderSpreadsheet();}).show();}
    private void saveSpreadsheet(){File src=currentFile;ArrayList<OfficeDocumentEngine.SheetData> snapshot=copySheets(sheets);File out=editedWorkspacePaths.contains(src.getAbsolutePath())?src:OfficeDocumentStorage.unique(OfficeDocumentStorage.documents(getFilesDir()),stripExt(src.getName())+"_edited.xlsx");runIo(()->{OfficeDocumentStorage.atomicWrite(getFilesDir(),out,tmp->OfficeDocumentEngine.writeWorkbook(tmp,snapshot));return out;},f->{currentFile=f;editedWorkspacePaths.add(f.getAbsolutePath());spreadsheetDirty=false;Ui.toast(this,"Workbook saved safely");});}
     private ArrayList<OfficeDocumentEngine.SheetData> copySheets(List<OfficeDocumentEngine.SheetData> source){ArrayList<OfficeDocumentEngine.SheetData> copy=new ArrayList<>();if(source!=null)for(OfficeDocumentEngine.SheetData sheet:source){OfficeDocumentEngine.SheetData next=new OfficeDocumentEngine.SheetData(sheet.name);for(ArrayList<String> row:sheet.cells)next.cells.add(new ArrayList<>(row));copy.add(next);}return copy;}
    private void openSlides(File f){runIo(()->OfficeDocumentEngine.readPresentation(f),data->{presentation=data;slideIndex=0;slideDirty=false;renderSlides();});}
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
    private void saveSlideText(){if(currentFile==null||slideEditor==null||presentation==null)return;File src=currentFile;int index=slideIndex;String value=slideEditor.getText().toString();File out=editedWorkspacePaths.contains(src.getAbsolutePath())?src:OfficeDocumentStorage.unique(OfficeDocumentStorage.documents(getFilesDir()),stripExt(src.getName())+"_edited.pptx");runIo(()->{OfficeDocumentStorage.atomicWrite(getFilesDir(),out,tmp->OfficeDocumentEngine.writePresentationText(src,tmp,index,value));return out;},f->{currentFile=f;editedWorkspacePaths.add(f.getAbsolutePath());slideDirty=false;openSlides(f);Ui.toast(this,"Presentation text saved to a copy");});}
    private void annotatePdfDialog(File source){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText page=new EditText(this);page.setSingleLine(true);page.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);page.setHint("Page number (starting at 1)");page.setText("1");EditText note=new EditText(this);note.setHint("Annotation text");note.setMinLines(2);box.addView(page);box.addView(note);new AlertDialog.Builder(this).setTitle("Annotate a PDF copy").setMessage("Adds a visible note to one page. The exported PDF is flattened; the original is preserved.").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Create annotated copy",(d,w)->{int n;try{n=Integer.parseInt(page.getText().toString().trim())-1;}catch(Exception e){Ui.toast(this,"Enter a valid page number");return;}String text=note.getText().toString().trim();if(text.isEmpty()){Ui.toast(this,"Enter annotation text");return;}if(text.length()>420){Ui.toast(this,"Annotation is limited to 420 characters so it fits on the page");return;}File out=OfficeDocumentStorage.unique(OfficeDocumentStorage.documents(getFilesDir()),stripExt(source.getName())+"_annotated.pdf");runIo(()->{OfficeDocumentStorage.atomicWrite(getFilesDir(),out,tmp->annotatePdf(source,tmp,n,text));return out;},f->{Ui.toast(this,"Annotated PDF copy created");openFile(f);});}).show();}
    private void annotatePdf(File source,File output,int selectedPage,String note)throws Exception{
        if(source.length()>100L*1024*1024)throw new IOException("PDF exceeds the 100 MB annotation limit");
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){
            PdfDocument result=new PdfDocument();
            try{
                int count=renderer.getPageCount();if(count<1||count>100)throw new IOException("PDF must contain between 1 and 100 pages for annotation");if(selectedPage<0||selectedPage>=count)throw new IOException("Page number is outside this PDF");
                android.graphics.Paint notePaint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);notePaint.setColor(Color.BLACK);notePaint.setTextSize(10f);
                for(int i=0;i<count;i++)try(PdfRenderer.Page page=renderer.openPage(i)){
                    int width=page.getWidth(),height=page.getHeight();if(width<1||height<1||(long)width*height>12000000L)throw new IOException("PDF page dimensions exceed the safe rendering limit");
                    float scale=Math.min(2f,(float)Math.sqrt(6000000d/((double)width*height)));int bw=Math.max(1,Math.round(width*scale)),bh=Math.max(1,Math.round(height*scale));Bitmap bitmap=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
                    try{
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);PdfDocument.Page dest=result.startPage(new PdfDocument.PageInfo.Builder(width,height,i+1).create());android.graphics.Canvas canvas=dest.getCanvas();canvas.drawBitmap(bitmap,null,new Rect(0,0,width,height),null);
                        if(i==selectedPage){float boxH=Math.min(110f,Math.max(58f,16f+(note.length()/75f)*13f));android.graphics.Paint bg=new android.graphics.Paint();bg.setColor(Color.argb(232,255,250,210));canvas.drawRect(0,height-boxH,width,height,bg);String remaining=note;float y=height-boxH+18f;while(!remaining.isEmpty()&&y<height-8f){int chars=notePaint.breakText(remaining,true,width-24f,null);if(chars<=0)break;canvas.drawText(remaining.substring(0,chars),12f,y,notePaint);remaining=remaining.substring(chars);y+=13f;}}
                        result.finishPage(dest);
                    }finally{bitmap.recycle();}
                }
                try(OutputStream os=new FileOutputStream(output)){result.writeTo(os);os.flush();}
            }finally{result.close();}
        }
    }


    private void handleIncomingIntent(Intent intent){
        if(intent==null){importDocument();return;}
        String path=intent.getStringExtra(EXTRA_FILE_PATH);
        if(path!=null&&!path.trim().isEmpty()){openFile(new File(path));return;}
        Uri uri=intent.getData();
        if(uri==null&&intent.getClipData()!=null&&intent.getClipData().getItemCount()>0)uri=intent.getClipData().getItemAt(0).getUri();
        if(uri==null&&Intent.ACTION_SEND.equals(intent.getAction())){
            if(android.os.Build.VERSION.SDK_INT>=33)uri=intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);
            else { //noinspection deprecation
                uri=(Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }
        if(uri!=null){Uri selected=uri;runIo(()->copyIncoming(selected),this::openFile);}
        else importDocument();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIncomingIntent(intent);}
    private File copyIncoming(Uri uri)throws Exception{
        String name=displayName(uri);String ext=OfficeDocumentEngine.extension(name);
        if(!isSupportedOfficeFile(ext))throw new IOException("Supported formats: Word, Excel, PowerPoint and PDF");
        File target=OfficeDocumentStorage.unique(OfficeDocumentStorage.documents(getFilesDir()),name);
        try(InputStream in=getContentResolver().openInputStream(uri)){OfficeDocumentStorage.copy(in,target,OfficeDocumentStorage.MAX_IMPORT_BYTES);}
        return target;
    }
    private static boolean isSupportedOfficeFile(String ext){
        return Arrays.asList("doc","docx","xls","xlsx","csv","tsv","ppt","pptx","pdf").contains(ext);
    }
    private void openPdfWorkspace(File file){
        root.removeAllViews();addHeader(file.getName(),"PDF WORKSPACE  •  OFFLINE",false);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(Ui.dp(8),Ui.dp(8),Ui.dp(8),Ui.dp(8));
        addSmall(actions,"Open PDF",()->{ArrayList<String> paths=new ArrayList<>();paths.add(file.getAbsolutePath());Intent view=new Intent(this,ViewerActivity.class);view.putStringArrayListExtra("paths",paths);view.putExtra("index",0);startActivity(view);});
        addSmall(actions,"Annotate copy",()->annotatePdfDialog(file));addSmall(actions,"Export",()->exportFile(file));root.addView(actions,new LinearLayout.LayoutParams(-1,Ui.dp(56)));
        TextView info=Ui.tv(this,"The original PDF is preserved. Annotations are saved as a separate flattened PDF copy.",13,Ui.SUB,false);info.setPadding(Ui.dp(14),Ui.dp(16),Ui.dp(14),Ui.dp(16));root.addView(info);
        LinearLayout.LayoutParams spacer=new LinearLayout.LayoutParams(-1,0,1);root.addView(new View(this),spacer);
    }
    private void exportFile(File f){if(f==null||!f.isFile())return;pendingExport=f;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime(f));i.putExtra(Intent.EXTRA_TITLE,f.getName());startActivityForResult(i,REQ_EXPORT);}
    private String mime(File f){String e=OfficeDocumentEngine.extension(f.getName());switch(e){case "pdf":return "application/pdf";case "doc":return "application/msword";case "docx":return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";case "xls":return "application/vnd.ms-excel";case "xlsx":return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";case "ppt":return "application/vnd.ms-powerpoint";case "pptx":return "application/vnd.openxmlformats-officedocument.presentationml.presentation";case "csv":return "text/csv";case "tsv":return "text/tab-separated-values";default:return "application/octet-stream";}}
    private void addSmall(LinearLayout row,String text,Runnable action){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER);item.setPadding(Ui.dp(7),Ui.dp(4),Ui.dp(7),Ui.dp(4));item.setBackground(Ui.glass(10,true));
        String asset=null;
        if(text.equals("Save"))asset="save";else if(text.equals("Export"))asset="export";else if(text.equals("B"))asset="bold";else if(text.equals("I"))asset="italic";else if(text.equals("U"))asset="underline";else if(text.equals("H1")||text.equals("H2"))asset="heading";else if(text.equals("•"))asset="bullet";else if(text.equals("Find"))asset="find-replace";else if(text.equals("Save copy"))asset="slide-edit";else if(text.equals("+ Sheet"))asset="add-sheet";
        if(asset!=null){ImageView ic=icon(asset);item.addView(ic,new LinearLayout.LayoutParams(Ui.dp(18),Ui.dp(18)));}
        TextView label=Ui.tv(this,text,12,Ui.WHITE,true);if(asset!=null)label.setPadding(Ui.dp(4),0,0,0);item.addView(label);
        item.setOnClickListener(v->action.run());Ui.press(item);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,Ui.dp(42));lp.setMargins(Ui.dp(3),Ui.dp(3),Ui.dp(3),Ui.dp(3));row.addView(item,lp);
    }
    private String stripExt(String n){int i=n.lastIndexOf('.');return i>0?n.substring(0,i):n;}
    private <T> void runIo(Callable<T> task,Consumer<T> success){
        io.execute(()->{try{T result=task.call();main.post(()->{if(!isFinishing()&&!isDestroyed())success.accept(result);});}catch(Throwable e){main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("Office document").setMessage(safeMessage(e)).setPositiveButton("OK",null).show();});}});
    }
    private String safeMessage(Throwable e){String m=e.getMessage();if(m==null||m.trim().isEmpty())m="The document could not be processed.";return m.length()>240?m.substring(0,240):m;}
}
