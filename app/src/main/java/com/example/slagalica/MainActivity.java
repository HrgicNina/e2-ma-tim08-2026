package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.NotificationChannelHelper;
import com.example.slagalica.domain.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationChannelHelper.createChannels(this);

        AuthService authService = new AuthService(new FirebaseAuthRepository());
        SessionManager sessionManager = new SessionManager(this);

        if (sessionManager.isGuestMode()) {
            sessionManager.clearGuestMode();
        }

        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);

        startActivity(intent);
        finish();
    }
}
