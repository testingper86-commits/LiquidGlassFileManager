package com.music.echo.offline.files;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.AlertDialog;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** On-device OCR shared by the image viewer and its built-in editor. */
final class ImageTextRecognition {
    interface Callback { void onComplete(Result result, Exception error); }
    static final class Region {
        final String text;
        final Rect bounds;
        final float confidence;
        final int level;
        Region(String text, Rect bounds, float confidence, int level) {
            this.text = text; this.bounds = new Rect(bounds); this.confidence = confidence; this.level = level;
        }
    }
    static final class Result {
        final String text;
        final List<Region> regions;
        final String engines;
        Result(String text, List<Region> regions, String engines) {
            this.text = text; this.regions = Collections.unmodifiableList(new ArrayList<>(regions)); this.engines = engines;
        }
    }

    private ImageTextRecognition() {}

    static void recognize(Bitmap source, Callback callback) {
        if (source == null || source.isRecycled()) {
            callback.onComplete(null, new IllegalArgumentException("Image is unavailable"));
            return;
        }
        final Bitmap enhanced;
        try { enhanced = enhance(source); }
        catch (RuntimeException failure) { runEngines(source, null, callback); return; }
        runEngines(source, enhanced, callback);
    }

    private static Bitmap enhance(Bitmap source) {
        int width = source.getWidth(), height = source.getHeight();
        if (width <= 0 || height <= 0 || (long) width * height > 20_000_000L) return source;
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        ColorMatrix matrix = new ColorMatrix(new float[]{
            1.22f,0,0,0,-24, 0,1.22f,0,0,-24, 0,0,1.22f,0,-24, 0,0,0,1,0
        });
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return output;
    }

    private static void runEngines(Bitmap original, Bitmap enhanced, Callback callback) {
        final ArrayList<Result> candidates = new ArrayList<>();
        final ArrayList<Exception> errors = new ArrayList<>();
        final int total = enhanced == null || enhanced == original ? 2 : 4;
        final int[] finished = {0};
        final String[] engineNames = {"Latin", "Devanagari"};
        final Bitmap[] images = enhanced == null || enhanced == original
                ? new Bitmap[]{original, original} : new Bitmap[]{original, enhanced, original, enhanced};
        final int[] scripts = enhanced == null || enhanced == original
                ? new int[]{0,1} : new int[]{0,0,1,1};
        for (int i = 0; i < total; i++) {
            final Bitmap image = images[i];
            final int script = scripts[i];
            final boolean enhancedPass = enhanced != null && enhanced != original && image == enhanced;
            TextRecognizer recognizer = script == 0
                    ? TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    : TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
            try {
                Task<Text> task = recognizer.process(InputImage.fromBitmap(image, 0));
                task.addOnSuccessListener(text -> {
                    synchronized (candidates) {
                        Result candidate = convert(text, engineNames[script] + (enhancedPass ? "+contrast" : ""));
                        if (!candidate.text.trim().isEmpty()) candidates.add(candidate);
                    }
                }).addOnFailureListener(errors::add).addOnCompleteListener(done -> {
                    recognizer.close();
                    synchronized (candidates) {
                        finished[0]++;
                        if (finished[0] == total) {
                            if (enhanced != null && enhanced != original && !enhanced.isRecycled()) enhanced.recycle();
                            if (candidates.isEmpty()) {
                                callback.onComplete(null, errors.isEmpty()
                                        ? new IllegalStateException("No readable text was detected") : errors.get(0));
                            } else {
                                candidates.sort(Comparator.comparingInt((Result r) -> r.text.length()).reversed());
                                callback.onComplete(candidates.get(0), null);
                            }
                        }
                    }
                });
            } catch (RuntimeException failure) {
                recognizer.close();
                synchronized (candidates) {
                    errors.add(failure); finished[0]++;
                    if (finished[0] == total) {
                        if (enhanced != null && enhanced != original && !enhanced.isRecycled()) enhanced.recycle();
                        callback.onComplete(null, errors.get(0));
                    }
                }
            }
        }
    }

