package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.data.PlayerEconomyRepository;
import com.example.slagalica.domain.MatchRealtimeClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.util.Map;

public class MatchActivity extends AppCompatActivity {

    public static final String ACTION_GAME_COMMAND = "com.example.slagalica.GAME_COMMAND";
    public static final String ACTION_GAME_EVENT = "com.example.slagalica.GAME_EVENT";
    public static final String EXTRA_ROOM_ID = "room_id";
    public static final String EXTRA_GAME = "game";
    public static final String EXTRA_EVENT = "event";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_AUTO_START_QUEUE = "auto_start_queue";
    public static final String EXTRA_AUTO_INVITE_TARGET = "auto_invite_target";

    private static final String[] GAME_NAMES = {
            "Korak po korak",
            "Skocko",
            "Moj broj",
            "Ko zna zna",
            "Spojnice",
            "Asocijacije"
    };

    private static final String WS_URL = "ws://10.0.2.2:8080";

    private TextView tvMatchStage;
    private TextView tvMatchInfo;
    private TextView tvMatchScore;
    private TextView tvMatchTokens;
    private TextView tvMatchStars;
    private TextView tvMatchLeague;
    private TextView[] gameSteps;
    private Button btnOpenCurrentGame;
    private Button btnNextGame;
    private Button btnSubmitScore;
    private Button btnFindRandom;
    private Button btnCancelQueue;
    private Button btnInviteFriend;
    private EditText etInviteTarget;
    private ActivityResultLauncher<Intent> gameLauncher;

    private int currentGameIndex = 0;
    private int myScore = 0;
    private int opponentScore = 0;
    private long tokens = 0;
    private long stars = 0;
    private long league = 0;

    private boolean inRoom = false;
    private boolean queueing = false;
    private boolean wsConnected = false;
    private boolean wsAuthenticated = false;
    private boolean pendingJoinRequest = false;
    private boolean friendlyRoom = false;
    private boolean rankedTokenReserved = false;
    private String roomId = null;
    private String myUid = null;
    private String myUsername = "";
    private String opponentUid = null;
    private String opponentUsername = "-";
    private int myPlayerNumber = 1;
    private boolean resultApplied = false;
    private boolean autoStartQueueRequested = false;
    private String autoInviteTarget = null;

