package com.music.echo.offline.files;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Free public-audio search using the Internet Archive API; playback uses the existing app service. */
public final class FreeOnlineMusicActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout results;
    private EditText query;
    private TextView status;
    private Button search;
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(13, 17, 28));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(34), dp(20), dp(24));
        scroll.addView(page);
        TextView title = new TextView(this);
        title.setText("Free Online Music");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, 1);
        page.addView(title);
        TextView note = new TextView(this);
        note.setText("Search publicly available audio on the Internet Archive. No API key or paid backend. Track availability and rights vary.");
        note.setTextColor(Color.LTGRAY);
        note.setPadding(0, dp(8), 0, dp(16));
        page.addView(note);
        query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Artist, title, or genre");
        query.setTextColor(Color.WHITE);
        query.setHintTextColor(Color.GRAY);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        page.addView(query, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout actions = new LinearLayout(this);
        search = new Button(this);
        search.setText("Search");
        actions.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button stop = new Button(this);
        stop.setText("Stop playback");
        actions.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1));
        page.addView(actions);
        status = new TextView(this);
        status.setText("Enter a search to find public audio.");
        status.setTextColor(Color.LTGRAY);
        status.setPadding(0, dp(12), 0, dp(12));
        page.addView(status);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results);
        search.setOnClickListener(v -> searchCatalog());
        query.setOnEditorActionListener((v, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) { searchCatalog(); return true; }
            return false;
        });
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, BackgroundAudioService.class);
            i.setAction(BackgroundAudioService.ACTION_STOP);
            startService(i);
            status.setText("Playback stopped.");
        });
        setContentView(scroll);
    }

    private void searchCatalog() {
        String term = query.getText().toString().trim().replace("\\", " ").replace("\"", " ");
        if (term.isEmpty()) { query.setError("Enter a search term"); return; }
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
        search.setEnabled(false);
        results.removeAllViews();
        status.setText("Searching public audio…");
        io.execute(() -> {
            try {
                String q = "mediatype:audio AND (title:\"" + term + "\" OR creator:\"" + term + "\")";
                String endpoint = "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(q, "UTF-8") +
                    "&fl[]=identifier&fl[]=title&fl[]=creator&rows=30&page=1&output=json";
                JSONArray docs = json(endpoint).getJSONObject("response").getJSONArray("docs");
                ArrayList<Item> items = new ArrayList<>();
                for (int n=0; n<docs.length(); n++) {
                    JSONObject d = docs.getJSONObject(n);
                    String id = d.optString("identifier", "");
                    if (!id.isEmpty()) items.add(new Item(id, d.optString("title", id), d.optString("creator", "Unknown artist")));
                }
                runOnUiThread(() -> {
                    if (destroyed) return;
                    search.setEnabled(true);
                    status.setText(items.size() + " results. Select Stream to play.");
                    for (Item item : items) addItem(item);
                    if (items.isEmpty()) status.setText("No results. Try a shorter or broader search.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!destroyed) { search.setEnabled(true); status.setText("Search failed: " + message(e)); } });
            }
        });
    }

    private void addItem(Item item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(Color.rgb(29, 36, 54));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        results.addView(card, lp);
        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, 1);
        card.addView(title);
        TextView artist = new TextView(this);
        artist.setText(item.creator);
        artist.setTextColor(Color.LTGRAY);
        artist.setPadding(0, dp(4), 0, dp(6));
        card.addView(artist);
        Button play = new Button(this);
        play.setText("Stream");
        play.setOnClickListener(v -> stream(item, play));
        card.addView(play, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void stream(Item item, Button button) {
        button.setEnabled(false);
        status.setText("Finding a playable file…");
        io.execute(() -> {
            try {
                JSONArray files = json("https://archive.org/metadata/" + Uri.encode(item.id)).optJSONArray("files");
                if (files == null) throw new Exception("No files listed");
                String filename = null, ogg = null;
                for (int i=0; i<files.length(); i++) {
                    JSONObject f = files.optJSONObject(i);
                    if (f == null || "metadata".equals(f.optString("source")) || "derivative".equals(f.optString("source"))) continue;
                    String name = f.optString("name", "");
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    if (lower.endsWith(".mp3")) { filename = name; break; }
                    if (ogg == null && (lower.endsWith(".ogg") || lower.endsWith(".oga"))) ogg = name;
                }
                if (filename == null) filename = ogg;
                if (filename == null) throw new Exception("No supported MP3/Ogg audio file");
                String streamUrl = "https://archive.org/download/" + Uri.encode(item.id) + "/" + Uri.encode(filename);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    button.setEnabled(true);
                    ArrayList<String> queue = new ArrayList<>();
                    queue.add(streamUrl);
                    Intent i = new Intent(this, BackgroundAudioService.class);
                    i.setAction(BackgroundAudioService.ACTION_START);
                    i.putExtra("path", streamUrl);
                    i.putExtra("queue", queue);
                    i.putExtra("index", 0);
                    startService(i);
                    status.setText("Streaming: " + item.title + "\nUse the playback notification for background controls.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!destroyed) { button.setEnabled(true); status.setText("Cannot stream this item: " + message(e)); } });
            }
        });
    }

    private static JSONObject json(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(18000);
        c.setRequestProperty("User-Agent", "ToxiFiles-FreeAudioCatalog/1.0");
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            if (in == null) throw new Exception("HTTP " + code);
            StringBuilder body = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) body.append(line);
            }
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            return new JSONObject(body.toString());
        } finally { c.disconnect(); }
    }

    private static String message(Exception e) { return e.getMessage() == null ? "Unknown error" : e.getMessage(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { destroyed = true; io.shutdownNow(); super.onDestroy(); }

    private static final class Item {
        final String id, title, creator;
        Item(String id, String title, String creator) {
            this.id = id;
            this.title = title == null || title.isEmpty() ? id : title;
            this.creator = creator == null || creator.isEmpty() ? "Unknown artist" : creator;
        }
    }
}