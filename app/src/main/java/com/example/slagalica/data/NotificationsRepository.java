package com.example.slagalica.data;

import com.example.slagalica.model.AppNotification;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsRepository {

    public interface LoadCallback {
        void onSuccess(List<AppNotification> items);

        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();

        void onError(String message);
    }

    private final FirebaseFirestore db;

    public NotificationsRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void loadForUser(String uid, Boolean readFilter, LoadCallback callback) {
        Query query = db.collection("users")
                .document(uid)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING);

        query.get()
                .addOnSuccessListener(snapshot -> {
                    List<AppNotification> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        AppNotification n = new AppNotification();
                        n.id = doc.getId();
                        n.type = value(doc.getString("type"));
                        n.title = value(doc.getString("title"));
                        n.message = value(doc.getString("message"));
                        Boolean read = doc.getBoolean("read");
                        n.read = read != null && read;
                        Timestamp createdAt = doc.getTimestamp("createdAt");
                        n.createdAtMillis = createdAt != null ? createdAt.toDate().getTime() : 0L;
                        n.actionType = value(doc.getString("actionType"));
                        n.actionPayload = value(doc.getString("actionPayload"));
                        if (readFilter == null || n.read == readFilter) {
                            items.add(n);
                        }
                    }
                    callback.onSuccess(items);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam notifikacije."));
    }

    public void markAsRead(String uid, String notificationId, ActionCallback callback) {
        db.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Ne mogu da oznacim notifikaciju."));
    }

    public void createTestNotification(String uid, String type, String title, String message, ActionCallback callback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("title", title);
        payload.put("message", message);
        payload.put("read", false);
        payload.put("createdAt", Timestamp.now());
        payload.put("actionType", "open");
        payload.put("actionPayload", type);

        db.collection("users")
                .document(uid)
                .collection("notifications")
                .add(payload)
                .addOnSuccessListener(doc -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Ne mogu da napravim test notifikaciju."));
    }

    private String value(String input) {
        return input == null ? "" : input;
    }
}
