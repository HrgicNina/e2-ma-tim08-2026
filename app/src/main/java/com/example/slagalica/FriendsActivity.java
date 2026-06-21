package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.FriendsService;
import com.example.slagalica.domain.SessionManager;
import com.example.slagalica.model.FriendProfile;
import com.google.firebase.firestore.ListenerRegistration;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FriendsActivity extends AppCompatActivity {

    private EditText etFriendSearch;
    private LinearLayout friendsListContainer;
    private TextView tvFriendsEmpty;
    private FriendsService friendsService;
    private AuthService authService;
    private String myUid = "";
    private ListenerRegistration friendsListener;
    private ActivityResultLauncher<ScanOptions> qrLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isGuestMode()) {
            Toast.makeText(this, "Prijatelji su dostupni samo registrovanim korisnicima.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_friends);
        friendsService = new FriendsService();
        authService = new AuthService();
        myUid = value(authService.getCurrentUserId());
        if (myUid.isEmpty()) {
            Toast.makeText(this, "Niste ulogovani.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etFriendSearch = findViewById(R.id.etFriendSearch);
        friendsListContainer = findViewById(R.id.friendsListContainer);
        tvFriendsEmpty = findViewById(R.id.tvFriendsEmpty);
        Button btnAddFriend = findViewById(R.id.btnAddFriend);
        Button btnScanFriendQr = findViewById(R.id.btnScanFriendQr);

        qrLauncher = registerForActivityResult(new ScanContract(), this::handleQrResult);
        btnAddFriend.setOnClickListener(v -> addFriendByUsername());
        btnScanFriendQr.setOnClickListener(v -> scanQr());
        etFriendSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEND) {
                addFriendByUsername();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (friendsService != null && !myUid.isEmpty()) {
            listenFriends();
        }
    }

    @Override
    protected void onStop() {
        if (friendsListener != null) {
            friendsListener.remove();
            friendsListener = null;
        }
        super.onStop();
    }

    private void listenFriends() {
        if (friendsListener != null) {
            friendsListener.remove();
        }
        friendsListener = friendsService.listenFriends(myUid, new FriendsService.LoadCallback() {
            @Override
            public void onSuccess(List<FriendProfile> friends) {
                runOnUiThread(() -> renderFriends(friends));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void renderFriends(List<FriendProfile> friends) {
        friendsListContainer.removeAllViews();
        if (friends == null || friends.isEmpty()) {
            tvFriendsEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvFriendsEmpty.setVisibility(View.GONE);
        Collections.sort(friends, Comparator.comparing(f -> value(f.username).toLowerCase()));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (FriendProfile friend : friends) {
            View row = inflater.inflate(R.layout.item_friend, friendsListContainer, false);
            TextView tvAvatar = row.findViewById(R.id.tvFriendAvatar);
            TextView tvUsername = row.findViewById(R.id.tvFriendUsername);
            TextView tvMeta = row.findViewById(R.id.tvFriendMeta);
            TextView tvStatus = row.findViewById(R.id.tvFriendStatus);
            Button btnInvite = row.findViewById(R.id.btnInviteFriend);

            tvAvatar.setText(AvatarFrameHelper.symbolForAvatar(friend.avatarId, friend.username));
            AvatarFrameHelper.apply(tvAvatar, friend.avatarFrameId);
            tvUsername.setText(value(friend.username));
            tvMeta.setText("Rang: " + rankLabel(friend.monthlyRank) + " | Zvezde: " + friend.stars + " | " + leagueIcon(friend.league) + " " + leagueName(friend.league));
            tvStatus.setText(statusLabel(friend));
            btnInvite.setEnabled(friend.canInvite());
            btnInvite.setAlpha(friend.canInvite() ? 1f : 0.55f);
            btnInvite.setOnClickListener(v -> inviteFriend(friend));
            friendsListContainer.addView(row);
        }
    }

    private void addFriendByUsername() {
        String username = etFriendSearch.getText().toString().trim();
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, "Unesite korisničko ime.", Toast.LENGTH_SHORT).show();
            return;
        }
        friendsService.addFriendByUsername(myUid, username, new FriendsService.ActionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    etFriendSearch.setText("");
                    Toast.makeText(FriendsActivity.this, "Prijatelj je dodat.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void scanQr() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Skeniraj QR kod prijatelja");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        qrLauncher.launch(options);
    }

    private void handleQrResult(ScanIntentResult result) {
        if (result == null || result.getContents() == null) {
            return;
        }
        friendsService.addFriendFromQr(myUid, result.getContents(), new FriendsService.ActionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(FriendsActivity.this, "Prijatelj je dodat preko QR koda.", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void inviteFriend(FriendProfile friend) {
        Intent intent = new Intent(this, MatchActivity.class);
        intent.putExtra(MatchActivity.EXTRA_AUTO_INVITE_TARGET, value(friend.uid));
        intent.putExtra(MatchActivity.EXTRA_RETURN_TO_FRIENDS_ON_INVITE_DECLINED, true);
        startActivity(intent);
        Toast.makeText(this, "Poziv poslat: " + value(friend.username), Toast.LENGTH_SHORT).show();
    }

    private String statusLabel(FriendProfile friend) {
        if (friend.isInMatch()) {
            return "U partiji";
        }
        if (friend.appActive) {
            return "Dostupan";
        }
        if (friend.isRecentlyLoggedIn()) {
            return "Ulogovan ranije";
        }
        return "Nije ulogovan";
    }

    private String rankLabel(long rank) {
        return rank <= 0L ? "-" : String.valueOf(rank);
    }

    private String leagueName(long league) {
        if (league >= 5) return "Legenda";
        if (league == 4) return "Dijamant";
        if (league == 3) return "Zlato";
        if (league == 2) return "Srebro";
        if (league == 1) return "Bronza";
        return "Početna";
    }

    private String leagueIcon(long league) {
        if (league >= 5) return "🏆";
        if (league == 4) return "◆";
        if (league == 3) return "🥇";
        if (league == 2) return "🥈";
        if (league == 1) return "🥉";
        return "★";
    }

    private String value(String input) {
        return input == null ? "" : input;
    }
}
