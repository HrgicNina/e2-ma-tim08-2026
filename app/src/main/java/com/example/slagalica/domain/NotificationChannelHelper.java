package com.example.slagalica.domain;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public final class NotificationChannelHelper {

    public static final String CHANNEL_CHAT = "chat_notifications";
    public static final String CHANNEL_RANKING = "ranking_notifications";
    public static final String CHANNEL_REWARDS = "reward_notifications";
    public static final String CHANNEL_OTHER = "other_notifications";

    private NotificationChannelHelper() {
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel chat = new NotificationChannel(
                CHANNEL_CHAT,
                "Obavestenja u cetu",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        NotificationChannel ranking = new NotificationChannel(
                CHANNEL_RANKING,
                "Obavestenja o rangiranju",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        NotificationChannel rewards = new NotificationChannel(
                CHANNEL_REWARDS,
                "Obavestenja o nagradama",
                NotificationManager.IMPORTANCE_HIGH
        );

        NotificationChannel other = new NotificationChannel(
                CHANNEL_OTHER,
                "Ostala obavestenja",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        manager.createNotificationChannel(chat);
        manager.createNotificationChannel(ranking);
        manager.createNotificationChannel(rewards);
        manager.createNotificationChannel(other);
    }
}
