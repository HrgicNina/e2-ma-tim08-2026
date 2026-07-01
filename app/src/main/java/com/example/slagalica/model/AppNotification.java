package com.example.slagalica.model;

public class AppNotification {
    public String id;
    public String type;
    public String title;
    public String message;
    public boolean read;
    public boolean localShown;
    public long createdAtMillis;
    public String actionType;
    public String actionPayload;
    public long rewardTokens;
    public boolean rewardClaimed;

    public AppNotification() {
    }
}
