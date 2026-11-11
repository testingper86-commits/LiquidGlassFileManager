package com.music.echo.offline.files;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.text.Selection;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline OCR shared by the existing image viewer and its built-in editor. */
final class ImageTextRecognition {
    interface Callback { void onComplete(Result result, Exception error); }

    static final class Region {
        final String text;
        final Rect bounds;
        final float confidence;
        /** 0=symbol, 1=word, 2=line, 3=paragraph/block. */
        final int level;
        Region(String text, Rect bounds, float confidence, int level) {
            this.text = text;
            this.bounds = new Rect(bounds);
            this.confidence = safeConfidence(confidence);
            this.level = level;
        }
    }

    static final class Result {
        final String text;
        final List<Region> regions;
        final String engines;
        final float confidence;
        Result(String text, List<Region> regions, String engines, float confidence) {
            this.text = text == null ? "" : text.trim();
            this.regions = Collections.unmodifiableList(new ArrayList<>(regions));
            this.engines = engines;
            this.confidence = safeConfidence(confidence);
        }
    }

    private static final long KANNADA_MODEL_BYTES = 3_608_331L;
    private static final String[] KANNADA_MODEL_PARTS = {
        "tessdata/kan.traineddata.part00", "tessdata/kan.traineddata.part01",
        "tessdata/kan.traineddata.part02", "tessdata/kan.traineddata.part03",
        "tessdata/kan.traineddata.part04", "tessdata/kan.traineddata.part05",
        "tessdata/kan.traineddata.part06", "tessdata/kan.traineddata.part07",
        "tessdata/kan.traineddata.part08"
    };
    private static final ExecutorService KANNADA_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "toxifiles-ocr-kannada");
        thread.setDaemon(true);
        return thread;
    });

    private ImageTextRecognition() {}

    static void recognize(Context context, Bitmap source, Callback callback) {
        if (callback == null) return;
        if (context == null || source == null || source.isRecycled()) {
            callback.onComplete(null, new IllegalArgumentException("Image is unavailable"));
            return;
        }
        Bitmap enhanced;
        try {
            enhanced = enhance(source);
        } catch (RuntimeException failure) {
            enhanced = source;
        }
        runEngines(context.getApplicationContext(), source, enhanced, callback);
    }

    /** Contrast-normalized second pass; never mutates or recycles the source image. */
    private static Bitmap enhance(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0 || (long) width * height > 20_000_000L) return source;
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        ColorMatrix matrix = new ColorMatrix(new float[]{
            1.22f, 0, 0, 0, -24, 0, 1.22f, 0, 0, -24,
            0, 0, 1.22f, 0, -24, 0, 0, 0, 1, 0
        });
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return output;
    }

    private static void runEngines(Context context, Bitmap original, Bitmap enhanced, Callback callback) {
        final int mlPassCount = enhanced == null || enhanced == original ? 2 : 4;
        final int totalPasses = mlPassCount + 1;
        final Object lock = new Object();
        final ArrayList<Result> candidates = new ArrayList<>();
        final ArrayList<Exception> errors = new ArrayList<>();
        final int[] finished = {0};
        final Bitmap[] images = enhanced == null || enhanced == original
                ? new Bitmap[]{original, original}
                : new Bitmap[]{original, enhanced, original, enhanced};
        final int[] scripts = enhanced == null || enhanced == original
                ? new int[]{0, 1} : new int[]{0, 0, 1, 1};
        final String[] engineNames = {"Latin", "Devanagari"};

        for (int i = 0; i < mlPassCount; i++) {
            final Bitmap image = images[i];
            final int script = scripts[i];
            final boolean enhancedPass = enhanced != null && enhanced != original && image == enhanced;
            TextRecognizer recognizer = null;
            try {
                recognizer = script == 0
                        ? TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        : TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
                final TextRecognizer activeRecognizer = recognizer;
                final Result[] candidate = {null};
                Task<Text> task = activeRecognizer.process(InputImage.fromBitmap(image, 0));
                task.addOnSuccessListener(text -> {
                    Result parsed = convert(text, engineNames[script] + (enhancedPass ? "+contrast" : ""));
                    if (!parsed.text.isEmpty()) candidate[0] = parsed;
                }).addOnCompleteListener(done -> {
                    activeRecognizer.close();
                    finishPass(lock, candidates, errors, finished, totalPasses, callback,
                            enhanced, original, candidate[0], done.getException());
                });
            } catch (RuntimeException failure) {
                if (recognizer != null) recognizer.close();
                finishPass(lock, candidates, errors, finished, totalPasses, callback,
                        enhanced, original, null, failure);
            }
        }

        try {
            KANNADA_EXECUTOR.execute(() -> {
                try {
                    Result kannada = recognizeKannada(context, original);
                    finishPass(lock, candidates, errors, finished, totalPasses, callback,
                            enhanced, original, kannada, null);
                } catch (Exception failure) {
                    finishPass(lock, candidates, errors, finished, totalPasses, callback,
                            enhanced, original, null, failure);
                }
            });
        } catch (RuntimeException failure) {
            finishPass(lock, candidates, errors, finished, totalPasses, callback,
                    enhanced, original, null, failure);
        }
    }

    private static void finishPass(Object lock, ArrayList<Result> candidates,
                                   ArrayList<Exception> errors, int[] finished, int totalPasses,
                                   Callback callback, Bitmap enhanced, Bitmap original,
                                   Result candidate, Exception error) {
        synchronized (lock) {
            if (candidate != null && !candidate.text.trim().isEmpty()) candidates.add(candidate);
            if (error != null) errors.add(error);
            finished[0]++;
            if (finished[0] != totalPasses) return;
            if (enhanced != null && enhanced != original && !enhanced.isRecycled()) enhanced.recycle();
            if (candidates.isEmpty()) {
                callback.onComplete(null, errors.isEmpty()
                        ? new IllegalStateException("No readable text was detected") : errors.get(0));
                return;
            }
            candidates.sort(Comparator
                    .comparingDouble((Result result) -> result.confidence).reversed()
                    .thenComparing(Comparator.comparingInt((Result result) -> result.text.length()).reversed()));
            callback.onComplete(candidates.get(0), null);
        }
    }

    private static Result recognizeKannada(Context context, Bitmap bitmap) throws IOException {
        File dataRoot = context.getFilesDir();
        File tessdata = new File(dataRoot, "tessdata");
        if (!tessdata.isDirectory() && !tessdata.mkdirs()) {
            throw new IOException("Cannot create private OCR model directory");
        }
        File model = new File(tessdata, "kan.traineddata");
        if (!model.isFile() || model.length() != KANNADA_MODEL_BYTES) {
            File temporary = new File(tessdata, "kan.traineddata.tmp");
            if (temporary.exists() && !temporary.delete()) throw new IOException("Cannot replace incomplete OCR model");
            long copied = 0;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                for (String part : KANNADA_MODEL_PARTS) {
                    try (InputStream input = context.getAssets().open(part)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                            copied += count;
                        }
                    }
                }
                output.getFD().sync();
            } catch (IOException failure) {
                temporary.delete();
                throw failure;
            }
            if (copied != KANNADA_MODEL_BYTES || temporary.length() != KANNADA_MODEL_BYTES) {
                temporary.delete();
                throw new IOException("Bundled Kannada OCR model failed its size check");
            }
            if (model.exists() && !model.delete()) {
                temporary.delete();
                throw new IOException("Cannot replace stale Kannada OCR model");
            }
            if (!temporary.renameTo(model)) {
                temporary.delete();
                throw new IOException("Cannot install bundled Kannada OCR model");
            }
        }

        TessBaseAPI api = new TessBaseAPI();
        try {
            if (!api.init(dataRoot.getAbsolutePath(), "kan")) {
                throw new IOException("Kannada OCR model could not be initialized");
            }
            api.setImage(bitmap);
            String recognized = api.getUTF8Text();
            float confidence = safeConfidence(api.meanConfidence() / 100f);
            ArrayList<Region> regions = new ArrayList<>();
            ResultIterator iterator = api.getResultIterator();
            if (iterator != null) {
                try {
                    iterator.begin();
                    do {
                        String word = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD);
                        Rect bounds = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD);
                        float wordConfidence = safeConfidence(
                                iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD) / 100f);
                        if (word != null && !word.trim().isEmpty() && bounds != null
                                && bounds.width() > 0 && bounds.height() > 0) {
                            regions.add(new Region(word.trim(), bounds, wordConfidence, 1));
                        }
                    } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD));
                } finally {
                    iterator.delete();
                }
            }
            if (recognized == null || recognized.trim().isEmpty()) {
                throw new IOException("No Kannada-script text was detected");
            }
            return new Result(recognized, regions, "Kannada script · offline Tesseract", confidence);
        } finally {
            api.recycle();
        }
    }

    private static Result convert(Text text, String engine) {
        StringBuilder all = new StringBuilder();
        ArrayList<Region> regions = new ArrayList<>();
        float confidenceSum = 0f;
        int confidenceCount = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            Rect blockBounds = block.getBoundingBox();
            if (blockBounds != null && block.getText() != null) {
                regions.add(new Region(block.getText(), blockBounds, 0f, 3));
            }
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText();
                if (lineText != null && !lineText.trim().isEmpty()) {
                    if (all.length() > 0) all.append('\n');
                    all.append(lineText);
                    Rect lineBounds = line.getBoundingBox();
                    float lineConfidence = safeConfidence(line.getConfidence());
                    confidenceSum += lineConfidence;
                    confidenceCount++;
                    if (lineBounds != null) regions.add(new Region(lineText, lineBounds, lineConfidence, 2));
                }
                for (Text.Element element : line.getElements()) {
                    Rect elementBounds = element.getBoundingBox();
                    if (elementBounds != null) {
                        regions.add(new Region(element.getText(), elementBounds,
                                safeConfidence(element.getConfidence()), 1));
                    }
                    for (Text.Symbol symbol : element.getSymbols()) {
                        Rect symbolBounds = symbol.getBoundingBox();
                        if (symbolBounds != null) {
                            regions.add(new Region(symbol.getText(), symbolBounds,
                                    safeConfidence(symbol.getConfidence()), 0));
                        }
                    }
                }
            }
        }
        float confidence = confidenceCount == 0 ? 0f : confidenceSum / confidenceCount;
        return new Result(all.toString(), regions, engine, confidence);
    }

    private static float safeConfidence(float value) {
        return Float.isNaN(value) || Float.isInfinite(value)
                ? 0f : Math.max(0f, Math.min(1f, value));
    }

    static void showTextActions(Context context, Result result) {
        if (result == null || result.text.trim().isEmpty()) return;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(8),
                ToxiVisualSystem.dp(16), ToxiVisualSystem.dp(8));
        TextView quality = ToxiVisualSystem.text(context,
                result.regions.size() + " text regions · On-device " + result.engines
                        + " · " + Math.round(result.confidence * 100f) + "% confidence",
                11, ToxiVisualSystem.TEXT_SECONDARY, false);
        content.addView(quality);
        TextView extracted = ToxiVisualSystem.text(context, result.text, 15,
                ToxiVisualSystem.TEXT_PRIMARY, false);
        extracted.setTextIsSelectable(true);
        extracted.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);
        extracted.setLinksClickable(true);
        extracted.setLineSpacing(ToxiVisualSystem.dp(3), 1f);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(extracted, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(300)));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView shareButton = ToxiVisualSystem.text(context, "Share extracted text", 13,
                ToxiVisualSystem.TEXT_PRIMARY, true);
        shareButton.setGravity(Gravity.CENTER);
        shareButton.setPadding(ToxiVisualSystem.dp(14), 0, ToxiVisualSystem.dp(14), 0);
        shareButton.setBackground(ToxiVisualSystem.accentSurface(14));
        shareButton.setOnClickListener(v -> share(context, result.text));
        actions.addView(shareButton, new LinearLayout.LayoutParams(-2, ToxiVisualSystem.dp(40)));
        content.addView(actions, new LinearLayout.LayoutParams(-1, ToxiVisualSystem.dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("Recognized text")
                .setView(content).setNegativeButton("Close", null)
                .setNeutralButton("Select all", null)
                .setPositiveButton("Copy", (d, w) -> copy(context, result.text)).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            extracted.requestFocus();
            Selection.selectAll((android.text.Spannable) extracted.getText());
        }));
        dialog.show();
    }

    static void copy(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Recognized text", text));
    }

    static void share(Context context, String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(send, "Share recognized text"));
    }

    static final class Overlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Region> regions;
        private final android.graphics.Matrix imageMatrix;
        private final CallbackSelection selection;
        private float startX;
        private float startY;
        private float currentX;
        private float currentY;
        private boolean selecting;

        interface CallbackSelection { void selected(String text); }

        Overlay(Context context, List<Region> regions, android.graphics.Matrix imageMatrix,
                CallbackSelection selection) {
            super(context);
            this.regions = regions;
            this.imageMatrix = new android.graphics.Matrix(imageMatrix);
            this.selection = selection;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ToxiVisualSystem.dp(1.5f));
            paint.setColor(Color.argb(210, 45, 220, 255));
            for (Region region : regions) {
                RectF box = new RectF(region.bounds);
                imageMatrix.mapRect(box);
                canvas.drawRoundRect(box, ToxiVisualSystem.dp(3), ToxiVisualSystem.dp(3), paint);
            }
            if (selecting) {
                RectF selected = new RectF(Math.min(startX, currentX), Math.min(startY, currentY),
                        Math.max(startX, currentX), Math.max(startY, currentY));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(48, 45, 220, 255));
                canvas.drawRect(selected, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.argb(235, 45, 220, 255));
                canvas.drawRect(selected, paint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = currentX = event.getX();
                    startY = currentY = event.getY();
                    selecting = true;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!selecting) return false;
                    currentX = event.getX();
                    currentY = event.getY();
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    selecting = false;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!selecting) return false;
                    currentX = event.getX();
                    currentY = event.getY();
                    selecting = false;
                    invalidate();
                    if (Math.hypot(currentX - startX, currentY - startY) >= ToxiVisualSystem.dp(12)) {
                        selectIntersectingRegions();
                    } else {
                        selectNearestRegion(currentX, currentY);
                    }
                    return true;
                default:
                    return true;
            }
        }

        private void selectNearestRegion(float x, float y) {
            float[] point = {x, y};
            android.graphics.Matrix inverse = new android.graphics.Matrix();
            if (!imageMatrix.invert(inverse)) return;
            inverse.mapPoints(point);
            Region best = null;
            float bestDistance = Float.MAX_VALUE;
            for (Region region : regions) {
                RectF bounds = new RectF(region.bounds);
                float dx = Math.max(Math.max(bounds.left - point[0], 0f), point[0] - bounds.right);
                float dy = Math.max(Math.max(bounds.top - point[1], 0f), point[1] - bounds.bottom);
                float distance = dx * dx + dy * dy;
                if (distance < bestDistance || (distance == bestDistance && best != null
                        && region.level < best.level)) {
                    bestDistance = distance;
                    best = region;
                }
            }
            float threshold = ToxiVisualSystem.dp(28);
            if (best != null && bestDistance <= threshold * threshold) selection.selected(best.text);
        }

        private void selectIntersectingRegions() {
            float[] points = {startX, startY, currentX, currentY};
            android.graphics.Matrix inverse = new android.graphics.Matrix();
            if (!imageMatrix.invert(inverse)) return;
            inverse.mapPoints(points);
            RectF area = new RectF(Math.min(points[0], points[2]), Math.min(points[1], points[3]),
                    Math.max(points[0], points[2]), Math.max(points[1], points[3]));
            if (area.width() < 1f) area.right = area.left + 1f;
            if (area.height() < 1f) area.bottom = area.top + 1f;
            ArrayList<Region> symbols = new ArrayList<>();
            ArrayList<Region> words = new ArrayList<>();
            ArrayList<Region> lines = new ArrayList<>();
            ArrayList<Region> blocks = new ArrayList<>();
            for (Region region : regions) {
                if (!RectF.intersects(area, new RectF(region.bounds))) continue;
                if (region.level == 0) symbols.add(region);
                else if (region.level == 1) words.add(region);
                else if (region.level == 2) lines.add(region);
                else blocks.add(region);
            }
            List<Region> chosen;
            if (area.width() <= ToxiVisualSystem.dp(28) && area.height() <= ToxiVisualSystem.dp(28)
                    && !symbols.isEmpty()) chosen = symbols;
            else if (lines.size() > 1) chosen = lines;
            else if (!words.isEmpty()) chosen = words;
            else if (!lines.isEmpty()) chosen = lines;
            else chosen = blocks;
            if (chosen.isEmpty()) return;
            chosen.sort(Comparator.comparingInt((Region region) -> region.bounds.top)
                    .thenComparingInt(region -> region.bounds.left));
            StringBuilder text = new StringBuilder();
            int previousLevel = -1;
            for (Region region : chosen) {
                String value = region.text == null ? "" : region.text.trim();
                if (value.isEmpty()) continue;
                if (text.length() > 0) text.append(region.level >= 2 || previousLevel >= 2 ? '\n' : ' ');
                text.append(value);
                previousLevel = region.level;
            }
            if (text.length() > 0) selection.selected(text.toString());
        }
    }
}
