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

import com.example.slagalica.domain.FcmTokenRegistrar;
import com.example.slagalica.domain.NotificationChannelHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class SlagalicaFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        FcmTokenRegistrar.saveCurrentToken(this, token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String targetUid = value(data.get("targetUid"));
        if (user == null || (!targetUid.isEmpty() && !targetUid.equals(user.getUid()))) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String id = value(data.get("notificationId"));
        String type = value(data.get("type"));
        String title = value(data.get("title"));
        String message = value(data.get("message"));
        String actionType = value(data.get("actionType"));
        String actionPayload = value(data.get("actionPayload"));
        if (title.isEmpty()) title = "Novo obavestenje";

        NotificationChannelHelper.createChannels(this);
        Intent openIntent = NotificationIntentRouter.buildOpenIntent(
                this, type, actionType, actionPayload, id
        );
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                ("fcm_" + id).hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelFor(type))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this)
                .notify(("fcm_system_" + id).hashCode(), notification.build());

        if (!id.isEmpty()) {
            FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
                    .collection("notifications").document(id)
                    .update("localShown", true);
        }
    }

    private String channelFor(String type) {
        if ("chat".equalsIgnoreCase(type)) return NotificationChannelHelper.CHANNEL_CHAT;
        if ("ranking".equalsIgnoreCase(type)) return NotificationChannelHelper.CHANNEL_RANKING;
        if ("rewards".equalsIgnoreCase(type)) return NotificationChannelHelper.CHANNEL_REWARDS;
        return NotificationChannelHelper.CHANNEL_OTHER;
    }

    private String value(String input) {
        return input == null ? "" : input;
    }
}
