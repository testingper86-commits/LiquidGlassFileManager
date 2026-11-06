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
        put(TEXT, "txt text md markdown rst adoc log csv tsv json jsonl ndjson xml xsd xsl xslt css scss less js mjs cjs jsx ts tsx java kt kts py pyw rb go rs swift c h cc cpp cxx hpp hxx cs fs vb php phtml sql sh bash zsh fish bat cmd ps1 ini cfg conf config properties toml env yml yaml srt vtt ass ssa vcf ics diff patch gradle kts make mk cmake dockerfile gitignore editorconfig");
        put(ARCHIVE, "zip rar 7z tar gz tgz bz2 xz");
        put(APK, "apk");
    }

    private static void put(int kind, String extensions) {
        for (String extension : extensions.split(" ")) M.put(extension, kind);
    }

    static String extensionOf(java.io.File file) {
        if (file == null) return "";
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1
                ? name.substring(dot + 1).trim().toLowerCase(java.util.Locale.ROOT) : "";
    }

    static int kindOfExt(String extension) {
        if (extension == null) return OTHER;
        Integer kind = M.get(extension.trim().toLowerCase(java.util.Locale.ROOT));
        return kind == null ? OTHER : kind;
    }

    static int kindOfFile(java.io.File file) {
        if (file == null) return OTHER;
        if (file.isDirectory()) return DIR;
        return kindOfExt(extensionOf(file));
    }

    static boolean isPdfExtension(String extension) {
        return "pdf".equals(extension == null ? "" : extension.trim().toLowerCase(java.util.Locale.ROOT));
    }

    static boolean isHtmlExtension(String extension) {
        if (extension == null) return false;
        String normalized = extension.trim().toLowerCase(java.util.Locale.ROOT);
        return "html".equals(normalized) || "htm".equals(normalized)
                || "xhtml".equals(normalized) || "xht".equals(normalized)
                || "shtml".equals(normalized) || "shtm".equals(normalized);
    }

    static int color(int kind) {
        switch (kind) {
            case DIR: return 0xFFB9C7D8;
            case IMAGE: return 0xFF6ACFB0;
            case VIDEO: return 0xFFFF8C72;
            case AUDIO: return 0xFFB49AFF;
            case PDF: return 0xFFFA6F68;
            case TEXT: return 0xFF77B8E8;
            case ARCHIVE: return 0xFFE2B66F;
            case APK: return 0xFF9BCB78;
            default: return 0xFF9AA8B7;
        }
    }

    static int glyph(int kind) {
        switch (kind) {
            case DIR: return ToxiIcons.FOLDER;
            case IMAGE: return ToxiIcons.IMAGE;
            case VIDEO: return ToxiIcons.VIDEO;
            case AUDIO: return ToxiIcons.AUDIO;
            case PDF: return ToxiIcons.PDF;
            case TEXT: return ToxiIcons.TEXT;
            case ARCHIVE: return ToxiIcons.ARCHIVE;
            case APK: return ToxiIcons.APK;
            default: return ToxiIcons.FILE;
        }
    }

    static String mime(Entry entry) {
        if (isPdfExtension(entry.ext)) return "application/pdf";
        if (isHtmlExtension(entry.ext)) return "text/html";
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
