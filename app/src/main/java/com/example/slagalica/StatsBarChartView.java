package com.example.slagalica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class StatsBarChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] values = new float[]{0f, 0f, 0f, 0f, 0f, 0f};
    private String[] labels = new String[]{"K", "S", "A", "Sk", "Kor", "M"};
    private int selectedIndex = 0;

    public StatsBarChartView(Context context) {
        super(context);
    }

    public StatsBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setData(float[] values, String[] labels, int selectedIndex) {
        if (values != null && values.length > 0) {
            this.values = values;
        }
        if (labels != null && labels.length == this.values.length) {
            this.labels = labels;
        }
        this.selectedIndex = Math.max(0, Math.min(selectedIndex, this.values.length - 1));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = values.length;
        if (count == 0) {
            return;
        }
        float top = getPaddingTop() + dp(8);
        float bottom = getHeight() - getPaddingBottom() - dp(22);
        float height = Math.max(1f, bottom - top);
        float slot = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) count;
        float barWidth = Math.min(dp(30), slot * 0.56f);

        for (int i = 0; i < count; i++) {
            float left = getPaddingLeft() + slot * i + (slot - barWidth) / 2f;
            float right = left + barWidth;
            float value = Math.max(0f, Math.min(100f, values[i]));
            float fillTop = bottom - height * value / 100f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(i == selectedIndex ? dp(2.4f) : dp(1.6f));
            paint.setColor(i == selectedIndex ? Color.WHITE : Color.argb(210, 255, 255, 255));
            canvas.drawRoundRect(new RectF(left, top, right, bottom), dp(5), dp(5), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(0, 174, 29));
            canvas.drawRoundRect(new RectF(left + dp(1.5f), fillTop, right - dp(1.5f), bottom - dp(1.5f)), dp(4), dp(4), paint);

            paint.setColor(i == selectedIndex ? Color.WHITE : Color.rgb(215, 236, 252));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(9));
            paint.setFakeBoldText(i == selectedIndex);
            canvas.drawText(labelAt(i), left + barWidth / 2f, getHeight() - getPaddingBottom() - dp(5), paint);
            paint.setFakeBoldText(false);
        }
    }

    private String labelAt(int index) {
        if (labels == null || index < 0 || index >= labels.length || labels[index] == null) {
            return "";
        }
        return labels[index];
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
