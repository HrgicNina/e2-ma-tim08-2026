package com.example.slagalica.data;

import com.example.slagalica.model.AppNotification;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

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
                        Boolean localShown = doc.getBoolean("localShown");
                        n.localShown = localShown != null && localShown;
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

    public void deleteHardcodedSeedNotifications(String uid, ActionCallback callback) {
        db.collection("users")
                .document(uid)
                .collection("notifications")
                .whereEqualTo("actionType", "open")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onSuccess();
                        return;
                    }
                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError("Ne mogu da obrisem test notifikacije."));
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da pronadjem test notifikacije."));
    }

    public void createOfflineInviteNotificationForUsername(
            String targetUsername,
            String fromUsername,
            ActionCallback callback
    ) {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            callback.onError("Nedostaje korisnicko ime primaoca.");
            return;
        }

        db.collection("users")
                .whereEqualTo("usernameLower", targetUsername.trim().toLowerCase())
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onError("Prijatelj ne postoji.");
                        return;
                    }

                    String targetUid = snapshot.getDocuments().get(0).getId();
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("type", "other");
                    payload.put("title", "Poziv za prijateljsku partiju");
                    payload.put("message", value(fromUsername) + " vas je pozvao/la na prijateljsku partiju.");
                    payload.put("read", false);
                    payload.put("localShown", false);
                    payload.put("createdAt", Timestamp.now());
                    payload.put("actionType", "open_match");
                    payload.put("actionPayload", value(fromUsername));

                    db.collection("users")
                            .document(targetUid)
                            .collection("notifications")
                            .add(payload)
                            .addOnSuccessListener(doc -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError("Ne mogu da posaljem offline notifikaciju."));
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da pronadjem primaoca poziva."));
    }

    public void markAsLocalShown(String uid, String notificationId, ActionCallback callback) {
        db.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notificationId)
                .update("localShown", true)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Ne mogu da oznacim lokalno prikazivanje notifikacije."));
    }

    private String value(String input) {
        return input == null ? "" : input;
    }
}
