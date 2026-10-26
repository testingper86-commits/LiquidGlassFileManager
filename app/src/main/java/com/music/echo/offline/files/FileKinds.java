package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.graphics.Color;
import android.webkit.MimeTypeMap;
import java.util.HashMap;

/** File type detection, colours and MIME types. */
final class FileKinds {
    static final int DIR = 0, IMAGE = 1, VIDEO = 2, AUDIO = 3, PDF = 4, DOC = 5, SHEET = 6, SLIDE = 7,
            TEXT = 8, ARCHIVE = 9, APK = 10, OTHER = 11;
    private static final HashMap<String, Integer> M = new HashMap<>();

    static {
        put(IMAGE, "jpg jpeg png webp gif bmp heic heif avif");
        put(VIDEO, "mp4 mkv webm 3gp mov avi m4v ts flv wmv mpg mpeg");
        put(AUDIO, "mp3 wav ogg m4a flac aac opus amr wma mid midi");
        put(PDF, "pdf");
        put(DOC, "doc docx odt rtf");
        put(SHEET, "xls xlsx ods");
        put(SLIDE, "ppt pptx odp");
        put(TEXT, "txt md log json xml html htm css js java kt py c cpp h sh ini conf yml yaml csv srt vcf");
        put(ARCHIVE, "zip rar 7z tar gz tgz bz2 xz");
        put(APK, "apk");
    }

    private static void put(int k, String s) { for (String e : s.split(" ")) M.put(e, k); }

    static int kindOfExt(String e) { Integer k = M.get(e); return k == null ? OTHER : k; }

    static int color(int k) {
        switch (k) {
            case DIR: return Color.rgb(255, 176, 64);
            case IMAGE: return Color.rgb(46, 196, 140);
            case VIDEO: return Color.rgb(236, 72, 130);
            case AUDIO: return Color.rgb(160, 90, 250);
            case PDF: return Color.rgb(232, 66, 66);
            case DOC: return Color.rgb(64, 130, 245);
            case SHEET: return Color.rgb(40, 160, 90);
            case SLIDE: return Color.rgb(240, 128, 40);
            case TEXT: return Color.rgb(100, 125, 160);
            case ARCHIVE: return Color.rgb(214, 150, 40);
            case APK: return Color.rgb(70, 190, 110);
            default: return Color.rgb(112, 122, 148);
        }
    }

    static int glyph(int k) {
        switch (k) {
            case DIR: return Glyphs.FOLDER;
            case IMAGE: return Glyphs.IMAGE;
            case VIDEO: return Glyphs.VIDEO;
            case AUDIO: return Glyphs.AUDIO;
            case PDF: case DOC: case SHEET: case SLIDE: case TEXT: return Glyphs.DOC;
            case ARCHIVE: return Glyphs.ARCHIVE;
            case APK: return Glyphs.APK;
            default: return Glyphs.FILE;
        }
    }

    static String mime(Entry e) {
        if (e.kind == APK) return "application/vnd.android.package-archive";
        String m = e.ext.isEmpty() ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(e.ext);
        if (m != null) return m;
        switch (e.kind) {
            case IMAGE: return "image/*";
            case VIDEO: return "video/*";
            case AUDIO: return "audio/*";
            case TEXT: return "text/plain";
            default: return "*/*";
        }
    }
}
