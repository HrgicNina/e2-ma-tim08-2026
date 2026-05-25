package com.example.slagalica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class StatsCircleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int percent = 0;
    private String label = "0%";

    public StatsCircleView(Context context) {
        super(context);
    }

    public StatsCircleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPercent(int percent) {
        this.percent = Math.max(0, Math.min(100, percent));
        this.label = this.percent + "%";
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(1, 35, 68));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(5));
        paint.setColor(Color.rgb(0, 174, 29));
        canvas.drawArc(cx - radius + dp(4), cy - radius + dp(4), cx + radius - dp(4), cy + radius - dp(4), -90f, 360f * percent / 100f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(dp(27));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(label, cx, cy - (metrics.ascent + metrics.descent) / 2f, paint);
        paint.setFakeBoldText(false);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
