package com.example.slagalica;

import android.content.Context;
import android.content.Intent;

import java.util.Locale;

public final class NotificationIntentRouter {

    private NotificationIntentRouter() {
    }

    public static Intent buildOpenIntent(
            Context context,
            String type,
            String actionType,
            String actionPayload,
            String notificationId
    ) {
        String normalizedAction = value(actionType).trim().toLowerCase(Locale.ROOT);
        Intent intent;

        if ("open_chat".equals(normalizedAction) || "chat".equalsIgnoreCase(value(type))) {
            intent = new Intent(context, ChatActivity.class);
        } else if ("open_ranking_rewards".equals(normalizedAction) || "rewards".equalsIgnoreCase(value(type))) {
            intent = new Intent(context, HomeActivity.class);
            if (!value(notificationId).isEmpty()) {
                intent.putExtra(HomeActivity.EXTRA_OPEN_REWARD_NOTIFICATION_ID, notificationId);
            }
        } else if ("open_rankings".equals(normalizedAction) || "ranking".equalsIgnoreCase(value(type))) {
            intent = new Intent(context, RankingsActivity.class);
        } else if ("respond_match_invite".equals(normalizedAction)) {
            intent = new Intent(context, MatchActivity.class);
            if (!value(actionPayload).isEmpty()) {
                intent.putExtra(MatchActivity.EXTRA_RESPOND_INVITE_ID, actionPayload);
                intent.putExtra(MatchActivity.EXTRA_PROMPT_INVITE_RESPONSE, true);
            }
        } else if ("open_match".equals(normalizedAction)) {
            intent = new Intent(context, MatchActivity.class);
            if (!value(actionPayload).isEmpty()) {
                intent.putExtra(MatchActivity.EXTRA_AUTO_INVITE_TARGET, actionPayload);
            }
        } else {
            intent = new Intent(context, NotificationsActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }
}
