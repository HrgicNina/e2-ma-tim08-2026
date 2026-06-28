package com.example.slagalica;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

final class ForfeitButtonHelper {

    private ForfeitButtonHelper() {
    }

    static void attach(Activity activity, View.OnClickListener listener) {
        ImageButton button = new ImageButton(activity);
        button.setContentDescription(activity.getString(R.string.match_give_up));
        button.setImageResource(R.drawable.ic_exit_match);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
        button.setElevation(dp(activity, 8));
        button.setOnClickListener(listener);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(177, 42, 42));
        background.setCornerRadius(dp(activity, 24));
        button.setBackground(background);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(activity, 48),
                dp(activity, 48),
                Gravity.BOTTOM | Gravity.END
        );
        params.setMargins(0, 0, dp(activity, 14), dp(activity, 14));
        activity.addContentView(button, params);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
