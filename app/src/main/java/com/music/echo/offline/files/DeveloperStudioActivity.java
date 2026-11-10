package com.music.echo.offline.files;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Restricted, on-device Developer Studio. No network clients or file execution are used. */
public final class DeveloperStudioActivity extends ComponentActivity {
    private static final int PICK_ONE=501, PICK_DIFF_A=502, PICK_DIFF_B=503, PICK_DB=504, PICK_TREE=505;
    private static final int REL=0, APK=1, DB=2, DIFF=3, FORMAT=4, REGEX=5, SYMBOL=6, DEP=7, BINARY=8, COLOR=9, JSON_XML=10, SIGNATURE=11;
    private static final String[] NAMES={"File Relationship Explorer","APK Inspection Laboratory","Database Explorer & SQL Query Studio","Code Diff Studio","Code Formatter & Validator","Regex Testing Laboratory","Code Symbol Navigator","Dependency Inspector","Binary Structure Analyzer","Color & Asset Inspector","JSON & XML Studio","File Signature Analyzer"};
    private static final String[] DESCRIPTIONS={
        "Map source-file imports and references across a selected project folder.",
        "Inspect APK metadata, components, permissions, signing certificates and archive entries. Never executes packages.",
        "Browse SQLite tables and run read-only SELECT, PRAGMA and EXPLAIN queries.",
        "Compare two text files with bounded line-level additions and removals.",
        "Format supported source text and validate strings, comments and delimiter structure.",
        "Test Java regular expressions against sample text with match groups and replacement preview.",
        "Locate class, interface, type and callable declarations with source line numbers.",
        "Extract imports and declared Gradle/Python/Node dependencies from local source files.",
        "Decode supported binary headers and report structure fields with offsets.",
        "Inspect image dimensions and sampled colors, or extract color literals from text assets.",
        "Parse, validate and pretty-print JSON and XML documents.",
        "Identify file types from magic bytes and detect common extension/signature mismatches."
    };
    private static final long MAX_APK=96L*1024*1024, MAX_DB=128L*1024*1024, MAX_TEXT=2L*1024*1024, MAX_ASSET=32L*1024*1024;
    private final TaskCoordinator.Scope taskScope=new TaskCoordinator.Scope();
    private final Handler main=new Handler(Looper.getMainLooper());
    private int current= -1;
    private Uri selected, diffA, diffB, tree;
    private TextView selectedLabel, resultView, status;
    private EditText sqlInput, regexInput, sampleInput, replacementInput;
    private ProgressBar progress;
    private Future<?> activeTask;
    private volatile boolean cancelled;
    private volatile long taskGeneration;
    private SQLiteDatabase database;
    private File databaseCopy;
    private volatile CancellationSignal activeSqlSignal;

    @Override public void onCreate(Bundle state){boolean lightAppearance=getSharedPreferences("lg_settings",MODE_PRIVATE).getBoolean("light",false);setTheme(lightAppearance?com.music.echo.offline.R.style.AppTheme_Light:com.music.echo.offline.R.style.AppTheme);super.onCreate(state);ToxiBackNavigation.bind(this, this::handleBackNavigation);ToxiVisualSystem.initialize(this);getWindow().setStatusBarColor(ToxiVisualSystem.CANVAS);getWindow().setNavigationBarColor(ToxiVisualSystem.CANVAS);if(Build.VERSION.SDK_INT>=23){int flags=getWindow().getDecorView().getSystemUiVisibility();int lightFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;if(Build.VERSION.SDK_INT>=26)lightFlags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;getWindow().getDecorView().setSystemUiVisibility(ToxiVisualSystem.LIGHT_MODE?(flags|lightFlags):(flags&~lightFlags));}if(!developerUnlocked()){finish();return;}showHome();}
    @Override protected void onResume(){super.onResume();if(!developerUnlocked())finish();}
    private boolean developerUnlocked(){return ToxiVisualSystem.PREFERENCES.getBoolean("developer_options_unlocked",false);}
    @Override protected void onDestroy(){cancelWork();if(database!=null)database.close();if(databaseCopy!=null)databaseCopy.delete();taskScope.close(true);super.onDestroy();}

