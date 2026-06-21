package com.example.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

public final class AvatarFrameHelper {
    public static final String DEFAULT_AVATAR_ID = "owl";
    public static final String[] ANIMAL_AVATAR_IDS = {"owl", "fox", "wolf", "rabbit"};
    public static final String[] ANIMAL_AVATAR_LABELS = {"Sovica", "Lisica", "Vuk", "Zec"};

    private AvatarFrameHelper() {
    }

    public static void apply(TextView view, String frameId) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#123F89"));
        drawable.setStroke(dp(view, 5), colorFor(frameId));
        view.setBackground(drawable);
    }

    public static void applyMatchFrames(TextView leftAvatar, TextView rightAvatar, Intent intent) {
        String leftFrame = intent == null
                ? "blue"
                : intent.getStringExtra(MatchActivity.EXTRA_MATCH_PLAYER1_FRAME);
        String rightFrame = intent == null
                ? "blue"
                : intent.getStringExtra(MatchActivity.EXTRA_MATCH_PLAYER2_FRAME);
        apply(leftAvatar, leftFrame);
        apply(rightAvatar, rightFrame);
    }

    public static void applyMatchAvatars(
            TextView leftAvatar,
            TextView rightAvatar,
            Intent intent,
            String leftFallbackName,
            String rightFallbackName
    ) {
        String leftAvatarId = intent == null
                ? DEFAULT_AVATAR_ID
                : intent.getStringExtra(MatchActivity.EXTRA_MATCH_PLAYER1_AVATAR);
        String rightAvatarId = intent == null
                ? DEFAULT_AVATAR_ID
                : intent.getStringExtra(MatchActivity.EXTRA_MATCH_PLAYER2_AVATAR);
        leftAvatar.setText(symbolForAvatar(leftAvatarId, leftFallbackName));
        rightAvatar.setText(symbolForAvatar(rightAvatarId, rightFallbackName));
        applyMatchFrames(leftAvatar, rightAvatar, intent);
    }

    public static String symbolForAvatar(String avatarId, String fallbackName) {
        String normalized = normalizeAvatarId(avatarId);
        if ("fox".equals(normalized)) return "\uD83E\uDD8A";
        if ("wolf".equals(normalized)) return "\uD83D\uDC3A";
        if ("rabbit".equals(normalized)) return "\uD83D\uDC30";
        return "\uD83E\uDD89";
    }

    public static String normalizeAvatarId(String avatarId) {
        if (avatarId != null) {
            for (String id : ANIMAL_AVATAR_IDS) {
                if (id.equals(avatarId)) {
                    return id;
                }
            }
        }
        return DEFAULT_AVATAR_ID;
    }

    public static String labelForAvatar(String avatarId) {
        String normalized = normalizeAvatarId(avatarId);
        for (int i = 0; i < ANIMAL_AVATAR_IDS.length; i++) {
            if (ANIMAL_AVATAR_IDS[i].equals(normalized)) {
                return symbolForAvatar(normalized, "") + "  " + ANIMAL_AVATAR_LABELS[i];
            }
        }
        return symbolForAvatar(DEFAULT_AVATAR_ID, "") + "  Sovica";
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
