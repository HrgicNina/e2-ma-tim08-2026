package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.SessionManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AuthService authService = new AuthService(new FirebaseAuthRepository());
        SessionManager sessionManager = new SessionManager(this);
        Intent intent = (sessionManager.isGuestMode() || authService.isLoggedInAndVerified())
                ? new Intent(this, HomeActivity.class)
                : new Intent(this, LoginActivity.class);

        startActivity(intent);
        finish();
    }
}
