package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.domain.AuthResultCallback;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginIdentity;
    private EditText etLoginPassword;
    private AuthService authService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new AuthService(new FirebaseAuthRepository());
        sessionManager = new SessionManager(this);

        etLoginIdentity = findViewById(R.id.etLoginIdentity);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnGuestMode = findViewById(R.id.btnGuestMode);
        TextView tvGoRegister = findViewById(R.id.tvGoRegister);

        btnLogin.setOnClickListener(v -> login());
        btnGuestMode.setOnClickListener(v -> continueAsGuest());
        tvGoRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void login() {
        sessionManager.clearGuestMode();
        authService.login(
                etLoginIdentity.getText().toString(),
                etLoginPassword.getText().toString(),
                new AuthResultCallback() {
                    @Override
                    public void onSuccess() {
                        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                        finish();
                    }

                    @Override
                    public void onEmailNotVerified() {
                        Toast.makeText(LoginActivity.this, R.string.verify_email_first, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void continueAsGuest() {
        sessionManager.setGuestMode(true);
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