    private final FirebaseAuthRepository authRepository = new FirebaseAuthRepository();
    private final PlayerEconomyRepository economyRepository = new PlayerEconomyRepository();
    private final MatchRealtimeClient realtimeClient = new MatchRealtimeClient();
    private final BroadcastReceiver gameCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_GAME_COMMAND.equals(intent.getAction())) {
                return;
            }
            if (!inRoom || roomId == null) {
                return;
            }
            String targetRoomId = intent.getStringExtra(EXTRA_ROOM_ID);
            if (TextUtils.isEmpty(targetRoomId) || !roomId.equals(targetRoomId)) {
                return;
            }
            String game = intent.getStringExtra(EXTRA_GAME);
            String event = intent.getStringExtra(EXTRA_EVENT);
            String dataRaw = intent.getStringExtra(EXTRA_DATA);
            if (TextUtils.isEmpty(game) || TextUtils.isEmpty(event)) {
                return;
            }
            JSONObject data;
            try {
                data = TextUtils.isEmpty(dataRaw) ? new JSONObject() : new JSONObject(dataRaw);
            } catch (Exception e) {
                data = new JSONObject();
            }
            realtimeClient.sendGameEvent(roomId, game, event, data);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Partija je trenutno dostupna samo registrovanim korisnicima.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        myUid = user.getUid();
        autoStartQueueRequested = getIntent().getBooleanExtra(EXTRA_AUTO_START_QUEUE, false);
        autoInviteTarget = getIntent().getStringExtra(EXTRA_AUTO_INVITE_TARGET);

        tvMatchStage = findViewById(R.id.tvMatchStage);
        tvMatchInfo = findViewById(R.id.tvMatchInfo);
        tvMatchScore = findViewById(R.id.tvMatchScore);
        tvMatchTokens = findViewById(R.id.tvMatchTokens);
        tvMatchStars = findViewById(R.id.tvMatchStars);
        tvMatchLeague = findViewById(R.id.tvMatchLeague);
        btnOpenCurrentGame = findViewById(R.id.btnMatchOpenGame);
        btnNextGame = findViewById(R.id.btnMatchNextGame);
        btnSubmitScore = findViewById(R.id.btnSubmitMatchScore);
        btnFindRandom = findViewById(R.id.btnFindRandom);
        btnCancelQueue = findViewById(R.id.btnCancelQueue);
        btnInviteFriend = findViewById(R.id.btnInviteFriend);
        etInviteTarget = findViewById(R.id.etInviteTarget);
        Button btnGiveUp = findViewById(R.id.btnMatchGiveUp);
        gameLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (!inRoom) return;
            if (currentGameIndex < GAME_NAMES.length - 1) {
                currentGameIndex++;
                renderMatch();
                openCurrentGame();
            } else {
                tvMatchInfo.setText("Sve igre su odigrane. Posalji rezultat.");
                renderMatch();
            }
        });

        gameSteps = new TextView[]{
                findViewById(R.id.tvMatchGame1),
                findViewById(R.id.tvMatchGame2),
                findViewById(R.id.tvMatchGame3),
                findViewById(R.id.tvMatchGame4),
                findViewById(R.id.tvMatchGame5),
                findViewById(R.id.tvMatchGame6)
        };

        btnFindRandom.setOnClickListener(v -> startRankedQueue());
        btnCancelQueue.setOnClickListener(v -> cancelQueue());
        btnInviteFriend.setOnClickListener(v -> inviteFriend());
        btnOpenCurrentGame.setOnClickListener(v -> openCurrentGame());
        btnNextGame.setOnClickListener(v -> moveToNextGame());
        btnSubmitScore.setOnClickListener(v -> submitMatchScore());
        btnGiveUp.setOnClickListener(v -> forfeitMatch());

        authRepository.getCurrentUsername(username -> {
            myUsername = username == null ? "" : username;
            connectRealtime();
        });
        registerReceiver(gameCommandReceiver, new IntentFilter(ACTION_GAME_COMMAND));
        loadEconomy();
        renderMatch();
    }

    private void connectRealtime() {
        realtimeClient.connect(WS_URL, myUid, myUsername, new MatchRealtimeClient.Listener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    wsConnected = true;
                    renderMatch();
                });
            }

            @Override
            public void onAuthenticated() {
                runOnUiThread(() -> {
                    wsAuthenticated = true;
                    if (pendingJoinRequest) {
                        pendingJoinRequest = false;
                        realtimeClient.joinRandomQueue();
                    }
                    handleAutoActionsIfNeeded();
                    renderMatch();
                });
            }

            @Override
            public void onDisconnected(String reason) {
                runOnUiThread(() -> {
                    wsConnected = false;
                    wsAuthenticated = false;
                    queueing = false;
                    Toast.makeText(MatchActivity.this, R.string.match_not_connected, Toast.LENGTH_SHORT).show();
                    renderMatch();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (queueing || pendingJoinRequest) {
                        queueing = false;
                        pendingJoinRequest = false;
                        if (rankedTokenReserved) {
                            economyRepository.refundReservedToken(myUid, new PlayerEconomyRepository.EconomyCallback() {
                                @Override
                                public void onSuccess(Map<String, Long> values) {
                                    rankedTokenReserved = false;
                                    tokens = values.get("tokens");
                                    tvMatchTokens.setText("Tokeni\n" + tokens);
                                    renderMatch();
                                }

                                @Override
                                public void onError(String err) {
                                    renderMatch();
                                }
                            });
                        } else {
                            renderMatch();
                        }
                    }
                    Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onMatchFound(String room, boolean friendly, int playerNumber, String oppUid, String oppUsername) {
                runOnUiThread(() -> {
                    inRoom = true;
                    queueing = false;
                    roomId = room;
                    friendlyRoom = friendly;
                    if (!TextUtils.isEmpty(myUid) && !TextUtils.isEmpty(oppUid)) {
                        myPlayerNumber = myUid.compareTo(oppUid) <= 0 ? 1 : 2;
                    } else {
                        myPlayerNumber = playerNumber == 2 ? 2 : 1;
                    }
                    opponentUid = oppUid;
                    opponentUsername = oppUsername;
                    resultApplied = false;
                    currentGameIndex = 0;
                    myScore = 0;
                    opponentScore = 0;
                    tvMatchInfo.setText(friendly
                            ? getString(R.string.match_friendly_info)
                            : getString(R.string.match_ranked_info));
                    renderMatch();
                    openCurrentGame();
                });
            }

            @Override
            public void onInviteReceived(String inviteId, String fromUid, String fromUsername) {
                runOnUiThread(() -> showInviteDialog(inviteId, fromUid, fromUsername));
            }

            @Override
            public void onInviteDeclined(String byUsername) {
                runOnUiThread(() -> Toast.makeText(MatchActivity.this, "Poziv odbijen: " + byUsername, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onInfo(String message) {
                runOnUiThread(() -> {
                    tvMatchInfo.setText(message);
                });
            }

            @Override
            public void onQueueJoined() {
                runOnUiThread(() -> {
                    queueing = true;
                    tvMatchInfo.setText(getString(R.string.match_waiting));
                    renderMatch();
                });
            }

            @Override
            public void onQueueCancelled() {
                runOnUiThread(() -> {
                    queueing = false;
                    renderMatch();
                });
            }

            @Override
            public void onMatchFinished(String winnerUid, int yourScore, int oppScore, boolean forfeit) {
                runOnUiThread(() -> {
                    myScore = yourScore;
                    opponentScore = oppScore;
                    finishLocalRoom();
                    if (!friendlyRoom && !resultApplied) {
                        boolean winner = myUid != null && myUid.equals(winnerUid);
                        applyRankedResult(winner, myScore);
                    }
                    String msg = (myUid != null && myUid.equals(winnerUid))
                            ? "Pobedili ste partiju!"
                            : "Izgubili ste partiju.";
                    if (forfeit) {
                        msg += " (odustajanje)";
                    }
                    Toast.makeText(MatchActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onGameEvent(String roomId, String game, String event, String fromUid, org.json.JSONObject data) {
                Intent i = new Intent(ACTION_GAME_EVENT);
                i.putExtra(EXTRA_ROOM_ID, roomId);
                i.putExtra(EXTRA_GAME, game);
                i.putExtra(EXTRA_EVENT, event);
                i.putExtra(EXTRA_DATA, data == null ? "{}" : data.toString());
                sendBroadcast(i);
            }
        });
    }

    private void showInviteDialog(String inviteId, String fromUid, String fromUsername) {
        new AlertDialog.Builder(this)
                .setTitle("Poziv za partiju")
                .setMessage(fromUsername + " vas poziva na prijateljsku partiju.")
                .setPositiveButton("Prihvati", (d, w) -> realtimeClient.respondInvite(inviteId, true))
                .setNegativeButton("Odbij", (d, w) -> realtimeClient.respondInvite(inviteId, false))
                .show();
    }

    private void loadEconomy() {
        economyRepository.grantDailyTokensIfNeeded(myUid, new PlayerEconomyRepository.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                economyRepository.getEconomy(myUid, new PlayerEconomyRepository.EconomyCallback() {
                    @Override
                    public void onSuccess(Map<String, Long> refreshed) {
                        tokens = refreshed.get("tokens");
                        stars = refreshed.get("stars");
                        league = refreshed.get("league");
                        runOnUiThread(() -> {
                            tvMatchTokens.setText("Tokeni\n" + tokens);
                            tvMatchStars.setText("Zvezde\n" + stars);
                            tvMatchLeague.setText("Liga\n" + league);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startRankedQueue() {
        if (inRoom || queueing) return;
        if (!wsConnected) {
            Toast.makeText(this, R.string.match_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        economyRepository.reserveTokenForRankedMatch(myUid, new PlayerEconomyRepository.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                rankedTokenReserved = true;
                tokens = values.get("tokens");
                if (wsAuthenticated) {
                    realtimeClient.joinRandomQueue();
                } else {
                    pendingJoinRequest = true;
                    tvMatchInfo.setText("Povezivanje sa serverom...");
                }
                runOnUiThread(() -> {
                    tvMatchTokens.setText("Tokeni\n" + tokens);
                    renderMatch();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void cancelQueue() {
        if (!queueing && !pendingJoinRequest) return;
        pendingJoinRequest = false;
        realtimeClient.cancelQueue();
        if (rankedTokenReserved) {
            economyRepository.refundReservedToken(myUid, new PlayerEconomyRepository.EconomyCallback() {
                @Override
                public void onSuccess(Map<String, Long> values) {
                    rankedTokenReserved = false;
                    tokens = values.get("tokens");
                    runOnUiThread(() -> {
                        tvMatchTokens.setText("Tokeni\n" + tokens);
                        renderMatch();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            renderMatch();
        }
    }

    private void inviteFriend() {
        if (inRoom || queueing) return;
        String target = etInviteTarget.getText().toString().trim();
        if (TextUtils.isEmpty(target)) {
            Toast.makeText(this, "Unesi username ili uid prijatelja.", Toast.LENGTH_SHORT).show();
            return;
        }
        realtimeClient.sendInvite(target);
        Toast.makeText(this, "Poziv poslat.", Toast.LENGTH_SHORT).show();
    }

    private void handleAutoActionsIfNeeded() {
        if (autoStartQueueRequested) {
            autoStartQueueRequested = false;
            if (!inRoom && !queueing && wsConnected && wsAuthenticated) {
                startRankedQueue();
            }
        }

        if (!TextUtils.isEmpty(autoInviteTarget)) {
            String target = autoInviteTarget;
            autoInviteTarget = null;
            if (!inRoom && !queueing && wsAuthenticated) {
                realtimeClient.sendInvite(target);
                Toast.makeText(this, "Poziv poslat igracu: " + target, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openCurrentGame() {
        if (!inRoom) {
            Toast.makeText(this, R.string.match_no_active_room, Toast.LENGTH_SHORT).show();
            return;
        }
        Class<?> target;
        switch (currentGameIndex) {
            case 0: target = StepByStepActivity.class; break;
            case 1: target = MastermindGameActivity.class; break;
            case 2: target = MyNumberGameActivity.class; break;
            case 3: target = QuizGameActivity.class; break;
            case 4: target = ConnectionsGameActivity.class; break;
            default: target = AssociationsGameActivity.class; break;
        }
        Intent intent = new Intent(this, target);
        intent.putExtra("match_room_id", roomId == null ? "" : roomId);
        intent.putExtra("match_game_index", currentGameIndex);
        intent.putExtra("match_my_player_number", myPlayerNumber);
        gameLauncher.launch(intent);
    }

    private void moveToNextGame() {
        if (!inRoom) return;
        Toast.makeText(this, "Igre sada idu automatski jedna za drugom.", Toast.LENGTH_SHORT).show();
    }

    private void submitMatchScore() {
        if (!inRoom || roomId == null) return;
        realtimeClient.submitScore(roomId, myScore);
        Toast.makeText(this, "Rezultat poslat serveru. Cekam protivnika...", Toast.LENGTH_SHORT).show();
    }

    private void applyRankedResult(boolean winner, int score) {
        economyRepository.applyRankedMatchResult(myUid, winner, score, new PlayerEconomyRepository.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                resultApplied = true;
                stars = values.get("stars");
                tokens = values.get("tokens");
                runOnUiThread(() -> {
                    tvMatchStars.setText("Zvezde\n" + stars);
                    tvMatchTokens.setText("Tokeni\n" + tokens);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void forfeitMatch() {
        if (inRoom && roomId != null) {
            realtimeClient.forfeit(roomId);
            finishLocalRoom();
            if (rankedTokenReserved && !friendlyRoom) {
                rankedTokenReserved = false;
            }
        }
        finish();
    }

    private void finishLocalRoom() {
        inRoom = false;
        queueing = false;
        pendingJoinRequest = false;
        roomId = null;
        opponentUid = null;
        opponentUsername = "-";
        rankedTokenReserved = false;
        currentGameIndex = 0;
        renderMatch();
    }

    private void renderMatch() {
        tvMatchStage.setText(getString(R.string.match_stage_title, currentGameIndex + 1, GAME_NAMES.length));
        String info;
        if (inRoom) {
            info = "Protivnik pronadjen. Pokrecem igru...";
        } else if (queueing || pendingJoinRequest || (wsConnected && wsAuthenticated)) {
            info = getString(R.string.match_waiting);
        } else if (!wsConnected) {
            info = getString(R.string.match_not_connected);
        } else {
            info = getString(R.string.match_waiting);
        }
        tvMatchInfo.setText(info);
        tvMatchScore.setText(getString(R.string.match_score_format, myScore, opponentScore));

        for (int i = 0; i < gameSteps.length; i++) {
            if (i >= GAME_NAMES.length) {
                gameSteps[i].setText("");
                gameSteps[i].setBackgroundResource(R.drawable.match_step_upcoming_bg);
                continue;
            }
            if (i < currentGameIndex) {
                gameSteps[i].setText(getString(R.string.match_step_done, i + 1, GAME_NAMES[i]));
                gameSteps[i].setBackgroundResource(R.drawable.match_step_done_bg);
            } else if (i == currentGameIndex && inRoom) {
                gameSteps[i].setText(getString(R.string.match_step_current, i + 1, GAME_NAMES[i]));
                gameSteps[i].setBackgroundResource(R.drawable.match_step_current_bg);
            } else {
                gameSteps[i].setText(getString(R.string.match_step_upcoming, i + 1, GAME_NAMES[i]));
                gameSteps[i].setBackgroundResource(R.drawable.match_step_upcoming_bg);
            }
        }

        btnOpenCurrentGame.setEnabled(inRoom);
        btnNextGame.setEnabled(false);
        btnNextGame.setAlpha(0.55f);
        btnSubmitScore.setEnabled(inRoom);
        btnFindRandom.setEnabled(!inRoom && !queueing && !pendingJoinRequest && wsConnected);
        btnCancelQueue.setEnabled(queueing || pendingJoinRequest);
        btnInviteFriend.setEnabled(!inRoom && !queueing && !pendingJoinRequest && wsAuthenticated);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(gameCommandReceiver);
        } catch (Exception ignored) {
        }
        if (queueing) {
            realtimeClient.cancelQueue();
            if (rankedTokenReserved) {
                economyRepository.refundReservedToken(myUid, new PlayerEconomyRepository.EconomyCallback() {
                    @Override public void onSuccess(Map<String, Long> values) { }
                    @Override public void onError(String message) { }
                });
            }
        }
        realtimeClient.disconnect();
    }
}
