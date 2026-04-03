package com.imgedt.editor.filter;

/**
 * Holds all adjustment parameter values and converts them to shader uniform values.
 * Slider ranges match Telegram's photo editor.
 */
public class FilterParams {

    // Slider values (user-facing, -100 to 100 or 0 to 100)
    public float exposureValue = 0;     // -100 to 100
    public float contrastValue = 0;     // -100 to 100
    public float saturationValue = 0;   // -100 to 100
    public float warmthValue = 0;       // -100 to 100
    public float highlightsValue = 0;   // -100 to 100
    public float shadowsValue = 0;      // -100 to 100
    public float vignetteValue = 0;     // 0 to 100
    public float grainValue = 0;        // 0 to 100
    public float sharpenValue = 0;      // 0 to 100
    public float fadeValue = 0;         // 0 to 100

    // Shader uniform getters (transform slider values to shader-compatible ranges)

    public float getExposure() {
        return exposureValue / 100.0f;
    }

    public float getContrast() {
        return (contrastValue / 100.0f) * 0.6f + 1.0f;
    }

    public float getSaturation() {
        float v = saturationValue / 100.0f;
        if (v > 0) {
            return v * 1.50f + 1.0f;
        }
        return v + 1.0f;
    }

    public float getWarmth() {
        return warmthValue / 100.0f;
    }

    public float getHighlights() {
        return (highlightsValue * 0.90f + 100.0f) / 100.0f;
    }

    public float getShadows() {
        return (shadowsValue * 0.75f + 100.0f) / 100.0f;
    }

    public float getVignette() {
        return vignetteValue / 100.0f;
    }

    public float getGrain() {
        return grainValue / 100.0f * 0.04f;
    }

    public float getSharpen() {
        return 0.11f + sharpenValue / 100.0f * 0.6f;
    }

    public float getFade() {
        return fadeValue / 100.0f;
    }

    public boolean hasAnyAdjustment() {
        return exposureValue != 0 || contrastValue != 0 || saturationValue != 0
                || warmthValue != 0 || highlightsValue != 0 || shadowsValue != 0
                || vignetteValue != 0 || grainValue != 0 || sharpenValue != 0
                || fadeValue != 0;
    }

    public void reset() {
        exposureValue = 0;
        contrastValue = 0;
        saturationValue = 0;
        warmthValue = 0;
        highlightsValue = 0;
        shadowsValue = 0;
        vignetteValue = 0;
        grainValue = 0;
        sharpenValue = 0;
        fadeValue = 0;
    }
}
