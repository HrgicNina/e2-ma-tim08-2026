package com.example.slagalica;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalica.domain.MatchRealtimeClient;
import com.example.slagalica.domain.NotificationChannelHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SlagalicaApp extends Application {

    private static final String WS_URL = "ws://10.0.2.2:8080";
    private static final long APP_PRESENCE_HEARTBEAT_MS = 60L * 1000L;
    private static final long INVITE_TIMEOUT_MS = 10_000L;

    private int startedActivities = 0;
    private boolean appForeground = false;
    private Activity currentActivity = null;
    private MatchRealtimeClient inviteClient = null;
    private String inviteClientUid = null;
    private boolean inviteConnecting = false;
    private AlertDialog globalInviteDialog = null;
    private String globalInviteId = null;
    private Runnable globalInviteTimeoutRunnable = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable presenceHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!appForeground) {
                return;
            }
            updateAppPresence(true);
            mainHandler.postDelayed(this, APP_PRESENCE_HEARTBEAT_MS);
        }
    };

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
                    startPresenceHeartbeat();
                }
            }

            @Override
            public void onActivityResumed(Activity activity) {
                currentActivity = activity;
                if (appForeground) {
                    updateAppPresence(true);
                    if (!isMatchFlowActivity(activity)) {
                        clearMatchPresence();
                    }
                    ensureGlobalInviteClient(activity);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (currentActivity == activity) {
                    currentActivity = null;
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (appForeground && startedActivities == 0) {
                    appForeground = false;
                    stopPresenceHeartbeat();
                    updateAppPresence(false);
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (currentActivity == activity) {
                    currentActivity = null;
                }
            }
        });
    }

    private void startPresenceHeartbeat() {
        mainHandler.removeCallbacks(presenceHeartbeatRunnable);
        presenceHeartbeatRunnable.run();
    }

    private void stopPresenceHeartbeat() {
        mainHandler.removeCallbacks(presenceHeartbeatRunnable);
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
        if (active) {
            payload.put("loggedIn", true);
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .update(payload);
    }

    private void clearMatchPresence() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("inMatch", false);
        payload.put("activeRoomId", "");
        payload.put("matchUpdatedAtMillis", System.currentTimeMillis());
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .update(payload);
    }

    private void ensureGlobalInviteClient(Activity activity) {
        if (isMatchFlowActivity(activity)) {
            disconnectGlobalInviteClient();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            disconnectGlobalInviteClient();
            return;
        }
        String uid = user.getUid();
        if (TextUtils.isEmpty(uid)) {
            disconnectGlobalInviteClient();
            return;
        }
        if (uid.equals(inviteClientUid) && (inviteClient != null || inviteConnecting)) {
            return;
        }
        disconnectGlobalInviteClient();
        inviteConnecting = true;
        inviteClientUid = uid;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser == null || !uid.equals(currentUser.getUid()) || !appForeground) {
                        inviteConnecting = false;
                        return;
                    }
                    Activity active = currentActivity;
                    if (isMatchFlowActivity(active)) {
                        inviteConnecting = false;
                        return;
                    }
                    String username = document == null ? "" : value(document.getString("username"));
                    connectGlobalInviteClient(uid, username);
                })
                .addOnFailureListener(e -> {
                    inviteConnecting = false;
                    inviteClientUid = null;
                });
    }

    private void connectGlobalInviteClient(String uid, String username) {
        inviteConnecting = false;
        MatchRealtimeClient client = new MatchRealtimeClient();
        inviteClient = client;
        client.connect(WS_URL, uid, username, new MatchRealtimeClient.Listener() {
            @Override
            public void onConnected() {
            }

            @Override
            public void onAuthenticated() {
            }

            @Override
            public void onDisconnected(String reason) {
                mainHandler.post(() -> clearGlobalInviteClientReference(client));
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> clearGlobalInviteClientReference(client));
            }

            @Override
            public void onMatchFound(String roomId, boolean friendly, int playerNumber, String opponentUid, String opponentUsername) {
            }

            @Override
            public void onInviteReceived(String inviteId, String fromUid, String fromUsername) {
                mainHandler.post(() -> showGlobalInvite(inviteId, fromUsername));
            }

            @Override
            public void onInviteSent(String inviteId, int expiresInSeconds) {
            }

            @Override
            public void onInviteDeclined(String byUsername) {
            }

            @Override
            public void onInviteExpired(String inviteId) {
                mainHandler.post(() -> {
                    if (!TextUtils.isEmpty(globalInviteId) && globalInviteId.equals(inviteId)) {
                        cancelBackgroundInviteNotification(inviteId);
                        dismissGlobalInviteDialog();
                        Activity active = currentActivity;
                        if (active != null && !(active instanceof MatchActivity)) {
                            Toast.makeText(active, "Poziv je istekao.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onInviteCancelled(String inviteId, String byUsername) {
                mainHandler.post(() -> {
                    if (!TextUtils.isEmpty(globalInviteId) && globalInviteId.equals(inviteId)) {
                        cancelBackgroundInviteNotification(inviteId);
                        dismissGlobalInviteDialog();
                        Activity active = currentActivity;
                        if (active != null && !(active instanceof MatchActivity)) {
                            Toast.makeText(active, "Poziv je otkazan: " + byUsername, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onInfo(String message) {
            }

            @Override
            public void onQueueJoined() {
            }

            @Override
            public void onQueueCancelled() {
            }

            @Override
            public void onMatchFinished(String winnerUid, int yourScore, int opponentScore, boolean forfeit, boolean draw) {
            }

            @Override
            public void onGameEvent(String roomId, String game, String event, String fromUid, JSONObject data) {
            }
        });
    }

    private void clearGlobalInviteClientReference(MatchRealtimeClient client) {
        if (inviteClient == client) {
            inviteClient = null;
            inviteClientUid = null;
            inviteConnecting = false;
            dismissGlobalInviteDialog();
        }
    }

    private void showGlobalInvite(String inviteId, String fromUsername) {
        Activity activity = currentActivity;
        if (activity == null || activity.isFinishing() || isMatchFlowActivity(activity)) {
            dismissGlobalInviteDialog();
            globalInviteId = inviteId;
            showBackgroundInviteNotification(inviteId, fromUsername);
            scheduleGlobalInviteAutoReject(inviteId);
            return;
        }
        dismissGlobalInviteDialog();
        globalInviteId = inviteId;
        String sender = TextUtils.isEmpty(fromUsername) ? "Igrac" : fromUsername;
        globalInviteDialog = new AlertDialog.Builder(activity)
                .setTitle("Poziv za partiju")
                .setMessage(sender + " vas poziva na prijateljsku partiju.")
                .setCancelable(false)
                .setPositiveButton("Prihvati", (dialog, which) -> openMatchForInvite(activity, inviteId))
                .setNegativeButton("Odbij", (dialog, which) -> {
                    if (inviteClient != null) {
                        inviteClient.respondInvite(inviteId, false);
                    }
                })
                .create();
        globalInviteDialog.setOnDismissListener(dialog -> {
            clearGlobalInviteTimeout();
            if (inviteId.equals(globalInviteId)) {
                globalInviteId = null;
            }
            globalInviteDialog = null;
        });
        globalInviteDialog.show();
        scheduleGlobalInviteAutoReject(inviteId);
    }

    private void openMatchForInvite(Activity activity, String inviteId) {
        clearGlobalInviteTimeout();
        cancelBackgroundInviteNotification(inviteId);
        Intent intent = new Intent(activity, MatchActivity.class);
        intent.putExtra(MatchActivity.EXTRA_RESPOND_INVITE_ID, inviteId);
        activity.startActivity(intent);
    }

    private void scheduleGlobalInviteAutoReject(String inviteId) {
        clearGlobalInviteTimeout();
        globalInviteTimeoutRunnable = () -> {
            if (inviteId.equals(globalInviteId)) {
                if (inviteClient != null) {
                    inviteClient.respondInvite(inviteId, false);
                }
                cancelBackgroundInviteNotification(inviteId);
                dismissGlobalInviteDialog();
            }
        };
        mainHandler.postDelayed(globalInviteTimeoutRunnable, INVITE_TIMEOUT_MS);
    }

    public void declineBackgroundInvite(String inviteId) {
        mainHandler.post(() -> {
            if (TextUtils.isEmpty(inviteId) || !inviteId.equals(globalInviteId)) {
                return;
            }
            if (inviteClient != null) {
                inviteClient.respondInvite(inviteId, false);
            }
            cancelBackgroundInviteNotification(inviteId);
            dismissGlobalInviteDialog();
        });
    }

    public MatchRealtimeClient takeInviteClientForMatch(String inviteId) {
        if (TextUtils.isEmpty(inviteId)
                || !inviteId.equals(globalInviteId)
                || inviteClient == null) {
            return null;
        }
        MatchRealtimeClient client = inviteClient;
        inviteClient = null;
        inviteClientUid = null;
        inviteConnecting = false;
        clearGlobalInviteTimeout();
        cancelBackgroundInviteNotification(inviteId);
        dismissGlobalInviteDialog();
        return client;
    }

    private void showBackgroundInviteNotification(String inviteId, String fromUsername) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String sender = TextUtils.isEmpty(fromUsername) ? "Igrac" : fromUsername;
        Intent acceptIntent = new Intent(this, MatchActivity.class);
        acceptIntent.putExtra(MatchActivity.EXTRA_RESPOND_INVITE_ID, inviteId);
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent acceptPendingIntent = PendingIntent.getActivity(
                this,
                ("invite_accept_" + inviteId).hashCode(),
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent declineIntent = new Intent(this, InviteActionReceiver.class);
        declineIntent.setAction(InviteActionReceiver.ACTION_DECLINE_INVITE);
        declineIntent.putExtra(InviteActionReceiver.EXTRA_INVITE_ID, inviteId);
        PendingIntent declinePendingIntent = PendingIntent.getBroadcast(
                this,
                ("invite_decline_" + inviteId).hashCode(),
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                this,
                NotificationChannelHelper.CHANNEL_OTHER
        )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Poziv za partiju")
                .setContentText(sender + " vas poziva na prijateljsku partiju.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setTimeoutAfter(INVITE_TIMEOUT_MS)
                .setContentIntent(acceptPendingIntent)
                .addAction(0, "Odbij", declinePendingIntent)
                .addAction(0, "Prihvati", acceptPendingIntent);

        NotificationManagerCompat.from(this)
                .notify(backgroundInviteNotificationId(inviteId), notification.build());
    }

    private void cancelBackgroundInviteNotification(String inviteId) {
        if (!TextUtils.isEmpty(inviteId)) {
            NotificationManagerCompat.from(this).cancel(backgroundInviteNotificationId(inviteId));
        }
    }

    private int backgroundInviteNotificationId(String inviteId) {
        return ("realtime_invite_" + inviteId).hashCode();
    }

    private void clearGlobalInviteTimeout() {
        if (globalInviteTimeoutRunnable != null) {
            mainHandler.removeCallbacks(globalInviteTimeoutRunnable);
            globalInviteTimeoutRunnable = null;
        }
    }

    private void dismissGlobalInviteDialog() {
        clearGlobalInviteTimeout();
        if (globalInviteDialog != null && globalInviteDialog.isShowing()) {
            globalInviteDialog.dismiss();
        }
        globalInviteDialog = null;
        globalInviteId = null;
    }

    private void disconnectGlobalInviteClient() {
        dismissGlobalInviteDialog();
        if (inviteClient != null) {
            inviteClient.disconnect();
            inviteClient = null;
        }
        inviteClientUid = null;
        inviteConnecting = false;
    }

    private boolean isMatchFlowActivity(Activity activity) {
        return activity instanceof MatchActivity
                || activity instanceof QuizGameActivity
                || activity instanceof ConnectionsGameActivity
                || activity instanceof AssociationsGameActivity
                || activity instanceof MastermindGameActivity
                || activity instanceof StepByStepActivity
                || activity instanceof MyNumberGameActivity
                || activity instanceof MatchResultSplashActivity;
    }

    private String value(String input) {
        return input == null ? "" : input;
    }
}

