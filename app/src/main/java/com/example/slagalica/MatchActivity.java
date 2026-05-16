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
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.slagalica.data.FirebaseAuthRepository;
import com.example.slagalica.data.NotificationsRepository;
import com.example.slagalica.data.PlayerEconomyRepository;
import com.example.slagalica.domain.MatchRealtimeClient;
import com.example.slagalica.domain.SessionManager;
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
    public static final String EXTRA_GAME_PLAYER1_SCORE = "game_player1_score";
    public static final String EXTRA_GAME_PLAYER2_SCORE = "game_player2_score";
    public static final String EXTRA_MATCH_FORFEIT = "match_forfeit";
    public static final String EXTRA_MATCH_SOLO_MODE = "match_solo_mode";
    public static final String EXTRA_MATCH_PLAYER1_NAME = "match_player1_name";
    public static final String EXTRA_MATCH_PLAYER2_NAME = "match_player2_name";
    public static final String EXTRA_MATCH_BASE_PLAYER1_SCORE = "match_base_player1_score";
    public static final String EXTRA_MATCH_BASE_PLAYER2_SCORE = "match_base_player2_score";

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
    private String player1DisplayName = "Igrac 1";
    private String player2DisplayName = "Igrac 2";
    private int myPlayerNumber = 1;
    private boolean resultApplied = false;
    private boolean autoStartQueueRequested = false;
    private String autoInviteTarget = null;
    private boolean guestMode = false;
    private boolean opponentForfeited = false;
    private String pendingInviteTargetForFallback = null;

    private final FirebaseAuthRepository authRepository = new FirebaseAuthRepository();
    private final NotificationsRepository notificationsRepository = new NotificationsRepository();
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

        SessionManager sessionManager = new SessionManager(this);
        guestMode = sessionManager.isGuestMode();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null && !guestMode) {
            Toast.makeText(this, "Partija je trenutno dostupna samo registrovanim korisnicima.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (guestMode) {
            myUid = sessionManager.getOrCreateGuestUid();
            myUsername = sessionManager.getGuestDisplayName();
        } else if (user != null) {
            myUid = user.getUid();
        }
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
            if (result.getResultCode() == RESULT_CANCELED
                    && result.getData() != null
                    && result.getData().getBooleanExtra(EXTRA_MATCH_FORFEIT, false)) {
                forfeitMatchAndGoHome();
                return;
            }
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                int player1GameScore = result.getData().getIntExtra(EXTRA_GAME_PLAYER1_SCORE, 0);
                int player2GameScore = result.getData().getIntExtra(EXTRA_GAME_PLAYER2_SCORE, 0);
                if (myPlayerNumber == 1) {
                    myScore = player1GameScore;
                    opponentScore = player2GameScore;
                } else {
                    myScore = player2GameScore;
                    opponentScore = player1GameScore;
                }
            }
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
        btnGiveUp.setOnClickListener(v -> showLeaveMatchDialog());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveMatchDialog();
            }
        });

        if (guestMode) {
            tvMatchTokens.setText("Tokeni\n-");
            tvMatchStars.setText("Zvezde\n-");
            tvMatchLeague.setText("Liga\nGost");
            etInviteTarget.setEnabled(false);
            btnInviteFriend.setEnabled(false);
            autoInviteTarget = null;
            connectRealtime();
        } else {
            authRepository.getCurrentUsername(username -> {
                myUsername = username == null ? "" : username;
                connectRealtime();
            });
            loadEconomy();
        }
        registerReceiver(gameCommandReceiver, new IntentFilter(ACTION_GAME_COMMAND));
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
                    renderMatch();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(pendingInviteTargetForFallback)
                            && message != null
                            && message.toLowerCase().contains("nije online")) {
                        String target = pendingInviteTargetForFallback;
                        pendingInviteTargetForFallback = null;
                        createOfflineInviteFallback(target);
                        return;
                    }
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
                    pendingInviteTargetForFallback = null;
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
                    if (myPlayerNumber == 1) {
                        player1DisplayName = displayNameOrFallback(myUsername, "Igrac 1");
                        player2DisplayName = displayNameOrFallback(opponentUsername, "Igrac 2");
                    } else {
                        player1DisplayName = displayNameOrFallback(opponentUsername, "Igrac 1");
                        player2DisplayName = displayNameOrFallback(myUsername, "Igrac 2");
                    }
                    resultApplied = false;
                    opponentForfeited = false;
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
                    if (message != null && message.toLowerCase().contains("poziv poslat")) {
                        pendingInviteTargetForFallback = null;
                    }
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
                    boolean iAmWinner = myUid != null && myUid.equals(winnerUid);
                    if (forfeit && iAmWinner) {
                        opponentForfeited = true;
                        notifyCurrentGameOpponentForfeit();
                        if (currentGameIndex >= GAME_NAMES.length - 1) {
                            finalizeSoloWinAfterForfeit();
                        } else {
                            Toast.makeText(MatchActivity.this, "Protivnik je napustio partiju. Nastavljate sami.", Toast.LENGTH_LONG).show();
                            tvMatchInfo.setText("Protivnik je odustao. Nastavite igru.");
                            renderMatch();
                        }
                        return;
                    }
                    myScore = yourScore;
                    opponentScore = oppScore;
                    finishLocalRoom();
                    if (!guestMode && !friendlyRoom && !resultApplied) {
                        if (forfeit && !iAmWinner) {
                            applyForfeitLoserResult();
                        } else {
                            applyRankedResult(iAmWinner, myScore);
                        }
                    }
                    String msg = iAmWinner
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
        if (guestMode) {
            tvMatchTokens.setText("Tokeni\n-");
            tvMatchStars.setText("Zvezde\n-");
            tvMatchLeague.setText("Liga\nGost");
            return;
        }
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

    private void startRankedQueue() {
        if (inRoom || queueing) return;
        if (!wsConnected) {
            renderMatch();
            return;
        }
        if (guestMode) {
                if (wsAuthenticated) {
                    realtimeClient.joinRandomQueue();
                } else {
                    pendingJoinRequest = true;
                    tvMatchInfo.setText(getString(R.string.match_waiting));
                    renderMatch();
                }
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
                    tvMatchInfo.setText(getString(R.string.match_waiting));
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
        if (guestMode) {
            Toast.makeText(this, "Gost moze da igra samo nasumicnu partiju.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (inRoom || queueing) return;
        String target = etInviteTarget.getText().toString().trim();
        if (TextUtils.isEmpty(target)) {
            Toast.makeText(this, "Unesi username ili uid prijatelja.", Toast.LENGTH_SHORT).show();
            return;
        }
        realtimeClient.sendInvite(target);
        pendingInviteTargetForFallback = target;
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
            if (!guestMode && !inRoom && !queueing && wsAuthenticated) {
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
        intent.putExtra(EXTRA_MATCH_SOLO_MODE, opponentForfeited);
        intent.putExtra(EXTRA_MATCH_PLAYER1_NAME, player1DisplayName);
        intent.putExtra(EXTRA_MATCH_PLAYER2_NAME, player2DisplayName);
        int basePlayer1 = myPlayerNumber == 1 ? myScore : opponentScore;
        int basePlayer2 = myPlayerNumber == 1 ? opponentScore : myScore;
        intent.putExtra(EXTRA_MATCH_BASE_PLAYER1_SCORE, basePlayer1);
        intent.putExtra(EXTRA_MATCH_BASE_PLAYER2_SCORE, basePlayer2);
        gameLauncher.launch(intent);
    }

    private void moveToNextGame() {
        if (!inRoom) return;
        Toast.makeText(this, "Igre sada idu automatski jedna za drugom.", Toast.LENGTH_SHORT).show();
    }

    private void submitMatchScore() {
        if (!inRoom || roomId == null) return;
        if (opponentForfeited) {
            finalizeSoloWinAfterForfeit();
            return;
        }
        realtimeClient.submitScore(roomId, myScore);
        Toast.makeText(this, "Rezultat poslat. Cekam protivnika...", Toast.LENGTH_SHORT).show();
    }

    private void finalizeSoloWinAfterForfeit() {
        if (!resultApplied && !guestMode && !friendlyRoom) {
            applyRankedResult(true, myScore);
        }
        finishLocalRoom();
        Toast.makeText(this, "Partija zavrsena. Pobedili ste odustajanjem protivnika.", Toast.LENGTH_LONG).show();
        renderMatch();
    }

    private void notifyCurrentGameOpponentForfeit() {
        if (TextUtils.isEmpty(roomId)) {
            return;
        }
        String gameId = currentGameEventId();
        if (TextUtils.isEmpty(gameId)) {
            return;
        }
        Intent i = new Intent(ACTION_GAME_EVENT);
        i.putExtra(EXTRA_ROOM_ID, roomId);
        i.putExtra(EXTRA_GAME, gameId);
        i.putExtra(EXTRA_EVENT, "opponent_forfeit");
        i.putExtra(EXTRA_DATA, "{}");
        sendBroadcast(i);
    }

    private String currentGameEventId() {
        switch (currentGameIndex) {
            case 0: return "step";
            case 1: return "master";
            case 2: return "number";
            case 3: return "quiz";
            case 4: return "connections";
            case 5: return "associations";
            default: return "";
        }
    }

    private String displayNameOrFallback(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
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

    private void applyForfeitLoserResult() {
        economyRepository.applyForfeitLoserPenalty(myUid, new PlayerEconomyRepository.EconomyCallback() {
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

    private void createOfflineInviteFallback(String targetUsername) {
        String senderName = TextUtils.isEmpty(myUsername) ? "Igrac" : myUsername;
        notificationsRepository.createOfflineInviteNotificationForUsername(
                targetUsername,
                senderName,
                new NotificationsRepository.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    MatchActivity.this,
                                    "Prijatelj nije online. Poslata je sistemska notifikacija.",
                                    Toast.LENGTH_LONG
                            ).show();
                            tvMatchInfo.setText("Offline poziv sacuvan kao notifikacija.");
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    private void showLeaveMatchDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Napustanje partije")
                .setMessage("Da li ste sigurni da zelite da napustite igru?")
                .setPositiveButton("Da", (dialog, which) -> forfeitMatchAndGoHome())
                .setNegativeButton("Ne", null)
                .show();
    }

    private void forfeitMatchAndGoHome() {
        if (inRoom && roomId != null) {
            realtimeClient.forfeit(roomId);
            finishLocalRoom();
            if (rankedTokenReserved && !friendlyRoom) {
                rankedTokenReserved = false;
            }
        }
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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
        opponentForfeited = false;
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
            info = getString(R.string.match_waiting);
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
        btnInviteFriend.setEnabled(!guestMode && !inRoom && !queueing && !pendingJoinRequest && wsAuthenticated);
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
