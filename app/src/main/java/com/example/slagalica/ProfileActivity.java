package com.example.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.EconomyService;
import com.example.slagalica.domain.PlayerStatsService;
import com.example.slagalica.domain.ResultCallback;
import com.example.slagalica.domain.SessionManager;
import com.example.slagalica.model.PlayerStats;

import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private final String[] gameIds = {"quiz", "connections", "associations", "master", "step", "number"};
    private final String[] gameLabels = {"KZZ", "Spoj", "Aso", "Sko", "Kor", "Broj"};
    private final String[] avatarIds = {"owl", "star", "crown", "bolt", "heart", "diamond"};
    private final String[] avatarSymbols = {"🦉", "⭐", "👑", "⚡", "♥", "♦"};
    private final int[] pieColors = {
            Color.rgb(0, 174, 29),
            Color.rgb(95, 195, 0),
            Color.rgb(164, 218, 0),
            Color.rgb(211, 232, 0),
            Color.rgb(255, 236, 0),
            Color.rgb(255, 196, 0),
            Color.rgb(255, 130, 50),
            Color.rgb(238, 88, 64)
    };

    private TextView tvAvatar;
    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvRegion;
    private TextView tvLeague;
    private TextView tvTokens;
    private TextView tvStars;
    private TextView tvStatsTotalMatches;
    private TextView tvStatsSelectedTitle;
    private TextView tvStatsLegend;
    private ImageView qrInviteView;
    private StatsCircleView viewWinCircle;
    private StatsBarChartView viewGameBars;
    private StatsPieChartView viewStatsPie;
    private Button[] gameButtons;

    private AuthService authService;
    private SessionManager sessionManager;
    private PlayerStatsService statsService;
    private PlayerStats currentStats = new PlayerStats();
    private String currentUsername = "";
    private String currentAvatarId = "owl";
    private String currentAvatarFrameId = "blue";
    private String currentInvitePayload = "";
    private int selectedGameIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        authService = new AuthService();
        sessionManager = new SessionManager(this);
        statsService = new PlayerStatsService();

        bindViews();
        bindActions();
        loadProfile();
        loadEconomy();
        loadStats();
    }

    private void bindViews() {
        tvAvatar = findViewById(R.id.tvProfileAvatar);
        tvUsername = findViewById(R.id.tvProfileUsername);
        tvEmail = findViewById(R.id.tvProfileEmail);
        tvRegion = findViewById(R.id.tvProfileRegion);
        tvLeague = findViewById(R.id.tvProfileLeague);
        tvTokens = findViewById(R.id.tvProfileTokens);
        tvStars = findViewById(R.id.tvProfileStars);
        tvStatsTotalMatches = findViewById(R.id.tvStatsTotalMatches);
        tvStatsSelectedTitle = findViewById(R.id.tvStatsSelectedTitle);
        tvStatsLegend = findViewById(R.id.tvStatsLegend);
        qrInviteView = findViewById(R.id.qrInviteView);
        viewWinCircle = findViewById(R.id.viewWinCircle);
        viewGameBars = findViewById(R.id.viewGameBars);
        viewStatsPie = findViewById(R.id.viewStatsPie);
        gameButtons = new Button[]{
                findViewById(R.id.btnStatsQuiz),
                findViewById(R.id.btnStatsConnections),
                findViewById(R.id.btnStatsAssociations),
                findViewById(R.id.btnStatsMaster),
                findViewById(R.id.btnStatsStep),
                findViewById(R.id.btnStatsNumber)
        };
    }

    private void bindActions() {
        findViewById(R.id.btnAvatarChange).setOnClickListener(v -> showAvatarDialog());
        findViewById(R.id.btnProfileLogout).setOnClickListener(v -> logout());
        qrInviteView.setOnClickListener(v -> showInviteQrDialog());
        for (int i = 0; i < gameButtons.length; i++) {
            final int index = i;
            gameButtons[i].setOnClickListener(v -> {
                selectedGameIndex = index;
                renderStats();
            });
        }
    }

    private void loadProfile() {
        String email = authService.getCurrentUserEmail();
        tvEmail.setText(email == null ? "" : email);
        authService.getCurrentUserProfile(profile -> runOnUiThread(() -> {
            currentUsername = value(profile.username, "Korisnik");
            currentAvatarId = value(profile.avatarId, "owl");
            currentAvatarFrameId = value(profile.avatarFrameId, "blue");
            tvUsername.setText(currentUsername);
            tvEmail.setText(value(profile.email, value(authService.getCurrentUserEmail(), "")));
            tvRegion.setText("Region: " + value(profile.region, "-"));
            tvAvatar.setText(symbolForAvatar(currentAvatarId, currentUsername));
            AvatarFrameHelper.apply(tvAvatar, currentAvatarFrameId);
            String uid = authService.getCurrentUserId();
            currentInvitePayload = "slagalica://friend?uid=" + value(uid, "") + "&username=" + Uri.encode(currentUsername);
            qrInviteView.setImageBitmap(QrCodeGenerator.create(currentInvitePayload, dp(180)));
        }));
    }

    private void showInviteQrDialog() {
        if (currentInvitePayload.isEmpty()) {
            Toast.makeText(this, "QR kod se jos ucitava.", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView enlargedQr = new ImageView(this);
        enlargedQr.setAdjustViewBounds(true);
        enlargedQr.setPadding(dp(20), dp(20), dp(20), dp(20));
        enlargedQr.setImageBitmap(QrCodeGenerator.create(currentInvitePayload, dp(320)));
        enlargedQr.setContentDescription(getString(R.string.profile_qr_description));

        new AlertDialog.Builder(this)
                .setTitle("QR kod za dodavanje prijatelja")
                .setView(enlargedQr)
                .setPositiveButton("Zatvori", null)
                .show();
    }

    private void loadEconomy() {
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        new EconomyService().getEconomy(uid, new EconomyService.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                Long tokens = values.get("tokens");
                Long stars = values.get("stars");
                Long league = values.get("league");
                runOnUiThread(() -> {
                    tvTokens.setText("Tokeni " + safe(tokens));
                    tvStars.setText("Zvezde " + safe(stars));
                    tvLeague.setText(leagueIcon(safe(league)) + " " + leagueName(safe(league)));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadStats() {
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            renderStats();
            return;
        }
        statsService.loadStats(uid, new PlayerStatsService.StatsCallback() {
            @Override
            public void onSuccess(PlayerStats stats) {
                runOnUiThread(() -> {
                    currentStats = stats;
                    renderStats();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                    renderStats();
                });
            }
        });
    }

    private void renderStats() {
        int winPercent = percent(currentStats.wins, currentStats.wins + currentStats.losses);
        viewWinCircle.setPercent(winPercent);
        tvStatsTotalMatches.setText(currentStats.matchesTotal + " partija");
        float[] bars = new float[gameIds.length];
        for (int i = 0; i < gameIds.length; i++) {
            bars[i] = percentFloat(currentStats.value(gameIds[i]), currentStats.maxValue(gameIds[i]));
            gameButtons[i].setAlpha(i == selectedGameIndex ? 1f : 0.72f);
        }
        viewGameBars.setData(bars, gameLabels, selectedGameIndex);
        renderSelectedGame();
    }

    private void renderSelectedGame() {
        String gameId = gameIds[selectedGameIndex];
        if ("quiz".equals(gameId)) {
            renderPie("Ko zna zna", new long[]{currentStats.quizCorrect, currentStats.quizNoAnswer, currentStats.quizWrong},
                    new String[]{"tačno", "bez odgovora", "netačno"});
        } else if ("connections".equals(gameId)) {
            renderPie("Spojnice", new long[]{currentStats.connectionsMatched, currentStats.connectionsMissed},
                    new String[]{"spojen par", "nespojen par"});
        } else if ("associations".equals(gameId)) {
            long[] values = new long[6];
            String[] labels = new String[]{"0 rešenja", "1 rešenje", "2 rešenja", "3 rešenja", "4 rešenja", "cela asocijacija"};
            System.arraycopy(currentStats.associationsSolvedCounts, 0, values, 0, values.length);
            renderPie("Asocijacije", values, labels);
        } else if ("master".equals(gameId)) {
            long[] values = new long[]{currentStats.mastermindAttempts[1], currentStats.mastermindAttempts[2],
                    currentStats.mastermindAttempts[3], currentStats.mastermindAttempts[4],
                    currentStats.mastermindAttempts[5], currentStats.mastermindAttempts[6],
                    currentStats.mastermindAttempts[0]};
            renderPie("Skočko", values, new String[]{"1. pokušaj", "2. pokušaj", "3. pokušaj", "4. pokušaj", "5. pokušaj", "6. pokušaj", "bez pogotka"});
        } else if ("step".equals(gameId)) {
            long[] values = new long[]{currentStats.stepSolvedAt[1], currentStats.stepSolvedAt[2], currentStats.stepSolvedAt[3],
                    currentStats.stepSolvedAt[4], currentStats.stepSolvedAt[5], currentStats.stepSolvedAt[6],
                    currentStats.stepSolvedAt[7], currentStats.stepSolvedAt[0]};
            renderPie("Korak po korak", values, new String[]{"korak 1", "korak 2", "korak 3", "korak 4", "korak 5", "korak 6", "korak 7", "bez pogotka"});
        } else {
            renderPie("Moj broj", new long[]{currentStats.numberExact, currentStats.numberDistance1To4,
                            currentStats.numberDistance5To9, currentStats.numberDistance10To19,
                            currentStats.numberDistance20To49, currentStats.numberDistance50To99,
                            currentStats.numberDistance100Plus},
                    new String[]{"tačan broj", "1-4", "5-9", "10-19", "20-49", "50-99", "100+"});
        }
    }

    private void renderPie(String title, long[] values, String[] labels) {
        tvStatsSelectedTitle.setText(title);
        long total = 0L;
        for (long value : values) {
            total += Math.max(0L, value);
        }
        float[] slices = new float[values.length];
        StringBuilder legend = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            slices[i] = Math.max(0L, values[i]);
            if (i > 0) {
                legend.append('\n');
            }
            legend.append(labels[i]).append(": ").append(percent(values[i], total)).append("%");
        }
        if (total == 0L) {
            slices = new float[]{1f};
            legend = new StringBuilder("Nema odigranih podataka");
        }
        viewStatsPie.setData(slices, pieColors);
        tvStatsLegend.setText(legend.toString());
    }

    private void showAvatarDialog() {
        CharSequence[] labels = new CharSequence[avatarIds.length];
        for (int i = 0; i < avatarIds.length; i++) {
            labels[i] = avatarSymbols[i] + "  Avatar " + (i + 1);
        }
        new AlertDialog.Builder(this)
                .setTitle("Izmeni avatar")
                .setItems(labels, (dialog, which) -> saveAvatar(avatarIds[which]))
                .show();
    }

    private void saveAvatar(String avatarId) {
        authService.updateAvatar(avatarId, currentAvatarFrameId, new ResultCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    currentAvatarId = avatarId;
                    tvAvatar.setText(symbolForAvatar(currentAvatarId, currentUsername));
                    AvatarFrameHelper.apply(tvAvatar, currentAvatarFrameId);
                    Toast.makeText(ProfileActivity.this, "Avatar je sacuvan.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void logout() {
        sessionManager.clearGuestMode();
        authService.logout(new ResultCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String symbolForAvatar(String avatarId, String username) {
        for (int i = 0; i < avatarIds.length; i++) {
            if (avatarIds[i].equals(avatarId)) {
                return avatarSymbols[i];
            }
        }
        String fallback = value(username, "U").trim();
        return fallback.isEmpty() ? "U" : fallback.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String leagueName(long league) {
        if (league >= 5) return "Legenda";
        if (league == 4) return "Dijamant liga";
        if (league == 3) return "Zlatna liga";
        if (league == 2) return "Srebrna liga";
        if (league == 1) return "Bronzana liga";
        return "Početna liga";
    }

    private String leagueIcon(long league) {
        if (league >= 5) return "🏆";
        if (league == 4) return "◆";
        if (league == 3) return "🥇";
        if (league == 2) return "🥈";
        if (league == 1) return "🥉";
        return "★";
    }

    private int percent(long part, long total) {
        if (total <= 0L) {
            return 0;
        }
        return Math.round(part * 100f / total);
    }

    private float percentFloat(long part, long total) {
        if (total <= 0L) {
            return 0f;
        }
        return Math.max(0f, Math.min(100f, part * 100f / total));
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