    private int dp(float v){return ToxiVisualSystem.dp(v);}
    private TextView text(String s,float size,boolean bold){return ToxiVisualSystem.text(this,s,size,ToxiVisualSystem.TEXT_PRIMARY,bold);}
    private TextView muted(String s){return ToxiVisualSystem.text(this,s,12,ToxiVisualSystem.TEXT_SECONDARY,false);}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private EditText editor(String hint,int minLines){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(13);e.setTypeface(android.graphics.Typeface.MONOSPACE);e.setTextColor(ToxiVisualSystem.TEXT_PRIMARY);e.setHintTextColor(ToxiVisualSystem.TEXT_SECONDARY);e.setBackground(ToxiVisualSystem.surface(14,true));e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setGravity(Gravity.TOP|Gravity.START);e.setMinLines(minLines);e.setSingleLine(false);return e;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(12),dp(16),dp(24));return l;}
    private void base(String title,String description){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(ToxiVisualSystem.CANVAS);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(6),dp(16),dp(6));header.setMinimumHeight(dp(60));
        ImageView back=ToxiVisualSystem.iconButton(this,ToxiIcons.BACK,ToxiVisualSystem.TEXT_PRIMARY);back.setContentDescription(current>=0?"Back to Developer Studio":"Back to ToxiFiles");back.setFocusable(true);back.setOnClickListener(v->handleBackNavigation());
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=text(title,19,true);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(0,-2,1f);headingLp.leftMargin=dp(8);header.addView(heading,headingLp);root.addView(header);
        TextView desc=muted(description);desc.setPadding(dp(18),0,dp(18),dp(10));root.addView(desc);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout content=vertical();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);page=content;
    }
    private void handleBackNavigation(){if(current>=0){cancelWork();showHome();}else{finish();}}
    
    private LinearLayout page;
    private void add(View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);page.addView(v,p);}
    private void showHome(){if(!developerUnlocked()){finish();return;}current=-1;base("Developer Studio","12 offline inspection tools · on-device processing · originals remain unchanged");add(section("SOURCE & CODE"));tile(REL);tile(DIFF);tile(FORMAT);tile(REGEX);tile(SYMBOL);tile(DEP);add(section("PACKAGES & BINARY"));tile(APK);tile(BINARY);tile(SIGNATURE);add(section("DATA & ASSETS"));tile(DB);tile(COLOR);tile(JSON_XML);add(muted("All analysis runs locally. Select files through Android's document picker. No inspected file is executed or uploaded."));}
    private TextView section(String s){TextView t=text(s,11,true);t.setLetterSpacing(.12f);t.setTextColor(ToxiVisualSystem.ACCENT);t.setPadding(dp(4),dp(14),0,dp(2));return t;}
    private void tile(int index){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(13),dp(14),dp(13));card.setBackground(ToxiVisualSystem.surface(16,true));ImageView icon=new ImageView(this);icon.setImageDrawable(ToxiIcons.symbol(iconFor(index),ToxiVisualSystem.ACCENT));icon.setContentDescription(NAMES[index]+" icon");icon.setBackground(ToxiVisualSystem.surface(12,true));icon.setPadding(dp(9),dp(9),dp(9),dp(9));card.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);TextView title=text(String.format(Locale.ROOT,"%02d  %s",index+1,NAMES[index]),14,true);copy.addView(title);TextView desc=muted(DESCRIPTIONS[index]);LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(-1,-2);d.topMargin=dp(5);copy.addView(desc,d);card.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));card.setFocusable(true);card.setClickable(true);card.setContentDescription(NAMES[index]+". "+DESCRIPTIONS[index]);card.setOnClickListener(v->openTool(index));add(card);}
    private int iconFor(int index){switch(index){case REL:return ToxiIcons.FOLDER;case APK:return ToxiIcons.APK;case DB:return ToxiIcons.DATABASE;case DIFF:return ToxiIcons.FILE;case FORMAT:return ToxiIcons.TEXT;case REGEX:return ToxiIcons.SEARCH;case SYMBOL:return ToxiIcons.INFO;case DEP:return ToxiIcons.ANALYTICS;case BINARY:return ToxiIcons.ARCHIVE;case COLOR:return ToxiIcons.PALETTE;case JSON_XML:return ToxiIcons.DOC;default:return ToxiIcons.SHIELD;}}

    private void openTool(int index){if(!developerUnlocked()){finish();return;}current=index;selected=null;diffA=null;diffB=null;tree=null;status=null;selectedLabel=null;resultView=null;sqlInput=null;regexInput=null;sampleInput=null;replacementInput=null;base(NAMES[index],DESCRIPTIONS[index]);
        if(index==REL){selectedLabel=muted("No project folder selected");add(label("PROJECT ROOT",12));add(selectedLabel);add(button("Choose project folder",()->pickTree()));add(button("Build relationship map",()->runRelationships()));}
        else if(index==DIFF){add(label("FILE A",12));add(button("Choose first file",()->pick(PICK_DIFF_A)));add(label("FILE B",12));add(button("Choose second file",()->pick(PICK_DIFF_B)));selectedLabel=muted("Choose both files to compare.");add(selectedLabel);add(button("Compare files",()->runDiff()));}
        else if(index==REGEX){add(label("REGULAR EXPRESSION",12));regexInput=editor("Pattern, e.g. (\\w+)=(\\d+)",2);add(regexInput);add(label("SAMPLE TEXT",12));sampleInput=editor("Paste sample text to test",8);add(sampleInput);add(label("REPLACEMENT (OPTIONAL)",12));replacementInput=editor("Replacement expression, e.g. $1: $2",2);add(replacementInput);add(button("Run regex test",()->runRegex()));}
        else if(index==DB){selectedLabel=muted("No SQLite database selected.");add(selectedLabel);add(button("Choose SQLite database",()->pick(PICK_DB)));sqlInput=editor("Read-only SQL query, e.g. SELECT name FROM sqlite_master",5);add(label("SQL QUERY STUDIO",12));add(sqlInput);add(button("Run read-only query",()->runSql()));}
        else {selectedLabel=muted("No file selected.");add(selectedLabel);add(button("Choose file",()->pick(PICK_ONE)));add(button(index==APK?"Inspect APK":index==FORMAT?"Format & validate":index==SYMBOL?"Find symbols":index==DEP?"Inspect dependencies":index==BINARY?"Analyze binary structure":index==COLOR?"Inspect colors & assets":index==JSON_XML?"Parse & format document":"Analyze file signature",()->runSelected()));}
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);add(progress);status=muted("Ready. Processing is local and analysis-only.");add(status);resultView=text("Results will appear here.",12,false);resultView.setTypeface(android.graphics.Typeface.MONOSPACE);resultView.setTextIsSelectable(true);resultView.setPadding(dp(12),dp(12),dp(12),dp(12));resultView.setBackground(ToxiVisualSystem.surface(14,true));add(resultView);add(button("Cancel processing",()->cancelWork()));}
    private TextView label(String s,float size){TextView t=text(s,size,true);t.setTextColor(ToxiVisualSystem.ACCENT);return t;}
    private void pick(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,request);}
    private void pickTree(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_TREE);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(!ToxiUriPermissions.isSafUri(this,u)){if(status!=null)status.setText("Unsupported document provider URI.");return;}ToxiUriPermissions.takeGranted(getContentResolver(),u,data.getFlags());
        if(request==PICK_TREE){tree=u;if(selectedLabel!=null)selectedLabel.setText("Project folder: "+u);return;}if(request==PICK_DIFF_A){diffA=u;if(selectedLabel!=null)selectedLabel.setText("File A selected: "+displayName(u));return;}if(request==PICK_DIFF_B){diffB=u;if(selectedLabel!=null)selectedLabel.setText("File B selected: "+displayName(u));return;}selected=u;if(selectedLabel!=null)selectedLabel.setText("Selected: "+displayName(u));if(request==PICK_DB)openDatabase(u);}
    private String displayName(Uri u){String name=u.getLastPathSegment();try(Cursor c=ToxiUriPermissions.query(this,u,new String[]{"_display_name"})){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}return name==null?"selected file":name;}
    private byte[] read(Uri uri,long limit)throws Exception{try(InputStream in=ToxiUriPermissions.openInputStream(this,uri)){if(in==null)throw new java.io.IOException("Selected document cannot be opened");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[]b=new byte[65536];long total=0;int n;while((n=in.read(b))!=-1){checkCancelled();total+=n;if(total>limit)throw new java.io.IOException("File exceeds supported limit of "+limit+" bytes");out.write(b,0,n);if((total&0x3fffff)<n)updateProgress("Reading "+(total/1024/1024)+" MB…");}return out.toByteArray();}}
    private File copyToCache(Uri uri,long limit,String suffix)throws Exception{File f=File.createTempFile("toxidev-",suffix,getCacheDir());boolean ok=false;try(InputStream in=ToxiUriPermissions.openInputStream(this,uri);FileOutputStream out=new FileOutputStream(f)){if(in==null)throw new java.io.IOException("Document cannot be opened");byte[]b=new byte[65536];long total=0;int n;while((n=in.read(b))!=-1){checkCancelled();total+=n;if(total>limit)throw new java.io.IOException("File exceeds supported limit of "+limit+" bytes");out.write(b,0,n);if((total&0x3fffff)<n)updateProgress("Reading "+(total/1024/1024)+" MB…");}out.flush();ok=true;return f;}finally{if(!ok)f.delete();}}
    private void runAsync(String message,java.util.concurrent.Callable<String> job){cancelWork();cancelled=false;final long generation=taskGeneration;if(progress!=null)progress.setVisibility(View.VISIBLE);if(status!=null)status.setText(message);if(resultView!=null)resultView.setText("");activeTask=taskScope.submit(TaskCoordinator.Lane.SERIAL,()->{String output;try{output=job.call();}catch(Exception e){output="Analysis failed: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}final String value=output;main.post(()->{if(generation!=taskGeneration||isFinishing()||isDestroyed())return;if(progress!=null)progress.setVisibility(View.GONE);if(status!=null)status.setText(cancelled?"Cancelled.":"Analysis complete.");if(resultView!=null)resultView.setText(value);});});}
    private void cancelWork(){taskGeneration++;cancelled=true;if(activeSqlSignal!=null){activeSqlSignal.cancel();activeSqlSignal=null;}if(activeTask!=null){activeTask.cancel(true);activeTask=null;}if(progress!=null)progress.setVisibility(View.GONE);if(status!=null&&current>=0)status.setText("Cancellation requested.");}
    private void updateProgress(String message){main.post(()->{if(!isFinishing()&&!isDestroyed()&&status!=null)status.setText(message);});}
    private void checkCancelled()throws java.io.IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new java.io.IOException("Operation cancelled");}

    private void runSelected(){if(selected==null){status.setText("Choose a file first.");return;}final Uri u=selected;final String name=displayName(u);switch(current){case APK:runAsync("Inspecting APK metadata and archive…",()->inspectApk(u,name));break;case FORMAT:runAsync("Formatting and validating source…",()->formatValidate(u,name));break;case SYMBOL:runAsync("Scanning source declarations…",()->DeveloperStudioCore.symbols(DeveloperStudioCore.decodeUtf8(read(u,MAX_TEXT))));break;case DEP:runAsync("Extracting local dependency declarations…",()->DeveloperStudioCore.dependencies(DeveloperStudioCore.decodeUtf8(read(u,MAX_TEXT)),name));break;case BINARY:runAsync("Reading binary header…",()->DeveloperStudioCore.binaryStructure(read(u,1024*1024),name));break;case COLOR:runAsync("Inspecting asset metadata and colors…",()->inspectAsset(u,name));break;case JSON_XML:runAsync("Parsing document…",()->parseDocument(u,name));break;case SIGNATURE:runAsync("Checking magic bytes…",()->DeveloperStudioCore.signature(read(u,65536),name));break;default:status.setText("This tool uses a different input flow.");}}
    private void runDiff(){if(diffA==null||diffB==null){status.setText("Choose both files first.");return;}Uri a=diffA,b=diffB;runAsync("Comparing text files…",()->DeveloperStudioCore.diff(DeveloperStudioCore.decodeUtf8(read(a,MAX_TEXT)),DeveloperStudioCore.decodeUtf8(read(b,MAX_TEXT))));}
    private void runRegex(){String p=regexInput.getText().toString(),s=sampleInput.getText().toString(),r=replacementInput.getText().toString();runAsync("Testing regular expression…",()->DeveloperStudioCore.regex(p,s,r.isEmpty()?null:r));}
    private void runRelationships(){if(tree==null){status.setText("Choose a project folder first.");return;}Uri root=tree;runAsync("Indexing local source relationships…",()->{DocumentFile folder=DocumentFile.fromTreeUri(this,root);if(folder==null||!folder.isDirectory())throw new java.io.IOException("Selected folder is unavailable");Map<String,String> files=new TreeMap<>();long indexedBytes=0;ArrayDeque<DocNode> queue=new ArrayDeque<>();Set<String> visited=new TreeSet<>();queue.add(new DocNode(folder,""));int nodes=0,skipped=0;while(!queue.isEmpty()){checkCancelled();DocNode node=queue.removeFirst();if(!visited.add(node.file.getUri().toString()))continue;if(++nodes>20000)throw new java.io.IOException("Project contains more than 20,000 folders and files.");for(DocumentFile child:node.file.listFiles()){checkCancelled();String n=child.getName();if(n==null||n.startsWith("."))continue;String path=node.path.isEmpty()?n:node.path+"/"+n;if(child.isDirectory())queue.addLast(new DocNode(child,path));else if(isSource(n)){if(files.size()>=DeveloperStudioCore.MAX_RELATION_FILES)throw new java.io.IOException("Project exceeds 10,000 source files.");try{byte[] data=read(child.getUri(),1024*1024);indexedBytes+=data.length;if(indexedBytes>32L*1024*1024)throw new java.io.IOException("Project source text exceeds the 32 MB analysis budget.");files.put(path,DeveloperStudioCore.decodeUtf8(data));}catch(java.io.IOException e){if(e.getMessage()!=null&&e.getMessage().contains("32 MB"))throw e;skipped++;}if(files.size()%50==0)updateProgress("Indexed "+files.size()+" source files…");}}}return DeveloperStudioCore.fileRelationships(files)+"\n\nFiles skipped (unreadable, binary or over 1 MB): "+skipped+". Total indexed source text: "+(indexedBytes/1024)+" KB.";});}
    private static final class DocNode{final DocumentFile file;final String path;DocNode(DocumentFile f,String p){file=f;path=p;}}
    private boolean isSource(String n){String x=n.toLowerCase(Locale.ROOT);return x.endsWith(".java")||x.endsWith(".kt")||x.endsWith(".kts")||x.endsWith(".js")||x.endsWith(".ts")||x.endsWith(".jsx")||x.endsWith(".tsx")||x.endsWith(".py")||x.endsWith(".c")||x.endsWith(".h")||x.endsWith(".cpp")||x.endsWith(".hpp")||x.endsWith(".go")||x.endsWith(".rs")||x.endsWith(".cs")||x.endsWith(".php")||x.endsWith(".gradle");}

    private String inspectApk(Uri uri,String name)throws Exception{File apk=copyToCache(uri,MAX_APK,".apk");try{int flags=PackageManager.GET_ACTIVITIES|PackageManager.GET_SERVICES|PackageManager.GET_RECEIVERS|PackageManager.GET_PROVIDERS|PackageManager.GET_PERMISSIONS;if(android.os.Build.VERSION.SDK_INT>=28)flags|=PackageManager.GET_SIGNING_CERTIFICATES;PackageInfo info=getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags);StringBuilder out=new StringBuilder("APK INSPECTION · READ ONLY\nFile: ").append(name).append("\nSize: ").append(apk.length()).append(" bytes\n");if(info==null)out.append("\nPackageManager could not parse package metadata. The APK may be malformed, unsupported or incomplete. Archive-level inspection follows.\n");else{out.append("\nPackage: ").append(info.packageName).append("\nVersion: ").append(info.versionName).append(" (code ").append(android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode).append(")\n");if(info.applicationInfo!=null){info.applicationInfo.sourceDir=apk.getAbsolutePath();info.applicationInfo.publicSourceDir=apk.getAbsolutePath();out.append("Application label: ").append(getPackageManager().getApplicationLabel(info.applicationInfo)).append("\n");}out.append("\nRequested permissions:\n");if(info.requestedPermissions!=null)for(String p:info.requestedPermissions)out.append(" • ").append(p).append('\n');out.append("\nComponents:\n");if(info.activities!=null)for(android.content.pm.ActivityInfo a:info.activities)out.append("Activity  ").append(a.name).append(" exported=").append(a.exported).append('\n');if(info.services!=null)for(android.content.pm.ServiceInfo a:info.services)out.append("Service   ").append(a.name).append(" exported=").append(a.exported).append('\n');if(info.receivers!=null)for(android.content.pm.ActivityInfo a:info.receivers)out.append("Receiver  ").append(a.name).append(" exported=").append(a.exported).append('\n');if(info.providers!=null)for(android.content.pm.ProviderInfo a:info.providers)out.append("Provider  ").append(a.name).append(" exported=").append(a.exported).append('\n');if(android.os.Build.VERSION.SDK_INT>=28&&info.signingInfo!=null){out.append("\nSigning certificates (SHA-256):\n");Signature[] signatures=info.signingInfo.getApkContentsSigners();for(Signature sig:signatures)out.append(DeveloperStudioCore.hex(MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()))).append('\n');}}
        out.append("\nARCHIVE CONTENTS\n");try(ZipFile zip=new ZipFile(apk)){java.util.Enumeration<? extends ZipEntry> e=zip.entries();int count=0;while(e.hasMoreElements()){checkCancelled();ZipEntry z=e.nextElement();if(++count>5000){out.append("… entry listing capped at 5,000\n");break;}if(count%100==0)updateProgress("Inspected "+count+" APK entries…");out.append(z.getName()).append("  ").append(z.isDirectory()?"[directory]":z.getSize()+" bytes").append('\n');}}out.append("\nSafety: APK was inspected as data only. No code was loaded, installed or executed.");return out.toString();}finally{apk.delete();}}

    private void openDatabase(Uri uri){runAsync("Opening SQLite database read-only…",()->{File copy=copyToCache(uri,MAX_DB,".db");SQLiteDatabase opened=null;try{opened=SQLiteDatabase.openDatabase(copy.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);Cursor c=opened.rawQuery("SELECT name, type, sql FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY name",null);StringBuilder b=new StringBuilder("SQLITE DATABASE · READ ONLY\nObjects:\n");try{while(c.moveToNext()){checkCancelled();b.append(c.getString(1)).append("  ").append(c.getString(0)).append('\n');}}finally{c.close();}SQLiteDatabase finalDb=opened;File finalCopy=copy;main.post(()->{if(database!=null)database.close();if(databaseCopy!=null)databaseCopy.delete();database=finalDb;databaseCopy=finalCopy;if(status!=null)status.setText("Database opened read-only.");if(resultView!=null)resultView.setText(b.toString());});opened=null;copy=null;return "Database ready. Queries are restricted to read-only statements.";}finally{if(opened!=null)opened.close();if(copy!=null)copy.delete();}});}
    private void runSql(){if(database==null||!database.isOpen()){status.setText("Select a SQLite database first.");return;}String sql=sqlInput.getText().toString().trim();if(!DeveloperStudioCore.isReadOnlySql(sql)){status.setText("Only one bounded read-only SELECT, WITH, EXPLAIN or approved inspection PRAGMA statement is allowed.");return;}SQLiteDatabase db=database;runAsync("Running read-only SQL…",()->{CancellationSignal signal=new CancellationSignal();activeSqlSignal=signal;Cursor c=null;try{
                // This local SQL editor accepts user-authored statements only. The SQL is never
                // concatenated with external data, is parsed as one bounded read-only statement,
                // and runs only against a database opened with SQLiteDatabase.OPEN_READONLY.
                // codeql[java/sql-injection]
                c=db.rawQuery(sql,null,signal); // lgtm[java/sql-injection]
                StringBuilder out=new StringBuilder("Rows: ");int rows=0;String[] cols=c.getColumnNames();int visibleColumns=Math.min(cols.length,40);for(int i=0;i<visibleColumns;i++)out.append(cols[i]).append("\t");if(cols.length>visibleColumns)out.append("…");out.append('\n');while(c.moveToNext()){checkCancelled();if(++rows>500){out.append("\nResult capped at 500 rows.");break;}for(int i=0;i<visibleColumns;i++){if(i>0)out.append("\t");String v=c.isNull(i)?"NULL":c.getString(i);if(v!=null&&v.length()>2048)v=v.substring(0,2048)+"…";out.append(v==null?"NULL":v.replace('\n',' '));}out.append('\n');if(out.length()>1_000_000){out.append("\nOutput capped at 1 MB.");break;}}out.insert(0,"Rows shown: "+Math.min(rows,500)+"\n\n");return out.toString();}finally{if(c!=null)c.close();activeSqlSignal=null;}});}

    private String formatValidate(Uri uri,String name)throws Exception{String src=DeveloperStudioCore.decodeUtf8(read(uri,MAX_TEXT));String validation=DeveloperStudioCore.validateSource(src);String ext=extension(name);if(ext.equals("json")||ext.equals("xml"))return parseTextDocument(src,ext)+"\n\n"+validation;return "STRUCTURAL VALIDATION\n"+validation+"\n\nFORMATTED SOURCE (best-effort indentation formatter; not a language compiler):\n"+DeveloperStudioCore.formatSource(src);}
    private String parseDocument(Uri uri,String name)throws Exception{return parseTextDocument(DeveloperStudioCore.decodeUtf8(read(uri,MAX_TEXT)),extension(name));}
    private String parseTextDocument(String src,String ext)throws Exception{if(ext.equals("json")){String t=src.trim();JSONTokener tokener=new JSONTokener(t);Object value=tokener.nextValue();if(tokener.nextClean()!=0)throw new IllegalArgumentException("Unexpected content after the JSON value.");String formatted;if(value instanceof JSONObject)formatted=((JSONObject)value).toString(2);else if(value instanceof JSONArray)formatted=((JSONArray)value).toString(2);else formatted=new JSONArray().put(value).toString(2).replaceFirst("(?s)^\\[\\n?", "").replaceFirst("(?s)\\n?\\]$", "").trim();return "JSON VALID · FORMATTED\n"+formatted;}if(ext.equals("xml"))return "XML VALID · FORMATTED\n"+formatXml(src);throw new IllegalArgumentException("Unsupported document type. JSON and XML are supported.");}
    private String formatXml(String src)throws Exception{XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);parser.setInput(new StringReader(src));XmlSerializer serializer=Xml.newSerializer();StringWriter writer=new StringWriter();serializer.setOutput(writer);serializer.startDocument("UTF-8",null);serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output",true);int event;while((event=parser.next())!=XmlPullParser.END_DOCUMENT){checkCancelled();switch(event){case XmlPullParser.START_TAG:serializer.startTag(parser.getNamespace(),parser.getName());for(int i=0;i<parser.getAttributeCount();i++)serializer.attribute(parser.getAttributeNamespace(i),parser.getAttributeName(i),parser.getAttributeValue(i));break;case XmlPullParser.END_TAG:serializer.endTag(parser.getNamespace(),parser.getName());break;case XmlPullParser.TEXT:serializer.text(parser.getText());break;case XmlPullParser.CDSECT:serializer.cdsect(parser.getText());break;case XmlPullParser.COMMENT:serializer.comment(parser.getText());break;case XmlPullParser.PROCESSING_INSTRUCTION:serializer.processingInstruction(parser.getText());break;}}serializer.endDocument();return writer.toString();}

    private String inspectAsset(Uri uri,String name)throws Exception{String ext=extension(name);byte[] data=read(uri,MAX_ASSET);if(ext.equals("png")||ext.equals("jpg")||ext.equals("jpeg")||ext.equals("webp")||ext.equals("gif")||ext.equals("bmp")){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)throw new java.io.IOException("Image decoder could not read this image.");int sample=1;while((long)bounds.outWidth/sample*(bounds.outHeight/sample)>1_000_000L)sample*=2;BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts);if(bitmap==null)throw new java.io.IOException("Image decoding failed.");try{Map<Integer,Integer> palette=new HashMap<>();int step=Math.max(1,Math.max(bitmap.getWidth(),bitmap.getHeight())/160);for(int y=0;y<bitmap.getHeight();y+=step){checkCancelled();for(int x=0;x<bitmap.getWidth();x+=step){int c=bitmap.getPixel(x,y);int q=Color.rgb((Color.red(c)/16)*16,(Color.green(c)/16)*16,(Color.blue(c)/16)*16);palette.put(q,palette.getOrDefault(q,0)+1);}}List<Map.Entry<Integer,Integer>> colors=new ArrayList<>(palette.entrySet());colors.sort((a,b)->Integer.compare(b.getValue(),a.getValue()));StringBuilder out=new StringBuilder("IMAGE ASSET\nDimensions: ").append(bounds.outWidth).append(" × ").append(bounds.outHeight).append("\nFormat: ").append(ext.toUpperCase(Locale.ROOT)).append("\nFile size: ").append(data.length).append(" bytes\nSampled palette:\n");for(int i=0;i<Math.min(12,colors.size());i++){int c=colors.get(i).getKey();out.append(String.format(Locale.ROOT,"#%02X%02X%02X  samples=%d%n",Color.red(c),Color.green(c),Color.blue(c),colors.get(i).getValue()));}return out.toString();}finally{bitmap.recycle();}}
        String src=DeveloperStudioCore.decodeUtf8(data);Matcher m=Pattern.compile("(?i)#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{4}|[0-9a-f]{3})\\b|\\brgba?\\([^)]*\\)|\\bhsla?\\([^)]*\\)").matcher(src);Set<String> found=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);while(m.find())found.add(m.group());StringBuilder out=new StringBuilder("TEXT / VECTOR ASSET\nFile: ").append(name).append("\nColor literals found: ").append(found.size()).append("\n");for(String c:found)out.append(c).append('\n');if(found.isEmpty())out.append("No supported CSS/XML color literals found.\n");return out.toString();}
    private String extension(String name){int p=name==null?-1:name.lastIndexOf('.');return p<0?"":name.substring(p+1).toLowerCase(Locale.ROOT);}
}
