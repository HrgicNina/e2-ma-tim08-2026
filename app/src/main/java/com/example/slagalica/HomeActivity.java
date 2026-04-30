package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.NotificationChannelHelper;
import com.example.slagalica.domain.SessionManager;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        NotificationChannelHelper.createChannels(this);

        AuthService authService = new AuthService(new FirebaseAuthRepository());
        SessionManager sessionManager = new SessionManager(this);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        Button btnStepByStep = findViewById(R.id.btnOpenStepByStep);
        Button btnMastermind = findViewById(R.id.btnOpenMastermind);
        Button btnMyNumber = findViewById(R.id.btnOpenMyNumber);
        TextView btnNotifications = findViewById(R.id.btnOpenNotifications);
        Button btnResetPassword = findViewById(R.id.btnOpenResetPassword);
        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnGuestRegister = findViewById(R.id.btnGuestRegister);

        if (sessionManager.isGuestMode()) {
            tvWelcome.setText(getString(R.string.welcome_guest));
            btnNotifications.setVisibility(android.view.View.GONE);
            btnResetPassword.setVisibility(android.view.View.GONE);
            btnLogout.setVisibility(android.view.View.GONE);
            btnGuestRegister.setVisibility(android.view.View.VISIBLE);
        } else {
            authService.getCurrentUsername(username -> {
                if (username != null && !username.trim().isEmpty()) {
                    tvWelcome.setText(getString(R.string.welcome_user, username));
                } else {
                    tvWelcome.setText(getString(R.string.welcome));
                }
            });
        }

        btnStepByStep.setOnClickListener(v -> startActivity(new Intent(this, StepByStepActivity.class)));
        btnMastermind.setOnClickListener(v -> startActivity(new Intent(this, MastermindGameActivity.class)));
        btnMyNumber.setOnClickListener(v -> startActivity(new Intent(this, MyNumberGameActivity.class)));
        btnNotifications.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        btnResetPassword.setOnClickListener(v -> startActivity(new Intent(this, ResetPasswordActivity.class)));
        btnGuestRegister.setOnClickListener(v -> {
            sessionManager.clearGuestMode();
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
            finish();
        });

        btnLogout.setOnClickListener(v -> {
            sessionManager.clearGuestMode();
            authService.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
