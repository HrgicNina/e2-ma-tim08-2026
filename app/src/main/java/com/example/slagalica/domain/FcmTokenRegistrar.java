package com.example.slagalica.domain;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public final class FcmTokenRegistrar {

    private static final String PREFS = "slagalica_fcm";
    private static final String LAST_UID = "last_fcm_uid";

    private FcmTokenRegistrar() {
    }

    public static void sync(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> saveToken(context, user.getUid(), token));
    }

    public static void saveCurrentToken(Context context, String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        saveToken(context, user.getUid(), token);
    }

    public static void unregister(Context context, Runnable onComplete) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            onComplete.run();
            return;
        }
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        clearStoredUid(context);
                        onComplete.run();
                        return;
                    }
                    FirebaseFirestore.getInstance()
                            .collection("users").document(user.getUid())
                            .collection("fcmTokens").document(tokenId(task.getResult()))
                            .delete()
                            .addOnCompleteListener(unused -> {
                                clearStoredUid(context);
                                onComplete.run();
                            });
                });
    }

    private static void saveToken(Context context, String uid, String token) {
        if (token == null || token.trim().isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String previousUid = prefs.getString(LAST_UID, null);
        String id = tokenId(token);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (previousUid != null && !previousUid.equals(uid)) {
            db.collection("users").document(previousUid)
                    .collection("fcmTokens").document(id).delete();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        payload.put("updatedAt", Timestamp.now());
        db.collection("users").document(uid)
                .collection("fcmTokens").document(id)
                .set(payload)
                .addOnSuccessListener(unused -> prefs.edit().putString(LAST_UID, uid).apply());
    }

    private static String tokenId(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception ignored) {
            return String.valueOf(token.hashCode());
        }
    }

    private static void clearStoredUid(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(LAST_UID).apply();
    }
}
