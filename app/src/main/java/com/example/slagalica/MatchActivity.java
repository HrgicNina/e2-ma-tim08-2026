package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.EconomyService;
import com.example.slagalica.domain.LeaderboardService;
import com.example.slagalica.domain.MatchRealtimeClient;
import com.example.slagalica.domain.NotificationService;
import com.example.slagalica.domain.PlayerStatsService;
import com.example.slagalica.domain.SessionManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.util.HashMap;
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
    public static final String EXTRA_RESPOND_INVITE_ID = "respond_invite_id";
    public static final String EXTRA_GAME_PLAYER1_SCORE = "game_player1_score";
    public static final String EXTRA_GAME_PLAYER2_SCORE = "game_player2_score";
    public static final String EXTRA_MATCH_FORFEIT = "match_forfeit";
    public static final String EXTRA_MATCH_SOLO_MODE = "match_solo_mode";
    public static final String EXTRA_MATCH_PLAYER1_NAME = "match_player1_name";
    public static final String EXTRA_MATCH_PLAYER2_NAME = "match_player2_name";
    public static final String EXTRA_MATCH_PLAYER1_FRAME = "match_player1_frame";
    public static final String EXTRA_MATCH_PLAYER2_FRAME = "match_player2_frame";
    public static final String EXTRA_MATCH_BASE_PLAYER1_SCORE = "match_base_player1_score";
    public static final String EXTRA_MATCH_BASE_PLAYER2_SCORE = "match_base_player2_score";
    public static final String EXTRA_MATCH_MY_TOKENS = "match_my_tokens";
    public static final String EXTRA_MATCH_MY_STARS = "match_my_stars";
    public static final String EXTRA_MATCH_MY_LEAGUE = "match_my_league";
    public static final String EXTRA_RESULT_PLAYER1_NAME = "result_player1_name";
    public static final String EXTRA_RESULT_PLAYER2_NAME = "result_player2_name";
    public static final String EXTRA_RESULT_PLAYER1_SCORE = "result_player1_score";
    public static final String EXTRA_RESULT_PLAYER2_SCORE = "result_player2_score";
    public static final String EXTRA_RESULT_IS_CURRENT_PLAYER1 = "result_is_current_player1";
    public static final String EXTRA_RESULT_STAR_DELTA = "result_star_delta";
    public static final String EXTRA_RESULT_TOKEN_DELTA = "result_token_delta";
    public static final String EXTRA_RESULT_ECONOMY_NOTE = "result_economy_note";
    public static final String EXTRA_RESULT_TOTAL_STARS = "result_total_stars";
    public static final String EXTRA_RESULT_TOTAL_TOKENS = "result_total_tokens";

    private static final String[] GAME_NAMES = {
            "Ko zna zna",
            "Spojnice",
            "Asocijacije",
            "Skocko",
            "Korak po korak",
            "Moj broj"
    };

    private static final String WS_URL = "ws://10.0.2.2:8080";
    private static final long RANDOM_QUEUE_TIMEOUT_MS = 30_000L;

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
    private String player1AvatarFrame = "blue";
    private String player2AvatarFrame = "blue";
    private int myPlayerNumber = 1;
    private boolean resultApplied = false;
    private boolean autoStartQueueRequested = false;
    private String autoInviteTarget = null;
    private String respondInviteId = null;
    private boolean guestMode = false;
    private boolean opponentForfeited = false;
    private boolean suppressInRoomInfoMessages = false;
    private boolean showMatchFoundInfoOnce = false;
    private String pendingInviteTargetForFallback = null;
    private boolean scoreSubmitted = false;
    private boolean outgoingInvitePending = false;
    private String outgoingInviteId = null;
    private AlertDialog incomingInviteDialog = null;
    private String incomingInviteId = null;
    private Runnable incomingInviteTimeoutRunnable = null;
    private Runnable queueTimeoutRunnable = null;
    private boolean queueTimeoutHandling = false;
    private boolean localForfeitExitRequested = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();
    private final EconomyService economyService = new EconomyService();
    private final LeaderboardService leaderboardService = new LeaderboardService();
    private final PlayerStatsService playerStatsService = new PlayerStatsService();
    private final MatchRealtimeClient realtimeClient = new MatchRealtimeClient();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private boolean matchStatsSubmitted = false;

    private interface EconomyDeltaCallback {
        void onReady(long starDelta, long tokenDelta, String note);
    }
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
        String currentUserId = authService.getCurrentUserId();
        if ((currentUserId == null || currentUserId.trim().isEmpty()) && !guestMode) {
            Toast.makeText(this, "Partija je trenutno dostupna samo registrovanim korisnicima.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (guestMode) {
            myUid = sessionManager.getOrCreateGuestUid();
            myUsername = sessionManager.getGuestDisplayName();
        } else {
            myUid = currentUserId;
        }
        autoStartQueueRequested = getIntent().getBooleanExtra(EXTRA_AUTO_START_QUEUE, false);
        autoInviteTarget = getIntent().getStringExtra(EXTRA_AUTO_INVITE_TARGET);
        respondInviteId = getIntent().getStringExtra(EXTRA_RESPOND_INVITE_ID);

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
                int basePlayer1 = myPlayerNumber == 1 ? myScore : opponentScore;
                int basePlayer2 = myPlayerNumber == 1 ? opponentScore : myScore;
                recordGameStatsIfNeeded(result.getData(), basePlayer1, basePlayer2);
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
            advanceAfterGameFinished();
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
        btnCancelQueue.setOnClickListener(v -> {
            if (outgoingInvitePending) {
                cancelOutgoingInvite();
            } else {
                cancelQueue();
            }
        });
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
            authService.getCurrentUsername(username -> {
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
                    cancelQueueTimeout();
                    clearOutgoingInviteState();
                    clearIncomingInviteState();
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
                        cancelQueueTimeout();
                        if (rankedTokenReserved) {
                            economyService.refundReservedToken(myUid, new EconomyService.EconomyCallback() {
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
                    if (outgoingInvitePending) {
                        clearOutgoingInviteState();
                        renderMatch();
                    }
                    pendingInviteTargetForFallback = null;
                    if (!shouldSuppressServerMessage(message)) {
                        Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onMatchFound(String room, boolean friendly, int playerNumber, String oppUid, String oppUsername) {
                runOnUiThread(() -> {
                    inRoom = true;
                    updateMatchPresence(true, room);
                    queueing = false;
                    pendingJoinRequest = false;
                    cancelQueueTimeout();
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
                    suppressInRoomInfoMessages = false;
                    showMatchFoundInfoOnce = true;
                    currentGameIndex = 0;
                    myScore = 0;
                    opponentScore = 0;
                    scoreSubmitted = false;
                    matchStatsSubmitted = false;
                    clearOutgoingInviteState();
                    clearIncomingInviteState();
                    tvMatchInfo.setText(friendly
                            ? getString(R.string.match_friendly_info)
                            : getString(R.string.match_ranked_info));
                    renderMatch();
                    loadMatchAvatarFrames(() -> uiHandler.postDelayed(() -> {
                        if (inRoom && roomId != null && !isFinishing()) {
                            openCurrentGame();
                        }
                    }, 900L));
                });
            }

            @Override
            public void onInviteReceived(String inviteId, String fromUid, String fromUsername) {
                runOnUiThread(() -> showInviteDialog(inviteId, fromUid, fromUsername));
            }

            @Override
            public void onInviteSent(String inviteId, int expiresInSeconds) {
                runOnUiThread(() -> {
                    outgoingInvitePending = true;
                    outgoingInviteId = inviteId;
                    tvMatchInfo.setText("Poziv poslat. Cekanje odgovora (" + expiresInSeconds + "s)...");
                    renderMatch();
                });
            }

            @Override
            public void onInviteDeclined(String byUsername) {
                runOnUiThread(() -> {
                    clearOutgoingInviteState();
                    Toast.makeText(MatchActivity.this, "Poziv odbijen: " + byUsername, Toast.LENGTH_SHORT).show();
                    renderMatch();
                });
            }

            @Override
            public void onInviteExpired(String inviteId) {
                runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(outgoingInviteId) && outgoingInviteId.equals(inviteId)) {
                        clearOutgoingInviteState();
                        Toast.makeText(MatchActivity.this, "Poziv je istekao (10s).", Toast.LENGTH_SHORT).show();
                        renderMatch();
                    }
                    if (!TextUtils.isEmpty(incomingInviteId) && incomingInviteId.equals(inviteId)) {
                        dismissIncomingInviteDialog();
                        Toast.makeText(MatchActivity.this, "Poziv je istekao.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onInviteCancelled(String inviteId, String byUsername) {
                runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(incomingInviteId) && incomingInviteId.equals(inviteId)) {
                        dismissIncomingInviteDialog();
                        Toast.makeText(MatchActivity.this, "Poziv je otkazan: " + byUsername, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onInfo(String message) {
                runOnUiThread(() -> {
                    if (inRoom || suppressInRoomInfoMessages) {
                        return;
                    }
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
                    pendingJoinRequest = false;
                    cancelQueueTimeout();
                    renderMatch();
                });
            }

            @Override
            public void onMatchFinished(String winnerUid, int yourScore, int oppScore, boolean forfeit, boolean draw) {
                runOnUiThread(() -> {
                    if (localForfeitExitRequested) {
                        return;
                    }
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
                    int finalPlayer1Score = myPlayerNumber == 1 ? myScore : opponentScore;
                    int finalPlayer2Score = myPlayerNumber == 1 ? opponentScore : myScore;
                    String finalPlayer1Name = player1DisplayName;
                    String finalPlayer2Name = player2DisplayName;
                    boolean wasFriendly = friendlyRoom;
                    boolean wasGuest = guestMode;
                    if (!wasGuest) {
                        recordMatchStatsOnce(iAmWinner, draw);
                    }
                    finishLocalRoom(false);
                    if (!wasGuest && !wasFriendly && !resultApplied) {
                        if (forfeit && !iAmWinner) {
                            applyForfeitLoserResult((starDelta, tokenDelta, note) ->
                                    openResultSplashAndFinish(
                                            finalPlayer1Name,
                                            finalPlayer2Name,
                                            finalPlayer1Score,
                                            finalPlayer2Score,
                                            starDelta,
                                            tokenDelta,
                                            note,
                                            stars,
                                            tokens
                                    ));
                        } else if (draw) {
                            applyRankedDrawResult((starDelta, tokenDelta, note) ->
                                    openResultSplashAndFinish(
                                            finalPlayer1Name,
                                            finalPlayer2Name,
                                            finalPlayer1Score,
                                            finalPlayer2Score,
                                            starDelta,
                                            tokenDelta,
                                            note,
                                            stars,
                                            tokens
                                    ));
                        } else {
                            applyRankedResult(iAmWinner, myScore, (starDelta, tokenDelta, note) ->
                                    openResultSplashAndFinish(
                                            finalPlayer1Name,
                                            finalPlayer2Name,
                                            finalPlayer1Score,
                                            finalPlayer2Score,
                                            starDelta,
                                            tokenDelta,
                                            note,
                                            stars,
                                            tokens
                                    ));
                        }
                    } else {
                        String note = null;
                        if (wasFriendly) {
                            note = "Prijateljska partija: bez promene zvezda.";
                        } else if (wasGuest) {
                            note = "Gost nalog: bez promene zvezda.";
                        }
                        openResultSplashAndFinish(
                                finalPlayer1Name,
                                finalPlayer2Name,
                                finalPlayer1Score,
                                finalPlayer2Score,
                                0L,
                                0L,
                                note,
                                stars,
                                tokens
                        );
                    }
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
        dismissIncomingInviteDialog();
        incomingInviteId = inviteId;
        incomingInviteDialog = new AlertDialog.Builder(this)
                .setTitle("Poziv za partiju")
                .setMessage(fromUsername + " vas poziva na prijateljsku partiju.")
                .setCancelable(false)
                .setPositiveButton("Prihvati", (d, w) -> {
                    clearIncomingInviteTimeout();
                    incomingInviteId = null;
                    incomingInviteDialog = null;
                    realtimeClient.respondInvite(inviteId, true);
                })
                .setNegativeButton("Odbij", (d, w) -> {
                    clearIncomingInviteTimeout();
                    incomingInviteId = null;
                    incomingInviteDialog = null;
                    realtimeClient.respondInvite(inviteId, false);
                })
                .create();
        incomingInviteDialog.setOnDismissListener(d -> {
            clearIncomingInviteTimeout();
            if (inviteId.equals(incomingInviteId)) {
                incomingInviteId = null;
            }
            incomingInviteDialog = null;
        });
        incomingInviteDialog.show();
        scheduleIncomingInviteAutoReject(inviteId);
    }

    private void loadEconomy() {
        if (guestMode) {
            tvMatchTokens.setText("Tokeni\n-");
            tvMatchStars.setText("Zvezde\n-");
            tvMatchLeague.setText("Liga\nGost");
            return;
        }
        economyService.getEconomy(myUid, new EconomyService.EconomyCallback() {
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
        queueTimeoutHandling = false;
        if (guestMode) {
                if (wsAuthenticated) {
                    realtimeClient.joinRandomQueue();
                } else {
                    pendingJoinRequest = true;
                    tvMatchInfo.setText(getString(R.string.match_waiting));
                    renderMatch();
                }
                scheduleQueueTimeout();
                return;
            }
        economyService.reserveTokenForRankedMatch(myUid, new EconomyService.EconomyCallback() {
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
                scheduleQueueTimeout();
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
        cancelQueueTimeout();
        realtimeClient.cancelQueue();
        if (rankedTokenReserved) {
            economyService.refundReservedToken(myUid, new EconomyService.EconomyCallback() {
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
        if (outgoingInvitePending) {
            Toast.makeText(this, "Vec cekate odgovor na prethodni poziv.", Toast.LENGTH_SHORT).show();
            return;
        }
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

        if (!TextUtils.isEmpty(respondInviteId)) {
            String inviteId = respondInviteId;
            respondInviteId = null;
            if (!guestMode && !inRoom && !queueing && wsAuthenticated) {
                realtimeClient.respondInvite(inviteId, true);
                tvMatchInfo.setText("Prihvatam poziv...");
                renderMatch();
            }
            return;
        }

        if (!TextUtils.isEmpty(autoInviteTarget)) {
            String target = autoInviteTarget;
            autoInviteTarget = null;
            if (!guestMode && !inRoom && !queueing && wsAuthenticated) {
                realtimeClient.sendInvite(target);
                pendingInviteTargetForFallback = target;
                outgoingInvitePending = true;
                Toast.makeText(this, "Poziv poslat igracu: " + target, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void cancelOutgoingInvite() {
        if (!outgoingInvitePending || TextUtils.isEmpty(outgoingInviteId)) {
            return;
        }
        realtimeClient.cancelInvite(outgoingInviteId);
        clearOutgoingInviteState();
        tvMatchInfo.setText("Poziv je otkazan.");
        renderMatch();
    }

    private void scheduleQueueTimeout() {
        cancelQueueTimeout();
        queueTimeoutRunnable = () -> {
            if (inRoom || queueTimeoutHandling) {
                return;
            }
            if (!queueing && !pendingJoinRequest) {
                return;
            }
            queueTimeoutHandling = true;
            pendingJoinRequest = false;
            queueing = false;
            realtimeClient.cancelQueue();
            if (!guestMode && rankedTokenReserved) {
                economyService.refundReservedToken(myUid, new EconomyService.EconomyCallback() {
                    @Override
                    public void onSuccess(Map<String, Long> values) {
                        rankedTokenReserved = false;
                        Long refreshedTokens = values.get("tokens");
                        tokens = refreshedTokens == null ? tokens : refreshedTokens;
                        runOnUiThread(() -> exitWaitingScreenNoOpponent());
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> exitWaitingScreenNoOpponent());
                    }
                });
            } else {
                exitWaitingScreenNoOpponent();
            }
        };
        uiHandler.postDelayed(queueTimeoutRunnable, RANDOM_QUEUE_TIMEOUT_MS);
    }

    private void cancelQueueTimeout() {
        if (queueTimeoutRunnable != null) {
            uiHandler.removeCallbacks(queueTimeoutRunnable);
            queueTimeoutRunnable = null;
        }
    }

    private void exitWaitingScreenNoOpponent() {
        cancelQueueTimeout();
        queueTimeoutHandling = false;
        Toast.makeText(this, "Protivnik nije pronadjen u roku od 30 sekundi.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void loadMatchAvatarFrames(Runnable onReady) {
        player1AvatarFrame = "blue";
        player2AvatarFrame = "blue";
        if (guestMode || TextUtils.isEmpty(myUid) || TextUtils.isEmpty(opponentUid)) {
            onReady.run();
            return;
        }

        Task<DocumentSnapshot> myProfile = db.collection("users").document(myUid).get();
        Task<DocumentSnapshot> opponentProfile = db.collection("users").document(opponentUid).get();
        boolean[] readyDelivered = {false};
        Runnable deliverReady = () -> {
            if (readyDelivered[0]) {
                return;
            }
            readyDelivered[0] = true;
            onReady.run();
        };
        uiHandler.postDelayed(deliverReady, 1500L);
        Tasks.whenAllComplete(myProfile, opponentProfile).addOnCompleteListener(unused -> {
            String myFrame = frameFromProfileTask(myProfile);
            String opponentFrame = frameFromProfileTask(opponentProfile);
            if (myPlayerNumber == 1) {
                player1AvatarFrame = myFrame;
                player2AvatarFrame = opponentFrame;
            } else {
                player1AvatarFrame = opponentFrame;
                player2AvatarFrame = myFrame;
            }
            deliverReady.run();
        });
    }

    private String frameFromProfileTask(Task<DocumentSnapshot> task) {
        if (task == null || !task.isSuccessful() || task.getResult() == null) {
            return "blue";
        }
        String frame = task.getResult().getString("avatarFrameId");
        return TextUtils.isEmpty(frame) ? "blue" : frame;
    }

    private void openCurrentGame() {
        if (!inRoom) {
            return;
        }
        showMatchFoundInfoOnce = false;
        Class<?> target;
        switch (currentGameIndex) {
            case 0: target = QuizGameActivity.class; break;
            case 1: target = ConnectionsGameActivity.class; break;
            case 2: target = AssociationsGameActivity.class; break;
            case 3: target = MastermindGameActivity.class; break;
            case 4: target = StepByStepActivity.class; break;
            default: target = MyNumberGameActivity.class; break;
        }
        Intent intent = new Intent(this, target);
        intent.putExtra("match_room_id", roomId == null ? "" : roomId);
        intent.putExtra("match_game_index", currentGameIndex);
        intent.putExtra("match_my_player_number", myPlayerNumber);
        intent.putExtra(EXTRA_MATCH_SOLO_MODE, opponentForfeited);
        intent.putExtra(EXTRA_MATCH_PLAYER1_NAME, player1DisplayName);
        intent.putExtra(EXTRA_MATCH_PLAYER2_NAME, player2DisplayName);
        intent.putExtra(EXTRA_MATCH_PLAYER1_FRAME, player1AvatarFrame);
        intent.putExtra(EXTRA_MATCH_PLAYER2_FRAME, player2AvatarFrame);
        int basePlayer1 = myPlayerNumber == 1 ? myScore : opponentScore;
        int basePlayer2 = myPlayerNumber == 1 ? opponentScore : myScore;
        intent.putExtra(EXTRA_MATCH_BASE_PLAYER1_SCORE, basePlayer1);
        intent.putExtra(EXTRA_MATCH_BASE_PLAYER2_SCORE, basePlayer2);
        intent.putExtra(EXTRA_MATCH_MY_TOKENS, tokens);
        intent.putExtra(EXTRA_MATCH_MY_STARS, stars);
        intent.putExtra(EXTRA_MATCH_MY_LEAGUE, league);
        gameLauncher.launch(intent);
    }

    private boolean shouldSuppressServerMessage(String message) {
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("partija ne postoji")
                || normalized.contains("nema aktivne partije")
                || normalized.contains("match does not exist")
                || normalized.contains("match not found");
    }

    private void moveToNextGame() {
        if (!inRoom) return;
        Toast.makeText(this, "Igre sada idu automatski jedna za drugom.", Toast.LENGTH_SHORT).show();
    }

    private void submitMatchScore() {
        if (!inRoom || roomId == null) return;
        if (scoreSubmitted) return;
        if (opponentForfeited) {
            finalizeSoloWinAfterForfeit();
            return;
        }
        scoreSubmitted = true;
        realtimeClient.submitScore(roomId, myScore);
    }

    private void finalizeSoloWinAfterForfeit() {
        int finalPlayer1Score = myPlayerNumber == 1 ? myScore : opponentScore;
        int finalPlayer2Score = myPlayerNumber == 1 ? opponentScore : myScore;
        String finalPlayer1Name = player1DisplayName;
        String finalPlayer2Name = player2DisplayName;
        boolean wasFriendly = friendlyRoom;
        boolean wasGuest = guestMode;
        if (!wasGuest) {
            recordMatchStatsOnce(true, false);
        }

        if (!wasGuest && !wasFriendly && !resultApplied) {
            applyRankedResult(true, myScore, (starDelta, tokenDelta, note) -> {
                finishLocalRoom(false);
                openResultSplashAndFinish(
                        finalPlayer1Name,
                        finalPlayer2Name,
                        finalPlayer1Score,
                        finalPlayer2Score,
                        starDelta,
                        tokenDelta,
                        note,
                        stars,
                        tokens
                );
            });
            return;
        }

        String note = "Protivnik je napustio partiju.";
        if (wasFriendly) {
            note = "Prijateljska partija: bez promene zvezda.";
        } else if (wasGuest) {
            note = "Gost nalog: bez promene zvezda.";
        }
        finishLocalRoom(false);
        openResultSplashAndFinish(
                finalPlayer1Name,
                finalPlayer2Name,
                finalPlayer1Score,
                finalPlayer2Score,
                0L,
                0L,
                note,
                stars,
                tokens
        );
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
            case 0: return "quiz";
            case 1: return "connections";
            case 2: return "associations";
            case 3: return "master";
            case 4: return "step";
            case 5: return "number";
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
        applyRankedResult(winner, score, null);
    }

    private void applyRankedResult(boolean winner, int score, EconomyDeltaCallback callback) {
        final long starsBefore = stars;
        final long tokensBefore = tokens;
        economyService.applyRankedMatchResult(myUid, winner, score, new EconomyService.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                resultApplied = true;
                stars = values.get("stars");
                tokens = values.get("tokens");
                long starDelta = stars - starsBefore;
                long tokenDelta = tokens - tokensBefore;
                triggerLeaderboardRolloverCheck();
                runOnUiThread(() -> {
                    tvMatchStars.setText("Zvezde\n" + stars);
                    tvMatchTokens.setText("Tokeni\n" + tokens);
                    if (callback != null) {
                        callback.onReady(starDelta, tokenDelta, null);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show();
                    if (callback != null) {
                        callback.onReady(0L, 0L, "Promena naloga nije dostupna (greska pri obradi rezultata).");
                    }
                });
            }
        });
    }

    private void applyRankedDrawResult(EconomyDeltaCallback callback) {
        final long starsBefore = stars;
        final long tokensBefore = tokens;
        economyService.applyRankedDrawResult(myUid, new EconomyService.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                resultApplied = true;
                stars = values.get("stars");
                tokens = values.get("tokens");
                long starDelta = stars - starsBefore;
                long tokenDelta = tokens - tokensBefore;
                triggerLeaderboardRolloverCheck();
                runOnUiThread(() -> {
                    tvMatchStars.setText("Zvezde\n" + stars);
                    tvMatchTokens.setText("Tokeni\n" + tokens);
                    if (callback != null) {
                        callback.onReady(starDelta, tokenDelta, null);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show();
                    if (callback != null) {
                        callback.onReady(0L, 0L, "Promena naloga nije dostupna (greska pri obradi neresenog).");
                    }
                });
            }
        });
    }

    private void applyForfeitLoserResult() {
        applyForfeitLoserResult(null);
    }

    private void applyForfeitLoserResult(EconomyDeltaCallback callback) {
        final long starsBefore = stars;
        final long tokensBefore = tokens;
        economyService.applyForfeitLoserPenalty(myUid, new EconomyService.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                resultApplied = true;
                stars = values.get("stars");
                tokens = values.get("tokens");
                long starDelta = stars - starsBefore;
                long tokenDelta = tokens - tokensBefore;
                triggerLeaderboardRolloverCheck();
                runOnUiThread(() -> {
                    tvMatchStars.setText("Zvezde\n" + stars);
                    tvMatchTokens.setText("Tokeni\n" + tokens);
                    if (callback != null) {
                        callback.onReady(starDelta, tokenDelta, null);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MatchActivity.this, message, Toast.LENGTH_SHORT).show();
                    if (callback != null) {
                        callback.onReady(0L, 0L, "Promena naloga nije dostupna (greska pri obradi kazne).");
                    }
                });
            }
        });
    }

    private void triggerLeaderboardRolloverCheck() {
        leaderboardService.processCycleRolloverAndRewards(new LeaderboardService.ActionCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void createOfflineInviteFallback(String targetUsername) {
        String senderName = TextUtils.isEmpty(myUsername) ? "Igrac" : myUsername;
        notificationService.createOfflineInviteNotificationForUsername(
                targetUsername,
                senderName,
                new NotificationService.UiActionCallback() {
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
        localForfeitExitRequested = true;

        boolean rankedPenaltyApplies = !guestMode && !friendlyRoom && !resultApplied;
        if (!guestMode) {
            recordMatchStatsOnce(false, false);
        }
        if (inRoom && roomId != null) {
            realtimeClient.forfeit(roomId);
            finishLocalRoom();
            if (rankedTokenReserved && !friendlyRoom) {
                rankedTokenReserved = false;
            }
        }

        if (rankedPenaltyApplies) {
            applyForfeitLoserResult((starDelta, tokenDelta, note) -> navigateHomeAfterForfeit());
            return;
        }
        navigateHomeAfterForfeit();
    }

    private void navigateHomeAfterForfeit() {
        Toast.makeText(this, "Izgubili ste partiju odustajanjem. Izgubili ste 10 zvezda.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void finishLocalRoom() {
        finishLocalRoom(true);
    }

    private void finishLocalRoom(boolean renderUi) {
        cancelQueueTimeout();
        updateMatchPresence(false, null);
        inRoom = false;
        queueing = false;
        pendingJoinRequest = false;
        roomId = null;
        opponentUid = null;
        opponentUsername = "-";
        player1AvatarFrame = "blue";
        player2AvatarFrame = "blue";
        rankedTokenReserved = false;
        opponentForfeited = false;
        scoreSubmitted = false;
        matchStatsSubmitted = false;
        clearOutgoingInviteState();
        clearIncomingInviteState();
        currentGameIndex = 0;
        suppressInRoomInfoMessages = false;
        showMatchFoundInfoOnce = false;
        if (renderUi) {
            renderMatch();
        }
    }

    private void recordGameStatsIfNeeded(Intent data, int basePlayer1, int basePlayer2) {
        if (guestMode || TextUtils.isEmpty(myUid) || data == null) {
            return;
        }
        int points = PlayerStatsService.pointsForCurrentPlayer(data, myPlayerNumber, basePlayer1, basePlayer2);
        data.putExtra(PlayerStatsService.EXTRA_STATS_GAME_POINTS, points);
        playerStatsService.recordGameFromResult(myUid, data, new PlayerStatsService.ActionCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void recordMatchStatsOnce(boolean win, boolean draw) {
        if (matchStatsSubmitted || guestMode || TextUtils.isEmpty(myUid)) {
            return;
        }
        matchStatsSubmitted = true;
        playerStatsService.recordMatchResult(myUid, win, draw, new PlayerStatsService.ActionCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void advanceAfterGameFinished() {
        if (currentGameIndex < GAME_NAMES.length - 1) {
            currentGameIndex++;
            renderMatch();
            openCurrentGame();
        } else {
            suppressInRoomInfoMessages = true;
            submitMatchScore();
        }
    }

    private void openResultSplashAndFinish(
            String player1Name,
            String player2Name,
            int player1Score,
            int player2Score,
            long starDelta,
            long tokenDelta,
            String economyNote,
            long totalStars,
            long totalTokens
    ) {
        Intent intent = new Intent(this, MatchResultSplashActivity.class);
        intent.putExtra(EXTRA_RESULT_PLAYER1_NAME, player1Name);
        intent.putExtra(EXTRA_RESULT_PLAYER2_NAME, player2Name);
        intent.putExtra(EXTRA_MATCH_PLAYER1_FRAME, player1AvatarFrame);
        intent.putExtra(EXTRA_MATCH_PLAYER2_FRAME, player2AvatarFrame);
        intent.putExtra(EXTRA_RESULT_PLAYER1_SCORE, player1Score);
        intent.putExtra(EXTRA_RESULT_PLAYER2_SCORE, player2Score);
        intent.putExtra(EXTRA_RESULT_IS_CURRENT_PLAYER1, myPlayerNumber == 1);
        intent.putExtra(EXTRA_RESULT_STAR_DELTA, starDelta);
        intent.putExtra(EXTRA_RESULT_TOKEN_DELTA, tokenDelta);
        intent.putExtra(EXTRA_RESULT_ECONOMY_NOTE, economyNote);
        intent.putExtra(EXTRA_RESULT_TOTAL_STARS, totalStars);
        intent.putExtra(EXTRA_RESULT_TOTAL_TOKENS, totalTokens);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void renderMatch() {
        tvMatchStage.setText(getString(R.string.match_stage_title, currentGameIndex + 1, GAME_NAMES.length));
        String info;
        if (inRoom) {
            if (showMatchFoundInfoOnce && currentGameIndex == 0) {
                info = "Protivnik pronadjen. Pokrecem igru...";
            } else {
                info = "";
            }
        } else if (queueing || pendingJoinRequest || outgoingInvitePending) {
            info = getString(R.string.match_waiting);
        } else {
            info = "";
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
        btnCancelQueue.setEnabled(queueing || pendingJoinRequest || outgoingInvitePending);
        btnInviteFriend.setEnabled(!guestMode && !inRoom && !queueing && !pendingJoinRequest && wsAuthenticated && !outgoingInvitePending);
    }

    private void scheduleIncomingInviteAutoReject(String inviteId) {
        clearIncomingInviteTimeout();
        incomingInviteTimeoutRunnable = () -> {
            if (incomingInviteDialog != null
                    && incomingInviteDialog.isShowing()
                    && inviteId.equals(incomingInviteId)) {
                realtimeClient.respondInvite(inviteId, false);
                dismissIncomingInviteDialog();
            }
        };
        uiHandler.postDelayed(incomingInviteTimeoutRunnable, 10_000L);
    }

    private void clearIncomingInviteTimeout() {
        if (incomingInviteTimeoutRunnable != null) {
            uiHandler.removeCallbacks(incomingInviteTimeoutRunnable);
            incomingInviteTimeoutRunnable = null;
        }
    }

    private void dismissIncomingInviteDialog() {
        clearIncomingInviteTimeout();
        if (incomingInviteDialog != null && incomingInviteDialog.isShowing()) {
            incomingInviteDialog.dismiss();
        }
        incomingInviteDialog = null;
        incomingInviteId = null;
    }

    private void clearIncomingInviteState() {
        dismissIncomingInviteDialog();
    }

    private void clearOutgoingInviteState() {
        outgoingInvitePending = false;
        outgoingInviteId = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelQueueTimeout();
        try {
            unregisterReceiver(gameCommandReceiver);
        } catch (Exception ignored) {
        }
        if (queueing) {
            realtimeClient.cancelQueue();
            if (rankedTokenReserved) {
                economyService.refundReservedToken(myUid, new EconomyService.EconomyCallback() {
                    @Override public void onSuccess(Map<String, Long> values) { }
                    @Override public void onError(String message) { }
                });
            }
        }
        clearIncomingInviteTimeout();
        if (inRoom) {
            updateMatchPresence(false, null);
        }
        realtimeClient.disconnect();
    }

    private void updateMatchPresence(boolean active, String activeRoomId) {
        if (guestMode || TextUtils.isEmpty(myUid)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("inMatch", active);
        payload.put("activeRoomId", activeRoomId == null ? "" : activeRoomId);
        payload.put("matchUpdatedAtMillis", System.currentTimeMillis());
        db.collection("users")
                .document(myUid)
                .update(payload);
    }
}
