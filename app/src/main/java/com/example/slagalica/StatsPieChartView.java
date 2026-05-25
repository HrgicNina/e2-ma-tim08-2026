package com.example.slagalica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class StatsPieChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] values = new float[]{1f};
    private int[] colors = new int[]{Color.rgb(0, 174, 29)};

    public StatsPieChartView(Context context) {
        super(context);
    }

    public StatsPieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setData(float[] values, int[] colors) {
        if (values != null && values.length > 0) {
            this.values = values;
        }
        if (colors != null && colors.length >= this.values.length) {
            this.colors = colors;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float total = 0f;
        for (float value : values) {
            total += Math.max(0f, value);
        }
        if (total <= 0f) {
            total = 1f;
        }
        float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        RectF rect = new RectF(left, top, left + size, top + size);
        float start = -90f;
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < values.length; i++) {
            float sweep = 360f * Math.max(0f, values[i]) / total;
            paint.setColor(colorAt(i));
            canvas.drawArc(rect, start, sweep, true, paint);
            start += sweep;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(100, 255, 255, 255));
        canvas.drawOval(rect, paint);
    }

    private int colorAt(int index) {
        if (colors == null || index < 0 || index >= colors.length) {
            return Color.rgb(0, 174, 29);
        }
        return colors[index];
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
