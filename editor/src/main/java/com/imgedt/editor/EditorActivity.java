package com.imgedt.editor;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.imgedt.editor.crop.CropActivity;
import com.imgedt.editor.filter.FilterParams;
import com.imgedt.editor.filter.FilterRenderer;
import com.imgedt.editor.paint.PaintActivity;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Main photo editor activity. Displays the image with OpenGL-based filter preview
 * and adjustment tools (exposure, contrast, saturation, warmth, etc.).
 */
public class EditorActivity extends Activity {

    public static final String EXTRA_IMAGE_URI = "image_uri";
    private static final int REQUEST_CROP = 100;
    private static final int REQUEST_PAINT = 101;

    private Uri imageUri;
    private TextureView previewView;
    private FilterRenderer renderer;
    private FilterParams filterParams;
    private Bitmap sourceBitmap;

    private SeekBar adjustmentSlider;
    private TextView sliderValueText;
    private TextView currentToolLabel;
    private LinearLayout toolsContainer;

    private int selectedTool = -1;

    private static final String[] TOOL_NAMES = {
            "Exposure", "Contrast", "Saturation", "Warmth",
            "Highlights", "Shadows", "Sharpen", "Fade",
            "Grain", "Vignette"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        filterParams = new FilterParams();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Preview (TextureView for OpenGL rendering)
        previewView = new TextureView(this);
        FrameLayout.LayoutParams previewLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        previewLp.bottomMargin = dp(200);
        root.addView(previewView, previewLp);

        // Bottom panel
        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setBackgroundColor(0xE0000000);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        bottomLp.gravity = Gravity.BOTTOM;
        root.addView(bottomPanel, bottomLp);

        // Current tool label and value
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_HORIZONTAL);
        labelRow.setPadding(0, dp(8), 0, 0);

        currentToolLabel = new TextView(this);
        currentToolLabel.setTextColor(Color.WHITE);
        currentToolLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        currentToolLabel.setText("Select a tool");
        labelRow.addView(currentToolLabel);

        sliderValueText = new TextView(this);
        sliderValueText.setTextColor(0xFF4FC3F7);
        sliderValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sliderValueText.setPadding(dp(8), 0, 0, 0);
        labelRow.addView(sliderValueText);

