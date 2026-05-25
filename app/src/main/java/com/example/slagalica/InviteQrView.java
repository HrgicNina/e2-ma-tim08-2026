package com.example.slagalica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class InviteQrView extends View {
    private static final int MODULES = 29;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String payload = "";

    public InviteQrView(Context context) {
        super(context);
    }

    public InviteQrView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPayload(String payload) {
        this.payload = payload == null ? "" : payload;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        float cell = size / MODULES;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(left, top, left + size, top + size, cell * 1.6f, cell * 1.6f, paint);
        paint.setColor(Color.rgb(12, 47, 82));
        drawFinder(canvas, left, top, cell, 1, 1);
        drawFinder(canvas, left, top, cell, MODULES - 8, 1);
        drawFinder(canvas, left, top, cell, 1, MODULES - 8);
        int seed = payload.hashCode();
        for (int y = 0; y < MODULES; y++) {
            for (int x = 0; x < MODULES; x++) {
                if (insideFinder(x, y)) {
                    continue;
                }
                int bit = mix(seed, x, y);
                if ((bit & 3) == 0 || ((x + y + seed) & 11) == 0) {
                    canvas.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell, top + (y + 1) * cell, paint);
                }
            }
        }
    }

    private void drawFinder(Canvas canvas, float left, float top, float cell, int x, int y) {
        drawRect(canvas, left, top, cell, x, y, 7, Color.rgb(12, 47, 82));
        drawRect(canvas, left, top, cell, x + 1, y + 1, 5, Color.WHITE);
        drawRect(canvas, left, top, cell, x + 2, y + 2, 3, Color.rgb(12, 47, 82));
    }

    private void drawRect(Canvas canvas, float left, float top, float cell, int x, int y, int cells, int color) {
        paint.setColor(color);
        canvas.drawRect(left + x * cell, top + y * cell, left + (x + cells) * cell, top + (y + cells) * cell, paint);
    }

    private boolean insideFinder(int x, int y) {
        return (x >= 1 && x <= 7 && y >= 1 && y <= 7)
                || (x >= MODULES - 8 && x <= MODULES - 2 && y >= 1 && y <= 7)
                || (x >= 1 && x <= 7 && y >= MODULES - 8 && y <= MODULES - 2);
    }

    private int mix(int seed, int x, int y) {
        int value = seed ^ (x * 73856093) ^ (y * 19349663);
        value ^= value >>> 13;
        value *= 1274126177;
        return value ^ (value >>> 16);
    }
}
