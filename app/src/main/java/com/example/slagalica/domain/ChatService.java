package com.example.slagalica.domain;

import android.text.TextUtils;

import com.example.slagalica.data.ChatRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatService {

    private static final long APP_ACTIVE_STALE_MS = 90_000L;

    public interface InitCallback {
        void onSuccess(ChatInitData data);
        void onError(String message);
    }

    public interface MessagesCallback {
        void onSuccess(List<ChatMessage> messages);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess(String messageId);
        void onError(String message);
    }

    public static class ChatInitData {
        public final String username;
        public final String region;
        public final String roomId;

        public ChatInitData(String username, String region, String roomId) {
            this.username = username;
            this.region = region;
            this.roomId = roomId;
        }
    }

    public static class ChatMessage {
        public String senderUid;
        public String senderName;
        public String text;
        public long createdAtMillis;
    }

    private final ChatRepository repository;

    public ChatService() {
        this(new ChatRepository());
    }

    public ChatService(ChatRepository repository) {
        this.repository = repository;
    }

    public void loadProfileAndResolveRoom(String uid, InitCallback callback) {
        repository.loadUserProfile(uid, new ChatRepository.ProfileCallback() {
            @Override
            public void onSuccess(ChatRepository.UserProfile profile) {
                String username = value(profile.username);
                String region = value(profile.region);
                if (TextUtils.isEmpty(username)) {
                    username = "Igrac";
                }
                if (TextUtils.isEmpty(region)) {
                    region = "Srbija";
                }
                callback.onSuccess(new ChatInitData(username, region, normalizeRegionRoom(region)));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public ListenerRegistration listenMessages(String roomId, MessagesCallback callback) {
        return repository.listenMessages(roomId, new ChatRepository.MessagesCallback() {
            @Override
            public void onSuccess(List<ChatRepository.ChatMessageEntity> messages) {
                List<ChatMessage> out = new ArrayList<>();
                for (ChatRepository.ChatMessageEntity item : messages) {
                    ChatMessage mapped = new ChatMessage();
                    mapped.senderUid = item.senderUid;
                    mapped.senderName = item.senderName;
                    mapped.text = item.text;
                    mapped.createdAtMillis = item.createdAtMillis;
                    out.add(mapped);
                }
                callback.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void sendMessage(String roomId, String myUid, String myUsername, String text, ActionCallback callback) {
        repository.sendMessage(roomId, myUid, myUsername, text, new ChatRepository.ActionCallback() {
            @Override
            public void onSuccess(String messageId) {
                callback.onSuccess(messageId);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void notifyRegionMembersIfNeeded(
            String myUid,
            String myUsername,
            String myRegion,
            String roomId,
            String messageText,
            String messageId
    ) {
        if (TextUtils.isEmpty(myRegion)) {
            return;
        }
        repository.loadUsersByRegion(myRegion, new ChatRepository.RegionUsersCallback() {
            @Override
            public void onSuccess(List<ChatRepository.RegionUserEntity> users) {
                List<String> targets = new ArrayList<>();
                for (ChatRepository.RegionUserEntity user : users) {
                    if (TextUtils.isEmpty(user.uid) || myUid.equals(user.uid)) {
                        continue;
                    }
                    if (isUserCurrentlyInApp(user)) {
                        continue;
                    }
                    targets.add(user.uid);
                }
                repository.createChatNotifications(targets, myUsername, trimPreview(messageText), roomId, messageId);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    public void setChatPresence(String uid, boolean active) {
        repository.setChatPresence(uid, active);
    }

    private String normalizeRegionRoom(String region) {
        String normalized = value(region).trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        if (normalized.isEmpty()) {
            return "srbija";
        }
        return normalized;
    }

    private String trimPreview(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= 90) {
            return trimmed;
        }
        return trimmed.substring(0, 90) + "...";
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private boolean isUserCurrentlyInApp(ChatRepository.RegionUserEntity user) {
        if (user == null || !user.appActive) {
            return false;
        }
        if (user.appLastSeenAtMillis <= 0L) {
            return true;
        }
        long age = System.currentTimeMillis() - user.appLastSeenAtMillis;
        return age >= 0L && age <= APP_ACTIVE_STALE_MS;
    }
}
