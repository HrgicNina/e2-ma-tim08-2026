package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class InviteActionReceiver extends BroadcastReceiver {

    public static final String ACTION_DECLINE_INVITE = "com.example.slagalica.DECLINE_INVITE";
    public static final String EXTRA_INVITE_ID = "invite_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_DECLINE_INVITE.equals(intent.getAction())) {
            return;
        }
        String inviteId = intent.getStringExtra(EXTRA_INVITE_ID);
        if (context.getApplicationContext() instanceof SlagalicaApp) {
            ((SlagalicaApp) context.getApplicationContext()).declineBackgroundInvite(inviteId);
        }
    }
}
