package com.example.slagalica.model;

public class FriendProfile {
    public String uid;
    public String username;
    public String avatarId;
    public long stars;
    public long league;
    public long monthlyRank;
    public boolean appActive;
    public boolean inMatch;
    public long appLastSeenAtMillis;
    public long matchUpdatedAtMillis;
    public String activeRoomId;

    public boolean canInvite() {
        return !isInMatch() && isRecentlyLoggedIn();
    }

    public boolean isInMatch() {
        if (!inMatch) {
            return false;
        }
        if (appActive) {
            return true;
        }
        if (matchUpdatedAtMillis <= 0L) {
            return false;
        }
        long twoHoursMs = 2L * 60L * 60L * 1000L;
        return System.currentTimeMillis() - matchUpdatedAtMillis <= twoHoursMs;
    }

    public boolean isRecentlyLoggedIn() {
        long lastSeen = appLastSeenAtMillis;
        if (appActive) {
            return true;
        }
        if (lastSeen <= 0L) {
            return false;
        }
        long oneDayMs = 24L * 60L * 60L * 1000L;
        return System.currentTimeMillis() - lastSeen <= oneDayMs;
    }
}
