package com.example.slagalica.data;

import android.text.TextUtils;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRepository {

    public interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onError(String message);
    }

    public interface MessagesCallback {
        void onSuccess(List<ChatMessageEntity> messages);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    public static class UserProfile {
        public final String username;
        public final String region;

        public UserProfile(String username, String region) {
            this.username = username;
            this.region = region;
        }
    }

    public static class ChatMessageEntity {
        public String senderUid;
        public String senderName;
        public String text;
        public long createdAtMillis;
    }

    public static class RegionUserEntity {
        public String uid;
        public boolean chatActive;
        public long chatLastSeenAtMillis;
        public boolean appActive;
        public long appLastSeenAtMillis;
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadUserProfile(String uid, ProfileCallback callback) {
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String username = value(doc.getString("username"));
                    String region = value(doc.getString("region"));
                    callback.onSuccess(new UserProfile(username, region));
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam korisnicke podatke za cet."));
    }

    public ListenerRegistration listenMessages(String roomId, MessagesCallback callback) {
        return db.collection("regionChats")
                .document(roomId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(300)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError("Greska pri osvezavanju poruka.");
                        return;
                    }
                    if (snapshot == null) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }
                    List<ChatMessageEntity> items = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ChatMessageEntity message = new ChatMessageEntity();
                        message.senderUid = value(doc.getString("senderUid"));
                        message.senderName = value(doc.getString("senderName"));
                        message.text = value(doc.getString("text"));
                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts != null) {
                            message.createdAtMillis = ts.toDate().getTime();
                        } else {
                            Long fallback = doc.getLong("createdAtMillis");
                            message.createdAtMillis = fallback == null ? 0L : fallback;
                        }
                        items.add(message);
                    }
                    callback.onSuccess(items);
                });
    }

    public void sendMessage(String roomId, String senderUid, String senderName, String text, ActionCallback callback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderUid", senderUid);
        payload.put("senderName", senderName);
        payload.put("text", text);
        payload.put("createdAt", Timestamp.now());
        payload.put("createdAtMillis", System.currentTimeMillis());

        db.collection("regionChats")
                .document(roomId)
                .collection("messages")
                .add(payload)
                .addOnSuccessListener(doc -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Poruka nije poslata."));
    }

    public void loadUsersByRegion(String region, RegionUsersCallback callback) {
        db.collection("users")
                .whereEqualTo("region", region)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<RegionUserEntity> out = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot userDoc : snapshot.getDocuments()) {
                            RegionUserEntity entity = new RegionUserEntity();
                            entity.uid = userDoc.getId();
                            Boolean active = userDoc.getBoolean("chatActive");
                            entity.chatActive = active != null && active;
                            Timestamp lastSeen = userDoc.getTimestamp("chatLastSeenAt");
                            if (lastSeen != null) {
                                entity.chatLastSeenAtMillis = lastSeen.toDate().getTime();
                            } else {
                                Long lastSeenMs = userDoc.getLong("chatLastSeenAtMillis");
                                entity.chatLastSeenAtMillis = lastSeenMs == null ? 0L : lastSeenMs;
                            }
                            Boolean appActive = userDoc.getBoolean("appActive");
                            entity.appActive = appActive != null && appActive;
                            Timestamp appLastSeen = userDoc.getTimestamp("appLastSeenAt");
                            if (appLastSeen != null) {
                                entity.appLastSeenAtMillis = appLastSeen.toDate().getTime();
                            } else {
                                Long appLastSeenMs = userDoc.getLong("appLastSeenAtMillis");
                                entity.appLastSeenAtMillis = appLastSeenMs == null ? 0L : appLastSeenMs;
                            }
                            out.add(entity);
                        }
                    }
                    callback.onSuccess(out);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam korisnike regiona."));
    }

    public interface RegionUsersCallback {
        void onSuccess(List<RegionUserEntity> users);
        void onError(String message);
    }

    public void createChatNotifications(List<String> targetUids, String senderName, String previewMessage, String roomId) {
        if (targetUids == null || targetUids.isEmpty()) {
            return;
        }
        WriteBatch batch = db.batch();
        int count = 0;
        for (String targetUid : targetUids) {
            if (TextUtils.isEmpty(targetUid)) {
                continue;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "chat");
            payload.put("title", "Nova poruka u cetu");
            payload.put("message", senderName + ": " + previewMessage);
            payload.put("read", false);
            payload.put("localShown", false);
            payload.put("createdAt", Timestamp.now());
            payload.put("actionType", "open_chat");
            payload.put("actionPayload", roomId);
            batch.set(
                    db.collection("users")
                            .document(targetUid)
                            .collection("notifications")
                            .document(),
                    payload
            );
            count++;
        }
        if (count > 0) {
            batch.commit();
        }
    }

    public void setChatPresence(String uid, boolean active) {
        if (TextUtils.isEmpty(uid)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("chatActive", active);
        payload.put("chatLastSeenAt", Timestamp.now());
        payload.put("chatLastSeenAtMillis", System.currentTimeMillis());
        db.collection("users")
                .document(uid)
                .update(payload);
    }

    private String value(String input) {
        return input == null ? "" : input;
    }
}
