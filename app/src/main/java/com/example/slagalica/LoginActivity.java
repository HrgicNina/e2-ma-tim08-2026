package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.AuthResultCallback;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginIdentity;
    private EditText etLoginPassword;
    private AuthService authService;
    private SessionManager sessionManager;
    private boolean loginInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new AuthService();
        sessionManager = new SessionManager(this);

        etLoginIdentity = findViewById(R.id.etLoginIdentity);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnGuestMode = findViewById(R.id.btnGuestMode);
        TextView tvGoRegister = findViewById(R.id.tvGoRegister);

        btnLogin.setOnClickListener(v -> login());
        btnGuestMode.setOnClickListener(v -> continueAsGuest());
        tvGoRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        bindEnterToLogin(etLoginIdentity);
        bindEnterToLogin(etLoginPassword);
    }

    private void bindEnterToLogin(EditText field) {
        field.setOnEditorActionListener((v, actionId, event) -> {
            boolean imeAction = actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_NEXT
                    || actionId == EditorInfo.IME_ACTION_UNSPECIFIED;
            boolean keyEnterDown = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (imeAction || keyEnterDown) {
                login();
                return true;
            }
            return false;
        });
        field.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                login();
                return true;
            }
            return false;
        });
    }

    private void login() {
        if (loginInProgress) {
            return;
        }
        loginInProgress = true;
        sessionManager.clearGuestMode();
        authService.login(
                etLoginIdentity.getText().toString(),
                etLoginPassword.getText().toString(),
                new AuthResultCallback() {
                    @Override
                    public void onSuccess() {
                        loginInProgress = false;
                        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                        finish();
                    }

                    @Override
                    public void onEmailNotVerified() {
                        loginInProgress = false;
                        Toast.makeText(LoginActivity.this, R.string.verify_email_first, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        loginInProgress = false;
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