    private static Result convert(Text text, String engine) {
        StringBuilder all = new StringBuilder();
        ArrayList<Region> regions = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText();
                if (lineText != null && !lineText.trim().isEmpty()) {
                    if (all.length() > 0) all.append('\n');
                    all.append(lineText);
                    Rect lineBounds = line.getBoundingBox();
                    if (lineBounds != null) regions.add(new Region(lineText, lineBounds, safeConfidence(line.getConfidence()), 2));
                }
                for (Text.Element element : line.getElements()) {
                    Rect elementBounds = element.getBoundingBox();
                    if (elementBounds != null) regions.add(new Region(element.getText(), elementBounds, safeConfidence(element.getConfidence()), 1));
                    for (Text.Symbol symbol : element.getSymbols()) {
                        Rect symbolBounds = symbol.getBoundingBox();
                        if (symbolBounds != null) regions.add(new Region(symbol.getText(), symbolBounds, safeConfidence(symbol.getConfidence()), 0));
                    }
                }
            }
        }
        return new Result(all.toString(), regions, engine);
    }

    private static float safeConfidence(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0f : Math.max(0f, Math.min(1f, value));
    }

    static void showTextActions(Context context, Result result) {
        if (result == null || result.text.trim().isEmpty()) return;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(8), ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(8));
        TextView quality = ToxiVisualSystem.text(context,
                result.regions.size() + " text regions · On-device " + result.engines, 11,
                ToxiVisualSystem.TEXT_SECONDARY, false);
        content.addView(quality);
        TextView extracted = ToxiVisualSystem.text(context, result.text, 15, ToxiVisualSystem.TEXT_PRIMARY, false);
        extracted.setTextIsSelectable(true);
        extracted.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);
        extracted.setLinksClickable(true);
        extracted.setLineSpacing(ToxiVisualSystem.dp(3), 1f);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(extracted, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(320)));
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("Recognized text")
                .setView(content).setNegativeButton("Close", null)
                .setNeutralButton("Select all", (d,w) -> {
                    extracted.requestFocus();
                    android.text.Selection.selectAll(selectable);
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Recognized text", result.text));
                }).setPositiveButton("Copy", (d,w) -> copy(context, result.text)).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnLongClickListener(v -> {
                share(context, result.text); return true;
            });
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            extracted.requestFocus();
            android.text.Selection.selectAll(extracted.getText());
            copy(context, result.text);
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnLongClickListener(v -> {
            share(context, result.text); return true;
        });
    }

    static void copy(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Recognized text", text));
    }

    static void share(Context context, String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(send, "Share recognized text"));
    }

    static final class Overlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Region> regions;
        private final android.graphics.Matrix imageMatrix;
        private final CallbackSelection selection;
        interface CallbackSelection { void selected(String text); }
        Overlay(Context context, List<Region> regions, android.graphics.Matrix imageMatrix, CallbackSelection selection) {
            super(context); this.regions = regions; this.imageMatrix = new android.graphics.Matrix(imageMatrix); this.selection = selection;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(ToxiVisualSystem.dp(1.5f));
            paint.setColor(Color.argb(210, 45, 220, 255));
            for (Region region : regions) {
                RectF box = new RectF(region.bounds);
                imageMatrix.mapRect(box);
                canvas.drawRoundRect(box, ToxiVisualSystem.dp(3), ToxiVisualSystem.dp(3), paint);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float[] point = {event.getX(), event.getY()};
            android.graphics.Matrix inverse = new android.graphics.Matrix();
            if (!imageMatrix.invert(inverse)) return true;
            inverse.mapPoints(point);
            Region best = null; float bestDistance = Float.MAX_VALUE;
            for (Region region : regions) {
                if (region.level < 0 || region.level > 3) continue;
                RectF bounds = new RectF(region.bounds);
                float dx = Math.max(Math.max(bounds.left - point[0], 0f), point[0] - bounds.right);
                float dy = Math.max(Math.max(bounds.top - point[1], 0f), point[1] - bounds.bottom);
                float distance = dx * dx + dy * dy;
                if (distance < bestDistance || (distance == bestDistance && best != null && region.level < best.level)) { bestDistance = distance; best = region; }
            }
            if (best != null && bestDistance <= ToxiVisualSystem.dp(28) * ToxiVisualSystem.dp(28))
                selection.selected(best.text);
            return true;
        }
    }
}
