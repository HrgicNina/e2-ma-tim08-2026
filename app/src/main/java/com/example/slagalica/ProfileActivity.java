package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.EconomyService;
import com.example.slagalica.domain.SessionManager;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvAvatar;
    private AuthService authService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        authService = new AuthService();
        sessionManager = new SessionManager(this);

        tvAvatar = findViewById(R.id.tvProfileAvatar);
        TextView tvUsername = findViewById(R.id.tvProfileUsername);
        TextView tvEmail = findViewById(R.id.tvProfileEmail);
        TextView tvRegion = findViewById(R.id.tvProfileRegion);
        TextView tvLeague = findViewById(R.id.tvProfileLeague);
        TextView tvTokens = findViewById(R.id.tvProfileTokens);
        TextView tvStars = findViewById(R.id.tvProfileStars);
        Button btnAvatarA = findViewById(R.id.btnAvatarA);
        Button btnAvatarB = findViewById(R.id.btnAvatarB);
        Button btnAvatarC = findViewById(R.id.btnAvatarC);
        Button btnLogout = findViewById(R.id.btnProfileLogout);

        tvUsername.setText(R.string.profile_username_placeholder);
        String email = authService.getCurrentUserEmail();
        tvEmail.setText(email == null ? "milica@example.com" : email);
        tvRegion.setText(R.string.profile_region_placeholder);

        authService.getCurrentUserProfile(profile -> {
            if (profile.username != null && !profile.username.trim().isEmpty()) {
                tvUsername.setText(profile.username);
                tvAvatar.setText(profile.username.substring(0, 1).toUpperCase());
            }
            if (profile.region != null && !profile.region.trim().isEmpty()) {
                tvRegion.setText(getString(R.string.profile_region_label, profile.region));
            }
        });
        loadEconomy(tvTokens, tvStars, tvLeague);

        setProgress(R.id.pbAvgQuiz, 72);
        setProgress(R.id.pbAvgConnections, 64);
        setProgress(R.id.pbAvgAssociations, 58);
        setProgress(R.id.pbAvgStep, 81);
        setProgress(R.id.pbAvgMastermind, 76);
        setProgress(R.id.pbAvgNumber, 69);
        setProgress(R.id.pbQuizRatio, 68);
        setProgress(R.id.pbNumberPercent, 42);
        setProgress(R.id.pbStep1, 90);
        setProgress(R.id.pbStep2, 78);
        setProgress(R.id.pbStep3, 66);
        setProgress(R.id.pbStep4, 52);
        setProgress(R.id.pbStep5, 39);
        setProgress(R.id.pbStep6, 24);
        setProgress(R.id.pbStep7, 14);
        setProgress(R.id.pbAssociationsRatio, 61);
        setProgress(R.id.pbMastermindAttempt1, 18);
        setProgress(R.id.pbMastermindAttempt2, 29);
        setProgress(R.id.pbMastermindAttempt3, 36);
        setProgress(R.id.pbMastermindAttempt4, 44);
        setProgress(R.id.pbMastermindAttempt5, 53);
        setProgress(R.id.pbMastermindAttempt6, 61);
        setProgress(R.id.pbConnectionsPercent, 74);
        setProgress(R.id.pbWinPercent, 63);

        btnAvatarA.setOnClickListener(v -> tvAvatar.setText("M"));
        btnAvatarB.setOnClickListener(v -> tvAvatar.setText("S"));
        btnAvatarC.setOnClickListener(v -> tvAvatar.setText("L"));

        btnLogout.setOnClickListener(v -> {
            sessionManager.clearGuestMode();
            authService.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void setProgress(int id, int value) {
        ProgressBar progressBar = findViewById(id);
        progressBar.setProgress(value);
    }

    private void loadEconomy(TextView tvTokens, TextView tvStars, TextView tvLeague) {
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        EconomyService economyService = new EconomyService();
        economyService.getEconomy(uid, new EconomyService.EconomyCallback() {
            @Override
            public void onSuccess(java.util.Map<String, Long> values) {
                Long tokens = values.get("tokens");
                Long stars = values.get("stars");
                Long league = values.get("league");
                runOnUiThread(() -> {
                    tvTokens.setText("Tokeni\n" + (tokens == null ? 0 : tokens));
                    tvStars.setText("Zvezde\n" + (stars == null ? 0 : stars));
                    tvLeague.setText("Liga: " + (league == null ? 0 : league));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
