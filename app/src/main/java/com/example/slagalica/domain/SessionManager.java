package com.example.slagalica.domain;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREFS_NAME = "slagalica_prefs";
    private static final String KEY_GUEST_MODE = "guest_mode";

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
}
