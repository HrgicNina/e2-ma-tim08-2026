package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.SessionManager;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        AuthService authService = new AuthService(new FirebaseAuthRepository());
        SessionManager sessionManager = new SessionManager(this);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        Button btnResetPassword = findViewById(R.id.btnOpenResetPassword);
        Button btnLogout = findViewById(R.id.btnLogout);

        if (sessionManager.isGuestMode()) {
            tvWelcome.setText(getString(R.string.welcome_guest));
            btnResetPassword.setVisibility(android.view.View.GONE);
        } else {
            String email = authService.getCurrentUserEmail();
            if (email != null) {
                tvWelcome.setText(getString(R.string.welcome_user, email));
            } else {
                tvWelcome.setText(getString(R.string.welcome));
            }
        }

        btnResetPassword.setOnClickListener(v -> startActivity(new Intent(this, ResetPasswordActivity.class)));

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
