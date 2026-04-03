package com.imgedt.editor.crop;

import android.graphics.RectF;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Robolectric JVM tests for CropState transform logic.
 * Uses Robolectric for android.graphics.Matrix and RectF stubs.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CropStateTest {

    private static final float DELTA = 0.001f;

    private CropState state;

    @Before
    public void setUp() {
        state = new CropState(400, 300);
    }

    // --- Constructor ---

    @Test
    public void constructor_setsWidthHeight() {
        assertEquals(400, state.width, DELTA);
        assertEquals(300, state.height, DELTA);
    }

    @Test
    public void constructor_defaultState() {
        assertEquals(0, state.x, DELTA);
        assertEquals(0, state.y, DELTA);
        assertEquals(1.0f, state.scale, DELTA);
        assertEquals(1.0f, state.minimumScale, DELTA);
        assertEquals(0, state.rotation, DELTA);
        assertEquals(0, state.orientation);
        assertFalse(state.mirrored);
    }

    // --- getOrientedWidth / getOrientedHeight ---

    @Test
    public void getOrientedWidth_orientation0() {
        state.orientation = 0;
        assertEquals(400, state.getOrientedWidth(), DELTA);
        assertEquals(300, state.getOrientedHeight(), DELTA);
    }

    @Test
    public void getOrientedWidth_orientation90() {
        state.orientation = 90;
        assertEquals(300, state.getOrientedWidth(), DELTA);
        assertEquals(400, state.getOrientedHeight(), DELTA);
    }

    @Test
    public void getOrientedWidth_orientation180() {
        state.orientation = 180;
        assertEquals(400, state.getOrientedWidth(), DELTA);
        assertEquals(300, state.getOrientedHeight(), DELTA);
    }

    @Test
    public void getOrientedWidth_orientation270() {
        state.orientation = 270;
        assertEquals(300, state.getOrientedWidth(), DELTA);
        assertEquals(400, state.getOrientedHeight(), DELTA);
    }

    // --- translate ---

    @Test
    public void translate_updatesPosition() {
        state.translate(10, 20);
        assertEquals(10, state.x, DELTA);
        assertEquals(20, state.y, DELTA);
    }

    @Test
    public void translate_accumulates() {
        state.translate(10, 20);
        state.translate(5, 3);
        assertEquals(15, state.x, DELTA);
        assertEquals(23, state.y, DELTA);
    }

    // --- scale ---

    @Test
    public void scale_updatesScaleFactor() {
        state.scale(2.0f, 0, 0);
        assertEquals(2.0f, state.scale, DELTA);
    }

    @Test
    public void scale_multiplies() {
        state.scale(2.0f, 0, 0);
        state.scale(0.5f, 0, 0);
        assertEquals(1.0f, state.scale, DELTA);
    }

    // --- rotate ---

    @Test
    public void rotate_updatesRotation() {
        state.rotate(15, 0, 0);
        assertEquals(15, state.rotation, DELTA);
    }

    @Test
    public void rotate_accumulates() {
        state.rotate(15, 0, 0);
        state.rotate(10, 0, 0);
        assertEquals(25, state.rotation, DELTA);
    }

    // --- reset ---

    @Test
    public void reset_clearsAll() {
        state.translate(50, 60);
        state.rotate(30, 0, 0);
        state.orientation = 90;
        state.mirrored = true;

        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);

        assertEquals(0, state.x, DELTA);
        assertEquals(0, state.y, DELTA);
        assertEquals(0, state.rotation, DELTA);
        assertEquals(0, state.orientation);
        assertFalse(state.mirrored);
    }

    @Test
    public void reset_setsScaleToMinimum() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);

        // minimumScale = max(200/400, 150/300) = max(0.5, 0.5) = 0.5
        assertEquals(0.5f, state.minimumScale, DELTA);
        assertEquals(state.minimumScale, state.scale, DELTA);
    }

    // --- resetTransform ---

    @Test
    public void resetTransform_preservesOrientation() {
        state.orientation = 90;
        state.mirrored = true;
        state.translate(50, 60);
        state.rotate(30, 0, 0);

        RectF cropRect = new RectF(0, 0, 200, 150);
        state.resetTransform(cropRect);

        assertEquals(0, state.x, DELTA);
        assertEquals(0, state.y, DELTA);
        assertEquals(0, state.rotation, DELTA);
        assertEquals(90, state.orientation);
        assertTrue(state.mirrored);
    }

    @Test
    public void resetTransform_setsScaleToMinimum() {
        state.orientation = 90;
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.resetTransform(cropRect);

        // With orientation 90, orientedW=300, orientedH=400
        // minimumScale = max(200/300, 150/400) = max(0.667, 0.375) = 0.667
        assertEquals(0.667f, state.minimumScale, DELTA);
        assertEquals(state.minimumScale, state.scale, DELTA);
    }

    // --- updateMinimumScale ---

    @Test
    public void updateMinimumScale_landscape() {
        // 100x200 image, 300x200 crop -> max(300/100, 200/200) = 3.0
        CropState s = new CropState(100, 200);
        s.updateMinimumScale(new RectF(0, 0, 300, 200));
        assertEquals(3.0f, s.minimumScale, DELTA);
    }

    @Test
    public void updateMinimumScale_portrait() {
        // 200x100 image, 100x300 crop -> max(100/200, 300/100) = 3.0
        CropState s = new CropState(200, 100);
        s.updateMinimumScale(new RectF(0, 0, 100, 300));
        assertEquals(3.0f, s.minimumScale, DELTA);
    }

    @Test
    public void updateMinimumScale_nullCropRect_noChange() {
        float before = state.minimumScale;
        state.updateMinimumScale(null);
        assertEquals(before, state.minimumScale, DELTA);
    }

    @Test
    public void updateMinimumScale_zeroDimensions_noChange() {
        CropState s = new CropState(0, 0);
        float before = s.minimumScale;
        s.updateMinimumScale(new RectF(0, 0, 100, 100));
        assertEquals(before, s.minimumScale, DELTA);
    }

    @Test
    public void updateMinimumScale_withOrientation90() {
        CropState s = new CropState(100, 200);
        s.orientation = 90;
        // orientedW=200, orientedH=100
        s.updateMinimumScale(new RectF(0, 0, 200, 100));
        // max(200/200, 100/100) = 1.0
        assertEquals(1.0f, s.minimumScale, DELTA);
    }

    // --- hasChanges ---

    @Test
    public void hasChanges_afterReset_false() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);
        assertFalse(state.hasChanges());
    }

    @Test
    public void hasChanges_translated_true() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);
        state.translate(1, 0);
        assertTrue(state.hasChanges());
    }

    @Test
    public void hasChanges_rotated_true() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);
        state.rotate(1, 0, 0);
        assertTrue(state.hasChanges());
    }

    @Test
    public void hasChanges_mirrored_true() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);
        state.mirrored = true;
        assertTrue(state.hasChanges());
    }

    @Test
    public void hasChanges_orientationChanged_true() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);
        state.orientation = 90;
        assertTrue(state.hasChanges());
    }

    @Test
    public void hasChanges_belowEpsilon_false() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);
        // EPSILON is 0.00001f, translate by less than that
        state.x = 0.000005f;
        assertFalse(state.hasChanges());
    }

    @Test
    public void hasChanges_scaleAtMinimum_false() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);
        // scale == minimumScale, so |scale - minimumScale| < EPSILON
        assertFalse(state.hasChanges());
    }

    @Test
    public void hasChanges_scaleChanged_true() {
        RectF cropRect = new RectF(0, 0, 200, 150);
        state.reset(cropRect);
        state.scale(2.0f, 0, 0);
        assertTrue(state.hasChanges());
    }
}
