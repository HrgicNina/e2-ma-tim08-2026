package com.example.slagalica;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.ResultCallback;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText etOldPassword;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        authService = new AuthService(new FirebaseAuthRepository());

        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        Button btnUpdatePassword = findViewById(R.id.btnUpdatePassword);

        btnUpdatePassword.setOnClickListener(v -> updatePassword());
    }

    private void updatePassword() {
        authService.resetPassword(
                etOldPassword.getText().toString(),
                etNewPassword.getText().toString(),
                etConfirmNewPassword.getText().toString(),
                new ResultCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(ResetPasswordActivity.this, R.string.password_updated, Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ResetPasswordActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
}
