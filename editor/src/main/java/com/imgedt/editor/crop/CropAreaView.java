package com.imgedt.editor.crop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * Overlay view that draws the crop rectangle with dimmed surrounds,
 * grid lines, and corner handles. Handles touch events for resizing the crop area.
 */
public class CropAreaView extends View {

    public interface Listener {
        void onAreaChanged(RectF cropRect, boolean finished);
    }

    private enum Control {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, LEFT, BOTTOM, RIGHT
    }

    private static final int SIDE_PADDING_DP = 16;
    private static final int MIN_SIZE_DP = 64;
    private static final int TOUCH_PADDING_DP = 20;
    private static final int HANDLE_LENGTH_DP = 20;
    private static final int HANDLE_THICKNESS_DP = 3;
    private static final int FRAME_THICKNESS_DP = 1;

    private final RectF cropRect = new RectF();
    private final Paint dimPaint = new Paint();
    private final Paint framePaint = new Paint();
    private final Paint handlePaint = new Paint();
    private final Paint gridPaint = new Paint();

    private float sidePadding;
    private float minSize;
    private float touchPadding;
    private float handleLength;
    private float handleThickness;
    private float frameThickness;

    private Control activeControl = Control.NONE;
    private float touchStartX, touchStartY;
    private final RectF startRect = new RectF();

    private float lockedAspectRatio = 0; // 0 = freeform
    private boolean showGrid = false;

    private Listener listener;

    public CropAreaView(Context context) {
        super(context);
        init();
    }

    private void init() {
        sidePadding = dp(SIDE_PADDING_DP);
        minSize = dp(MIN_SIZE_DP);
        touchPadding = dp(TOUCH_PADDING_DP);
        handleLength = dp(HANDLE_LENGTH_DP);
        handleThickness = dp(HANDLE_THICKNESS_DP);
        frameThickness = dp(FRAME_THICKNESS_DP);

        dimPaint.setColor(0xAA000000);
        framePaint.setColor(Color.WHITE);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(frameThickness);
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(0x55FFFFFF);
        gridPaint.setStrokeWidth(dp(0.5f));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setLockedAspectRatio(float ratio) {
        this.lockedAspectRatio = ratio;
    }

    public RectF getCropRect() {
        return new RectF(cropRect);
    }

    public void setCropRect(RectF rect) {
        cropRect.set(rect);
        invalidate();
    }

    /**
     * Calculate the initial crop rectangle for a given aspect ratio (0 = freeform).
     */
    public void calculateInitialRect(float aspectRatio) {
        float availW = getWidth() - sidePadding * 2;
        float availH = getHeight() - sidePadding * 2;

        if (availW <= 0 || availH <= 0) return;

        float w, h;
        if (aspectRatio <= 0) {
            // Freeform: fill available area
            w = availW;
            h = availH;
        } else if (aspectRatio >= availW / availH) {
            // Wide: fill width
            w = availW;
            h = w / aspectRatio;
        } else {
            // Tall: fill height
            h = availH;
            w = h * aspectRatio;
        }

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        cropRect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cropRect.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();

        // Dimmed surrounds
        canvas.drawRect(0, 0, w, cropRect.top, dimPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, dimPaint);
        canvas.drawRect(0, cropRect.bottom, w, h, dimPaint);

        // Frame
        canvas.drawRect(cropRect, framePaint);

        // Grid lines (3x3)
        if (showGrid) {
            float thirdW = cropRect.width() / 3;
            float thirdH = cropRect.height() / 3;
            canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, gridPaint);
            canvas.drawLine(cropRect.left + 2 * thirdW, cropRect.top, cropRect.left + 2 * thirdW, cropRect.bottom, gridPaint);
            canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, gridPaint);
            canvas.drawLine(cropRect.left, cropRect.top + 2 * thirdH, cropRect.right, cropRect.top + 2 * thirdH, gridPaint);
        }

