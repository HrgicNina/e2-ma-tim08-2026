package com.example.slagalica;

import android.content.res.ColorStateList;
import android.content.Intent;
import android.app.Dialog;
import android.os.Bundle;
import android.view.Window;
import android.view.ViewGroup;
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

    private static final String STATE_SELECTED_REGION = "selected_region";

    private EditText etEmail;
    private EditText etUsername;
    private TextView tvRegion;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private AuthService authService;
    private String selectedRegion = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authService = new AuthService();

        etEmail = findViewById(R.id.etRegEmail);
        etUsername = findViewById(R.id.etRegUsername);
        tvRegion = findViewById(R.id.tvRegRegion);
        etPassword = findViewById(R.id.etRegPassword);
        etConfirmPassword = findViewById(R.id.etRegConfirmPassword);

        if (savedInstanceState != null) {
            selectedRegion = savedInstanceState.getString(STATE_SELECTED_REGION, "");
        }
        renderRegionPicker();

        Button btnRegister = findViewById(R.id.btnRegister);
        TextView tvGoLogin = findViewById(R.id.tvGoLogin);

        tvRegion.setOnClickListener(v -> showRegionPickerDialog());
        btnRegister.setOnClickListener(v -> register());
        tvGoLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SELECTED_REGION, selectedRegion);
        super.onSaveInstanceState(outState);
    }

    private void renderRegionPicker() {
        boolean hasSelection = !selectedRegion.isEmpty();
        tvRegion.setSelected(hasSelection);
        tvRegion.setText(hasSelection ? selectedRegion : getString(R.string.btn_pick_region));
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
        pickerMap.setData(null, selectedRegion, selectedRegion);
        container.addView(pickerMap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        Button closeButton = new Button(this);
        closeButton.setText(R.string.btn_cancel);
        closeButton.setTextSize(20);
        closeButton.setTextColor(getColor(R.color.app_on_primary));
        closeButton.setAllCaps(false);
        closeButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.app_primary_blue)));
        LinearLayout.LayoutParams closeButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        closeButtonParams.topMargin = dp(12);
        container.addView(closeButton, closeButtonParams);

        pickerMap.setRegionClickListener(region -> {
            selectedRegion = region;
            renderRegionPicker();
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
                selectedRegion,
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
