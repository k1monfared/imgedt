package com.imgedt.editor.filter;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * JVM unit tests for FilterParams slider-to-uniform conversions.
 * These run on the local JVM without any Android framework.
 */
public class FilterParamsTest {

    private static final float DELTA = 0.001f;

    private FilterParams params;

    @Before
    public void setUp() {
        params = new FilterParams();
    }

    // --- Default state ---

    @Test
    public void defaultValuesAreZero() {
        assertEquals(0, params.exposureValue, DELTA);
        assertEquals(0, params.contrastValue, DELTA);
        assertEquals(0, params.saturationValue, DELTA);
        assertEquals(0, params.warmthValue, DELTA);
        assertEquals(0, params.highlightsValue, DELTA);
        assertEquals(0, params.shadowsValue, DELTA);
        assertEquals(0, params.vignetteValue, DELTA);
        assertEquals(0, params.grainValue, DELTA);
        assertEquals(0, params.sharpenValue, DELTA);
        assertEquals(0, params.fadeValue, DELTA);
    }

    // --- hasAnyAdjustment ---

    @Test
    public void hasAnyAdjustment_allZero_returnsFalse() {
        assertFalse(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_exposureNonZero_returnsTrue() {
        params.exposureValue = 1;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_contrastNonZero_returnsTrue() {
        params.contrastValue = -50;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_saturationNonZero_returnsTrue() {
        params.saturationValue = 10;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_warmthNonZero_returnsTrue() {
        params.warmthValue = -1;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_highlightsNonZero_returnsTrue() {
        params.highlightsValue = 100;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_shadowsNonZero_returnsTrue() {
        params.shadowsValue = -100;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_vignetteNonZero_returnsTrue() {
        params.vignetteValue = 50;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_grainNonZero_returnsTrue() {
        params.grainValue = 25;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_sharpenNonZero_returnsTrue() {
        params.sharpenValue = 75;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_fadeNonZero_returnsTrue() {
        params.fadeValue = 10;
        assertTrue(params.hasAnyAdjustment());
    }

    @Test
    public void hasAnyAdjustment_allFieldsNonZero_returnsTrue() {
        params.exposureValue = 10;
        params.contrastValue = 20;
        params.saturationValue = 30;
        params.warmthValue = 40;
        params.highlightsValue = 50;
        params.shadowsValue = -10;
        params.vignetteValue = 60;
        params.grainValue = 70;
        params.sharpenValue = 80;
        params.fadeValue = 90;
        assertTrue(params.hasAnyAdjustment());
    }

    // --- reset ---

    @Test
    public void reset_clearsAllValues() {
        params.exposureValue = 50;
        params.contrastValue = -30;
        params.saturationValue = 100;
        params.warmthValue = -100;
        params.highlightsValue = 75;
        params.shadowsValue = -50;
        params.vignetteValue = 80;
        params.grainValue = 40;
        params.sharpenValue = 60;
        params.fadeValue = 20;

        params.reset();

        assertEquals(0, params.exposureValue, DELTA);
        assertEquals(0, params.contrastValue, DELTA);
        assertEquals(0, params.saturationValue, DELTA);
        assertEquals(0, params.warmthValue, DELTA);
        assertEquals(0, params.highlightsValue, DELTA);
        assertEquals(0, params.shadowsValue, DELTA);
        assertEquals(0, params.vignetteValue, DELTA);
        assertEquals(0, params.grainValue, DELTA);
        assertEquals(0, params.sharpenValue, DELTA);
        assertEquals(0, params.fadeValue, DELTA);
        assertFalse(params.hasAnyAdjustment());
    }

    // --- Exposure conversion: val/100 -> [-1.0, 1.0] ---

    @Test
    public void getExposure_atMinimum() {
        params.exposureValue = -100;
        assertEquals(-1.0f, params.getExposure(), DELTA);
    }

    @Test
    public void getExposure_atZero() {
        params.exposureValue = 0;
        assertEquals(0.0f, params.getExposure(), DELTA);
    }

    @Test
    public void getExposure_atMaximum() {
        params.exposureValue = 100;
        assertEquals(1.0f, params.getExposure(), DELTA);
    }

    @Test
    public void getExposure_midValue() {
        params.exposureValue = 50;
        assertEquals(0.5f, params.getExposure(), DELTA);
    }

    // --- Contrast conversion: (val/100)*0.6+1.0 -> [0.4, 1.6] ---

    @Test
    public void getContrast_atMinimum() {
        params.contrastValue = -100;
        assertEquals(0.4f, params.getContrast(), DELTA);
    }

    @Test
    public void getContrast_atZero() {
        params.contrastValue = 0;
        assertEquals(1.0f, params.getContrast(), DELTA);
    }

    @Test
    public void getContrast_atMaximum() {
        params.contrastValue = 100;
        assertEquals(1.6f, params.getContrast(), DELTA);
    }

    // --- Saturation: asymmetric formula ---

    @Test
    public void getSaturation_atMaxPositive() {
        params.saturationValue = 100;
        // v=1.0: 1.0*1.50+1.0 = 2.50
        assertEquals(2.50f, params.getSaturation(), DELTA);
    }

    @Test
    public void getSaturation_atMaxNegative() {
        params.saturationValue = -100;
        // v=-1.0: -1.0+1.0 = 0.0
        assertEquals(0.0f, params.getSaturation(), DELTA);
    }

    @Test
    public void getSaturation_atZero() {
        params.saturationValue = 0;
        // Both branches yield 1.0 at zero
        assertEquals(1.0f, params.getSaturation(), DELTA);
    }

    @Test
    public void getSaturation_smallPositive() {
        params.saturationValue = 1;
        // v=0.01: 0.01*1.50+1.0 = 1.015
        assertEquals(1.015f, params.getSaturation(), DELTA);
    }

    @Test
    public void getSaturation_smallNegative() {
        params.saturationValue = -1;
        // v=-0.01: -0.01+1.0 = 0.99
        assertEquals(0.99f, params.getSaturation(), DELTA);
    }

    // --- Warmth: val/100 -> [-1.0, 1.0] ---

    @Test
    public void getWarmth_atMinimum() {
        params.warmthValue = -100;
        assertEquals(-1.0f, params.getWarmth(), DELTA);
    }

    @Test
    public void getWarmth_atMaximum() {
        params.warmthValue = 100;
        assertEquals(1.0f, params.getWarmth(), DELTA);
    }

    // --- Highlights: (val*0.90+100)/100 -> [0.10, 1.90] ---

    @Test
    public void getHighlights_atMinimum() {
        params.highlightsValue = -100;
        // (-100*0.90+100)/100 = (-90+100)/100 = 0.10
        assertEquals(0.10f, params.getHighlights(), DELTA);
    }

    @Test
    public void getHighlights_atZero() {
        params.highlightsValue = 0;
        assertEquals(1.0f, params.getHighlights(), DELTA);
    }

    @Test
    public void getHighlights_atMaximum() {
        params.highlightsValue = 100;
        // (100*0.90+100)/100 = 190/100 = 1.90
        assertEquals(1.90f, params.getHighlights(), DELTA);
    }

    // --- Shadows: (val*0.75+100)/100 -> [0.25, 1.75] ---

    @Test
    public void getShadows_atMinimum() {
        params.shadowsValue = -100;
        // (-100*0.75+100)/100 = 25/100 = 0.25
        assertEquals(0.25f, params.getShadows(), DELTA);
    }

    @Test
    public void getShadows_atZero() {
        params.shadowsValue = 0;
        assertEquals(1.0f, params.getShadows(), DELTA);
    }

    @Test
    public void getShadows_atMaximum() {
        params.shadowsValue = 100;
        // (100*0.75+100)/100 = 175/100 = 1.75
        assertEquals(1.75f, params.getShadows(), DELTA);
    }

    // --- Vignette: val/100 -> [0.0, 1.0] ---

    @Test
    public void getVignette_atMinimum() {
        params.vignetteValue = 0;
        assertEquals(0.0f, params.getVignette(), DELTA);
    }

    @Test
    public void getVignette_atMaximum() {
        params.vignetteValue = 100;
        assertEquals(1.0f, params.getVignette(), DELTA);
    }

    // --- Grain: val/100*0.04 -> [0.0, 0.04] ---

    @Test
    public void getGrain_atMinimum() {
        params.grainValue = 0;
        assertEquals(0.0f, params.getGrain(), DELTA);
    }

    @Test
    public void getGrain_atMaximum() {
        params.grainValue = 100;
        assertEquals(0.04f, params.getGrain(), DELTA);
    }

    // --- Sharpen: 0.11+val/100*0.6 -> [0.11, 0.71] ---

    @Test
    public void getSharpen_atMinimum() {
        params.sharpenValue = 0;
        assertEquals(0.11f, params.getSharpen(), DELTA);
    }

    @Test
    public void getSharpen_atMaximum() {
        params.sharpenValue = 100;
        assertEquals(0.71f, params.getSharpen(), DELTA);
    }

    @Test
    public void getSharpen_midValue() {
        params.sharpenValue = 50;
        // 0.11 + 0.5*0.6 = 0.41
        assertEquals(0.41f, params.getSharpen(), DELTA);
    }

    // --- Fade: val/100 -> [0.0, 1.0] ---

    @Test
    public void getFade_atMinimum() {
        params.fadeValue = 0;
        assertEquals(0.0f, params.getFade(), DELTA);
    }

    @Test
    public void getFade_atMaximum() {
        params.fadeValue = 100;
        assertEquals(1.0f, params.getFade(), DELTA);
    }

    // --- Out-of-range behavior (documents no clamping) ---

    @Test
    public void getExposure_beyondRange_noClamp() {
        params.exposureValue = 200;
        assertEquals(2.0f, params.getExposure(), DELTA);
    }

    @Test
    public void getContrast_beyondRange_noClamp() {
        params.contrastValue = -200;
        // (-200/100)*0.6+1.0 = -1.2+1.0 = -0.2
        assertEquals(-0.2f, params.getContrast(), DELTA);
    }
}
