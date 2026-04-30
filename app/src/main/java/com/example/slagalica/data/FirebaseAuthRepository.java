package com.example.slagalica.data;

import androidx.annotation.NonNull;

import com.example.slagalica.domain.AuthResultCallback;
import com.example.slagalica.domain.ResultCallback;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirebaseAuthRepository {
    public interface UsernameCallback {
        void onLoaded(String username);
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
        db.collection("users")
                .whereEqualTo("usernameLower", username.toLowerCase())
                .limit(1)
                .get()
                .addOnCompleteListener(usernameTask -> {
                    if (!usernameTask.isSuccessful()) {
                        callback.onError("Registracija nije uspela.");
                        return;
                    }

                    if (usernameTask.getResult() != null && !usernameTask.getResult().isEmpty()) {
                        callback.onError("Korisnicko ime je zauzeto.");
                        return;
                    }

                    auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(createTask -> {
                                if (!createTask.isSuccessful()) {
                                    callback.onError("Registracija nije uspela.");
                                    return;
                                }

                                FirebaseUser user = auth.getCurrentUser();
                                if (user == null) {
                                    callback.onError("Registracija nije uspela.");
                                    return;
                                }

                                Map<String, Object> userDoc = new HashMap<>();
                                userDoc.put("uid", user.getUid());
                                userDoc.put("email", email);
                                userDoc.put("username", username);
                                userDoc.put("usernameLower", username.toLowerCase());
                                userDoc.put("region", region);
                                userDoc.put("tokens", 5);
                                userDoc.put("stars", 0);
                                userDoc.put("league", 0);

                                db.collection("users")
                                        .document(user.getUid())
                                        .set(userDoc)
                                        .addOnSuccessListener(unused -> sendVerificationAndSignOut(user, callback))
                                        .addOnFailureListener(e -> {
                                            callback.onError("Registracija nije uspela.");
                                            user.delete();
                                        });
                            });
                });
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

    public void logout() {
        auth.signOut();
    }

    public String getCurrentUserEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : null;
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
                            callback.onSuccess();
                        } else {
                            auth.signOut();
                            callback.onEmailNotVerified();
                        }
                    });
                });
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
}
