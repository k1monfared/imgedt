package com.photoeditor.editor.paint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas-based painting view with brush stamp rendering.
 * Uses Bitmap/Canvas for simplicity and reliability, with stamp-based rendering
 * that follows Telegram's approach (stamps along path at intervals).
 */
public class PaintView extends View {

    public interface Listener {
        void onStrokeCompleted();
    }

    private Bitmap canvasBitmap;
    private Bitmap paintBitmap; // Temporary for current stroke
    private Canvas paintCanvas;
    private Canvas canvasCanvas;

    private final Paint brushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint displayPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint();

    private int brushColor = Color.RED;
    private float brushSize = 20f;
    private float brushAlpha = 0.85f;
    private boolean isEraser = false;

    private float lastX, lastY;
    private boolean isDrawing = false;

    private final List<Bitmap> undoStack = new ArrayList<>();
    private static final int MAX_UNDO = 20;

    private Listener listener;

    public PaintView(Context context) {
        super(context);

        brushPaint.setStyle(Paint.Style.FILL);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        eraserPaint.setStyle(Paint.Style.FILL);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void init(int width, int height) {
        canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvasCanvas = new Canvas(canvasBitmap);
        paintBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        paintCanvas = new Canvas(paintBitmap);
    }

    public void setBrushColor(int color) {
        this.brushColor = color;
    }

    public void setBrushSize(float size) {
        this.brushSize = size;
    }

    public void setBrushAlpha(float alpha) {
        this.brushAlpha = alpha;
    }

    public void setEraser(boolean eraser) {
        this.isEraser = eraser;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        Bitmap prev = undoStack.remove(undoStack.size() - 1);
        canvasBitmap.recycle();
        canvasBitmap = prev;
        canvasCanvas = new Canvas(canvasBitmap);
        invalidate();
    }

    /**
     * Get the painting result as a bitmap with transparency.
     */
    public Bitmap getResult() {
        return Bitmap.createBitmap(canvasBitmap);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (canvasBitmap == null && w > 0 && h > 0) {
            init(w, h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvasBitmap != null) {
            canvas.drawBitmap(canvasBitmap, 0, 0, displayPaint);
        }
        if (isDrawing && paintBitmap != null) {
            canvas.drawBitmap(paintBitmap, 0, 0, displayPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (canvasBitmap == null) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                saveUndoState();
                isDrawing = true;
                lastX = x;
                lastY = y;
                // Clear paint layer
                paintCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                // Draw initial stamp
                drawStamp(paintCanvas, x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isDrawing) return false;
                drawStrokeBetween(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!isDrawing) return false;
                isDrawing = false;
                // Commit stroke to canvas
                commitStroke();
                invalidate();
                if (listener != null) listener.onStrokeCompleted();
                return true;
        }
        return false;
    }

    private void drawStrokeBetween(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        float spacing = 0.15f;
        float step = Math.max(1.0f, spacing * brushSize);
        float steps = dist / step;

        for (float i = 0; i < steps; i++) {
            float t = i / steps;
            float px = x0 + dx * t;
            float py = y0 + dy * t;
            drawStamp(paintCanvas, px, py);
        }
    }

    private void drawStamp(Canvas canvas, float x, float y) {
        float radius = brushSize / 2;
        if (isEraser) {
            eraserPaint.setAlpha(255);
            canvas.drawCircle(x, y, radius, eraserPaint);
        } else {
            brushPaint.setColor(brushColor);
            brushPaint.setAlpha((int) (brushAlpha * 40)); // Low per-stamp alpha for smooth accumulation
            canvas.drawCircle(x, y, radius, brushPaint);
        }
    }

    private void commitStroke() {
        if (isEraser) {
            // For eraser, apply paint layer to canvas
            Paint commitPaint = new Paint();
            commitPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            canvasCanvas.drawBitmap(paintBitmap, 0, 0, commitPaint);
        } else {
            // For brush, composite paint layer onto canvas
            canvasCanvas.drawBitmap(paintBitmap, 0, 0, displayPaint);
        }
        // Clear paint layer
        paintCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
    }

    private void saveUndoState() {
        if (undoStack.size() >= MAX_UNDO) {
            Bitmap oldest = undoStack.remove(0);
            oldest.recycle();
        }
        undoStack.add(Bitmap.createBitmap(canvasBitmap));
    }

    public void recycle() {
        if (canvasBitmap != null) canvasBitmap.recycle();
        if (paintBitmap != null) paintBitmap.recycle();
        for (Bitmap b : undoStack) b.recycle();
        undoStack.clear();
    }
}
