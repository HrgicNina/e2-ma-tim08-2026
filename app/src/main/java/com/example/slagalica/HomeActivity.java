package com.example.slagalica;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.BackgroundNotificationScheduler;
import com.example.slagalica.domain.EconomyService;
import com.example.slagalica.domain.FcmTokenRegistrar;
import com.example.slagalica.domain.LeaderboardService;
import com.example.slagalica.domain.NotificationChannelHelper;
import com.example.slagalica.domain.NotificationService;
import com.example.slagalica.domain.RegionsService;
import com.example.slagalica.domain.SessionManager;
import com.example.slagalica.model.AppNotification;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_REWARD_NOTIFICATION_ID = "open_reward_notification_id";
    private static final int REQUEST_CODE_NOTIFICATIONS = 1201;
    private SessionManager sessionManager;
    private AuthService authService;
    private EconomyService economyService;
    private TextView tvHomeTokens;
    private TextView tvHomeStars;
    private TextView tvHomeLeague;
    private TextView btnProfile;
    private ListenerRegistration economyListener;
    private final LeaderboardService leaderboardService = new LeaderboardService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        NotificationChannelHelper.createChannels(this);

        authService = new AuthService();
        sessionManager = new SessionManager(this);
        economyService = new EconomyService();
        NotificationService notificationService = new NotificationService();
        String rewardNotificationIdFromIntent = getIntent().getStringExtra(EXTRA_OPEN_REWARD_NOTIFICATION_ID);

        btnProfile = findViewById(R.id.btnOpenProfile);
        TextView btnOpenChat = findViewById(R.id.btnOpenChat);
        TextView btnOpenRankings = findViewById(R.id.btnOpenRankings);
        TextView btnSettings = findViewById(R.id.btnOpenSettings);
        TextView btnNotifications = findViewById(R.id.btnOpenNotifications);
        TextView tvHomeUsername = findViewById(R.id.tvHomeUsername);
        tvHomeTokens = findViewById(R.id.tvHomeTokens);
        tvHomeStars = findViewById(R.id.tvHomeStars);
        tvHomeLeague = findViewById(R.id.tvHomeLeague);
        View homeStatsRow = findViewById(R.id.homeStatsRow);
        Button btnStartGame = findViewById(R.id.btnStartGame);
        Button btnOpenRegions = findViewById(R.id.btnOpenRegions);
        View friendsContainer = findViewById(R.id.friendsContainer);
        Button btnOpenFriends = findViewById(R.id.btnOpenFriends);
        Button btnGuestRegister = findViewById(R.id.btnGuestRegister);

        btnNotifications.setText("\uD83D\uDD14");
        btnOpenChat.setText("\uD83D\uDCAC");
        btnOpenRankings.setText("\uD83C\uDFC6");
        btnSettings.setText("\u2699\uFE0F");
        refreshHomeProfile();

        if (sessionManager.isGuestMode()) {
            homeStatsRow.setVisibility(View.GONE);

            btnStartGame.setVisibility(View.VISIBLE);
            btnOpenRegions.setVisibility(View.GONE);
            friendsContainer.setVisibility(View.GONE);

            btnNotifications.setVisibility(View.GONE);
            btnOpenChat.setVisibility(View.GONE);
            btnOpenRankings.setVisibility(View.GONE);
            btnSettings.setVisibility(View.GONE);
            btnProfile.setVisibility(View.GONE);
            tvHomeUsername.setVisibility(View.GONE);
            btnGuestRegister.setVisibility(View.VISIBLE);
        } else {
            requestPostNotificationsIfNeeded();
            FcmTokenRegistrar.sync(this);
            BackgroundNotificationScheduler.ensureScheduled(this);
            homeStatsRow.setVisibility(View.VISIBLE);
            tvHomeTokens.setText(R.string.home_tokens_value);
            tvHomeStars.setText(R.string.home_stars_value);
            tvHomeLeague.setText(R.string.home_league_value);
            btnGuestRegister.setVisibility(View.GONE);
            btnOpenRegions.setVisibility(View.VISIBLE);
            processRegionAwardsQuietly();
            grantDailyTokensOnStartup(economyService, tvHomeTokens, tvHomeStars, tvHomeLeague);
            bootstrapNotificationsAndRewards(notificationService, rewardNotificationIdFromIntent);
        }

        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnOpenChat.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegionsActivity.class);
            intent.putExtra(RegionsActivity.EXTRA_CHAT_MODE, true);
            startActivity(intent);
        });
        btnOpenRankings.setOnClickListener(v -> startActivity(new Intent(this, RankingsActivity.class)));
        btnOpenRegions.setOnClickListener(v -> startActivity(new Intent(this, RegionsActivity.class)));
        btnNotifications.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        btnSettings.setOnClickListener(v -> showSettingsMenu(btnSettings));
        btnOpenFriends.setOnClickListener(v -> startActivity(new Intent(this, FriendsActivity.class)));

        btnStartGame.setOnClickListener(v -> {
            Intent intent = new Intent(this, MatchActivity.class);
            intent.putExtra("auto_start_queue", true);
            startActivity(intent);
        });

        btnGuestRegister.setOnClickListener(v -> {
            sessionManager.clearGuestMode();
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHomeProfile();
        refreshEconomyOnHomeIfRegistered();
    }

    private void refreshHomeProfile() {
        if (btnProfile == null || sessionManager == null || sessionManager.isGuestMode()) {
            return;
        }
        authService.getCurrentUserProfile(profile -> runOnUiThread(() -> {
            String username = profile.username == null ? "" : profile.username.trim();
            btnProfile.setText(AvatarFrameHelper.symbolForAvatar(profile.avatarId, username));
            AvatarFrameHelper.apply(btnProfile, profile.avatarFrameId);
            TextView usernameView = findViewById(R.id.tvHomeUsername);
            usernameView.setText(username.isEmpty() ? "korisnik" : username);
        }));
    }

    @Override
    protected void onStart() {
        super.onStart();
        startEconomyRealtimeListenerIfRegistered();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopEconomyRealtimeListener();
    }

    private void showRewardDialogIfNeeded(NotificationService notificationService) {
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        notificationService.load(NotificationService.Filter.UNREAD, new NotificationService.UiLoadCallback() {
            @Override
            public void onSuccess(List<AppNotification> items) {
                if (items == null || items.isEmpty()) {
                    return;
                }
                for (AppNotification item : items) {
                    if (!"rewards".equalsIgnoreCase(item.type) || item.read) {
                        continue;
                    }
                    runOnUiThread(() -> openRewardDialog(item, notificationService, uid));
                    return;
                }
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void showSpecificRewardDialogIfPresent(NotificationService notificationService, String notificationId) {
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        notificationService.load(NotificationService.Filter.UNREAD, new NotificationService.UiLoadCallback() {
            @Override
            public void onSuccess(List<AppNotification> items) {
                if (items == null || items.isEmpty()) {
                    return;
                }
                for (AppNotification item : items) {
                    if (!notificationId.equals(item.id) || !"rewards".equalsIgnoreCase(item.type)) {
                        continue;
                    }
                    runOnUiThread(() -> openRewardDialog(item, notificationService, uid));
                    return;
                }
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void openRewardDialog(AppNotification item, NotificationService notificationService, String uid) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(18), dp(24), dp(10));
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView trophy = new TextView(this);
        trophy.setText("\uD83C\uDFC6");
        trophy.setTextSize(54f);
        trophy.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView message = new TextView(this);
        message.setText(item.message);
        message.setTextSize(16f);
        message.setPadding(0, dp(10), 0, dp(2));
        message.setGravity(Gravity.CENTER);

        box.addView(trophy);
        box.addView(message);

        PropertyValuesHolder sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f, 1f);
        PropertyValuesHolder sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f, 1f);
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(trophy, sx, sy);
        pulse.setDuration(800);
        pulse.setRepeatCount(3);
        pulse.start();

        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
        tg.startTone(ToneGenerator.TONE_PROP_ACK, 220);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nagrada")
                .setView(box)
                .setPositiveButton("Preuzmi", (d, w) -> {
                    notificationService.markAsRead(item.id, new NotificationService.UiActionCallback() {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
                    tg.release();
                })
                .create();
        dialog.setOnDismissListener(d -> {
            try {
                tg.release();
            } catch (Exception ignored) {
            }
        });
        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void grantDailyTokensOnStartup(
            EconomyService economyService,
            TextView tvHomeTokens,
            TextView tvHomeStars,
            TextView tvHomeLeague
    ) {
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        economyService.grantDailyTokensIfNeeded(uid, new EconomyService.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                economyService.getEconomy(uid, new EconomyService.EconomyCallback() {
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

    private void refreshEconomyOnHomeIfRegistered() {
        if (sessionManager == null || sessionManager.isGuestMode()) {
            return;
        }
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        economyService.getEconomy(uid, new EconomyService.EconomyCallback() {
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
            }
        });
    }

    private void startEconomyRealtimeListenerIfRegistered() {
        if (sessionManager == null || sessionManager.isGuestMode()) {
            return;
        }
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        stopEconomyRealtimeListener();
        economyListener = economyService.observeEconomy(uid, new EconomyService.EconomyObserver() {
            @Override
            public void onChanged(Map<String, Long> values) {
                Long tokens = values.get("tokens");
                Long stars = values.get("stars");
                Long league = values.get("league");
                runOnUiThread(() -> {
                    tvHomeTokens.setText("Tokeni\n" + (tokens == null ? 0 : tokens));
                    tvHomeStars.setText("Zvezde\n" + (stars == null ? 0 : stars));
                    tvHomeLeague.setText("Liga\n" + (league == null ? 0 : league));
                });
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void stopEconomyRealtimeListener() {
        if (economyListener != null) {
            economyListener.remove();
            economyListener = null;
        }
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

    private void showUnreadSystemNotificationsOnStartup(NotificationService notificationService) {
        String uid = authService.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        notificationService.load(NotificationService.Filter.UNREAD, new NotificationService.UiLoadCallback() {
            @Override
            public void onSuccess(List<AppNotification> items) {
                runOnUiThread(() -> {
                    boolean shownInAppPopup = false;
                    for (AppNotification item : items) {
                        if (item.localShown) {
                            continue;
                        }
                        if (!isOfflineInviteNotification(item)) {
                            showLocalSystemNotification(item);
                            if (!shownInAppPopup && shouldShowImmediateInAppPopup(item)) {
                                showImmediateInAppPopup(item, notificationService);
                                shownInAppPopup = true;
                            }
                        }
                        notificationService.markAsLocalShown(item.id, new NotificationService.UiActionCallback() {
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

    private boolean shouldShowImmediateInAppPopup(AppNotification item) {
        if (item == null) {
            return false;
        }
        return !"rewards".equalsIgnoreCase(item.type) && !isOfflineInviteNotification(item);
    }

    private void processRegionAwardsQuietly() {
        new RegionsService().processPreviousMonthlyRegionAwards(new RegionsService.ActionCallback() {
            @Override
            public void onSuccess() {
                refreshHomeProfile();
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private boolean isOfflineInviteNotification(AppNotification item) {
        return item != null && "open_match".equalsIgnoreCase(value(item.actionType));
    }

    private String value(String input) {
        return input == null ? "" : input;
    }

    private void showImmediateInAppPopup(AppNotification item, NotificationService notificationService) {
        String title = (item.title == null || item.title.trim().isEmpty()) ? "Novo obavestenje" : item.title;
        String message = item.message == null ? "" : item.message;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(14));
        card.setBackgroundResource(R.drawable.profile_card_bg);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(notificationBadge(item.type));
        tvBadge.setTextSize(22f);
        tvBadge.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.app_on_surface));
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        tvTitle.setPadding(0, dp(6), 0, dp(4));

        TextView tvMessage = new TextView(this);
        tvMessage.setText(message);
        tvMessage.setTextColor(ContextCompat.getColor(this, R.color.app_on_surface));
        tvMessage.setTextSize(16f);
        tvMessage.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(16), 0, 0);

        Button btnLater = new Button(this);
        btnLater.setText("Kasnije");
        btnLater.setAllCaps(false);
        btnLater.setTextSize(15f);
        btnLater.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.app_surface_light_blue));
        btnLater.setTextColor(ContextCompat.getColor(this, R.color.app_on_surface));

        Button btnOpen = new Button(this);
        btnOpen.setText("Otvori");
        btnOpen.setAllCaps(false);
        btnOpen.setTextSize(15f);
        btnOpen.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.app_primary_blue));
        btnOpen.setTextColor(ContextCompat.getColor(this, R.color.app_on_primary));

        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        laterParams.rightMargin = dp(10);
        actions.addView(btnLater, laterParams);
        actions.addView(btnOpen);

        card.addView(tvBadge);
        card.addView(tvTitle);
        card.addView(tvMessage);
        card.addView(actions);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(card)
                .create();

        btnLater.setOnClickListener(v -> dialog.dismiss());
        btnOpen.setOnClickListener(v -> {
            if (item.id != null && !item.id.trim().isEmpty()) {
                notificationService.markAsRead(item.id, new NotificationService.UiActionCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
            }
            Intent openIntent = NotificationIntentRouter.buildOpenIntent(
                    this,
                    item.type,
                    item.actionType,
                    item.actionPayload,
                    item.id
            );
            dialog.dismiss();
            startActivity(openIntent);
        });

        dialog.show();
    }

    private String notificationBadge(String type) {
        if ("chat".equalsIgnoreCase(type)) {
            return "\uD83D\uDCAC";
        }
        if ("ranking".equalsIgnoreCase(type)) {
            return "\uD83C\uDFC6";
        }
        if ("rewards".equalsIgnoreCase(type)) {
            return "\uD83C\uDFC1";
        }
        return "\uD83D\uDD14";
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

        Intent openIntent = NotificationIntentRouter.buildOpenIntent(
                this,
                item.type,
                item.actionType,
                item.actionPayload,
                item.id
        );
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

    private void requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_NOTIFICATIONS
        );
    }

    private void bootstrapNotificationsAndRewards(NotificationService notificationService, String rewardNotificationIdFromIntent) {
        leaderboardService.processCycleRolloverAndRewards(new LeaderboardService.ActionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> openRewardAndSystemNotifications(notificationService, rewardNotificationIdFromIntent));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> openRewardAndSystemNotifications(notificationService, rewardNotificationIdFromIntent));
            }
        });
    }

    private void openRewardAndSystemNotifications(NotificationService notificationService, String rewardNotificationIdFromIntent) {
        if (rewardNotificationIdFromIntent != null && !rewardNotificationIdFromIntent.trim().isEmpty()) {
            showSpecificRewardDialogIfPresent(notificationService, rewardNotificationIdFromIntent);
        } else {
            showRewardDialogIfNeeded(notificationService);
        }
        showUnreadSystemNotificationsOnStartup(notificationService);
    }
}
