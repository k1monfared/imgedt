package com.photoeditor.editor.crop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * View that displays the image being cropped and handles pan/zoom gestures.
 * Works in coordination with CropAreaView for the crop overlay.
 */
public class CropView extends View {

    private Bitmap bitmap;
    private CropState state;
    private CropAreaView areaView;

    private final Matrix displayMatrix = new Matrix();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    private ScaleGestureDetector scaleDetector;
    private float lastTouchX, lastTouchY;
    private boolean isDragging;
    private int activePointerId = -1;

    public CropView(Context context) {
        super(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (state == null) return false;
                float sf = detector.getScaleFactor();
                float newScale = state.scale * sf;
                if (newScale < state.minimumScale * 0.5f || newScale > 30f) return false;
                state.scale(sf, detector.getFocusX() - getWidth() / 2f, detector.getFocusY() - getHeight() / 2f);
                invalidate();
                return true;
            }
        });
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        if (bitmap != null) {
            state = new CropState(bitmap.getWidth(), bitmap.getHeight());
        }
        invalidate();
    }

    public void setAreaView(CropAreaView areaView) {
        this.areaView = areaView;
    }

    public CropState getState() {
        return state;
    }

    /**
     * Reset the crop state to fit the bitmap into the given crop rect.
     */
    public void resetToFit(RectF cropRect) {
        if (state == null || cropRect == null) return;
        state.reset(cropRect);
        invalidate();
    }

    /**
     * Apply rotation in 90-degree increments.
     */
    public void rotate90(int degrees) {
        if (state == null || areaView == null) return;
        state.orientation = (state.orientation + degrees + 360) % 360;
        RectF cropRect = areaView.getCropRect();
        state.reset(cropRect);
        invalidate();
    }

    /**
     * Set free rotation angle.
     */
    public void setFreeRotation(float angle) {
        if (state == null) return;
        float delta = angle - state.rotation;
        state.rotate(delta, 0, 0);
        fitContentInBounds();
        invalidate();
    }

    /**
     * Mirror horizontally.
     */
    public void mirror() {
        if (state == null) return;
        state.mirrored = !state.mirrored;
        invalidate();
    }

    /**
     * Produce the final cropped bitmap.
     */
    public Bitmap cropBitmap(int maxSide) {
        if (bitmap == null || state == null || areaView == null) return null;

        RectF cropRect = areaView.getCropRect();
        float cropW = cropRect.width();
        float cropH = cropRect.height();

        // Scale output to fit within maxSide
        float outputScale;
        if (cropW > cropH) {
            outputScale = Math.min(maxSide / cropW, 1.0f);
        } else {
            outputScale = Math.min(maxSide / cropH, 1.0f);
        }

        // Compute output dimensions relative to bitmap space
        float sc = Math.max(bitmap.getWidth(), bitmap.getHeight())
                / Math.max(state.getOrientedWidth(), state.getOrientedHeight());

        int outW = Math.round(cropW * sc / state.scale * outputScale);
        int outH = Math.round(cropH * sc / state.scale * outputScale);
        if (outW <= 0 || outH <= 0) return null;

        Bitmap output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Matrix m = new Matrix();
        m.postTranslate(-bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f);
        if (state.mirrored) m.postScale(-1, 1);
        m.postScale(1 / sc, 1 / sc);
        m.postRotate(state.orientation);
        m.postConcat(state.matrix);
        float finalScale = outputScale * sc / state.scale;
        m.postScale(finalScale, finalScale);
        m.postTranslate(outW / 2f, outH / 2f);

        canvas.drawBitmap(bitmap, m, bitmapPaint);
        return output;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || state == null) return;

        updateDisplayMatrix();
        canvas.drawBitmap(bitmap, displayMatrix, bitmapPaint);
    }

    private void updateDisplayMatrix() {
        if (areaView == null) return;
        RectF cropRect = areaView.getCropRect();
        float cx = cropRect.centerX();
        float cy = cropRect.centerY();

        displayMatrix.reset();
        displayMatrix.postTranslate(-bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f);
        if (state.mirrored) displayMatrix.postScale(-1, 1);
        displayMatrix.postRotate(state.orientation);
        displayMatrix.postConcat(state.matrix);
        displayMatrix.postTranslate(cx, cy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging && !scaleDetector.isInProgress()) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex < 0) break;
                    float x = event.getX(pointerIndex);
                    float y = event.getY(pointerIndex);
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    lastTouchX = x;
                    lastTouchY = y;
                    if (state != null) {
                        state.translate(dx, dy);
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                activePointerId = -1;
                fitContentInBounds();
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    int newIndex = pointerIndex == 0 ? 1 : 0;
                    lastTouchX = event.getX(newIndex);
                    lastTouchY = event.getY(newIndex);
                    activePointerId = event.getPointerId(newIndex);
                }
                return true;
        }
        return true;
    }

    /**
     * Ensure the image covers the crop rect completely.
     */
    private void fitContentInBounds() {
        if (state == null || areaView == null) return;

        RectF cropRect = areaView.getCropRect();
        float ow = state.getOrientedWidth();
        float oh = state.getOrientedHeight();

        // Build content rect
        RectF contentRect = new RectF(-ow / 2, -oh / 2, ow / 2, oh / 2);
        Matrix m = new Matrix(state.matrix);
        m.mapRect(contentRect);

        // Offset by crop center
        float cx = cropRect.centerX();
        float cy = cropRect.centerY();
        contentRect.offset(cx, cy);

        // Check scale
        float minScale = Math.max(
                cropRect.width() / contentRect.width() * state.scale,
                cropRect.height() / contentRect.height() * state.scale
        );
        if (state.scale < minScale) {
            float sf = minScale / state.scale;
            state.scale(sf, 0, 0);
            // Recompute content rect
            contentRect.set(-ow / 2, -oh / 2, ow / 2, oh / 2);
            m = new Matrix(state.matrix);
            m.mapRect(contentRect);
            contentRect.offset(cx, cy);
        }

        // Check translation
        float dx = 0, dy = 0;
        if (contentRect.left > cropRect.left) dx = cropRect.left - contentRect.left;
        else if (contentRect.right < cropRect.right) dx = cropRect.right - contentRect.right;
        if (contentRect.top > cropRect.top) dy = cropRect.top - contentRect.top;
        else if (contentRect.bottom < cropRect.bottom) dy = cropRect.bottom - contentRect.bottom;

        if (dx != 0 || dy != 0) {
            state.translate(dx, dy);
        }

        invalidate();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