        // Corner handles
        drawHandle(canvas, cropRect.left, cropRect.top, true, true);
        drawHandle(canvas, cropRect.right, cropRect.top, false, true);
        drawHandle(canvas, cropRect.left, cropRect.bottom, true, false);
        drawHandle(canvas, cropRect.right, cropRect.bottom, false, false);
    }

    private void drawHandle(Canvas canvas, float cx, float cy, boolean left, boolean top) {
        float hl = handleLength;
        float ht = handleThickness;

        // Horizontal bar
        float hx = left ? cx : cx - hl;
        float hy = top ? cy - ht / 2 : cy - ht / 2;
        canvas.drawRect(hx, hy, hx + hl, hy + ht, handlePaint);

        // Vertical bar
        float vx = left ? cx - ht / 2 : cx - ht / 2;
        float vy = top ? cy : cy - hl;
        canvas.drawRect(vx, vy, vx + ht, vy + hl, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeControl = hitTest(event.getX(), event.getY());
                if (activeControl == Control.NONE) return false;
                touchStartX = event.getX();
                touchStartY = event.getY();
                startRect.set(cropRect);
                showGrid = true;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activeControl == Control.NONE) return false;
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                applyResize(dx, dy);
                invalidate();
                if (listener != null) listener.onAreaChanged(cropRect, false);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeControl != Control.NONE) {
                    activeControl = Control.NONE;
                    showGrid = false;
                    invalidate();
                    if (listener != null) listener.onAreaChanged(cropRect, true);
                }
                return true;
        }
        return false;
    }

    private Control hitTest(float x, float y) {
        float tp = touchPadding;
        // Corners (priority)
        if (dist(x, y, cropRect.left, cropRect.top) < tp) return Control.TOP_LEFT;
        if (dist(x, y, cropRect.right, cropRect.top) < tp) return Control.TOP_RIGHT;
        if (dist(x, y, cropRect.left, cropRect.bottom) < tp) return Control.BOTTOM_LEFT;
        if (dist(x, y, cropRect.right, cropRect.bottom) < tp) return Control.BOTTOM_RIGHT;
        // Edges
        if (Math.abs(y - cropRect.top) < tp && x > cropRect.left && x < cropRect.right) return Control.TOP;
        if (Math.abs(y - cropRect.bottom) < tp && x > cropRect.left && x < cropRect.right) return Control.BOTTOM;
        if (Math.abs(x - cropRect.left) < tp && y > cropRect.top && y < cropRect.bottom) return Control.LEFT;
        if (Math.abs(x - cropRect.right) < tp && y > cropRect.top && y < cropRect.bottom) return Control.RIGHT;
        return Control.NONE;
    }

    private void applyResize(float dx, float dy) {
        float l = startRect.left;
        float t = startRect.top;
        float r = startRect.right;
        float b = startRect.bottom;

        switch (activeControl) {
            case TOP_LEFT:     l += dx; t += dy; break;
            case TOP_RIGHT:    r += dx; t += dy; break;
            case BOTTOM_LEFT:  l += dx; b += dy; break;
            case BOTTOM_RIGHT: r += dx; b += dy; break;
            case TOP:          t += dy; break;
            case BOTTOM:       b += dy; break;
            case LEFT:         l += dx; break;
            case RIGHT:        r += dx; break;
        }

        // Enforce minimum size
        if (r - l < minSize) {
            if (activeControl == Control.LEFT || activeControl == Control.TOP_LEFT || activeControl == Control.BOTTOM_LEFT) {
                l = r - minSize;
            } else {
                r = l + minSize;
            }
        }
        if (b - t < minSize) {
            if (activeControl == Control.TOP || activeControl == Control.TOP_LEFT || activeControl == Control.TOP_RIGHT) {
                t = b - minSize;
            } else {
                b = t + minSize;
            }
        }

        // Clamp to bounds
        l = Math.max(sidePadding, l);
        t = Math.max(sidePadding, t);
        r = Math.min(getWidth() - sidePadding, r);
        b = Math.min(getHeight() - sidePadding, b);

        // Apply aspect ratio constraint
        if (lockedAspectRatio > 0) {
            float w = r - l;
            float h = b - t;
            float targetH = w / lockedAspectRatio;
            if (Math.abs(dx) > Math.abs(dy)) {
                // Width-dominant drag
                h = targetH;
                switch (activeControl) {
                    case TOP_LEFT: case TOP_RIGHT: case TOP: t = b - h; break;
                    default: b = t + h; break;
                }
            } else {
                // Height-dominant drag
                w = h * lockedAspectRatio;
                switch (activeControl) {
                    case TOP_LEFT: case BOTTOM_LEFT: case LEFT: l = r - w; break;
                    default: r = l + w; break;
                }
            }
        }

        cropRect.set(l, t, r, b);
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
