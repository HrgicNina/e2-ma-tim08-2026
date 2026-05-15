package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.NotificationChannelHelper;
import com.example.slagalica.domain.SessionManager;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        NotificationChannelHelper.createChannels(this);

        AuthService authService = new AuthService(new FirebaseAuthRepository());
        SessionManager sessionManager = new SessionManager(this);

        TextView btnProfile = findViewById(R.id.btnOpenProfile);
        TextView btnSettings = findViewById(R.id.btnOpenSettings);
        TextView btnNotifications = findViewById(R.id.btnOpenNotifications);
        TextView tvHomeUsername = findViewById(R.id.tvHomeUsername);
        TextView tvHomeTokens = findViewById(R.id.tvHomeTokens);
        TextView tvHomeStars = findViewById(R.id.tvHomeStars);
        TextView tvHomeLeague = findViewById(R.id.tvHomeLeague);
        Button btnStartGame = findViewById(R.id.btnStartGame);
        View friend1 = findViewById(R.id.friendItem1);
        View friend2 = findViewById(R.id.friendItem2);
        View friend3 = findViewById(R.id.friendItem3);
        TextView tvFriendsLabel = findViewById(R.id.tvFriendsLabel);
        Button btnGuestRegister = findViewById(R.id.btnGuestRegister);

        btnNotifications.setText("\uD83D\uDD14");
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
            tvHomeTokens.setText(R.string.home_tokens_guest);
            tvHomeStars.setText(R.string.home_stars_guest);
            tvHomeLeague.setText(R.string.home_league_guest);

            btnStartGame.setVisibility(View.GONE);
            tvFriendsLabel.setVisibility(View.GONE);
            friend1.setVisibility(View.GONE);
            friend2.setVisibility(View.GONE);
            friend3.setVisibility(View.GONE);

            btnNotifications.setVisibility(View.GONE);
            btnSettings.setVisibility(View.GONE);
            btnProfile.setVisibility(View.GONE);
            tvHomeUsername.setVisibility(View.GONE);
            btnGuestRegister.setVisibility(View.VISIBLE);
        } else {
            tvHomeTokens.setText(R.string.home_tokens_value);
            tvHomeStars.setText(R.string.home_stars_value);
            tvHomeLeague.setText(R.string.home_league_value);
            btnGuestRegister.setVisibility(View.GONE);
        }

        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
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
}
