package com.example.slagalica;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

public final class AvatarFrameHelper {
    private AvatarFrameHelper() {
    }

    public static void apply(TextView view, String frameId) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#123F89"));
        drawable.setStroke(dp(view, 5), colorFor(frameId));
        view.setBackground(drawable);
    }

    private static int colorFor(String frameId) {
        if ("gold".equals(frameId)) return Color.parseColor("#F6C65B");
        if ("silver".equals(frameId)) return Color.parseColor("#C9D3DF");
        if ("bronze".equals(frameId)) return Color.parseColor("#C47A3A");
        return Color.parseColor("#6EB6FF");
    }

    private static int dp(TextView view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density);
    }
}
