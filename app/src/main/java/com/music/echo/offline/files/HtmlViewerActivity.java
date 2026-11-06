package com.music.echo.offline.files;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Private HTML viewer launched only by MainActivity's existing file-opening route. */
public final class HtmlViewerActivity extends Activity {
    static final String EXTRA_FILE_PATH = "html_file_path";
    private static final String ASSET_DOMAIN_SUFFIX = ".appassets.androidplatform.net";
    private String assetHost;
    private WebView webView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ToxiVisualSystem.initialize(this);
        if (AppLock.enabled(this)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);

        String rawPath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        final File document;
        try {
            if (rawPath == null || rawPath.trim().isEmpty()) throw new IOException("Missing document path");
            document = new File(rawPath).getCanonicalFile();
            if (!document.isFile() || !document.canRead()
                    || !FileKinds.isHtmlExtension(FileKinds.extensionOf(document))) {
                throw new IOException("Unsupported or unreadable HTML document");
            }
        } catch (IOException | SecurityException ex) {
            ToxiVisualSystem.toast(this, "Can't open this HTML file");
            finish();
            return;
        }

        assetHost = "doc-" + documentOriginId(document) + ASSET_DOMAIN_SUFFIX;

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ToxiVisualSystem.CANVAS);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(ToxiVisualSystem.surface(24, true));
        bar.setPadding(ToxiVisualSystem.dp(5), 0, ToxiVisualSystem.dp(12), 0);
        ImageView back = ToxiVisualSystem.iconButton(this, ToxiIcons.BACK, ToxiVisualSystem.TEXT_PRIMARY);
        back.setContentDescription("Back to file manager");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(ToxiVisualSystem.dp(46), ToxiVisualSystem.dp(46)));
        TextView title = ToxiVisualSystem.text(this, document.getName(), 14, ToxiVisualSystem.TEXT_PRIMARY, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView reload = ToxiVisualSystem.iconButton(this, ToxiIcons.REFRESH, ToxiVisualSystem.TEXT_PRIMARY);
        reload.setContentDescription("Reload HTML document");
        reload.setOnClickListener(v -> webView.reload());
        LinearLayout.LayoutParams reloadLp = new LinearLayout.LayoutParams(ToxiVisualSystem.dp(44), ToxiVisualSystem.dp(44));
        reloadLp.leftMargin = ToxiVisualSystem.dp(6);
        bar.addView(reload, reloadLp);
        column.addView(bar, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(56)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSafeBrowsingEnabled(true);
        settings.setDefaultTextEncodingName("UTF-8");

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(assetHost)
                .addPathHandler("/local/", new ConfinedDirectoryHandler(document.getParentFile(), document))
                .build();
        webView.setWebViewClient(new WebViewClientCompat() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return intercept(loader, request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return intercept(loader, Uri.parse(url));
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isLocalDocumentUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isLocalDocumentUrl(Uri.parse(url));
            }
        });
        column.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        DynamicIslandView island = new DynamicIslandView(this);
        root.addView(island, new FrameLayout.LayoutParams(-1,-1));
        webView.loadUrl("https://" + assetHost + "/local/" + Uri.encode(document.getName()));
    }

    private WebResourceResponse intercept(WebViewAssetLoader loader, Uri uri) {
        if (!isLocalDocumentUrl(uri)) return blockedResponse();
        WebResourceResponse response = loader.shouldInterceptRequest(uri);
        return response != null ? response : blockedResponse();
    }

    private boolean isLocalDocumentUrl(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && assetHost != null && assetHost.equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/local/");
    }

    private static String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static String documentOriginId(File document) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(document.getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder(32);
            for (int i = 0; i < 16; i++) id.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            return id.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static WebResourceResponse blockedResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                java.util.Collections.<String, String>emptyMap(),
                new java.io.ByteArrayInputStream(new byte[0]));
    }

    private static final class ConfinedDirectoryHandler implements WebViewAssetLoader.PathHandler {
        private final File root;
        private final String rootPrefix;
        private final String documentCanonicalPath;

        ConfinedDirectoryHandler(File directory, File document) {
            String selectedPath;
            try { selectedPath = document == null ? null : document.getCanonicalPath(); }
            catch (IOException | SecurityException ex) { selectedPath = null; }
            documentCanonicalPath = selectedPath;
            File canonicalDirectory;
            try {
                canonicalDirectory = directory == null ? null : directory.getCanonicalFile();
            } catch (IOException | SecurityException ex) {
                canonicalDirectory = null;
            }
            root = canonicalDirectory;
            String base = canonicalDirectory == null ? null : canonicalDirectory.getPath();
            rootPrefix = base == null ? null
                    : (base.endsWith(File.separator) ? base : base + File.separator);
        }

        @Override public WebResourceResponse handle(String path) {
            if (root == null || rootPrefix == null || path == null || path.isEmpty()
                    || path.indexOf((char) 0) >= 0 || path.indexOf(92) >= 0) return null;
            try {
                String decoded = Uri.decode(path);
                if (decoded.isEmpty() || decoded.indexOf((char) 0) >= 0 || decoded.indexOf(92) >= 0
                        || decoded.startsWith("/") || decoded.equals("..") || decoded.equals(".")
                        || decoded.contains("../") || decoded.contains("/..") || decoded.contains("./")) {
                    return null;
                }
                File requested = new File(root, decoded);
                String canonical = requested.getCanonicalPath();
                if (!canonical.startsWith(rootPrefix) || !requested.isFile() || !requested.canRead()) return null;
                String extension = extensionOf(requested).toLowerCase(Locale.ROOT);
                if (!canonical.equals(documentCanonicalPath) && !isAllowedAssetExtension(extension)) return null;
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime == null) mime = "application/octet-stream";
                String charset = mime.startsWith("text/") || mime.contains("javascript")
                        || mime.contains("json") || mime.contains("xml") ? "UTF-8" : null;
                InputStream stream = new FileInputStream(requested);
                return new WebResourceResponse(mime, charset, stream);
            } catch (IOException | SecurityException ex) {
                return null;
            }
        }

        private static boolean isAllowedAssetExtension(String extension) {
            switch (extension) {
                case "css": case "js": case "mjs": case "json":
                case "png": case "jpg": case "jpeg": case "gif": case "webp": case "bmp": case "svg": case "ico":
                case "woff": case "woff2": case "ttf": case "otf":
                case "mp3": case "m4a": case "wav": case "ogg": case "mp4": case "webm": case "m4v":
                    return true;
                default: return false;
            }
        }
    }

    @Override public void onBackPressed() { finish(); }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
