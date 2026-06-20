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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        FriendsListenerSession session = new FriendsListenerSession(callback);
        session.start(uid);
        return session;
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

    private FriendProfile mapUser(DocumentSnapshot doc) {
        FriendProfile profile = new FriendProfile();
        profile.uid = doc.getId();
        profile.username = value(doc.getString("username"));
        profile.avatarId = value(doc.getString("avatarId"));
        profile.avatarFrameId = value(doc.getString("avatarFrameId"));
        profile.stars = value(doc.getLong("stars"));
        profile.league = value(doc.getLong("league"));
        Boolean loggedIn = doc.getBoolean("loggedIn");
        Boolean appActive = doc.getBoolean("appActive");
        Boolean inMatch = doc.getBoolean("inMatch");
        profile.loggedIn = loggedIn != null && loggedIn;
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

    private class FriendsListenerSession implements ListenerRegistration {
        private final LoadCallback callback;
        private final Map<String, ListenerRegistration> profileListeners = new HashMap<>();
        private final Map<String, FriendProfile> profiles = new HashMap<>();
        private final Set<String> friendIds = new HashSet<>();
        private final Set<String> pendingProfiles = new HashSet<>();
        private ListenerRegistration friendshipListener;
        private boolean removed;

        FriendsListenerSession(LoadCallback callback) {
            this.callback = callback;
        }

        void start(String uid) {
            friendshipListener = db.collection("users")
                    .document(uid)
                    .collection("friends")
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null) {
                            notifyError("Ne mogu da ucitam prijatelje.");
                            return;
                        }

                        Set<String> updatedIds = new HashSet<>();
                        if (snapshot != null) {
                            for (QueryDocumentSnapshot doc : snapshot) {
                                updatedIds.add(doc.getId());
                            }
                        }
                        updateProfileListeners(updatedIds);
                    });
        }

        private void updateProfileListeners(Set<String> updatedIds) {
            List<ListenerRegistration> obsoleteListeners = new ArrayList<>();
            Set<String> newIds = new HashSet<>();

            synchronized (this) {
                if (removed) {
                    return;
                }

                for (String oldId : new HashSet<>(friendIds)) {
                    if (!updatedIds.contains(oldId)) {
                        friendIds.remove(oldId);
                        pendingProfiles.remove(oldId);
                        profiles.remove(oldId);
                        ListenerRegistration listener = profileListeners.remove(oldId);
                        if (listener != null) {
                            obsoleteListeners.add(listener);
                        }
                    }
                }

                for (String id : updatedIds) {
                    if (friendIds.add(id)) {
                        pendingProfiles.add(id);
                        newIds.add(id);
                    }
                }
            }

            for (ListenerRegistration listener : obsoleteListeners) {
                listener.remove();
            }
            for (String id : newIds) {
                listenProfile(id);
            }
            notifyProfilesIfReady();
        }

        private void listenProfile(String id) {
            ListenerRegistration listener = db.collection("users")
                    .document(id)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null) {
                            synchronized (FriendsListenerSession.this) {
                                pendingProfiles.remove(id);
                            }
                            notifyError("Ne mogu da ucitam profil prijatelja.");
                            notifyProfilesIfReady();
                            return;
                        }

                        synchronized (FriendsListenerSession.this) {
                            if (removed || !friendIds.contains(id)) {
                                return;
                            }
                            if (snapshot != null && snapshot.exists()) {
                                profiles.put(id, mapUser(snapshot));
                            } else {
                                profiles.remove(id);
                            }
                            pendingProfiles.remove(id);
                        }
                        notifyProfilesIfReady();
                    });

            synchronized (this) {
                if (removed || !friendIds.contains(id)) {
                    listener.remove();
                    return;
                }
                profileListeners.put(id, listener);
            }
        }

        private void notifyProfilesIfReady() {
            List<FriendProfile> currentProfiles = new ArrayList<>();
            synchronized (this) {
                if (removed || !pendingProfiles.isEmpty()) {
                    return;
                }
                for (String id : friendIds) {
                    FriendProfile profile = profiles.get(id);
                    if (profile != null) {
                        currentProfiles.add(profile);
                    }
                }
            }
            callback.onSuccess(currentProfiles);
        }

        private void notifyError(String message) {
            synchronized (this) {
                if (removed) {
                    return;
                }
            }
            callback.onError(message);
        }

        @Override
        public void remove() {
            List<ListenerRegistration> listeners;
            ListenerRegistration mainListener;
            synchronized (this) {
                if (removed) {
                    return;
                }
                removed = true;
                mainListener = friendshipListener;
                listeners = new ArrayList<>(profileListeners.values());
                profileListeners.clear();
                profiles.clear();
                friendIds.clear();
                pendingProfiles.clear();
            }
            if (mainListener != null) {
                mainListener.remove();
            }
            for (ListenerRegistration listener : listeners) {
                listener.remove();
            }
        }
    }
}
