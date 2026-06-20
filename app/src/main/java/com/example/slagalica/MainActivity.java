package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.NotificationChannelHelper;
import com.example.slagalica.domain.SessionManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationChannelHelper.createChannels(this);

        AuthService authService = new AuthService();
        SessionManager sessionManager = new SessionManager(this);

        if (sessionManager.isGuestMode()) {
            sessionManager.clearGuestMode();
        }

        Intent intent = new Intent(
                this,
                authService.isLoggedInAndVerified() ? HomeActivity.class : LoginActivity.class
        );

        startActivity(intent);
        finish();
    }
}
