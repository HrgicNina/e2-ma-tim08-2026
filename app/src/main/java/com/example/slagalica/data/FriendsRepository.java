package com.example.slagalica.data;

import com.example.slagalica.model.FriendProfile;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FriendsRepository {

    public interface LoadCallback {
        void onSuccess(List<FriendProfile> friends);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface UserCallback {
        void onSuccess(FriendProfile user);
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ListenerRegistration listenFriends(String uid, LoadCallback callback) {
        return db.collection("users")
                .document(uid)
                .collection("friends")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError("Ne mogu da ucitam prijatelje.");
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }
                    List<String> ids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        ids.add(doc.getId());
                    }
                    loadFriendProfiles(ids, callback);
                });
    }

    public void addFriendByUsername(String myUid, String username, ActionCallback callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onError("Unesite korisnicko ime.");
            return;
        }
        db.collection("users")
                .whereEqualTo("usernameLower", username.trim().toLowerCase(Locale.ROOT))
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onError("Korisnik nije pronadjen.");
                        return;
                    }
                    addFriendBySnapshot(myUid, snapshot.getDocuments().get(0), callback);
                })
                .addOnFailureListener(e -> callback.onError("Pretraga nije uspela."));
    }

    public void addFriendByUid(String myUid, String friendUid, ActionCallback callback) {
        if (friendUid == null || friendUid.trim().isEmpty()) {
            callback.onError("QR kod nije ispravan.");
            return;
        }
        db.collection("users")
                .document(friendUid.trim())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        callback.onError("Korisnik nije pronadjen.");
                        return;
                    }
                    addFriendBySnapshot(myUid, snapshot, callback);
                })
                .addOnFailureListener(e -> callback.onError("Dodavanje nije uspelo."));
    }

    public void loadUserByUid(String uid, UserCallback callback) {
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        callback.onError("Korisnik nije pronadjen.");
                        return;
                    }
                    callback.onSuccess(mapUser(snapshot));
                })
                .addOnFailureListener(e -> callback.onError("Korisnik nije ucitan."));
    }

    private void addFriendBySnapshot(String myUid, DocumentSnapshot friendDoc, ActionCallback callback) {
        String friendUid = friendDoc.getId();
        if (friendUid.equals(myUid)) {
            callback.onError("Ne mozete dodati sebe.");
            return;
        }
        db.collection("users")
                .document(myUid)
                .get()
                .addOnSuccessListener(myDoc -> {
                    if (myDoc == null || !myDoc.exists()) {
                        callback.onError("Vas profil nije pronadjen.");
                        return;
                    }
                    db.collection("users")
                            .document(myUid)
                            .collection("friends")
                            .document(friendUid)
                            .get()
                            .addOnSuccessListener(existing -> {
                                if (existing != null && existing.exists()) {
                                    callback.onError("Korisnik je vec u prijateljima.");
                                    return;
                                }
                                writeFriendship(myUid, myDoc, friendUid, friendDoc, callback);
                            })
                            .addOnFailureListener(e -> callback.onError("Provera prijatelja nije uspela."));
                })
                .addOnFailureListener(e -> callback.onError("Vas profil nije ucitan."));
    }

    private void writeFriendship(String myUid, DocumentSnapshot myDoc, String friendUid, DocumentSnapshot friendDoc, ActionCallback callback) {
        WriteBatch batch = db.batch();
        batch.set(
                db.collection("users").document(myUid).collection("friends").document(friendUid),
                friendPayload(friendUid, friendDoc)
        );
        batch.set(
                db.collection("users").document(friendUid).collection("friends").document(myUid),
                friendPayload(myUid, myDoc)
        );
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Prijatelj nije dodat."));
    }

    private Map<String, Object> friendPayload(String uid, DocumentSnapshot doc) {
        Map<String, Object> payload = new HashMap<>();
        String username = value(doc.getString("username"));
        payload.put("uid", uid);
        payload.put("username", username);
        payload.put("usernameLower", username.toLowerCase(Locale.ROOT));
        payload.put("addedAt", Timestamp.now());
        return payload;
    }

    private void loadFriendProfiles(List<String> ids, LoadCallback callback) {
        List<FriendProfile> friends = new ArrayList<>();
        if (ids.isEmpty()) {
            callback.onSuccess(friends);
            return;
        }
        final int[] remaining = {ids.size()};
        final boolean[] failed = {false};
        for (String id : ids) {
            db.collection("users")
                    .document(id)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!failed[0] && snapshot != null && snapshot.exists()) {
                            friends.add(mapUser(snapshot));
                        }
                        remaining[0]--;
                        if (!failed[0] && remaining[0] == 0) {
                            callback.onSuccess(friends);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (!failed[0]) {
                            failed[0] = true;
                            callback.onError("Ne mogu da ucitam profil prijatelja.");
                        }
                    });
        }
    }

    private FriendProfile mapUser(DocumentSnapshot doc) {
        FriendProfile profile = new FriendProfile();
        profile.uid = doc.getId();
        profile.username = value(doc.getString("username"));
        profile.avatarId = value(doc.getString("avatarId"));
        profile.stars = value(doc.getLong("stars"));
        profile.league = value(doc.getLong("league"));
        Boolean appActive = doc.getBoolean("appActive");
        Boolean inMatch = doc.getBoolean("inMatch");
        profile.appActive = appActive != null && appActive;
        profile.inMatch = inMatch != null && inMatch;
        profile.appLastSeenAtMillis = value(doc.getLong("appLastSeenAtMillis"));
        profile.matchUpdatedAtMillis = value(doc.getLong("matchUpdatedAtMillis"));
        profile.activeRoomId = value(doc.getString("activeRoomId"));
        return profile;
    }

    private String value(String input) {
        return input == null ? "" : input;
    }

    private long value(Long input) {
        return input == null ? 0L : input;
    }
}
