package com.example.slagalica.model;

public class AppNotification {
    public String id;
    public String type;
    public String title;
    public String message;
    public boolean read;
    public long createdAtMillis;
    public String actionType;
    public String actionPayload;

    public AppNotification() {
    }
}
