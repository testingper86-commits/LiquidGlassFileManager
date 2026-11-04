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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Private HTML viewer launched only by MainActivity's existing file-opening route. */
public final class HtmlViewerActivity extends Activity {
    static final String EXTRA_FILE_PATH = "html_file_path";
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private WebView webView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        if (AppLock.enabled(this)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);

        String rawPath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        final File document;
        try {
            if (rawPath == null || rawPath.trim().isEmpty()) throw new IOException("Missing document path");
            document = new File(rawPath).getCanonicalFile();
            if (!document.isFile() || !document.canRead()
                    || !FileKinds.isHtmlExtension(extensionOf(document))) {
                throw new IOException("Unsupported or unreadable HTML document");
            }
        } catch (IOException | SecurityException ex) {
            Ui.toast(this, "Can't open this HTML file");
            finish();
            return;
        }

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05070C);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.glass(24, true));
        bar.setPadding(Ui.dp(5), 0, Ui.dp(12), 0);
        TextView back = Ui.tv(this, "‹", 30, Ui.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back to file manager");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(52)));
        TextView title = Ui.tv(this, document.getName(), 14, Ui.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView reload = Ui.tv(this, "↻", 23, Ui.WHITE, false);
        reload.setGravity(Gravity.CENTER);
        reload.setContentDescription("Reload HTML document");
        reload.setOnClickListener(v -> webView.reload());
        bar.addView(reload, new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(48)));
        column.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(56)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
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
                .addPathHandler("/local/", new ConfinedDirectoryHandler(document.getParentFile()))
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
        webView.loadUrl("https://" + ASSET_HOST + "/local/" + Uri.encode(document.getName()));
    }

    private static WebResourceResponse intercept(WebViewAssetLoader loader, Uri uri) {
        if (!isLocalDocumentUrl(uri)) return blockedResponse();
        WebResourceResponse response = loader.shouldInterceptRequest(uri);
        return response != null ? response : blockedResponse();
    }

    private static boolean isLocalDocumentUrl(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && ASSET_HOST.equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/local/");
    }

    private static String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static WebResourceResponse blockedResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                java.util.Collections.<String, String>emptyMap(),
                new java.io.ByteArrayInputStream(new byte[0]));
    }

    private static final class ConfinedDirectoryHandler implements WebViewAssetLoader.PathHandler {
        private final File root;
        private final String rootPrefix;

        ConfinedDirectoryHandler(File directory) {
            root = directory;
            String base;
            try { base = directory.getCanonicalPath(); }
            catch (IOException ex) { base = directory.getAbsolutePath(); }
            rootPrefix = base.endsWith(File.separator) ? base : base + File.separator;
        }

        @Override public WebResourceResponse handle(String path) throws IOException {
            if (path == null || path.isEmpty() || path.indexOf((char) 0) >= 0
                    || path.indexOf('\\') >= 0) return null;
            String decoded = Uri.decode(path);
            if (decoded.startsWith("/") || decoded.equals("..") || decoded.equals(".")
                    || decoded.contains("../") || decoded.contains("/..") || decoded.contains("./")) return null;
            File requested = new File(root, decoded);
            String canonical = requested.getCanonicalPath();
            if (!canonical.startsWith(rootPrefix) || !requested.isFile() || !requested.canRead()) return null;
            String extension = extensionOf(requested).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime == null) mime = "application/octet-stream";
            String charset = mime.startsWith("text/") || mime.contains("javascript")
                    || mime.contains("json") || mime.contains("xml") ? "UTF-8" : null;
            InputStream stream = new FileInputStream(requested);
            return new WebResourceResponse(mime, charset, stream);
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
