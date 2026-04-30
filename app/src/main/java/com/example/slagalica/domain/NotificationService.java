package com.example.slagalica.domain;

import com.example.slagalica.data.NotificationsRepository;
import com.example.slagalica.model.AppNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class NotificationService {

    public enum Filter {
        ALL,
        UNREAD,
        READ
    }

    public interface UiLoadCallback {
        void onSuccess(List<AppNotification> items);

        void onError(String message);
    }

    public interface UiActionCallback {
        void onSuccess();

        void onError(String message);
    }

    private final NotificationsRepository repository;

    public NotificationService(NotificationsRepository repository) {
        this.repository = repository;
    }

    public void load(Filter filter, UiLoadCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Niste ulogovani.");
            return;
        }
        Boolean readFilter = null;
        if (filter == Filter.UNREAD) {
            readFilter = false;
        } else if (filter == Filter.READ) {
            readFilter = true;
        }
        repository.loadForUser(user.getUid(), readFilter, new NotificationsRepository.LoadCallback() {
            @Override
            public void onSuccess(List<AppNotification> items) {
                callback.onSuccess(items);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void markAsRead(String notificationId, UiActionCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Niste ulogovani.");
            return;
        }
        repository.markAsRead(user.getUid(), notificationId, new NotificationsRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void createTest(String type, String title, String message, UiActionCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Niste ulogovani.");
            return;
        }
        repository.createTestNotification(user.getUid(), type, title, message, new NotificationsRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
