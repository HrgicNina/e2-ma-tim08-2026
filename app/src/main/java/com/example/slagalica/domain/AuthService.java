package com.example.slagalica.domain;

import android.text.TextUtils;
import android.util.Patterns;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.model.RegistrationData;

public class AuthService {

    private final FirebaseAuthRepository repository;

    public AuthService(FirebaseAuthRepository repository) {
        this.repository = repository;
    }

    public boolean isLoggedInAndVerified() {
        return repository.isLoggedInAndVerified();
    }

    public void login(String identity, String password, AuthResultCallback callback) {
        if (TextUtils.isEmpty(identity) || TextUtils.isEmpty(password)) {
            callback.onError("Popuni sva polja.");
            return;
        }
        repository.loginWithIdentity(identity.trim(), password.trim(), callback);
    }

    public void register(RegistrationData data, ResultCallback callback) {
        if (TextUtils.isEmpty(data.email) || TextUtils.isEmpty(data.username) || TextUtils.isEmpty(data.region)
                || TextUtils.isEmpty(data.password) || TextUtils.isEmpty(data.confirmPassword)) {
            callback.onError("Popuni sva polja.");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(data.email).matches()) {
            callback.onError("Email nije ispravan.");
            return;
        }

        if (data.password.length() < 6) {
            callback.onError("Lozinka mora imati minimum 6 karaktera.");
            return;
        }

        if (!data.password.equals(data.confirmPassword)) {
            callback.onError("Lozinke se ne poklapaju.");
            return;
        }

        repository.register(
                data.email.trim(),
                data.username.trim(),
                data.region.trim(),
                data.password.trim(),
                callback
        );
    }

    public void resetPassword(String oldPassword, String newPassword, String confirmPassword, ResultCallback callback) {
        if (TextUtils.isEmpty(oldPassword) || TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
            callback.onError("Popuni sva polja.");
            return;
        }

        if (newPassword.length() < 6) {
            callback.onError("Lozinka mora imati minimum 6 karaktera.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            callback.onError("Lozinke se ne poklapaju.");
            return;
        }

        repository.resetPassword(oldPassword.trim(), newPassword.trim(), callback);
    }

    public void logout() {
        repository.logout();
    }

    public String getCurrentUserEmail() {
        return repository.getCurrentUserEmail();
    }
}
