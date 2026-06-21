package com.example.slagalica.data;

import android.util.Log;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.example.slagalica.domain.AuthResultCallback;
import com.example.slagalica.domain.ResultCallback;
import com.example.slagalica.model.RegionCatalog;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FirebaseAuthRepository {
    private static final String TAG = "FirebaseAuthRepository";
    private static final String PENDING_PROFILE_PREFIX = "slagalica|";

    public interface UsernameCallback {
        void onLoaded(String username);
    }

    public interface UserProfileCallback {
        void onLoaded(UserProfile profile);
    }

    public static class UserProfile {
        public final String username;
        public final String email;
        public final String region;
        public final String avatarId;
        public final String avatarFrameId;

        public UserProfile(String username, String email, String region, String avatarId, String avatarFrameId) {
            this.username = username;
            this.email = email;
            this.region = region;
            this.avatarId = avatarId;
            this.avatarFrameId = avatarFrameId;
        }
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public FirebaseAuthRepository() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    public boolean isLoggedInAndVerified() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }

    public void loginWithIdentity(String identity, String password, AuthResultCallback callback) {
        if (identity.contains("@")) {
            loginWithEmail(identity, password, callback);
            return;
        }

        db.collection("users")
                .whereEqualTo("usernameLower", identity.toLowerCase())
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                        callback.onError("Korisnik ne postoji.");
                        return;
                    }

                    String email = task.getResult().getDocuments().get(0).getString("email");
                    if (email == null) {
                        callback.onError("Prijava nije uspela.");
                        return;
                    }
                    loginWithEmail(email, password, callback);
                });
    }

    public void register(String email, String username, String region, String password, ResultCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(createTask -> {
                    if (!createTask.isSuccessful()) {
                        Log.e(TAG, "Firebase Auth account creation failed", createTask.getException());
                        callback.onError("Registracija nije uspela.");
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Registracija nije uspela.");
                        return;
                    }

                    savePendingProfileAndSendVerification(user, username, region, callback);
                });
    }

    private void savePendingProfileAndSendVerification(
            FirebaseUser user,
            String username,
            String region,
            ResultCallback callback
    ) {
        UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                .setDisplayName(encodePendingProfile(username, region))
                .build();
        user.updateProfile(profileUpdate).addOnCompleteListener(profileTask -> {
            if (!profileTask.isSuccessful()) {
                Log.e(TAG, "Saving pending registration profile failed", profileTask.getException());
                discardIncompleteUser(user);
                callback.onError("Registracija nije uspela.");
                return;
            }
            sendVerificationAndSignOut(user, callback);
        });
    }

    private Map<String, Object> buildUserDocument(
            FirebaseUser user,
            String username,
            String region
    ) {
        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("uid", user.getUid());
        userDoc.put("email", user.getEmail() == null ? "" : user.getEmail());
        userDoc.put("username", username);
        userDoc.put("usernameLower", username.toLowerCase());
        userDoc.put("region", region);
        float[] regionPoint = RegionCatalog.randomPoint(region);
        userDoc.put("regionPointX", regionPoint[0]);
        userDoc.put("regionPointY", regionPoint[1]);
        userDoc.put("tokens", 5);
        userDoc.put("stars", 0);
        userDoc.put("starTokenMilestonesAwarded", 0);
        userDoc.put("league", 0);
        userDoc.put("avatarId", "owl");
        userDoc.put("avatarFrameId", "blue");
        userDoc.put("loggedIn", true);
        userDoc.put("loggedInAt", Timestamp.now());
        userDoc.put("loggedInAtMillis", System.currentTimeMillis());
        userDoc.put("lastDailyTokenGrantAt", System.currentTimeMillis());
        userDoc.put("weeklyCycleId", currentWeeklyCycleId());
        userDoc.put("monthlyCycleId", currentMonthlyCycleId());
        userDoc.put("weeklyCycleStars", 0);
        userDoc.put("monthlyCycleStars", 0);
        userDoc.put("weeklyCycleMatches", 0);
        userDoc.put("monthlyCycleMatches", 0);
        return userDoc;
    }

    private void discardIncompleteUser(FirebaseUser user) {
        user.delete().addOnCompleteListener(task -> auth.signOut());
    }

    public void resetPassword(String oldPassword, String newPassword, ResultCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            callback.onError("Niste ulogovani.");
            return;
        }

        auth.signInWithEmailAndPassword(user.getEmail(), oldPassword)
                .addOnCompleteListener(reauthTask -> {
                    if (!reauthTask.isSuccessful()) {
                        callback.onError("Stara lozinka nije tacna.");
                        return;
                    }

                    user.updatePassword(newPassword)
                            .addOnCompleteListener(updateTask -> {
                                if (updateTask.isSuccessful()) {
                                    callback.onSuccess();
                                } else {
                                    callback.onError("Promena lozinke nije uspela.");
                                }
                            });
                });
    }

    public void logout(ResultCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            auth.signOut();
            callback.onSuccess();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("loggedIn", false);
        updates.put("loggedOutAt", Timestamp.now());
        updates.put("appActive", false);
        updates.put("appLastSeenAt", Timestamp.now());
        updates.put("appLastSeenAtMillis", System.currentTimeMillis());
        updates.put("fcmToken", FieldValue.delete());
        db.collection("users")
                .document(user.getUid())
                .update(updates)
                .addOnCompleteListener(task -> {
                    auth.signOut();
                    callback.onSuccess();
                });
    }

    public String getCurrentUserEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void getCurrentUsername(UsernameCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onLoaded(null);
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        callback.onLoaded(null);
                        return;
                    }
                    callback.onLoaded(task.getResult().getString("username"));
                });
    }

    public void getCurrentUserProfile(UserProfileCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onLoaded(new UserProfile(null, null, null, null, null));
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        callback.onLoaded(new UserProfile(null, null, null, null, null));
                        return;
                    }

                    String username = task.getResult().getString("username");
                    String email = task.getResult().getString("email");
                    String region = task.getResult().getString("region");
                    String avatarId = task.getResult().getString("avatarId");
                    String avatarFrameId = task.getResult().getString("avatarFrameId");
                    callback.onLoaded(new UserProfile(username, email, region, avatarId, avatarFrameId));
                });
    }

    public void updateAvatar(String avatarId, String avatarFrameId, ResultCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Niste ulogovani.");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("avatarId", avatarId);
        updates.put("avatarFrameId", avatarFrameId);
        db.collection("users")
                .document(user.getUid())
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Avatar nije sacuvan."));
    }

    private void loginWithEmail(String email, String password, AuthResultCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener((@NonNull com.google.android.gms.tasks.Task<AuthResult> task) -> {
                    if (!task.isSuccessful()) {
                        callback.onError("Prijava nije uspela. Proveri podatke.");
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        callback.onError("Prijava nije uspela. Proveri podatke.");
                        return;
                    }

                    user.reload().addOnCompleteListener(reloadTask -> {
                        FirebaseUser refreshedUser = auth.getCurrentUser();
                        if (!reloadTask.isSuccessful() || refreshedUser == null) {
                            callback.onError("Prijava nije uspela. Proveri podatke.");
                            return;
                        }

                        if (refreshedUser.isEmailVerified()) {
                            refreshedUser.getIdToken(true).addOnCompleteListener(tokenTask -> {
                                if (!tokenTask.isSuccessful()) {
                                    callback.onError("Prijava nije uspela. Proveri podatke.");
                                    return;
                                }
                                ensureVerifiedUserProfile(refreshedUser, callback);
                            });
                        } else {
                            auth.signOut();
                            callback.onEmailNotVerified();
                        }
                    });
                });
    }

    private void ensureVerifiedUserProfile(FirebaseUser user, AuthResultCallback callback) {
        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnCompleteListener(profileTask -> {
                    if (!profileTask.isSuccessful() || profileTask.getResult() == null) {
                        Log.e(TAG, "Loading user profile after verification failed", profileTask.getException());
                        callback.onError("Prijava nije uspela. Profil nije ucitan.");
                        return;
                    }
                    if (profileTask.getResult().exists()) {
                        markUserLoggedIn(user, callback);
                        return;
                    }

                    String[] pendingProfile = decodePendingProfile(user.getDisplayName());
                    if (pendingProfile == null) {
                        callback.onError("Prijava nije uspela. Profil nije pronadjen.");
                        return;
                    }

                    String username = pendingProfile[0];
                    String region = pendingProfile[1];
                    db.collection("users")
                            .document(user.getUid())
                            .set(buildUserDocument(user, username, region))
                            .addOnSuccessListener(unused -> {
                                UserProfileChangeRequest completedProfile = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(username)
                                        .build();
                                user.updateProfile(completedProfile);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Creating verified Firestore user profile failed", e);
                                callback.onError("Prijava nije uspela. Profil nije sacuvan.");
                            });
                });
    }

    private void markUserLoggedIn(FirebaseUser user, AuthResultCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("loggedIn", true);
        updates.put("loggedInAt", Timestamp.now());
        updates.put("loggedInAtMillis", System.currentTimeMillis());
        db.collection("users")
                .document(user.getUid())
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Saving login state failed", e);
                    auth.signOut();
                    callback.onError("Prijava nije uspela. Status nije sacuvan.");
                });
    }

    private String encodePendingProfile(String username, String region) {
        return PENDING_PROFILE_PREFIX + encode(username) + "|" + encode(region);
    }

    private String[] decodePendingProfile(String displayName) {
        if (displayName == null || !displayName.startsWith(PENDING_PROFILE_PREFIX)) {
            return null;
        }
        String[] parts = displayName.substring(PENDING_PROFILE_PREFIX.length()).split("\\|", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new String[]{decode(parts[0]), decode(parts[1])};
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Pending registration profile is invalid", e);
            return null;
        }
    }

    private String encode(String value) {
        return Base64.encodeToString(
                value.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
    }

    private String decode(String value) {
        return new String(Base64.decode(value, Base64.URL_SAFE), StandardCharsets.UTF_8);
    }

    private void sendVerificationAndSignOut(FirebaseUser user, ResultCallback callback) {
        user.sendEmailVerification().addOnCompleteListener(verificationTask -> {
            if (verificationTask.isSuccessful()) {
                auth.signOut();
                callback.onSuccess();
            } else {
                callback.onError("Neuspesno slanje verifikacionog emaila.");
            }
        });
    }

    private String currentWeeklyCycleId() {
        Calendar start = Calendar.getInstance();
        start.setFirstDayOfWeek(Calendar.MONDAY);
        start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return "W_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
    }

    private String currentMonthlyCycleId() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return "M_" + new SimpleDateFormat("yyyyMM", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
    }
}
