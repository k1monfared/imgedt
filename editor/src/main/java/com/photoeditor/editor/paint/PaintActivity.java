package com.photoeditor.editor.paint;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Activity for freehand drawing on top of an image.
 * Provides brush color selection, size adjustment, eraser, and undo.
 */
public class PaintActivity extends Activity {

    public static final String EXTRA_IMAGE_URI = "image_uri";
    public static final String EXTRA_RESULT_PATH = "result_path";

    private PaintView paintView;
    private ImageView backgroundView;
    private Bitmap sourceBitmap;

    private int currentColor = Color.RED;
    private float currentSize = 20f;
    private boolean isEraser = false;

    private static final int[] COLORS = {
            Color.RED, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50,
            0xFF2196F3, 0xFF9C27B0, Color.WHITE, Color.BLACK
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Background image
        backgroundView = new ImageView(this);
        backgroundView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(backgroundView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Paint overlay
        paintView = new PaintView(this);
        FrameLayout.LayoutParams paintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        paintLp.bottomMargin = dp(140);
        root.addView(paintView, paintLp);

        // Bottom panel
        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setBackgroundColor(0xE0000000);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        bottomLp.gravity = Gravity.BOTTOM;
        root.addView(bottomPanel, bottomLp);

        // Brush size slider
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        sizeRow.setPadding(dp(16), dp(4), dp(16), dp(4));

        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("Size");
        sizeLabel.setTextColor(Color.WHITE);
        sizeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sizeRow.addView(sizeLabel);

        SeekBar sizeSlider = new SeekBar(this);
        sizeSlider.setMax(100);
        sizeSlider.setProgress(20);
        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSize = Math.max(2, progress);
                paintView.setBrushSize(currentSize);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sliderLp.leftMargin = dp(8);
        sizeRow.addView(sizeSlider, sliderLp);
        bottomPanel.addView(sizeRow);

        // Color row
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER);
        colorRow.setPadding(dp(8), dp(4), dp(8), dp(4));

        // Eraser button
        TextView eraserBtn = new TextView(this);
        eraserBtn.setText("Eraser");
        eraserBtn.setTextColor(Color.WHITE);
        eraserBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        eraserBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        eraserBtn.setOnClickListener(v -> {
            isEraser = !isEraser;
            paintView.setEraser(isEraser);
            eraserBtn.setTextColor(isEraser ? 0xFF4FC3F7 : Color.WHITE);
        });
        colorRow.addView(eraserBtn);

        for (int color : COLORS) {
            View swatch = new View(this);
            swatch.setBackgroundColor(color);
            LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            swatchLp.setMargins(dp(4), 0, dp(4), 0);
            swatch.setOnClickListener(v -> {
                currentColor = color;
                paintView.setBrushColor(color);
                isEraser = false;
                paintView.setEraser(false);
                eraserBtn.setTextColor(Color.WHITE);
            });
            colorRow.addView(swatch, swatchLp);
        }
        bottomPanel.addView(colorRow);

        // Action row
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(dp(8), dp(4), dp(8), dp(8));

        TextView cancelBtn = createButton("Cancel");
        cancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        actionRow.addView(cancelBtn);

        addSpacer(actionRow);

        TextView undoBtn = createButton("Undo");
        undoBtn.setOnClickListener(v -> paintView.undo());
        actionRow.addView(undoBtn);

        addSpacer(actionRow);

        TextView doneBtn = createButton("Done");
        doneBtn.setTextColor(0xFF4FC3F7);
        doneBtn.setOnClickListener(v -> applyPaint());
        actionRow.addView(doneBtn);

        bottomPanel.addView(actionRow);

        setContentView(root);
        loadImage();
    }

    private void loadImage() {
        Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
        if (imageUri == null) { finish(); return; }
        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            if (is != null) {
                sourceBitmap = BitmapFactory.decodeStream(is);
                is.close();
                backgroundView.setImageBitmap(sourceBitmap);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void applyPaint() {
        if (sourceBitmap == null || paintView == null) return;

        Bitmap paintLayer = paintView.getResult();
        Bitmap result = Bitmap.createBitmap(sourceBitmap.getWidth(), sourceBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(sourceBitmap, 0, 0, null);

        // Scale paint layer to match source bitmap
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        float scaleX = (float) sourceBitmap.getWidth() / paintLayer.getWidth();
        float scaleY = (float) sourceBitmap.getHeight() / paintLayer.getHeight();
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(scaleX, scaleY);
        canvas.drawBitmap(paintLayer, m, p);
        paintLayer.recycle();

        try {
            java.io.File cacheFile = new java.io.File(getCacheDir(), "painted_" + System.currentTimeMillis() + ".jpg");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);
            result.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();
            result.recycle();

            Intent data = new Intent();
            data.putExtra(EXTRA_RESULT_PATH, cacheFile.getAbsolutePath());
            setResult(RESULT_OK, data);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView createButton(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setPadding(dp(16), dp(8), dp(16), dp(8));
        btn.setGravity(Gravity.CENTER);
        return btn;
    }

    private void addSpacer(LinearLayout row) {
        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (paintView != null) paintView.recycle();
        if (sourceBitmap != null) sourceBitmap.recycle();
    }
}
