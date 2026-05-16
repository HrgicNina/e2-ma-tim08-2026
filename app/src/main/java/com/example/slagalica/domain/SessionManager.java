package com.example.slagalica.domain;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.UUID;

public class SessionManager {

    private static final String PREFS_NAME = "slagalica_prefs";
    private static final String KEY_GUEST_MODE = "guest_mode";
    private static final String KEY_GUEST_UID = "guest_uid";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setGuestMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_GUEST_MODE, enabled).apply();
    }

    public boolean isGuestMode() {
        return prefs.getBoolean(KEY_GUEST_MODE, false);
    }

    public void clearGuestMode() {
        prefs.edit().remove(KEY_GUEST_MODE).apply();
    }

    public String getOrCreateGuestUid() {
        String existing = prefs.getString(KEY_GUEST_UID, null);
        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }
        String generated = "guest_" + UUID.randomUUID().toString().replace("-", "");
        prefs.edit().putString(KEY_GUEST_UID, generated).apply();
        return generated;
    }

    public String getGuestDisplayName() {
        String uid = getOrCreateGuestUid();
        String suffix = uid.length() >= 4 ? uid.substring(uid.length() - 4) : uid;
        return "Gost-" + suffix.toUpperCase(Locale.ROOT);
    }
}
