package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.ResultCallback;
import com.example.slagalica.model.RegistrationData;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etUsername;
    private AutoCompleteTextView etRegion;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authService = new AuthService(new FirebaseAuthRepository());

        etEmail = findViewById(R.id.etRegEmail);
        etUsername = findViewById(R.id.etRegUsername);
        etRegion = findViewById(R.id.etRegRegion);
        etPassword = findViewById(R.id.etRegPassword);
        etConfirmPassword = findViewById(R.id.etRegConfirmPassword);

        String[] regions = getResources().getStringArray(R.array.serbia_regions);
        etRegion.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, regions));

        Button btnRegister = findViewById(R.id.btnRegister);
        TextView tvGoLogin = findViewById(R.id.tvGoLogin);

        btnRegister.setOnClickListener(v -> register());
        tvGoLogin.setOnClickListener(v -> finish());
    }

    private void register() {
        RegistrationData data = new RegistrationData(
                etEmail.getText().toString(),
                etUsername.getText().toString(),
                etRegion.getText().toString(),
                etPassword.getText().toString(),
                etConfirmPassword.getText().toString()
        );

        authService.register(data, new ResultCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(RegisterActivity.this, R.string.registration_success_verify, Toast.LENGTH_LONG).show();
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
