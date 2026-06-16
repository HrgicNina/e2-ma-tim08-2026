package com.example.slagalica;

import android.content.Intent;
import android.app.Dialog;
import android.os.Bundle;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

        authService = new AuthService();

        etEmail = findViewById(R.id.etRegEmail);
        etUsername = findViewById(R.id.etRegUsername);
        etRegion = findViewById(R.id.etRegRegion);
        etPassword = findViewById(R.id.etRegPassword);
        etConfirmPassword = findViewById(R.id.etRegConfirmPassword);

        String[] regions = getResources().getStringArray(R.array.serbia_regions);
        etRegion.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, regions));

        Button btnPickRegion = findViewById(R.id.btnPickRegion);
        Button btnRegister = findViewById(R.id.btnRegister);
        TextView tvGoLogin = findViewById(R.id.tvGoLogin);

        btnPickRegion.setOnClickListener(v -> showRegionPickerDialog());
        btnRegister.setOnClickListener(v -> register());
        tvGoLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void showRegionPickerDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Izaberi region");
        title.setTextSize(22);
        title.setTextColor(0xFF1E2A25);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        RegionMapView pickerMap = new RegionMapView(this);
        pickerMap.setData(null, etRegion.getText().toString(), etRegion.getText().toString());
        container.addView(pickerMap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        Button closeButton = new Button(this);
        closeButton.setText("Otkazi");
        container.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        pickerMap.setRegionClickListener(region -> {
            etRegion.setText(region, false);
            dialog.dismiss();
        });
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(container);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
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

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
