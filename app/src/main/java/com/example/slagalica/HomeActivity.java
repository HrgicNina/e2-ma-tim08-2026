package com.example.slagalica;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.data.NotificationsRepository;
import com.example.slagalica.data.PlayerEconomyRepository;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.NotificationChannelHelper;
import com.example.slagalica.domain.SessionManager;
import com.example.slagalica.model.AppNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        NotificationChannelHelper.createChannels(this);

        AuthService authService = new AuthService(new FirebaseAuthRepository());
        SessionManager sessionManager = new SessionManager(this);
        PlayerEconomyRepository economyRepository = new PlayerEconomyRepository();
        NotificationsRepository notificationsRepository = new NotificationsRepository();

        TextView btnProfile = findViewById(R.id.btnOpenProfile);
        TextView btnOpenChat = findViewById(R.id.btnOpenChat);
        TextView btnSettings = findViewById(R.id.btnOpenSettings);
        TextView btnNotifications = findViewById(R.id.btnOpenNotifications);
        TextView tvHomeUsername = findViewById(R.id.tvHomeUsername);
        TextView tvHomeTokens = findViewById(R.id.tvHomeTokens);
        TextView tvHomeStars = findViewById(R.id.tvHomeStars);
        TextView tvHomeLeague = findViewById(R.id.tvHomeLeague);
        View homeStatsRow = findViewById(R.id.homeStatsRow);
        Button btnStartGame = findViewById(R.id.btnStartGame);
        View friend1 = findViewById(R.id.friendItem1);
        View friend2 = findViewById(R.id.friendItem2);
        View friend3 = findViewById(R.id.friendItem3);
        TextView tvFriendsLabel = findViewById(R.id.tvFriendsLabel);
        Button btnGuestRegister = findViewById(R.id.btnGuestRegister);

        btnNotifications.setText("\uD83D\uDD14");
        btnOpenChat.setText("\uD83D\uDCAC");
        btnSettings.setText("\u2699\uFE0F");
        authService.getCurrentUsername(username -> {
            if (username != null && !username.trim().isEmpty()) {
                btnProfile.setText(username.substring(0, 1).toUpperCase());
                tvHomeUsername.setText(username);
            } else {
                btnProfile.setText("U");
                tvHomeUsername.setText("korisnik");
            }
        });

        if (sessionManager.isGuestMode()) {
            homeStatsRow.setVisibility(View.GONE);

            btnStartGame.setVisibility(View.VISIBLE);
            tvFriendsLabel.setVisibility(View.GONE);
            friend1.setVisibility(View.GONE);
            friend2.setVisibility(View.GONE);
            friend3.setVisibility(View.GONE);

            btnNotifications.setVisibility(View.GONE);
            btnOpenChat.setVisibility(View.GONE);
            btnSettings.setVisibility(View.GONE);
            btnProfile.setVisibility(View.GONE);
            tvHomeUsername.setVisibility(View.GONE);
            btnGuestRegister.setVisibility(View.VISIBLE);
        } else {
            homeStatsRow.setVisibility(View.VISIBLE);
            tvHomeTokens.setText(R.string.home_tokens_value);
            tvHomeStars.setText(R.string.home_stars_value);
            tvHomeLeague.setText(R.string.home_league_value);
            btnGuestRegister.setVisibility(View.GONE);
            grantDailyTokensOnStartup(economyRepository, tvHomeTokens, tvHomeStars, tvHomeLeague);
            showUnreadSystemNotificationsOnStartup(notificationsRepository);
        }

        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnOpenChat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)));
        btnNotifications.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        btnSettings.setOnClickListener(v -> showSettingsMenu(btnSettings));

        btnStartGame.setOnClickListener(v -> {
            Intent intent = new Intent(this, MatchActivity.class);
            intent.putExtra("auto_start_queue", true);
            startActivity(intent);
        });

        friend1.setOnClickListener(v -> sendInviteToHardcodedFriend("marko"));
        friend2.setOnClickListener(v -> sendInviteToHardcodedFriend("ana"));
        friend3.setOnClickListener(v -> sendInviteToHardcodedFriend("milica"));

        btnGuestRegister.setOnClickListener(v -> {
            sessionManager.clearGuestMode();
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });

    }

    private void grantDailyTokensOnStartup(
            PlayerEconomyRepository economyRepository,
            TextView tvHomeTokens,
            TextView tvHomeStars,
            TextView tvHomeLeague
    ) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        economyRepository.grantDailyTokensIfNeeded(user.getUid(), new PlayerEconomyRepository.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                economyRepository.getEconomy(user.getUid(), new PlayerEconomyRepository.EconomyCallback() {
                    @Override
                    public void onSuccess(Map<String, Long> refreshed) {
                        Long tokens = refreshed.get("tokens");
                        Long stars = refreshed.get("stars");
                        Long league = refreshed.get("league");
                        runOnUiThread(() -> {
                            tvHomeTokens.setText("Tokeni\n" + (tokens == null ? 0 : tokens));
                            tvHomeStars.setText("Zvezde\n" + (stars == null ? 0 : stars));
                            tvHomeLeague.setText("Liga\n" + (league == null ? 0 : league));
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendInviteToHardcodedFriend(String username) {
        Intent intent = new Intent(this, MatchActivity.class);
        intent.putExtra("auto_invite_target", username);
        startActivity(intent);
        Toast.makeText(this, "Saljem poziv igracu: " + username, Toast.LENGTH_SHORT).show();
    }

    private void showSettingsMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "Promeni lozinku");
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startActivity(new Intent(this, ResetPasswordActivity.class));
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showUnreadSystemNotificationsOnStartup(NotificationsRepository notificationsRepository) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        notificationsRepository.loadForUser(user.getUid(), false, new NotificationsRepository.LoadCallback() {
            @Override
            public void onSuccess(List<AppNotification> items) {
                runOnUiThread(() -> {
                    for (AppNotification item : items) {
                        if (item.localShown) {
                            continue;
                        }
                        showLocalSystemNotification(item);
                        notificationsRepository.markAsLocalShown(user.getUid(), item.id, new NotificationsRepository.ActionCallback() {
                            @Override
                            public void onSuccess() {
                            }

                            @Override
                            public void onError(String message) {
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void showLocalSystemNotification(AppNotification item) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String channelId = NotificationChannelHelper.CHANNEL_OTHER;
        if ("chat".equalsIgnoreCase(item.type)) {
            channelId = NotificationChannelHelper.CHANNEL_CHAT;
        } else if ("ranking".equalsIgnoreCase(item.type)) {
            channelId = NotificationChannelHelper.CHANNEL_RANKING;
        } else if ("rewards".equalsIgnoreCase(item.type)) {
            channelId = NotificationChannelHelper.CHANNEL_REWARDS;
        }

        Intent openIntent = new Intent(this, NotificationsActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                ("notif_open_" + item.id).hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(item.title == null || item.title.trim().isEmpty() ? "Novo obavestenje" : item.title)
                .setContentText(item.message == null ? "" : item.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(item.message == null ? "" : item.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this)
                .notify(("local_" + item.id).hashCode(), builder.build());
    }
}
