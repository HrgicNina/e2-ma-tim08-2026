package com.example.slagalica;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SlagalicaApp extends Application {

    private int startedActivities = 0;
    private boolean appForeground = false;

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
                startedActivities++;
                if (!appForeground && startedActivities > 0) {
                    appForeground = true;
                    updateAppPresence(true);
                }
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (appForeground) {
                    // Keep app presence fresh, especially right after login.
                    updateAppPresence(true);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (appForeground && startedActivities == 0) {
                    appForeground = false;
                    updateAppPresence(false);
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private void updateAppPresence(boolean active) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("appActive", active);
        payload.put("appLastSeenAt", Timestamp.now());
        payload.put("appLastSeenAtMillis", System.currentTimeMillis());

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .update(payload);
    }
}

