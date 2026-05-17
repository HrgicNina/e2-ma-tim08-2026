package com.example.slagalica.domain;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.slagalica.BackgroundNotificationWorker;

import java.util.concurrent.TimeUnit;

public final class BackgroundNotificationScheduler {

    private static final String UNIQUE_WORK_NAME = "background_notifications_sync";

    private BackgroundNotificationScheduler() {
    }

    public static void ensureScheduled(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackgroundNotificationWorker.class,
                15,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                );
    }
}

