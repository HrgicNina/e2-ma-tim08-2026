package com.example.slagalica.data;

import com.example.slagalica.model.AppNotification;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
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
                        Long rewardTokens = doc.getLong("rewardTokens");
                        n.rewardTokens = rewardTokens == null ? 0L : rewardTokens;
                        Boolean rewardClaimed = doc.getBoolean("rewardClaimed");
                        n.rewardClaimed = rewardClaimed != null && rewardClaimed;
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

    public void claimReward(String uid, String notificationId, ActionCallback callback) {
        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference notificationRef = userRef.collection("notifications").document(notificationId);

        db.runTransaction(transaction -> {
                    DocumentSnapshot notification = transaction.get(notificationRef);
                    if (!notification.exists()) {
                        return false;
                    }
                    Boolean rewardClaimed = notification.getBoolean("rewardClaimed");
                    Long rewardTokens = notification.getLong("rewardTokens");
                    long tokens = rewardTokens == null ? 0L : rewardTokens;
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("read", true);
                    if (!Boolean.TRUE.equals(rewardClaimed) && tokens > 0L) {
                        transaction.update(userRef, "tokens", FieldValue.increment(tokens));
                        updates.put("rewardClaimed", true);
                        updates.put("claimedAt", Timestamp.now());
                    }
                    transaction.update(notificationRef, updates);
                    return true;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Ne mogu da preuzmem nagradu."));
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

        String target = targetUsername.trim();
        db.collection("users")
                .document(target)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        createOfflineInviteNotificationForUid(doc.getId(), fromUsername, callback);
                        return;
                    }
                    findInviteTargetByUsername(target, fromUsername, callback);
                })
                .addOnFailureListener(e -> findInviteTargetByUsername(target, fromUsername, callback));
    }

    private void findInviteTargetByUsername(String targetUsername, String fromUsername, ActionCallback callback) {
        db.collection("users")
                .whereEqualTo("usernameLower", targetUsername.trim().toLowerCase())
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onError("Prijatelj ne postoji.");
                        return;
                    }
                    createOfflineInviteNotificationForUid(snapshot.getDocuments().get(0).getId(), fromUsername, callback);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da pronadjem primaoca poziva."));
    }

    private void createOfflineInviteNotificationForUid(String targetUid, String fromUsername, ActionCallback callback) {
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
