package com.imgedt.editor.paint;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
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

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.imgedt.editor.ImageUtils;
import com.imgedt.editor.R;

/**
 * Activity for freehand drawing on top of an image.
 * Provides brush color/opacity selection, size adjustment, eraser, and undo.
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
    private View selectedSwatch;
    private ImageView eraserBtn;
    private ImageView undoBtn;
    private ImageView redoBtn;

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
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

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
        paintLp.bottomMargin = dp(172);
        root.addView(paintView, paintLp);

        // Bottom panel
        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setBackgroundColor(0xE0000000);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(172));
        bottomLp.gravity = Gravity.BOTTOM;
        root.addView(bottomPanel, bottomLp);

        // Brush size slider
        bottomPanel.addView(createSliderRow("Size", 100, 20, (progress) -> {
            currentSize = Math.max(2, progress);
            paintView.setBrushSize(currentSize);
        }));

        // Brush opacity slider
        bottomPanel.addView(createSliderRow("Opacity", 100, 85, (progress) -> {
            float alpha = Math.max(0.05f, progress / 100f);
            paintView.setBrushAlpha(alpha);
        }));

        // Color row with eraser
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER);
        colorRow.setPadding(dp(8), dp(4), dp(8), dp(4));

        // Eraser icon button
        eraserBtn = new ImageView(this);
        eraserBtn.setScaleType(ImageView.ScaleType.CENTER);
        eraserBtn.setImageResource(R.drawable.ic_paint_eraser);
        eraserBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        GradientDrawable eraserBg = new GradientDrawable();
        eraserBg.setCornerRadius(dp(6));
        eraserBg.setColor(0x18FFFFFF);
        eraserBtn.setBackground(eraserBg);
        eraserBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        eraserBtn.setOnClickListener(v -> {
            isEraser = !isEraser;
            paintView.setEraser(isEraser);
            eraserBtn.setColorFilter(isEraser ? 0xFF4FC3F7 : Color.WHITE, PorterDuff.Mode.SRC_IN);
            if (isEraser) {
                clearSwatchSelection();
            }
        });
        LinearLayout.LayoutParams eraserLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        eraserLp.setMargins(dp(4), 0, dp(8), 0);
        colorRow.addView(eraserBtn, eraserLp);

        // Color swatches
        for (int i = 0; i < COLORS.length; i++) {
            final int color = COLORS[i];
            View swatch = new View(this);
            swatch.setTag(color);
            LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            swatchLp.setMargins(dp(3), 0, dp(3), 0);
            updateSwatchDrawable(swatch, color, false);
            swatch.setOnClickListener(v -> {
                currentColor = color;
                paintView.setBrushColor(color);
                isEraser = false;
                paintView.setEraser(false);
                eraserBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                setSelectedSwatch(v, color);
            });
            colorRow.addView(swatch, swatchLp);

            // Select RED by default
            if (i == 0) {
                setSelectedSwatch(swatch, color);
            }
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

        // Undo icon button
        undoBtn = new ImageView(this);
        undoBtn.setScaleType(ImageView.ScaleType.CENTER);
        undoBtn.setImageResource(R.drawable.ic_undo);
        undoBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        undoBtn.setAlpha(0.4f);
        GradientDrawable undoBg = new GradientDrawable();
        undoBg.setCornerRadius(dp(6));
        undoBg.setColor(0x18FFFFFF);
        undoBtn.setBackground(undoBg);
        undoBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        undoBtn.setOnClickListener(v -> {
            paintView.undo();
            updateUndoRedoState();
        });
        actionRow.addView(undoBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        addSpacer(actionRow);

        // Redo icon button
        redoBtn = new ImageView(this);
        redoBtn.setScaleType(ImageView.ScaleType.CENTER);
        redoBtn.setImageResource(R.drawable.ic_redo);
        redoBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        redoBtn.setAlpha(0.4f);
        GradientDrawable redoBg = new GradientDrawable();
        redoBg.setCornerRadius(dp(6));
        redoBg.setColor(0x18FFFFFF);
        redoBtn.setBackground(redoBg);
        redoBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        redoBtn.setOnClickListener(v -> {
            paintView.redo();
            updateUndoRedoState();
        });
        actionRow.addView(redoBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        addSpacer(actionRow);

        TextView doneBtn = createButton("Done");
        doneBtn.setTextColor(0xFF4FC3F7);
        doneBtn.setOnClickListener(v -> applyPaint());
        actionRow.addView(doneBtn);

        bottomPanel.addView(actionRow);

        // Update undo/redo state after each stroke
        paintView.setListener(this::updateUndoRedoState);

        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        loadImage();
    }

    private interface SliderCallback {
        void onChanged(int progress);
    }

    private LinearLayout createSliderRow(String label, int max, int initial, SliderCallback callback) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(2), dp(16), dp(2));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xAAFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setWidth(dp(48));
        row.addView(tv);

        SeekBar slider = new SeekBar(this);
        slider.setMax(max);
        slider.setProgress(initial);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) callback.onChanged(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        row.addView(slider, sliderLp);
        return row;
    }

    private void setSelectedSwatch(View swatch, int color) {
        if (selectedSwatch != null) {
            int prevColor = (int) selectedSwatch.getTag();
            updateSwatchDrawable(selectedSwatch, prevColor, false);
        }
        updateSwatchDrawable(swatch, color, true);
        selectedSwatch = swatch;
    }

    private void clearSwatchSelection() {
        if (selectedSwatch != null) {
            int prevColor = (int) selectedSwatch.getTag();
            updateSwatchDrawable(selectedSwatch, prevColor, false);
            selectedSwatch = null;
        }
    }

    private void updateSwatchDrawable(View swatch, int color, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(4));
        if (selected) {
            gd.setStroke(dp(2), Color.WHITE);
        }
        swatch.setBackground(gd);
    }

    private void updateUndoRedoState() {
        if (undoBtn != null) {
            undoBtn.setAlpha(paintView.canUndo() ? 1.0f : 0.4f);
        }
        if (redoBtn != null) {
            redoBtn.setAlpha(paintView.canRedo() ? 1.0f : 0.4f);
        }
    }

    private void loadImage() {
        Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
        if (imageUri == null) { finish(); return; }

        sourceBitmap = ImageUtils.decodeBitmapWithOrientation(getContentResolver(), imageUri);
        if (sourceBitmap != null) {
            backgroundView.setImageBitmap(sourceBitmap);
        } else {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (paintView != null && paintView.canUndo()) {
            applyPaint();
        } else {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        }
    }

    private void applyPaint() {
        if (sourceBitmap == null || paintView == null) return;

        Bitmap paintLayer = paintView.getResult();
        Bitmap result = Bitmap.createBitmap(sourceBitmap.getWidth(), sourceBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(sourceBitmap, 0, 0, null);

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
