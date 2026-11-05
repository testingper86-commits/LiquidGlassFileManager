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
import android.text.style.UnderlineSpan;
import android.text.style.RelativeSizeSpan;
import android.graphics.Typeface;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

/** Offline format catalog and bounded Apache POI adapters. Macro content is never executed. */
final class OfficeDocumentEngine {
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
        add(f,"docx","Word","Word document",Level.PARTIAL,"Edit text and basic character formatting; advanced layout, fields and embedded objects are not fully preserved.");
        add(f,"doc","Word","Legacy Word",Level.VIEW_ONLY,"Read-only text extraction for legacy DOC files.");
        add(f,"xlsx","Excel","Excel workbook",Level.PARTIAL,"Edit worksheets, cell values and supported formulas; advanced charts, pivots and external links may not be preserved.");
        add(f,"xls","Excel","Legacy Excel",Level.PARTIAL,"Edit cell data and formulas; advanced charts, macros and unsupported BIFF records may not survive XLSX export.");
        add(f,"csv","Excel","CSV spreadsheet",Level.FULL,"Edit a single worksheet and export as XLSX.");
        add(f,"tsv","Excel","TSV spreadsheet",Level.FULL,"Edit a single worksheet and export as XLSX.");
        add(f,"pptx","PowerPoint","PowerPoint presentation",Level.PARTIAL,"Edit slide text while retaining package assets; advanced layout, transitions and animations are not editable.");
        add(f,"ppt","PowerPoint","Legacy PowerPoint",Level.VIEW_ONLY,"Read-only text extraction for legacy PPT files.");
        add(f,"pdf","PDF","PDF document",Level.PARTIAL,"Open with the native Android PDF renderer; annotations are exported as a separate flattened copy.");
        CATALOG=Collections.unmodifiableList(f);
    }
    private OfficeDocumentEngine() {}
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
static boolean editable(String name){FormatInfo f=format(name);return f!=null&&(f.level==Level.FULL||f.level==Level.PARTIAL)&&Arrays.asList("docx","xlsx","xls","csv","tsv").contains(f.ext);}
static String extractText(File file) throws Exception {
        if(file==null||!file.isFile())throw new FileNotFoundException("Document is unavailable");
        if(file.length()>50L*1024*1024)throw new IOException("File exceeds the 50 MB processing limit");
        String ext=extension(file.getName());
        if(ext.equals("docx"))return readDocx(file);
        if(ext.equals("doc"))return readDoc(file);
        if(ext.equals("xlsx")||ext.equals("xls"))return readWorkbook(file);
        if(ext.equals("csv")||ext.equals("tsv"))return readText(file);
        if(ext.equals("pptx"))return readPptx(file);
        if(ext.equals("ppt"))return readLegacyPpt(file);
        throw new IOException("Unsupported document format");
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
    private static String readLegacyPpt(File f)throws Exception{
        if(f.length()>50L*1024*1024)throw new IOException("Legacy presentation exceeds the 50 MB processing limit");
        try(org.apache.poi.sl.usermodel.SlideShow<?,?> slideshow=org.apache.poi.sl.usermodel.SlideShowFactory.create(f)){
            org.apache.poi.sl.extractor.SlideShowExtractor extractor=new org.apache.poi.sl.extractor.SlideShowExtractor(slideshow);
            extractor.setCloseFilesystem(false);
            String text=extractor.getText();if(text==null)text="";if(text.length()>2_000_000)throw new IOException("Legacy presentation text exceeds the 2 MB preview limit");return text;
        }
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
    static ArrayList<ArrayList<String>> parseDelimited(String input,char delimiter){ArrayList<ArrayList<String>> rows=new ArrayList<>();ArrayList<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;for(int i=0;i<(input==null?0:input.length());i++){char c=input.charAt(i);if(c=='"'){if(quoted&&i+1<input.length()&&input.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}else if(c==delimiter&&!quoted){row.add(cell.toString());cell.setLength(0);}else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;row.add(cell.toString());rows.add(row);row=new ArrayList<>();cell.setLength(0);}else cell.append(c);}boolean trailingLineBreak=input!=null&&!input.isEmpty()&&(input.charAt(input.length()-1)=='\n'||input.charAt(input.length()-1)=='\r')&&!quoted;row.add(cell.toString());if(!trailingLineBreak&&!row.isEmpty()&&!(row.size()==1&&row.get(0).isEmpty()&&rows.isEmpty()))rows.add(row);return rows;}
    static String toDelimited(List<? extends List<String>> rows,char delimiter){StringBuilder b=new StringBuilder();for(List<String> row:rows){for(int c=0;c<row.size();c++){if(c>0)b.append(delimiter);String v=row.get(c)==null?"":row.get(c);boolean q=v.indexOf(delimiter)>=0||v.indexOf('"')>=0||v.indexOf('\n')>=0||v.indexOf('\r')>=0;if(q)b.append('"');b.append(q?v.replace("\"","\"\""):v);if(q)b.append('"');}b.append('\n');}return b.toString();}
    static ArrayList<SheetData> readSheets(File f)throws Exception{
        ArrayList<SheetData> out=new ArrayList<>();String ext=extension(f.getName());if(ext.equals("csv")||ext.equals("tsv")){SheetData s=new SheetData("Sheet1");char d=ext.equals("tsv")?'\t':',';for(ArrayList<String> row:parseDelimited(readText(f),d))s.cells.add(row);out.add(s);return out;}
        try(Workbook w=WorkbookFactory.create(f)){DataFormatter fmt=new DataFormatter(Locale.ROOT);org.apache.poi.ss.usermodel.FormulaEvaluator ev=w.getCreationHelper().createFormulaEvaluator();for(int i=0;i<w.getNumberOfSheets();i++){SheetData s=new SheetData(w.getSheetName(i));org.apache.poi.ss.usermodel.Sheet sh=w.getSheetAt(i);for(int r=0;r<=Math.min(sh.getLastRowNum(),1999);r++){Row row=sh.getRow(r);ArrayList<String> vals=new ArrayList<>();int n=row==null?0:Math.min(Math.max(0,row.getLastCellNum()),40);for(int c=0;c<n;c++){Cell cell=row.getCell(c);String v="";if(cell!=null){if(cell.getCellType()==CellType.FORMULA)v="="+cell.getCellFormula();else v=fmt.formatCellValue(cell,ev);}vals.add(v);}s.cells.add(vals);}out.add(s);}}return out;
    }
    static void writeWorkbook(File out,List<SheetData> sheets)throws Exception{
        if(sheets==null||sheets.isEmpty())throw new IOException("At least one worksheet is required");if(sheets.size()>100)throw new IOException("Workbook exceeds the 100-sheet limit");try(Workbook w=new XSSFWorkbook()){int si=0;for(SheetData data:sheets){String name=safeSheetName(data.name,si++);org.apache.poi.ss.usermodel.Sheet sh=w.createSheet(name);for(int r=0;r<Math.min(data.cells.size(),2000);r++){Row row=sh.createRow(r);ArrayList<String> vals=data.cells.get(r);for(int c=0;c<Math.min(vals.size(),40);c++){String v=vals.get(c);if(v==null||v.isEmpty())continue;Cell cell=row.createCell(c);if(v.startsWith("=")&&v.length()>1){try{cell.setCellFormula(v.substring(1));}catch(Exception bad){cell.setCellValue(v);}}else if(v.matches("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")){try{cell.setCellValue(Double.parseDouble(v));}catch(Exception bad){cell.setCellValue(v);}}else cell.setCellValue(v);}}}try{w.getCreationHelper().createFormulaEvaluator().evaluateAll();}catch(RuntimeException unsupportedFormula){/* Preserve formulas; unsupported functions remain unevaluated. */}try(OutputStream os=new FileOutputStream(out)){w.write(os);}}
    }
    private static String safeSheetName(String s,int i){String n=(s==null||s.trim().isEmpty()?"Sheet"+(i+1):s).replaceAll("[\\\\/?*\\[\\]:]","_");return n.length()>31?n.substring(0,31):n;}
    static void writeWord(File out,CharSequence text)throws Exception{
        try(XWPFDocument d=new XWPFDocument()){String value=text==null?"":text.toString();if(value.length()>5_000_000)throw new IOException("Word document exceeds the 5 MB editor limit");String[] lines=value.split("\\n",-1);Spanned sp=text instanceof Spanned?(Spanned)text:null;int offset=0;for(String line:lines){XWPFParagraph p=d.createParagraph();p.setAlignment(ParagraphAlignment.LEFT);if(line.isEmpty())p.createRun().setText("");else if(sp==null)p.createRun().setText(line);else{int pos=0;while(pos<line.length()){int flags=0;StyleSpan[] spans=sp.getSpans(offset+pos,offset+pos+1,StyleSpan.class);for(StyleSpan st:spans)flags|=st.getStyle();boolean underline=sp.getSpans(offset+pos,offset+pos+1,UnderlineSpan.class).length>0;float relative=1f;RelativeSizeSpan[] sizes=sp.getSpans(offset+pos,offset+pos+1,RelativeSizeSpan.class);if(sizes.length>0)relative=sizes[0].getSizeChange();int end=pos+1;while(end<line.length()){int f2=0;for(StyleSpan st:sp.getSpans(offset+end,offset+end+1,StyleSpan.class))f2|=st.getStyle();boolean u2=sp.getSpans(offset+end,offset+end+1,UnderlineSpan.class).length>0;float r2=1f;RelativeSizeSpan[] z2=sp.getSpans(offset+end,offset+end+1,RelativeSizeSpan.class);if(z2.length>0)r2=z2[0].getSizeChange();if(f2!=flags||u2!=underline||Math.abs(r2-relative)>.001f)break;end++;}XWPFRun run=p.createRun();run.setBold((flags&Typeface.BOLD)!=0);run.setItalic((flags&Typeface.ITALIC)!=0);run.setUnderline(underline?org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE:org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);if(relative>1.01f)run.setFontSize(Math.max(10,Math.min(36,Math.round(11f*relative))));run.setText(line.substring(pos,end));pos=end;}}offset+=line.length()+1;}try(OutputStream os=new FileOutputStream(out)){d.write(os);}}
    }
    static PresentationData readPresentation(File f)throws Exception{
        if(!extension(f.getName()).equals("pptx"))throw new IOException("Presentation format is view-only or unsupported");
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

    static void writePresentationText(File source,File output,int slideIndex,String text)throws Exception{
        if(source==null||!source.isFile()||source.length()>100L*1024*1024)throw new IOException("Presentation is unavailable or exceeds the 100 MB limit");
        if(slideIndex<0)throw new IOException("Invalid slide selection");
        String value=text==null?"":text.replace('\r',' ').replace('\n',' ');
        if(value.length()>50000)throw new IOException("Slide text exceeds the 50 KB editing limit");
        try(ZipFile zip=new ZipFile(source)){
            ArrayList<String> slideNames=new ArrayList<>();Enumeration<? extends ZipEntry> all=zip.entries();
            while(all.hasMoreElements()){ZipEntry e=all.nextElement();if(!e.isDirectory()&&e.getName().matches("ppt/slides/slide[0-9]+\\.xml"))slideNames.add(e.getName());}
            slideNames.sort(Comparator.comparingInt(n->{Matcher m=Pattern.compile("slide([0-9]+)\\.xml").matcher(n);return m.find()?Integer.parseInt(m.group(1)):Integer.MAX_VALUE;}));
            if(slideIndex>=slideNames.size())throw new IOException("Selected slide no longer exists");
            String target=slideNames.get(slideIndex);File parent=output.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create presentation destination");
            long total=0;boolean changed=false;
            try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))){
                Enumeration<? extends ZipEntry> entries=zip.entries();byte[] buffer=new byte[32768];
                while(entries.hasMoreElements()){
                    ZipEntry entry=entries.nextElement();if(entry.isDirectory())continue;
                    if(entry.getSize()>50L*1024*1024)throw new IOException("Presentation contains an oversized package part");
                    out.putNextEntry(new ZipEntry(entry.getName()));
                    try(InputStream in=zip.getInputStream(entry)){
                        if(entry.getName().equals(target)){
                            ByteArrayOutputStream bytes=new ByteArrayOutputStream();int n;while((n=in.read(buffer))!=-1){total+=n;if(total>150L*1024*1024||bytes.size()+n>8L*1024*1024)throw new IOException("Presentation package exceeds its safe processing limit");bytes.write(buffer,0,n);}
                            javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
                            org.w3c.dom.Document doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes.toByteArray()));org.w3c.dom.NodeList nodes=doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main","t");if(nodes.getLength()==0)throw new IOException("Selected slide has no editable text runs");
                            nodes.item(0).setTextContent(value);for(int i=1;i<nodes.getLength();i++)nodes.item(i).setTextContent("");
                            javax.xml.transform.TransformerFactory tf=javax.xml.transform.TransformerFactory.newInstance();tf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING,true);javax.xml.transform.Transformer transformer=tf.newTransformer();transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING,"UTF-8");transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION,"no");transformer.transform(new javax.xml.transform.dom.DOMSource(doc),new javax.xml.transform.stream.StreamResult(out));changed=true;
                        }else{int n;while((n=in.read(buffer))!=-1){total+=n;if(total>150L*1024*1024)throw new IOException("Presentation package exceeds its safe processing limit");out.write(buffer,0,n);}}
                    }
                    out.closeEntry();
                }
            }
            if(!changed){output.delete();throw new IOException("Selected slide could not be updated");}
        }catch(Exception e){if(output.exists())output.delete();throw e;}
    }

    static String extension(String name){int i=name==null?-1:name.lastIndexOf('.');return i<0?"":name.substring(i+1).toLowerCase(Locale.ROOT);}
}