        bottomPanel.addView(labelRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Slider
        adjustmentSlider = new SeekBar(this);
        adjustmentSlider.setMax(200);
        adjustmentSlider.setProgress(100);
        adjustmentSlider.setPadding(dp(16), dp(4), dp(16), dp(4));
        adjustmentSlider.setVisibility(View.INVISIBLE);
        adjustmentSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && selectedTool >= 0) {
                    applySliderValue(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        bottomPanel.addView(adjustmentSlider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Tool buttons (horizontal scroll)
        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        toolsContainer = new LinearLayout(this);
        toolsContainer.setOrientation(LinearLayout.HORIZONTAL);
        toolsContainer.setPadding(dp(8), dp(4), dp(8), dp(4));
        toolsScroll.addView(toolsContainer);
        bottomPanel.addView(toolsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Crop button (first in tool list)
        TextView cropBtn = new TextView(this);
        cropBtn.setText("Crop");
        cropBtn.setTextColor(0xFFFF9800);
        cropBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cropBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        cropBtn.setGravity(Gravity.CENTER);
        cropBtn.setOnClickListener(v -> launchCrop());
        toolsContainer.addView(cropBtn);

        // Draw button
        TextView drawBtn = new TextView(this);
        drawBtn.setText("Draw");
        drawBtn.setTextColor(0xFFFF9800);
        drawBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        drawBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        drawBtn.setGravity(Gravity.CENTER);
        drawBtn.setOnClickListener(v -> launchPaint());
        toolsContainer.addView(drawBtn);

        for (int i = 0; i < TOOL_NAMES.length; i++) {
            addToolButton(i, TOOL_NAMES[i]);
        }

        // Action buttons (cancel / done)
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(0, dp(4), 0, dp(8));

        TextView cancelBtn = createActionButton("Cancel");
        cancelBtn.setOnClickListener(v -> finish());
        actionRow.addView(cancelBtn);

        View spacer = new View(this);
        actionRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        TextView resetBtn = createActionButton("Reset");
        resetBtn.setOnClickListener(v -> {
            filterParams.reset();
            selectedTool = -1;
            adjustmentSlider.setVisibility(View.INVISIBLE);
            currentToolLabel.setText("Select a tool");
            sliderValueText.setText("");
            renderer.requestRender();
        });
        actionRow.addView(resetBtn);

        View spacer2 = new View(this);
        actionRow.addView(spacer2, new LinearLayout.LayoutParams(0, 1, 1));

        TextView doneBtn = createActionButton("Save");
        doneBtn.setTextColor(0xFF4FC3F7);
        doneBtn.setOnClickListener(v -> saveResult());
        actionRow.addView(doneBtn);

        bottomPanel.addView(actionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        // Load the image
        loadImage();

        // Fit TextureView to bitmap aspect ratio after layout
        root.post(() -> {
            if (sourceBitmap == null) return;
            float bitmapAspect = (float) sourceBitmap.getWidth() / sourceBitmap.getHeight();
            int availW = root.getWidth();
            int availH = root.getHeight() - dp(200); // minus bottom panel
            if (availW <= 0 || availH <= 0) return;
            float areaAspect = (float) availW / availH;

            int newW, newH;
            if (bitmapAspect > areaAspect) {
                newW = availW;
                newH = (int) (availW / bitmapAspect);
            } else {
                newH = availH;
                newW = (int) (availH * bitmapAspect);
            }

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) previewView.getLayoutParams();
            lp.width = newW;
            lp.height = newH;
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = (availH - newH) / 2;
            lp.bottomMargin = 0;
            previewView.setLayoutParams(lp);
        });

        // Set up GL renderer when TextureView is ready
        previewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int width, int height) {
                renderer = new FilterRenderer(previewView, filterParams);
                if (sourceBitmap != null) {
                    renderer.setSourceBitmap(sourceBitmap);
                }
                renderer.start();
            }
            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) {}
            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) {
                if (renderer != null) {
                    renderer.release();
                }
                return true;
            }
            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) {}
        });
    }

    private void launchCrop() {
        if (imageUri == null) return;
        Intent cropIntent = new Intent(this, CropActivity.class);
        cropIntent.putExtra(CropActivity.EXTRA_IMAGE_URI, imageUri);
        startActivityForResult(cropIntent, REQUEST_CROP);
    }

    private void launchPaint() {
        if (imageUri == null) return;
        Intent paintIntent = new Intent(this, PaintActivity.class);
        paintIntent.putExtra(PaintActivity.EXTRA_IMAGE_URI, imageUri);
        startActivityForResult(paintIntent, REQUEST_PAINT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            String path = null;
            if (requestCode == REQUEST_CROP) {
                path = data.getStringExtra(CropActivity.EXTRA_RESULT_PATH);
            } else if (requestCode == REQUEST_PAINT) {
                path = data.getStringExtra(PaintActivity.EXTRA_RESULT_PATH);
            }
            if (path != null) {
                Bitmap result = BitmapFactory.decodeFile(path);
                if (result != null) {
                    if (sourceBitmap != null) sourceBitmap.recycle();
                    sourceBitmap = result;
                    if (renderer != null) {
                        renderer.setSourceBitmap(sourceBitmap);
                        renderer.requestRender();
                    }
                    imageUri = Uri.fromFile(new java.io.File(path));
                }
            }
        }
    }

    private void loadImage() {
        Intent intent = getIntent();
        if (intent == null) return;

        imageUri = intent.getParcelableExtra(EXTRA_IMAGE_URI);
        if (imageUri == null) return;

        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            if (is != null) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                sourceBitmap = BitmapFactory.decodeStream(is, null, opts);
                is.close();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void addToolButton(int index, String name) {
        TextView btn = new TextView(this);
        btn.setText(name);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setPadding(dp(12), dp(10), dp(12), dp(10));
        btn.setGravity(Gravity.CENTER);
        btn.setOnClickListener(v -> selectTool(index));
        toolsContainer.addView(btn);
    }

    private void selectTool(int index) {
        selectedTool = index;
        currentToolLabel.setText(TOOL_NAMES[index]);
        adjustmentSlider.setVisibility(View.VISIBLE);

        float currentValue = getToolValue(index);
        boolean isCentered = isCenteredTool(index);

        if (isCentered) {
            adjustmentSlider.setMax(200);
            adjustmentSlider.setProgress((int) (currentValue + 100));
        } else {
            adjustmentSlider.setMax(100);
            adjustmentSlider.setProgress((int) currentValue);
        }

        updateSliderValueText(currentValue, isCentered);

        // Highlight selected tool (offset by 2 for Crop and Draw buttons at indices 0,1)
        for (int i = 0; i < toolsContainer.getChildCount(); i++) {
            TextView child = (TextView) toolsContainer.getChildAt(i);
            if (i < 2) {
                child.setTextColor(Color.WHITE);
            } else {
                child.setTextColor((i - 2) == index ? 0xFF4FC3F7 : Color.WHITE);
            }
        }
    }

    private void applySliderValue(int progress) {
        boolean centered = isCenteredTool(selectedTool);
        float value = centered ? (progress - 100) : progress;
        setToolValue(selectedTool, value);
        updateSliderValueText(value, centered);
        renderer.requestRender();
    }

    private void updateSliderValueText(float value, boolean centered) {
        int intVal = Math.round(value);
        if (centered) {
            sliderValueText.setText(intVal > 0 ? "+" + intVal : String.valueOf(intVal));
        } else {
            sliderValueText.setText(String.valueOf(intVal));
        }
    }

    private boolean isCenteredTool(int index) {
        // Exposure, Contrast, Saturation, Warmth, Highlights, Shadows are centered (-100 to 100)
        // Sharpen, Fade, Grain, Vignette are 0 to 100
        return index <= 5;
    }

    private float getToolValue(int index) {
        switch (index) {
            case 0: return filterParams.exposureValue;
            case 1: return filterParams.contrastValue;
            case 2: return filterParams.saturationValue;
            case 3: return filterParams.warmthValue;
            case 4: return filterParams.highlightsValue;
            case 5: return filterParams.shadowsValue;
            case 6: return filterParams.sharpenValue;
            case 7: return filterParams.fadeValue;
            case 8: return filterParams.grainValue;
            case 9: return filterParams.vignetteValue;
            default: return 0;
        }
    }

    private void setToolValue(int index, float value) {
        switch (index) {
            case 0: filterParams.exposureValue = value; break;
            case 1: filterParams.contrastValue = value; break;
            case 2: filterParams.saturationValue = value; break;
            case 3: filterParams.warmthValue = value; break;
            case 4: filterParams.highlightsValue = value; break;
            case 5: filterParams.shadowsValue = value; break;
            case 6: filterParams.sharpenValue = value; break;
            case 7: filterParams.fadeValue = value; break;
            case 8: filterParams.grainValue = value; break;
            case 9: filterParams.vignetteValue = value; break;
        }
    }

    private void saveResult() {
        if (renderer == null || sourceBitmap == null) return;

        Bitmap result = renderer.renderToBitmap();
        if (result == null) {
            Toast.makeText(this, "Failed to render image", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "edited_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoEditor");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os != null) {
                    result.compress(Bitmap.CompressFormat.JPEG, 95, os);
                    os.close();
                    Toast.makeText(this, "Photo saved to gallery", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            result.recycle();
        }
    }

    private TextView createActionButton(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setPadding(dp(20), dp(8), dp(20), dp(8));
        btn.setGravity(Gravity.CENTER);
        return btn;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
