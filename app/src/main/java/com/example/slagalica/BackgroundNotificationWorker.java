package com.example.slagalica;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.slagalica.domain.NotificationChannelHelper;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BackgroundNotificationWorker extends Worker {

    public BackgroundNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Result.success();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                getApplicationContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }

        try {
            NotificationChannelHelper.createChannels(getApplicationContext());
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            QuerySnapshot snapshot = Tasks.await(
                    db.collection("users")
                            .document(user.getUid())
                            .collection("notifications")
                            .whereEqualTo("read", false)
                            .whereEqualTo("localShown", false)
                            .limit(40)
                            .get(),
                    20,
                    TimeUnit.SECONDS
            );

            if (snapshot == null || snapshot.isEmpty()) {
                return Result.success();
            }

            List<DocumentSnapshot> shown = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                String id = doc.getId();
                String type = value(doc.getString("type"));
                String title = value(doc.getString("title"));
                String message = value(doc.getString("message"));
                String actionType = value(doc.getString("actionType"));
                String actionPayload = value(doc.getString("actionPayload"));

                if (title.isEmpty()) {
                    title = "Novo obavestenje";
                }

                Intent openIntent = NotificationIntentRouter.buildOpenIntent(
                        getApplicationContext(),
                        type,
                        actionType,
                        actionPayload,
                        id
                );

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        getApplicationContext(),
                        ("bg_notif_open_" + (id.isEmpty() ? UUID.randomUUID().toString() : id)).hashCode(),
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                String channelId = resolveChannel(type);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

                NotificationManagerCompat.from(getApplicationContext())
                        .notify(("bg_local_" + id).hashCode(), builder.build());
                shown.add(doc);
            }

            if (!shown.isEmpty()) {
                WriteBatch batch = db.batch();
                for (DocumentSnapshot doc : shown) {
                    batch.update(doc.getReference(), "localShown", true);
                }
                Tasks.await(batch.commit(), 20, TimeUnit.SECONDS);
            }

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
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

    private String value(String input) {
        return input == null ? "" : input;
    }
}

