package com.example.slagalica.domain;

import android.net.Uri;

import com.example.slagalica.data.FriendsRepository;
import com.example.slagalica.model.FriendProfile;
import com.example.slagalica.model.LeaderboardEntry;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FriendsService {

    public interface LoadCallback {
        void onSuccess(List<FriendProfile> friends);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    private final FriendsRepository repository;
    private final LeaderboardService leaderboardService;

    public FriendsService() {
        this(new FriendsRepository(), new LeaderboardService());
    }

    public FriendsService(FriendsRepository repository, LeaderboardService leaderboardService) {
        this.repository = repository;
        this.leaderboardService = leaderboardService;
    }

    public ListenerRegistration listenFriends(String uid, LoadCallback callback) {
        return repository.listenFriends(uid, new FriendsRepository.LoadCallback() {
            @Override
            public void onSuccess(List<FriendProfile> friends) {
                attachMonthlyRanks(friends, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void addFriendByUsername(String myUid, String username, ActionCallback callback) {
        repository.addFriendByUsername(myUid, username, adapt(callback));
    }

    public void addFriendFromQr(String myUid, String qrPayload, ActionCallback callback) {
        String uid = extractUid(qrPayload);
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError("QR kod nije za dodavanje prijatelja.");
            return;
        }
        repository.addFriendByUid(myUid, uid, adapt(callback));
    }

    public String buildInvitePayload(String uid, String username) {
        return "slagalica://friend?uid=" + safe(uid) + "&username=" + Uri.encode(safe(username));
    }

    private void attachMonthlyRanks(List<FriendProfile> friends, LoadCallback callback) {
        leaderboardService.loadMonthlyLeaderboard(new LeaderboardService.LoadCallback() {
            @Override
            public void onSuccess(LeaderboardService.CycleWindow cycle, List<LeaderboardEntry> entries) {
                Map<String, Long> ranks = new HashMap<>();
                for (int i = 0; i < entries.size(); i++) {
                    ranks.put(entries.get(i).uid, (long) i + 1L);
                }
                for (FriendProfile friend : friends) {
                    Long rank = ranks.get(friend.uid);
                    friend.monthlyRank = rank == null ? 0L : rank;
                }
                callback.onSuccess(friends);
            }

            @Override
            public void onError(String message) {
                callback.onSuccess(friends);
            }
        });
    }

    private String extractUid(String payload) {
        String value = safe(payload).trim();
        if (value.isEmpty()) {
            return "";
        }
        try {
            Uri uri = Uri.parse(value);
            if ("slagalica".equalsIgnoreCase(uri.getScheme()) && "friend".equalsIgnoreCase(uri.getHost())) {
                return safe(uri.getQueryParameter("uid"));
            }
        } catch (Exception ignored) {
        }
        if (value.toLowerCase(Locale.ROOT).startsWith("uid:")) {
            return value.substring(4).trim();
        }
        return "";
    }

    private FriendsRepository.ActionCallback adapt(ActionCallback callback) {
        return new FriendsRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    private String safe(String input) {
        return input == null ? "" : input;
    }
}
