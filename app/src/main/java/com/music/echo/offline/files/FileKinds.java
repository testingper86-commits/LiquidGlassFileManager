package com.music.echo.offline.files;

import android.graphics.Color;
import android.webkit.MimeTypeMap;
import java.util.HashMap;

/** File type detection, colours and MIME types. */
final class FileKinds {
    static final int DIR = 0, IMAGE = 1, VIDEO = 2, AUDIO = 3, PDF = 4,
            TEXT = 5, ARCHIVE = 6, APK = 7, OTHER = 8;
    private static final HashMap<String, Integer> M = new HashMap<>();

    static {
        put(IMAGE, "jpg jpeg png webp gif bmp heic heif avif");
        put(VIDEO, "mp4 mkv webm 3gp mov avi m4v ts flv wmv mpg mpeg");
        put(AUDIO, "mp3 wav ogg m4a flac aac opus amr wma mid midi");
        put(PDF, "pdf");
        put(TEXT, "txt md log json xml html htm css js java kt py c cpp h sh ini conf yml yaml srt vcf");
        put(ARCHIVE, "zip rar 7z tar gz tgz bz2 xz");
        put(APK, "apk");
    }

    private static void put(int kind, String extensions) {
        for (String extension : extensions.split(" ")) M.put(extension, kind);
    }

    static int kindOfExt(String extension) {
        Integer kind = M.get(extension);
        return kind == null ? OTHER : kind;
    }

    static boolean isHtmlExtension(String extension) {
        if (extension == null) return false;
        String normalized = extension.trim().toLowerCase(java.util.Locale.ROOT);
        return "html".equals(normalized) || "htm".equals(normalized);
    }

    static int color(int kind) {
        switch (kind) {
            case DIR: return Color.rgb(255, 176, 64);
            case IMAGE: return Color.rgb(46, 196, 140);
            case VIDEO: return Color.rgb(236, 72, 130);
            case AUDIO: return Color.rgb(160, 90, 250);
            case PDF: return Color.rgb(232, 66, 66);
            case TEXT: return Color.rgb(100, 125, 160);
            case ARCHIVE: return Color.rgb(214, 150, 40);
            case APK: return Color.rgb(70, 190, 110);
            default: return Color.rgb(112, 122, 148);
        }
    }

    static int glyph(int kind) {
        switch (kind) {
            case DIR: return Glyphs.FOLDER;
            case IMAGE: return Glyphs.IMAGE;
            case VIDEO: return Glyphs.VIDEO;
            case AUDIO: return Glyphs.AUDIO;
            case PDF: case TEXT: return Glyphs.DOC;
            case ARCHIVE: return Glyphs.ARCHIVE;
            case APK: return Glyphs.APK;
            default: return Glyphs.FILE;
        }
    }

    static String mime(Entry entry) {
        if (entry.kind == APK) return "application/vnd.android.package-archive";
        String mime = entry.ext.isEmpty() ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(entry.ext);
        if (mime != null) return mime;
        switch (entry.kind) {
            case IMAGE: return "image/*";
            case VIDEO: return "video/*";
            case AUDIO: return "audio/*";
            case TEXT: return "text/plain";
            default: return "*/*";
        }
    }
}
