package com.imgedt.editor.crop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * A horizontal rotation wheel for fine-grained rotation control (-45 to +45 degrees).
 */
public class CropRotationWheel extends View {

    public interface Listener {
        void onRotationChanged(float angle);
        void onRotationEnded(float angle);
    }

    private static final float MAX_ANGLE = 90f;
    private static final float TICK_SPACING = 5f;
    private static final float SNAP_THRESHOLD = 0.5f;

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float rotation = 0;
    private float lastTouchX;
    private float density;

    private Listener listener;

    public CropRotationWheel(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;

        tickPaint.setColor(0x88FFFFFF);
        tickPaint.setStrokeWidth(dp(1));
        centerPaint.setColor(0xFF4FC3F7);
        centerPaint.setStrokeWidth(dp(2));
        labelPaint.setColor(0xAAFFFFFF);
        labelPaint.setTextSize(dp(11));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float angle) {
        this.rotation = Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, angle));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float halfW = getWidth() / 2f;

        // Pixels per degree
        float pxPerDeg = halfW / MAX_ANGLE * 0.8f;

        // Draw ticks
        for (float deg = -MAX_ANGLE; deg <= MAX_ANGLE; deg += TICK_SPACING) {
            float x = cx + (deg - rotation) * pxPerDeg;
            if (x < 0 || x > getWidth()) continue;

            boolean isMajor = Math.abs(deg) % 15 < 0.1f;
            float tickH = isMajor ? dp(12) : dp(6);
            tickPaint.setColor(isMajor ? 0xCCFFFFFF : 0x66FFFFFF);
            canvas.drawLine(x, cy - tickH / 2, x, cy + tickH / 2, tickPaint);

            if (isMajor) {
                String label = deg == 0 ? "0" : String.format("%.0f", deg);
                canvas.drawText(label, x, cy + tickH / 2 + dp(12), labelPaint);
            }
        }

        // Center indicator
        canvas.drawLine(cx, cy - dp(14), cx, cy + dp(14), centerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                lastTouchX = event.getX();
                float halfW = getWidth() / 2f;
                float pxPerDeg = halfW / MAX_ANGLE * 0.8f;
                float newRotation = rotation - dx / pxPerDeg;
                newRotation = Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, newRotation));
                if (Math.abs(newRotation) < SNAP_THRESHOLD) newRotation = 0;
                if (newRotation != rotation) {
                    rotation = newRotation;
                    invalidate();
                    if (listener != null) listener.onRotationChanged(rotation);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) listener.onRotationEnded(rotation);
                return true;
        }
        return false;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
