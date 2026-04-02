package com.photoeditor.editor.crop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Activity for cropping and rotating images.
 * Displays the image with a crop overlay, aspect ratio presets, rotation wheel,
 * and 90-degree rotate / mirror buttons.
 */
public class CropActivity extends Activity {

    public static final String EXTRA_IMAGE_URI = "image_uri";
    public static final String EXTRA_RESULT_PATH = "result_path";
    private static final int MAX_OUTPUT_SIDE = 1280;

    private CropView cropView;
    private CropAreaView areaView;
    private CropRotationWheel rotationWheel;
    private Bitmap sourceBitmap;

    private float currentAspectRatio = 0; // 0 = freeform
    private int selectedAspectIndex = 0;

    private static final String[] ASPECT_NAMES = {"Free", "1:1", "4:3", "3:4", "16:9", "9:16"};
    private static final float[] ASPECT_RATIOS = {0, 1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Crop view (image display + pan/zoom)
        cropView = new CropView(this);
        FrameLayout.LayoutParams cropLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cropLp.bottomMargin = dp(160);
        root.addView(cropView, cropLp);

        // Area view (crop overlay, on top of crop view)
        areaView = new CropAreaView(this);
        FrameLayout.LayoutParams areaLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        areaLp.bottomMargin = dp(160);
        root.addView(areaView, areaLp);

        cropView.setAreaView(areaView);
        areaView.setListener((cropRect, finished) -> {
            if (finished) {
                cropView.getState().updateMinimumScale(cropRect);
            }
        });

        // Bottom panel
        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setBackgroundColor(0xE0000000);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(160));
        bottomLp.gravity = Gravity.BOTTOM;
        root.addView(bottomPanel, bottomLp);

        // Rotation wheel
        rotationWheel = new CropRotationWheel(this);
        rotationWheel.setListener(new CropRotationWheel.Listener() {
            @Override
            public void onRotationChanged(float angle) {
                cropView.setFreeRotation(angle);
            }
            @Override
            public void onRotationEnded(float angle) {
                cropView.setFreeRotation(angle);
            }
        });
        bottomPanel.addView(rotationWheel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // Aspect ratio buttons
        LinearLayout aspectRow = new LinearLayout(this);
        aspectRow.setOrientation(LinearLayout.HORIZONTAL);
        aspectRow.setGravity(Gravity.CENTER);
        aspectRow.setPadding(dp(8), dp(4), dp(8), dp(4));

        for (int i = 0; i < ASPECT_NAMES.length; i++) {
            final int index = i;
            TextView btn = new TextView(this);
            btn.setText(ASPECT_NAMES[i]);
            btn.setTextColor(i == 0 ? 0xFF4FC3F7 : Color.WHITE);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            btn.setPadding(dp(10), dp(6), dp(10), dp(6));
            btn.setGravity(Gravity.CENTER);
            btn.setTag(i);
            btn.setOnClickListener(v -> selectAspect(index, aspectRow));
            aspectRow.addView(btn);
        }
        bottomPanel.addView(aspectRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Action buttons (rotate, mirror, cancel, done)
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(dp(8), dp(4), dp(8), dp(12));

        TextView cancelBtn = createActionButton("Cancel");
        cancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        actionRow.addView(cancelBtn);

        addSpacer(actionRow);

        TextView rotate90Btn = createActionButton("Rotate");
        rotate90Btn.setOnClickListener(v -> {
            cropView.rotate90(90);
            rotationWheel.setRotation(0);
        });
        actionRow.addView(rotate90Btn);

        addSpacer(actionRow);

        TextView mirrorBtn = createActionButton("Mirror");
        mirrorBtn.setOnClickListener(v -> cropView.mirror());
        actionRow.addView(mirrorBtn);

        addSpacer(actionRow);

        TextView resetBtn = createActionButton("Reset");
        resetBtn.setOnClickListener(v -> {
            rotationWheel.setRotation(0);
            selectAspect(0, aspectRow);
            RectF rect = areaView.getCropRect();
            cropView.resetToFit(rect);
        });
        actionRow.addView(resetBtn);

        addSpacer(actionRow);

        TextView doneBtn = createActionButton("Done");
        doneBtn.setTextColor(0xFF4FC3F7);
        doneBtn.setOnClickListener(v -> applyCrop());
        actionRow.addView(doneBtn);

        bottomPanel.addView(actionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        loadImage();

        // Initialize crop area after layout
        root.post(() -> {
            float bitmapAspect = sourceBitmap != null ?
                    (float) sourceBitmap.getWidth() / sourceBitmap.getHeight() : 1f;
            areaView.calculateInitialRect(bitmapAspect);
            RectF cropRect = areaView.getCropRect();
            cropView.resetToFit(cropRect);
        });
    }

    private void loadImage() {
        Intent intent = getIntent();
        if (intent == null) return;
        Uri imageUri = intent.getParcelableExtra(EXTRA_IMAGE_URI);
        if (imageUri == null) return;
        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            if (is != null) {
                sourceBitmap = BitmapFactory.decodeStream(is);
                is.close();
                cropView.setBitmap(sourceBitmap);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void selectAspect(int index, LinearLayout row) {
        selectedAspectIndex = index;
        currentAspectRatio = ASPECT_RATIOS[index];
        areaView.setLockedAspectRatio(currentAspectRatio);
        areaView.calculateInitialRect(currentAspectRatio > 0 ? currentAspectRatio :
                (sourceBitmap != null ? (float) sourceBitmap.getWidth() / sourceBitmap.getHeight() : 1f));
        RectF cropRect = areaView.getCropRect();
        cropView.resetToFit(cropRect);

        for (int i = 0; i < row.getChildCount(); i++) {
            ((TextView) row.getChildAt(i)).setTextColor(i == index ? 0xFF4FC3F7 : Color.WHITE);
        }
    }

    private void applyCrop() {
        if (cropView == null) return;
        Bitmap result = cropView.cropBitmap(MAX_OUTPUT_SIDE);
        if (result == null) {
            Toast.makeText(this, "Failed to crop", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to cache for the editor to pick up
        try {
            java.io.File cacheFile = new java.io.File(getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);
            result.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();
            result.recycle();

            Intent data = new Intent();
            data.putExtra(EXTRA_RESULT_PATH, cacheFile.getAbsolutePath());
            setResult(RESULT_OK, data);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save crop: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView createActionButton(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
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
}
