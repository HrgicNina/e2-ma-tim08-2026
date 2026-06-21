package com.example.slagalica;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalica.domain.NotificationChannelHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class SlagalicaMessagingService extends FirebaseMessagingService {

    public static void syncCurrentToken() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> saveToken(user.getUid(), token));
    }

    @Override
    public void onNewToken(@NonNull String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            saveToken(user.getUid(), token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String notificationId = value(data.get("notificationId"));
        if (notificationId.isEmpty()) {
            notificationId = value(remoteMessage.getMessageId());
        }
        if (notificationId.isEmpty()) {
            notificationId = String.valueOf(System.currentTimeMillis());
        }

        String type = value(data.get("type"));
        String title = value(data.get("title"));
        String message = value(data.get("message"));
        String actionType = value(data.get("actionType"));
        String actionPayload = value(data.get("actionPayload"));
        if (title.isEmpty()) {
            title = "Novo obavestenje";
        }

        NotificationChannelHelper.createChannels(this);
        Intent openIntent = NotificationIntentRouter.buildOpenIntent(
                this,
                type,
                actionType,
                actionPayload,
                notificationId
        );
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                ("push_open_" + notificationId).hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, resolveChannel(type))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this)
                .notify(("push_" + notificationId).hashCode(), builder.build());
    }

    private static void saveToken(String uid, String token) {
        if (uid == null || uid.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token.trim());
    }

    private String resolveChannel(String type) {
        if ("chat".equalsIgnoreCase(type)) {
            return NotificationChannelHelper.CHANNEL_CHAT;
        }
        if ("ranking".equalsIgnoreCase(type)) {
            return NotificationChannelHelper.CHANNEL_RANKING;
        }
        if ("rewards".equalsIgnoreCase(type)) {
            return NotificationChannelHelper.CHANNEL_REWARDS;
        }
        return NotificationChannelHelper.CHANNEL_OTHER;
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }
}
