package com.photoeditor.editor.crop;

import android.graphics.Matrix;
import android.graphics.RectF;

/**
 * Holds the crop transform state: translation, scale, rotation, orientation, mirror.
 */
public class CropState {

    private static final float EPSILON = 0.00001f;

    public float width, height;
    public float x, y;
    public float scale;
    public float minimumScale;
    public float rotation; // free rotation (-45 to +45 degrees)
    public int orientation; // 0, 90, 180, 270
    public boolean mirrored;
    public final Matrix matrix = new Matrix();

    public CropState(float width, float height) {
        this.width = width;
        this.height = height;
        this.scale = 1.0f;
        this.minimumScale = 1.0f;
    }

    public float getOrientedWidth() {
        return orientation % 180 != 0 ? height : width;
    }

    public float getOrientedHeight() {
        return orientation % 180 != 0 ? width : height;
    }

    public void translate(float dx, float dy) {
        x += dx;
        y += dy;
        matrix.postTranslate(dx, dy);
    }

    public void scale(float s, float pivotX, float pivotY) {
        scale *= s;
        matrix.postScale(s, s, pivotX, pivotY);
    }

    public void rotate(float angle, float pivotX, float pivotY) {
        rotation += angle;
        matrix.postRotate(angle, pivotX, pivotY);
    }

    public void reset(RectF cropRect) {
        x = 0;
        y = 0;
        rotation = 0;
        mirrored = false;
        matrix.reset();

        updateMinimumScale(cropRect);
        scale = minimumScale;
        matrix.setScale(scale, scale);
    }

    public void updateMinimumScale(RectF cropRect) {
        if (cropRect == null) return;
        float ow = getOrientedWidth();
        float oh = getOrientedHeight();
        if (ow == 0 || oh == 0) return;

        float cropW = cropRect.width();
        float cropH = cropRect.height();
        minimumScale = Math.max(cropW / ow, cropH / oh);
    }

    public boolean hasChanges() {
        return Math.abs(x) > EPSILON
                || Math.abs(y) > EPSILON
                || Math.abs(scale - minimumScale) > EPSILON
                || Math.abs(rotation) > EPSILON
                || orientation != 0
                || mirrored;
    }
}
