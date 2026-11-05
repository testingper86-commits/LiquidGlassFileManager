package com.music.echo.offline.files;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import android.text.Spanned;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Offline format catalog and bounded Apache POI adapters. Macro content is never executed. */
final class ToxiFiles360Engine {
    enum Level { FULL, PARTIAL, VIEW_ONLY, UNSUPPORTED }
    static final class FormatInfo {
        final String ext, category, label, details;
        final Level level;
        FormatInfo(String e, String c, String l, Level v, String d) {
            ext=e; category=c; label=l; level=v; details=d;
        }
    }
    static final class SheetData {
        final String name;
        final ArrayList<ArrayList<String>> cells = new ArrayList<>();
        SheetData(String n) { name=n; }
        String get(int r,int c) { return r<cells.size() && c<cells.get(r).size() ? cells.get(r).get(c) : ""; }
        void set(int r,int c,String value) {
            while(cells.size()<=r) cells.add(new ArrayList<String>());
            ArrayList<String> row=cells.get(r);
            while(row.size()<=c) row.add("");
            row.set(c,value==null?"":value);
        }
    }
    static final class PresentationData {
        final ArrayList<String> slides = new ArrayList<>();
    }
    private static final List<FormatInfo> CATALOG;
    static {
        ArrayList<FormatInfo> f=new ArrayList<>();
        add(f,"docx","Word","Word Open XML",Level.PARTIAL,"Text, paragraphs, basic bold/italic and new-document export; advanced layout, fields and embedded objects are not preserved by the simplified editor.");
        add(f,"doc","Word","Legacy Word",Level.VIEW_ONLY,"Apache POI HWPF text extraction; complex layout and reliable editing are not supported.");
        add(f,"docm","Word","Macro-enabled Word",Level.VIEW_ONLY,"Read-only extraction; macros are never executed or rewritten.");
        add(f,"dotx","Word","Word template",Level.VIEW_ONLY,"Template text extraction; editing is exported as DOCX.");
        add(f,"dot","Word","Legacy Word template",Level.VIEW_ONLY,"Read-only legacy extraction where POI can parse the document.");
        add(f,"dotm","Word","Macro-enabled Word template",Level.VIEW_ONLY,"Read-only; macros are never executed.");
        add(f,"rtf","Word","Rich Text Format",Level.PARTIAL,"Text extraction and plain-text RTF export; advanced styling is not retained.");
        add(f,"odt","Word","OpenDocument Text",Level.VIEW_ONLY,"Safe text extraction from content.xml; editing/export is DOCX or TXT.");
        add(f,"xlsx","Excel","Excel Open XML",Level.PARTIAL,"Workbook, worksheets, cell values and formula evaluation/editing; advanced charts, pivots and external links may be lost.");
        add(f,"xls","Excel","Legacy Excel",Level.PARTIAL,"Apache POI HSSF workbook and cell editing; advanced charts, macros and unsupported BIFF records may not survive conversion.");
        add(f,"xlsm","Excel","Macro-enabled Excel",Level.VIEW_ONLY,"Read-only extraction; macros are never executed or rewritten.");
        add(f,"xlsb","Excel","Binary Excel",Level.UNSUPPORTED,"No verified safe Android editing engine is bundled for XLSB.");
        add(f,"xltx","Excel","Excel template",Level.VIEW_ONLY,"Read-only extraction; export editable workbooks as XLSX.");
        add(f,"xlt","Excel","Legacy Excel template",Level.VIEW_ONLY,"Read-only extraction.");
        add(f,"xltm","Excel","Macro-enabled Excel template",Level.VIEW_ONLY,"Read-only; macros are never executed.");
        add(f,"csv","Excel","Comma-separated values",Level.FULL,"Quoted CSV import/export and cell editing; single sheet, values only.");
        add(f,"tsv","Excel","Tab-separated values",Level.FULL,"TSV import/export and cell editing; single sheet, values only.");
        add(f,"ods","Excel","OpenDocument Spreadsheet",Level.VIEW_ONLY,"Safe text extraction from content.xml; editing/export is XLSX or CSV.");
        add(f,"pptx","PowerPoint","PowerPoint Open XML",Level.VIEW_ONLY,"Safe slide text extraction and navigation. Rich slide editing is not enabled because the Android POI port lacks the desktop geometry APIs required for reliable shape editing.");
        add(f,"ppt","PowerPoint","Legacy PowerPoint",Level.UNSUPPORTED,"The legacy binary presentation renderer is not enabled on Android.");
        add(f,"pptm","PowerPoint","Macro-enabled PowerPoint",Level.VIEW_ONLY,"Read-only text extraction; macros are never executed.");
        add(f,"ppsx","PowerPoint","PowerPoint show",Level.VIEW_ONLY,"Read-only slide text extraction.");
        add(f,"pps","PowerPoint","Legacy PowerPoint show",Level.UNSUPPORTED,"The legacy binary presentation renderer is not enabled on Android.");
        add(f,"ppsm","PowerPoint","Macro-enabled show",Level.VIEW_ONLY,"Read-only; macros are never executed.");
        add(f,"potx","PowerPoint","PowerPoint template",Level.VIEW_ONLY,"Read-only slide text extraction.");
        add(f,"pot","PowerPoint","Legacy PowerPoint template",Level.UNSUPPORTED,"The legacy binary presentation renderer is not enabled on Android.");
        add(f,"potm","PowerPoint","Macro-enabled template",Level.VIEW_ONLY,"Read-only; macros are never executed.");
        add(f,"pdf","PDF","Portable Document Format",Level.PARTIAL,"Native Android PDF rendering and PDF creation. Search and in-place annotation editing are not available; notes are stored separately.");
        add(f,"txt","Notes","Plain text",Level.FULL,"Read, edit, create and save locally.");
        add(f,"one","Other","OneNote file",Level.UNSUPPORTED,"No compatible offline engine is bundled.");
        add(f,"onepkg","Other","OneNote package",Level.UNSUPPORTED,"No compatible offline engine is bundled.");
        add(f,"vsdx","Other","Visio Open XML",Level.UNSUPPORTED,"No verified diagram renderer is bundled.");
        add(f,"vsd","Other","Legacy Visio",Level.UNSUPPORTED,"No verified diagram renderer is bundled.");
        add(f,"pub","Other","Publisher",Level.UNSUPPORTED,"No verified Android layout/rendering engine is bundled.");
        add(f,"xps","Other","XML Paper Specification",Level.UNSUPPORTED,"No compatible offline renderer is bundled.");
        CATALOG=Collections.unmodifiableList(f);
    }
    private ToxiFiles360Engine() {}
    static {
        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl");
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl");
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl");
        System.setProperty("org.apache.poi.ss.ignoreMissingFontSystem", "true");
        org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(20L * 1024 * 1024);
        org.apache.poi.openxml4j.util.ZipSecureFile.setMaxTextSize(32L * 1024 * 1024);
        org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.01d);
    }
    private static void add(List<FormatInfo> f,String e,String c,String l,Level v,String d){f.add(new FormatInfo(e,c,l,v,d));}
    static List<FormatInfo> catalog(){return CATALOG;}
    static FormatInfo format(String name){if(name==null)return null; int p=name.lastIndexOf('.'); String e=(p>=0?name.substring(p+1):name).toLowerCase(Locale.ROOT); for(FormatInfo f:CATALOG)if(f.ext.equals(e))return f;return null;}
    static boolean editable(String name){FormatInfo f=format(name);return f!=null&&(f.level==Level.FULL||f.level==Level.PARTIAL)&&Arrays.asList("txt","csv","tsv","rtf","docx","xlsx","xls").contains(f.ext);}
    static String extractText(File file) throws Exception {
        if(file==null||!file.isFile())throw new FileNotFoundException("Document is unavailable");
        if(file.length()>50L*1024*1024)throw new IOException("File exceeds the 50 MB processing limit");
        FormatInfo info=format(file.getName()); if(info==null||info.level==Level.UNSUPPORTED)throw new IOException("Unsupported format");
        String ext=info.ext;
        if(ext.equals("txt"))return readText(file);
        if(ext.equals("csv")||ext.equals("tsv"))return readText(file);
        if(ext.equals("rtf"))return stripRtf(readText(file));
        if(ext.equals("docx"))return readDocx(file);
        if(ext.equals("doc")||ext.equals("dot"))return readDoc(file);
        if(ext.equals("xlsx")||ext.equals("xls")||ext.equals("xltx")||ext.equals("xlt"))return readWorkbook(file);
        if(ext.equals("pptx")||ext.equals("ppsx")||ext.equals("potx"))return readPptx(file);
        
        if(ext.equals("odt")||ext.equals("ods"))return readOpenDocument(file);
        if(ext.equals("docm")||ext.equals("dotx")||ext.equals("dotm")||ext.equals("xlsm")||ext.equals("pptm")||ext.equals("ppsm")||ext.equals("potm"))return readOoxmlText(file);
        if(ext.equals("vsd")||ext.equals("vsdx"))return readOoxmlText(file);
        throw new IOException("This format is not supported for text extraction");
    }
    private static String readText(File f)throws IOException{
        StringBuilder b=new StringBuilder(); try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){char[] c=new char[8192];int n;while((n=r.read(c))>0){b.append(c,0,n);if(b.length()>5_000_000)throw new IOException("Text exceeds the 5 MB editor limit");}}return b.toString();
    }
    private static String readDocx(File f)throws Exception{
        try(InputStream in=new FileInputStream(f);XWPFDocument d=new XWPFDocument(in)){
            StringBuilder b=new StringBuilder();
            for(XWPFParagraph p:d.getParagraphs()){b.append(p.getText()).append('\n');if(b.length()>2_000_000)throw new IOException("Word preview exceeds the 2 MB limit");}
            for(org.apache.poi.xwpf.usermodel.XWPFTable t:d.getTables())for(org.apache.poi.xwpf.usermodel.XWPFTableRow r:t.getRows()){for(org.apache.poi.xwpf.usermodel.XWPFTableCell c:r.getTableCells())b.append(c.getText()).append('\t');b.append('\n');if(b.length()>2_000_000)throw new IOException("Word preview exceeds the 2 MB limit");}
            return b.toString();
        }
    }
    private static String readDoc(File f)throws Exception{
        try(InputStream in=new FileInputStream(f);HWPFDocument d=new HWPFDocument(in);WordExtractor x=new WordExtractor(d)){
            String text=x.getText();if(text.length()>2_000_000)throw new IOException("Word preview exceeds the 2 MB limit");return text;
        }
    }
    private static String readWorkbook(File f)throws Exception{
        try(Workbook w=WorkbookFactory.create(f)){DataFormatter fmt=new DataFormatter(Locale.ROOT);org.apache.poi.ss.usermodel.FormulaEvaluator evaluator=w.getCreationHelper().createFormulaEvaluator();StringBuilder b=new StringBuilder();for(int s=0;s<w.getNumberOfSheets();s++){if(s>0)b.append("\n\n[Sheet: ").append(w.getSheetName(s)).append("]\n");org.apache.poi.ss.usermodel.Sheet sh=w.getSheetAt(s);int maxRow=Math.min(sh.getLastRowNum(),999);for(int r=0;r<=maxRow;r++){Row row=sh.getRow(r);if(row==null){b.append('\n');continue;}int max=Math.min(Math.max(0,row.getLastCellNum()),80);for(int c=0;c<max;c++){if(c>0)b.append('\t');Cell cell=row.getCell(c);if(cell!=null){try{b.append(fmt.formatCellValue(cell,evaluator));}catch(RuntimeException formulaError){b.append(cell.getCellType()==CellType.FORMULA?"="+cell.getCellFormula():"");}}}b.append('\n');if(b.length()>2_000_000)throw new IOException("Workbook preview exceeds the 2 MB limit");}}return b.toString();}
    }
    private static String readPptx(File f)throws Exception{
        PresentationData data=readPresentation(f);
        StringBuilder b=new StringBuilder();int n=1;
        for(String slide:data.slides)b.append("[Slide ").append(n++).append("]\n").append(slide).append("\n\n");
        return b.toString();
    }
    private static String readOpenDocument(File f)throws Exception{return extractZipXml(f,"content.xml");}
    private static String readOoxmlText(File f)throws Exception{
        try(ZipFile z=new ZipFile(f)){StringBuilder b=new StringBuilder();Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(e.isDirectory()||!e.getName().endsWith(".xml")||e.getSize()>8L*1024*1024)continue;String n=e.getName().toLowerCase(Locale.ROOT);if(!(n.contains("document")||n.contains("slide")||n.contains("sharedstrings")||n.contains("workbook")||n.contains("visio/pages/page")))continue;try(InputStream in=z.getInputStream(e)){b.append(xmlText(in)).append('\n');}if(b.length()>2_000_000)break;}if(b.length()==0)throw new IOException("No readable document text found");return b.toString();}
    }
    private static String extractZipXml(File f,String entry)throws Exception{
        try(ZipFile z=new ZipFile(f)){ZipEntry e=z.getEntry(entry);if(e==null||e.getSize()>16L*1024*1024)throw new IOException("Invalid or oversized OpenDocument XML");try(InputStream in=z.getInputStream(e)){return xmlText(in);}}
    }
    private static String xmlText(InputStream in)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;long total=0;
        while((count=in.read(buffer))!=-1){total+=count;if(total>16L*1024*1024)throw new IOException("XML part exceeds the 16 MB safety limit");bytes.write(buffer,0,count);}
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        org.w3c.dom.Document d=f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes.toByteArray()));
        StringBuilder b=new StringBuilder();appendText(d.getDocumentElement(),b);return b.toString();
    }
    private static void appendText(Node n,StringBuilder b){if(n==null)return;if(n.getNodeType()==Node.TEXT_NODE){String s=n.getNodeValue();if(s!=null&&!s.trim().isEmpty())b.append(s.trim()).append(' ');}NodeList c=n.getChildNodes();for(int i=0;i<c.getLength();i++)appendText(c.item(i),b);}
    static String stripRtf(String s){if(s==null)return "";return s.replaceAll("\\\\'[0-9a-fA-F]{2}"," ").replaceAll("\\\\[a-zA-Z]+-?\\d* ?"," ").replaceAll("[{}]"," ").replaceAll("\\s+"," ").trim();}
    static ArrayList<ArrayList<String>> parseDelimited(String input,char delimiter){ArrayList<ArrayList<String>> rows=new ArrayList<>();ArrayList<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;for(int i=0;i<(input==null?0:input.length());i++){char c=input.charAt(i);if(c=='"'){if(quoted&&i+1<input.length()&&input.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}else if(c==delimiter&&!quoted){row.add(cell.toString());cell.setLength(0);}else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;row.add(cell.toString());rows.add(row);row=new ArrayList<>();cell.setLength(0);}else cell.append(c);}boolean trailingLineBreak=input!=null&&!input.isEmpty()&&(input.charAt(input.length()-1)=='\n'||input.charAt(input.length()-1)=='\r')&&!quoted;row.add(cell.toString());if(!trailingLineBreak&&!row.isEmpty()&&!(row.size()==1&&row.get(0).isEmpty()&&rows.isEmpty()))rows.add(row);return rows;}
    static String toDelimited(List<? extends List<String>> rows,char delimiter){StringBuilder b=new StringBuilder();for(List<String> row:rows){for(int c=0;c<row.size();c++){if(c>0)b.append(delimiter);String v=row.get(c)==null?"":row.get(c);boolean q=v.indexOf(delimiter)>=0||v.indexOf('"')>=0||v.indexOf('\n')>=0||v.indexOf('\r')>=0;if(q)b.append('"');b.append(q?v.replace("\"","\"\""):v);if(q)b.append('"');}b.append('\n');}return b.toString();}
    static ArrayList<SheetData> readSheets(File f)throws Exception{
        ArrayList<SheetData> out=new ArrayList<>();String ext=extension(f.getName());if(ext.equals("csv")||ext.equals("tsv")){SheetData s=new SheetData("Sheet1");char d=ext.equals("tsv")?'\t':',';for(ArrayList<String> row:parseDelimited(readText(f),d))s.cells.add(row);out.add(s);return out;}
        try(Workbook w=WorkbookFactory.create(f)){DataFormatter fmt=new DataFormatter(Locale.ROOT);org.apache.poi.ss.usermodel.FormulaEvaluator ev=w.getCreationHelper().createFormulaEvaluator();for(int i=0;i<w.getNumberOfSheets();i++){SheetData s=new SheetData(w.getSheetName(i));org.apache.poi.ss.usermodel.Sheet sh=w.getSheetAt(i);for(int r=0;r<=Math.min(sh.getLastRowNum(),1999);r++){Row row=sh.getRow(r);ArrayList<String> vals=new ArrayList<>();int n=row==null?0:Math.min(Math.max(0,row.getLastCellNum()),40);for(int c=0;c<n;c++){Cell cell=row.getCell(c);String v="";if(cell!=null){if(cell.getCellType()==CellType.FORMULA)v="="+cell.getCellFormula();else v=fmt.formatCellValue(cell,ev);}vals.add(v);}s.cells.add(vals);}out.add(s);}}return out;
    }
    static void writeWorkbook(File out,List<SheetData> sheets)throws Exception{
        if(sheets==null||sheets.isEmpty())throw new IOException("At least one worksheet is required");if(sheets.size()>100)throw new IOException("Workbook exceeds the 100-sheet limit");try(Workbook w=new XSSFWorkbook()){int si=0;for(SheetData data:sheets){String name=safeSheetName(data.name,si++);org.apache.poi.ss.usermodel.Sheet sh=w.createSheet(name);for(int r=0;r<Math.min(data.cells.size(),2000);r++){Row row=sh.createRow(r);ArrayList<String> vals=data.cells.get(r);for(int c=0;c<Math.min(vals.size(),40);c++){String v=vals.get(c);if(v==null||v.isEmpty())continue;Cell cell=row.createCell(c);if(v.startsWith("=")&&v.length()>1){try{cell.setCellFormula(v.substring(1));}catch(Exception bad){cell.setCellValue(v);}}else if(v.matches("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")){try{cell.setCellValue(Double.parseDouble(v));}catch(Exception bad){cell.setCellValue(v);}}else cell.setCellValue(v);}}}try(OutputStream os=new FileOutputStream(out)){w.write(os);}}
    }
    private static String safeSheetName(String s,int i){String n=(s==null||s.trim().isEmpty()?"Sheet"+(i+1):s).replaceAll("[\\\\/?*\\[\\]:]","_");return n.length()>31?n.substring(0,31):n;}
    static void writeWord(File out,CharSequence text)throws Exception{
        try(XWPFDocument d=new XWPFDocument()){String value=text==null?"":text.toString();if(value.length()>5_000_000)throw new IOException("Word document exceeds the 5 MB editor limit");String[] lines=value.split("\\n",-1);Spanned sp=text instanceof Spanned?(Spanned)text:null;int offset=0;for(String line:lines){XWPFParagraph p=d.createParagraph();p.setAlignment(ParagraphAlignment.LEFT);if(line.isEmpty())p.createRun().setText("");else if(sp==null)p.createRun().setText(line);else{int pos=0;while(pos<line.length()){int flags=0;StyleSpan[] spans=sp.getSpans(offset+pos,offset+pos+1,StyleSpan.class);for(StyleSpan st:spans)flags|=st.getStyle();int end=pos+1;while(end<line.length()){int f2=0;for(StyleSpan st:sp.getSpans(offset+end,offset+end+1,StyleSpan.class))f2|=st.getStyle();if(f2!=flags)break;end++;}XWPFRun run=p.createRun();run.setBold((flags&Typeface.BOLD)!=0);run.setItalic((flags&Typeface.ITALIC)!=0);run.setText(line.substring(pos,end));pos=end;}}offset+=line.length()+1;}try(OutputStream os=new FileOutputStream(out)){d.write(os);}}
    }
    static PresentationData readPresentation(File f)throws Exception{
        if(!extension(f.getName()).equals("pptx")&&!extension(f.getName()).equals("ppsx")&&!extension(f.getName()).equals("potx"))throw new IOException("Presentation format is view-only or unsupported");
        PresentationData data=new PresentationData();
        try(ZipFile z=new ZipFile(f)){
            ArrayList<ZipEntry> slides=new ArrayList<>();Enumeration<? extends ZipEntry> entries=z.entries();
            while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();if(!e.isDirectory()&&e.getName().matches("ppt/slides/slide[0-9]+\\.xml"))slides.add(e);}
            slides.sort(Comparator.comparingInt(e->{Matcher m=Pattern.compile("slide([0-9]+)\\.xml").matcher(e.getName());return m.find()?Integer.parseInt(m.group(1)):Integer.MAX_VALUE;}));
            if(slides.size()>500)throw new IOException("Presentation exceeds the 500-slide limit");
            int totalText=0;
            for(ZipEntry e:slides){if(e.getSize()>8L*1024*1024)throw new IOException("Slide XML exceeds the 8 MB limit");try(InputStream in=z.getInputStream(e)){String slide=xmlText(in);totalText+=slide.length();if(totalText>2_000_000)throw new IOException("Presentation text exceeds the 2 MB preview limit");data.slides.add(slide);}}
        }
        if(data.slides.isEmpty())throw new IOException("No readable slides found in this presentation");
        return data;
    }
    static String extension(String name){int i=name==null?-1:name.lastIndexOf('.');return i<0?"":name.substring(i+1).toLowerCase(Locale.ROOT);}
    static String exportRtf(String text){String s=text==null?"":text;return "{\\rtf1\\ansi\\deff0 "+s.replace("\\","\\\\").replace("{","\\{").replace("}","\\}").replace("\n","\\par\n")+"}";}
}
