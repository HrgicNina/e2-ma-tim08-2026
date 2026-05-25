package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.ConnectionsRepository;
import com.example.slagalica.domain.ConnectionsGameService;
import com.example.slagalica.domain.EconomyService;
import com.example.slagalica.domain.PlayerStatsService;
import com.example.slagalica.model.ConnectionRound;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ConnectionsGameActivity extends AppCompatActivity {
    private static final String GAME_ID = "connections";
    private static final long ROUND_TRANSITION_MS = 1200L;

    private enum Phase {
        LOADING,
        STARTER,
        STEAL,
        ROUND_FINISHED,
        FINISHED
    }

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvPhaseInfo;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvHeaderLeftAvatar;
    private TextView tvHeaderLeftName;
    private TextView tvHeaderLeftScore;
    private TextView tvHeaderRightAvatar;
    private TextView tvHeaderRightName;
    private TextView tvHeaderRightScore;
    private TurnIndicatorAnimator turnIndicatorAnimator;
    private Button[] leftButtons;
    private Button[] rightButtons;
    private Button btnContinue;

    private ConnectionsGameService connectionsService;
    private final List<ConnectionRound> rounds = new ArrayList<>();
    private final boolean[] matchedLeft = new boolean[ConnectionsGameService.PAIR_COUNT];
    private final boolean[] matchedRight = new boolean[ConnectionsGameService.PAIR_COUNT];
    private final boolean[] wrongLeft = new boolean[ConnectionsGameService.PAIR_COUNT];
    private final int[] matchOwnerLeft = new int[ConnectionsGameService.PAIR_COUNT];
    private final int[] matchOwnerRight = new int[ConnectionsGameService.PAIR_COUNT];

    private Phase phase = Phase.LOADING;
    private int currentRound = 0;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int selectedLeft = -1;
    private int selectedRight = -1;
    private boolean gameFinished = false;
    private boolean remoteFinishHandled = false;
    private int lastTimerSeconds = 30;
    private int statsMatched = 0;
    private int statsMissed = 0;
    private CountDownTimer roundTimer;
    private CountDownTimer transitionTimer;

    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private boolean soloMode = false;
    private String player1DisplayName = "Igrac 1";
    private String player2DisplayName = "Igrac 2";
    private long myTokens = 0L;
    private long myStars = 0L;
    private long myLeague = 0L;
    private Long opponentTokens = null;
    private Long opponentStars = null;
    private Long opponentLeague = null;
    private final EconomyService economyService = new EconomyService();
    private final BroadcastReceiver gameEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!MatchActivity.ACTION_GAME_EVENT.equals(intent.getAction())) {
                return;
            }
            String room = intent.getStringExtra(MatchActivity.EXTRA_ROOM_ID);
            String game = intent.getStringExtra(MatchActivity.EXTRA_GAME);
            String event = intent.getStringExtra(MatchActivity.EXTRA_EVENT);
            String raw = intent.getStringExtra(MatchActivity.EXTRA_DATA);
            if (!GAME_ID.equals(game) || !matchRoomId.equals(room)) {
                return;
            }
            if ("force_finish".equals(event)) {
                try {
                    applyForceFinish(new JSONObject(raw == null ? "{}" : raw));
                } catch (Exception ignored) {
                }
                return;
            }
            if ("opponent_forfeit".equals(event)) {
                enableSoloModeAfterForfeit();
                return;
            }
            if (!"state".equals(event)) {
                return;
            }
            try {
                applyRemoteState(new JSONObject(raw == null ? "{}" : raw));
            } catch (Exception ignored) {
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connections_game);

        connectionsService = new ConnectionsGameService(new ConnectionsRepository());
        matchRoomId = getIntent().getStringExtra("match_room_id");
        if (matchRoomId == null) {
            matchRoomId = "";
        }
        myPlayerNumber = getIntent().getIntExtra("match_my_player_number", 1);
        soloMode = getIntent().getBooleanExtra(MatchActivity.EXTRA_MATCH_SOLO_MODE, false) || TextUtils.isEmpty(matchRoomId);
        player1Score = getIntent().getIntExtra(MatchActivity.EXTRA_MATCH_BASE_PLAYER1_SCORE, 0);
        player2Score = getIntent().getIntExtra(MatchActivity.EXTRA_MATCH_BASE_PLAYER2_SCORE, 0);
        player1DisplayName = displayNameOrFallback(
                getIntent().getStringExtra(MatchActivity.EXTRA_MATCH_PLAYER1_NAME),
                "Igrac 1");
        player2DisplayName = displayNameOrFallback(
                getIntent().getStringExtra(MatchActivity.EXTRA_MATCH_PLAYER2_NAME),
                "Igrac 2");
        myTokens = getIntent().getLongExtra(MatchActivity.EXTRA_MATCH_MY_TOKENS, 0L);
        myStars = getIntent().getLongExtra(MatchActivity.EXTRA_MATCH_MY_STARS, 0L);
        myLeague = getIntent().getLongExtra(MatchActivity.EXTRA_MATCH_MY_LEAGUE, 0L);

        tvRound = findViewById(R.id.tvConnectionsRound);
        tvCurrentPlayer = findViewById(R.id.tvConnectionsCurrentPlayer);
        tvPhaseInfo = findViewById(R.id.tvConnectionsPhaseInfo);
        tvTimer = findViewById(R.id.tvConnectionsTimer);
        tvScore = findViewById(R.id.tvConnectionsScore);
        tvHeaderLeftAvatar = findViewById(R.id.tvHeaderLeftAvatar);
        tvHeaderLeftName = findViewById(R.id.tvHeaderLeftName);
        tvHeaderLeftScore = findViewById(R.id.tvHeaderLeftScore);
        tvHeaderRightAvatar = findViewById(R.id.tvHeaderRightAvatar);
        tvHeaderRightName = findViewById(R.id.tvHeaderRightName);
        tvHeaderRightScore = findViewById(R.id.tvHeaderRightScore);
        turnIndicatorAnimator = new TurnIndicatorAnimator(tvHeaderLeftAvatar, tvHeaderRightAvatar);
        btnContinue = findViewById(R.id.btnConnectionsContinue);
        bindMatchHeader();
        setupMyProfileTap();
        loadOpponentStatus();

        leftButtons = new Button[]{
                findViewById(R.id.btnLeft1),
                findViewById(R.id.btnLeft2),
                findViewById(R.id.btnLeft3),
                findViewById(R.id.btnLeft4),
                findViewById(R.id.btnLeft5)
        };

        rightButtons = new Button[]{
                findViewById(R.id.btnRight1),
                findViewById(R.id.btnRight2),
                findViewById(R.id.btnRight3),
                findViewById(R.id.btnRight4),
                findViewById(R.id.btnRight5)
        };

        for (int i = 0; i < ConnectionsGameService.PAIR_COUNT; i++) {
            final int index = i;
            leftButtons[i].setOnClickListener(v -> selectLeft(index));
            rightButtons[i].setOnClickListener(v -> selectRight(index));
        }

        btnContinue.setEnabled(false);
        btnContinue.setOnClickListener(v -> {
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });

        renderLoading();
        if (isRoundLoader()) {
            loadRoundsAndStart();
        }
    }

    private boolean isRoundLoader() {
        return soloMode || myPlayerNumber == 1;
    }

    private boolean isController() {
        return soloMode || currentPlayer == myPlayerNumber;
    }

    private boolean isRunningPhase() {
        return phase == Phase.STARTER || phase == Phase.STEAL;
    }

    private void loadRoundsAndStart() {
        connectionsService.getGameRounds(loadedRounds -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            rounds.clear();
            rounds.addAll(loadedRounds);
            currentRound = 0;
            startRound();
        });
    }

    private void renderLoading() {
        phase = Phase.LOADING;
        tvRound.setText(R.string.connections_title);
        tvCurrentPlayer.setText("");
        tvPhaseInfo.setText(isRoundLoader()
                ? R.string.connections_loading_rounds
                : R.string.connections_waiting_rounds);
        tvTimer.setText(getString(R.string.connections_timer_seconds, 0));
        updateScoreText();
        for (Button button : leftButtons) {
            button.setText("");
            setConnectionButtonBackground(button, R.drawable.connections_item_bg);
            setConnectionButtonInteractive(button, false);
        }
        for (Button button : rightButtons) {
            button.setText("");
            setConnectionButtonBackground(button, R.drawable.connections_item_bg);
            setConnectionButtonInteractive(button, false);
        }
        btnContinue.setText(R.string.connections_open_next_phase);
        btnContinue.setEnabled(false);
        refreshTurnIndicator();
    }

    private void startRound() {
        if (rounds.size() < ConnectionsGameService.ROUND_COUNT) {
            renderLoading();
            return;
        }
        cancelRoundTimer();
        cancelTransitionTimer();
        phase = Phase.STARTER;
        currentPlayer = currentRound == 0 ? 1 : 2;
        selectedLeft = -1;
        selectedRight = -1;
        lastTimerSeconds = ConnectionsGameService.PHASE_TIME_MILLIS / 1000;

        for (int i = 0; i < ConnectionsGameService.PAIR_COUNT; i++) {
            matchedLeft[i] = false;
            matchedRight[i] = false;
            wrongLeft[i] = false;
            matchOwnerLeft[i] = 0;
            matchOwnerRight[i] = 0;
        }

        refreshUiFromState();
        if (isController()) {
            startTimer(ConnectionsGameService.PHASE_TIME_MILLIS);
        }
        publishState();
    }

    private void refreshUiFromState() {
        if (rounds.isEmpty()) {
            renderLoading();
            return;
        }

        if (gameFinished || phase == Phase.FINISHED) {
            tvRound.setText(R.string.connections_title);
            tvCurrentPlayer.setText("");
            tvPhaseInfo.setText(R.string.connections_round_done);
            tvTimer.setText(getString(R.string.connections_timer_seconds, 0));
            btnContinue.setEnabled(false);
            updateRoundTexts();
            refreshButtons();
            updateScoreText();
            refreshTurnIndicator();
            return;
        }

        tvRound.setText(getString(R.string.connections_round_title, currentRound + 1));
        tvCurrentPlayer.setText(phase == Phase.ROUND_FINISHED ? "" : getString(R.string.connections_current_player, currentPlayer));
        if (phase == Phase.STARTER) {
            tvPhaseInfo.setText(R.string.connections_phase_starter);
        } else if (phase == Phase.STEAL) {
            tvPhaseInfo.setText(getString(R.string.connections_phase_steal, currentPlayer));
        } else if (phase == Phase.ROUND_FINISHED) {
            tvPhaseInfo.setText(R.string.connections_round_finished);
        } else {
            tvPhaseInfo.setText("");
        }
        tvTimer.setText(getString(R.string.connections_timer_seconds, Math.max(0, lastTimerSeconds)));
        updateScoreText();
        updateRoundTexts();
        btnContinue.setText(currentRound == ConnectionsGameService.ROUND_COUNT - 1
                ? R.string.connections_finish_round
                : R.string.connections_open_next_round);
        btnContinue.setEnabled(false);
        refreshButtons();
        refreshTurnIndicator();
    }

    private void updateRoundTexts() {
        if (rounds.isEmpty()) {
            return;
        }
        ConnectionRound round = rounds.get(currentRound);
        for (int i = 0; i < ConnectionsGameService.PAIR_COUNT; i++) {
            leftButtons[i].setText(round.getLeftItems().get(i));
            rightButtons[i].setText(round.getRightItems().get(i));
        }
    }

    private void startTimer(long durationMs) {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                lastTimerSeconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(getString(R.string.connections_timer_seconds, lastTimerSeconds));
                publishState();
            }

            @Override
            public void onFinish() {
                lastTimerSeconds = 0;
                tvTimer.setText(getString(R.string.connections_timer_seconds, 0));
                completePhase();
            }
        }.start();
    }

    private void selectLeft(int index) {
        if (!canSelectLeft(index)) {
            return;
        }
        selectedLeft = index;
        refreshButtons();
        publishState();
        attemptMatch();
    }

    private void selectRight(int index) {
        if (!canSelectRight(index)) {
            return;
        }
        selectedRight = index;
        refreshButtons();
        publishState();
        attemptMatch();
    }

    private boolean canSelectLeft(int index) {
        return isController()
                && isRunningPhase()
                && !gameFinished
                && !matchedLeft[index]
                && !wrongLeft[index];
    }

    private boolean canSelectRight(int index) {
        return isController()
                && isRunningPhase()
                && !gameFinished
                && !matchedRight[index];
    }

    private void attemptMatch() {
        if (selectedLeft == -1 || selectedRight == -1 || rounds.isEmpty()) {
            return;
        }

        ConnectionRound round = rounds.get(currentRound);
        if (connectionsService.isCorrect(round, selectedLeft, selectedRight)) {
            matchedLeft[selectedLeft] = true;
            matchedRight[selectedRight] = true;
            matchOwnerLeft[selectedLeft] = currentPlayer;
            matchOwnerRight[selectedRight] = currentPlayer;
            if (currentPlayer == 1) {
                player1Score += ConnectionsGameService.POINTS_PER_MATCH;
            } else {
                player2Score += ConnectionsGameService.POINTS_PER_MATCH;
            }
            if (currentPlayer == myPlayerNumber) {
                statsMatched++;
            }
        } else {
            wrongLeft[selectedLeft] = true;
            if (currentPlayer == myPlayerNumber) {
                statsMissed++;
            }
        }

        selectedLeft = -1;
        selectedRight = -1;
        refreshUiFromState();
        publishState();

        if (!connectionsService.hasUnmatchedPairs(matchedLeft) || connectionsService.isPhaseComplete(matchedLeft, wrongLeft)) {
            schedulePhaseCompletion();
        }
    }

    private void schedulePhaseCompletion() {
        cancelRoundTimer();
        cancelTransitionTimer();
        transitionTimer = new CountDownTimer(ROUND_TRANSITION_MS, ROUND_TRANSITION_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                completePhase();
            }
        }.start();
    }

    private void completePhase() {
        if (!isRunningPhase() || gameFinished) {
            return;
        }
        cancelRoundTimer();
        selectedLeft = -1;
        selectedRight = -1;
        if (phase == Phase.STARTER && connectionsService.hasUnmatchedPairs(matchedLeft)) {
            openStealPhase();
            return;
        }
        finishRound();
    }

    private void openStealPhase() {
        phase = Phase.STEAL;
        currentPlayer = currentPlayer == 1 ? 2 : 1;
        resetWrongLeft();
        lastTimerSeconds = ConnectionsGameService.PHASE_TIME_MILLIS / 1000;
        refreshUiFromState();
        publishState();
        if (isController()) {
            startTimer(ConnectionsGameService.PHASE_TIME_MILLIS);
        }
    }

    private void finishRound() {
        cancelRoundTimer();
        phase = Phase.ROUND_FINISHED;
        selectedLeft = -1;
        selectedRight = -1;
        resetWrongLeft();
        lastTimerSeconds = 0;
        refreshUiFromState();
        publishState();
        scheduleNextStep();
    }

    private void scheduleNextStep() {
        cancelTransitionTimer();
        transitionTimer = new CountDownTimer(ROUND_TRANSITION_MS, ROUND_TRANSITION_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                if (isFinishing() || isDestroyed() || gameFinished) {
                    return;
                }
                if (currentRound < ConnectionsGameService.ROUND_COUNT - 1) {
                    currentRound++;
                    startRound();
                    return;
                }
                finishGameWithResult();
            }
        }.start();
    }

    private void finishGameWithResult() {
        if (gameFinished) {
            return;
        }
        gameFinished = true;
        phase = Phase.FINISHED;
        cancelRoundTimer();
        cancelTransitionTimer();
        refreshUiFromState();
        publishState();
        sendForceFinishEvent();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
        PlayerStatsService.putBaseGameStats(resultIntent, GAME_ID, 0, 20);
        resultIntent.putExtra(PlayerStatsService.EXTRA_STATS_CONNECTIONS_MATCHED, statsMatched);
        resultIntent.putExtra(PlayerStatsService.EXTRA_STATS_CONNECTIONS_MISSED, statsMissed);
        setResult(RESULT_OK, resultIntent);
        btnContinue.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 500);
    }

    private void resetWrongLeft() {
        for (int i = 0; i < wrongLeft.length; i++) {
            wrongLeft[i] = false;
        }
    }

    private void refreshButtons() {
        boolean spectator = isRunningPhase() && !isController();
        float alpha = spectator ? 0.55f : 1f;
        for (int i = 0; i < ConnectionsGameService.PAIR_COUNT; i++) {
            setConnectionButtonInteractive(leftButtons[i], canSelectLeft(i));
            setConnectionButtonInteractive(rightButtons[i], canSelectRight(i));
            leftButtons[i].setAlpha(alpha);
            rightButtons[i].setAlpha(alpha);

            if (matchedLeft[i]) {
                setConnectionButtonBackground(leftButtons[i], R.drawable.connections_matched_player1_bg);
            } else if (selectedLeft == i) {
                setConnectionButtonBackground(leftButtons[i], R.drawable.connections_selected_gray_bg);
            } else if (wrongLeft[i]) {
                setConnectionButtonBackground(leftButtons[i], R.drawable.connections_wrong_bg);
            } else {
                setConnectionButtonBackground(leftButtons[i], R.drawable.connections_item_bg);
            }

            if (matchedRight[i]) {
                setConnectionButtonBackground(rightButtons[i], R.drawable.connections_matched_player1_bg);
            } else if (selectedRight == i) {
                setConnectionButtonBackground(rightButtons[i], R.drawable.connections_selected_gray_bg);
            } else {
                setConnectionButtonBackground(rightButtons[i], R.drawable.connections_item_bg);
            }
        }
    }

    private void setConnectionButtonBackground(Button button, int drawableRes) {
        button.setBackgroundTintList(null);
        button.setBackgroundResource(drawableRes);
    }

    private void setConnectionButtonInteractive(Button button, boolean interactive) {
        button.setEnabled(true);
        button.setClickable(interactive);
        button.setFocusable(interactive);
    }

    private void updateScoreText() {
        tvScore.setText(getString(R.string.connections_score_format, player1Score, player2Score));
        tvHeaderLeftScore.setText(String.valueOf(player1Score));
        tvHeaderRightScore.setText(String.valueOf(player2Score));
    }

    private void refreshTurnIndicator() {
        if (gameFinished || phase == Phase.FINISHED || phase == Phase.ROUND_FINISHED || phase == Phase.LOADING) {
            turnIndicatorAnimator.setActivePlayer(null);
            return;
        }
        turnIndicatorAnimator.setActivePlayer(currentPlayer);
    }

    private void bindMatchHeader() {
        tvHeaderLeftName.setText(headerName(player1DisplayName));
        tvHeaderRightName.setText(headerName(player2DisplayName));
        tvHeaderLeftAvatar.setText(initialForName(player1DisplayName, "1"));
        tvHeaderRightAvatar.setText(initialForName(player2DisplayName, "2"));
        tvHeaderLeftScore.setText(String.valueOf(player1Score));
        tvHeaderRightScore.setText(String.valueOf(player2Score));
    }

    private void loadOpponentStatus() {
        String opponentName = myPlayerNumber == 1 ? player2DisplayName : player1DisplayName;
        if (isLikelyGuestName(opponentName)) {
            opponentTokens = null;
            opponentStars = null;
            opponentLeague = null;
            return;
        }
        economyService.getEconomyByUsername(opponentName, new EconomyService.EconomyCallback() {
            @Override
            public void onSuccess(java.util.Map<String, Long> values) {
                opponentTokens = values.get("tokens");
                opponentStars = values.get("stars");
                opponentLeague = values.get("league");
            }

            @Override
            public void onError(String message) {
                opponentTokens = null;
                opponentStars = null;
                opponentLeague = null;
            }
        });
    }

    private void setupMyProfileTap() {
        TextView myAvatar = myPlayerNumber == 1 ? tvHeaderLeftAvatar : tvHeaderRightAvatar;
        TextView myName = myPlayerNumber == 1 ? tvHeaderLeftName : tvHeaderRightName;
        TextView opponentAvatar = myPlayerNumber == 1 ? tvHeaderRightAvatar : tvHeaderLeftAvatar;
        TextView opponentName = myPlayerNumber == 1 ? tvHeaderRightName : tvHeaderLeftName;

        myAvatar.setOnClickListener(v -> showMyStatusDialog());
        myName.setOnClickListener(v -> showMyStatusDialog());
        opponentAvatar.setOnClickListener(v -> showOpponentStatusDialog());
        opponentName.setOnClickListener(v -> showOpponentStatusDialog());
    }

    private void showMyStatusDialog() {
        String myDisplay = myPlayerNumber == 1 ? player1DisplayName : player2DisplayName;
        showStatusDialog(
                "Moj status",
                myDisplay,
                myTokens,
                myStars,
                myLeague,
                false
        );
    }

    private void showOpponentStatusDialog() {
        String opponentName = myPlayerNumber == 1 ? player2DisplayName : player1DisplayName;
        showStatusDialog(
                "Status protivnika",
                opponentName,
                opponentTokens,
                opponentStars,
                opponentLeague,
                isLikelyGuestName(opponentName)
        );
    }

    private void showStatusDialog(
            String title,
            String username,
            Long tokens,
            Long stars,
            Long league,
            boolean guest
    ) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(statusMessage(username, tokens, stars, league, guest))
                .setPositiveButton("Zatvori", null)
                .create();
        dialog.show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextSize(17f);
            messageView.setLineSpacing(6f, 1.15f);
        }
    }

    private boolean isLikelyGuestName(String name) {
        if (name == null) {
            return true;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() || trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("gost");
    }

    private String statusMessage(
            String username,
            Long tokens,
            Long stars,
            Long league,
            boolean guest
    ) {
        String safeName = (username == null || username.trim().isEmpty()) ? "-" : username.trim();
        if (guest) {
            return "Igrac: " + safeName + "\n\nNeregistrovan igrac";
        }
        String t = tokens == null ? "-" : String.valueOf(tokens);
        String s = stars == null ? "-" : String.valueOf(stars);
        String l = league == null ? "-" : String.valueOf(league);
        return "Igrac: " + safeName + "\n\nTokeni: " + t + "\nZvezde: " + s + "\nLiga: " + l;
    }

    private String displayNameOrFallback(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String initialForName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return String.valueOf(Character.toUpperCase(trimmed.charAt(0)));
    }

    private String headerName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 9) {
            return trimmed;
        }
        return trimmed.substring(0, 9) + "...";
    }

    private void publishState() {
        if (soloMode || TextUtils.isEmpty(matchRoomId) || rounds.isEmpty()) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("sender", myPlayerNumber);
            data.put("round", currentRound);
            data.put("phase", phase.name());
            data.put("currentPlayer", currentPlayer);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("selectedLeft", selectedLeft);
            data.put("selectedRight", selectedRight);
            data.put("gameFinished", gameFinished);
            data.put("timer", lastTimerSeconds);
            data.put("matchedLeft", booleanArrayToJson(matchedLeft));
            data.put("matchedRight", booleanArrayToJson(matchedRight));
            data.put("wrongLeft", booleanArrayToJson(wrongLeft));
            data.put("ownerLeft", intArrayToJson(matchOwnerLeft));
            data.put("ownerRight", intArrayToJson(matchOwnerRight));
            data.put("rounds", roundsToJson());

            Intent i = new Intent(MatchActivity.ACTION_GAME_COMMAND);
            i.putExtra(MatchActivity.EXTRA_ROOM_ID, matchRoomId);
            i.putExtra(MatchActivity.EXTRA_GAME, GAME_ID);
            i.putExtra(MatchActivity.EXTRA_EVENT, "state");
            i.putExtra(MatchActivity.EXTRA_DATA, data.toString());
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    private void applyRemoteState(JSONObject data) {
        if (soloMode || data.optInt("sender", 0) == myPlayerNumber) {
            return;
        }
        JSONArray roundsJson = data.optJSONArray("rounds");
        if (roundsJson != null && roundsJson.length() >= ConnectionsGameService.ROUND_COUNT) {
            rounds.clear();
            rounds.addAll(parseRounds(roundsJson));
        }
        if (rounds.isEmpty()) {
            return;
        }

        currentRound = data.optInt("round", currentRound);
        if (currentRound < 0 || currentRound >= rounds.size()) {
            return;
        }
        phase = phaseFromString(data.optString("phase", phase.name()));
        currentPlayer = data.optInt("currentPlayer", currentPlayer);
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        selectedLeft = data.optInt("selectedLeft", selectedLeft);
        selectedRight = data.optInt("selectedRight", selectedRight);
        gameFinished = data.optBoolean("gameFinished", gameFinished);
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        jsonToBooleanArray(data.optJSONArray("matchedLeft"), matchedLeft);
        jsonToBooleanArray(data.optJSONArray("matchedRight"), matchedRight);
        jsonToBooleanArray(data.optJSONArray("wrongLeft"), wrongLeft);
        jsonToIntArray(data.optJSONArray("ownerLeft"), matchOwnerLeft);
        jsonToIntArray(data.optJSONArray("ownerRight"), matchOwnerRight);
        cancelRoundTimer();
        cancelTransitionTimer();
        refreshUiFromState();
        if (gameFinished || phase == Phase.FINISHED) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
            setResult(RESULT_OK, resultIntent);
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
            return;
        }
        if (isController() && isRunningPhase() && lastTimerSeconds > 0) {
            startTimer(Math.max(1000L, lastTimerSeconds * 1000L));
        }
    }

    private Phase phaseFromString(String value) {
        try {
            return Phase.valueOf(value);
        } catch (Exception ignored) {
            return phase;
        }
    }

    private JSONArray roundsToJson() {
        JSONArray out = new JSONArray();
        for (ConnectionRound round : rounds) {
            JSONObject item = new JSONObject();
            JSONArray left = new JSONArray();
            JSONArray right = new JSONArray();
            JSONArray mapping = new JSONArray();
            try {
                item.put("title", round.getTitle());
                for (String value : round.getLeftItems()) {
                    left.put(value);
                }
                for (String value : round.getRightItems()) {
                    right.put(value);
                }
                for (Integer value : round.getMapping()) {
                    mapping.put(value);
                }
                item.put("leftItems", left);
                item.put("rightItems", right);
                item.put("mapping", mapping);
                out.put(item);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private List<ConnectionRound> parseRounds(JSONArray values) {
        List<ConnectionRound> parsed = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONArray leftJson = item.optJSONArray("leftItems");
            JSONArray rightJson = item.optJSONArray("rightItems");
            JSONArray mappingJson = item.optJSONArray("mapping");
            if (leftJson == null || rightJson == null || mappingJson == null
                    || leftJson.length() != ConnectionsGameService.PAIR_COUNT
                    || rightJson.length() != ConnectionsGameService.PAIR_COUNT
                    || mappingJson.length() != ConnectionsGameService.PAIR_COUNT) {
                continue;
            }
            List<String> left = new ArrayList<>();
            List<String> right = new ArrayList<>();
            List<Integer> mapping = new ArrayList<>();
            for (int j = 0; j < ConnectionsGameService.PAIR_COUNT; j++) {
                left.add(leftJson.optString(j));
                right.add(rightJson.optString(j));
                mapping.add(mappingJson.optInt(j, -1));
            }
            if (isValidMapping(mapping)) {
                parsed.add(new ConnectionRound(item.optString("title", ""), left, right, mapping));
            }
        }
        return parsed;
    }

    private boolean isValidMapping(List<Integer> mapping) {
        if (mapping.size() != ConnectionsGameService.PAIR_COUNT) {
            return false;
        }
        for (Integer value : mapping) {
            if (value == null || value < 0 || value >= ConnectionsGameService.PAIR_COUNT) {
                return false;
            }
        }
        return true;
    }

    private JSONArray booleanArrayToJson(boolean[] values) {
        JSONArray out = new JSONArray();
        for (boolean value : values) {
            out.put(value);
        }
        return out;
    }

    private JSONArray intArrayToJson(int[] values) {
        JSONArray out = new JSONArray();
        for (int value : values) {
            out.put(value);
        }
        return out;
    }

    private void jsonToBooleanArray(JSONArray values, boolean[] target) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < target.length && i < values.length(); i++) {
            target[i] = values.optBoolean(i, target[i]);
        }
    }

    private void jsonToIntArray(JSONArray values, int[] target) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < target.length && i < values.length(); i++) {
            target[i] = values.optInt(i, target[i]);
        }
    }

    private void showLeaveGameDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Napustanje partije")
                .setMessage("Da li ste sigurni da zelite da napustite igru?")
                .setPositiveButton("Da", (dialog, which) -> {
                    Intent data = new Intent();
                    data.putExtra(MatchActivity.EXTRA_MATCH_FORFEIT, true);
                    setResult(RESULT_CANCELED, data);
                    finish();
                })
                .setNegativeButton("Ne", null)
                .show();
    }

    private void enableSoloModeAfterForfeit() {
        soloMode = true;
        refreshUiFromState();
        if (isRunningPhase() && roundTimer == null && lastTimerSeconds > 0) {
            startTimer(Math.max(1000L, lastTimerSeconds * 1000L));
        }
    }

    private void sendForceFinishEvent() {
        if (TextUtils.isEmpty(matchRoomId)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            Intent i = new Intent(MatchActivity.ACTION_GAME_COMMAND);
            i.putExtra(MatchActivity.EXTRA_ROOM_ID, matchRoomId);
            i.putExtra(MatchActivity.EXTRA_GAME, GAME_ID);
            i.putExtra(MatchActivity.EXTRA_EVENT, "force_finish");
            i.putExtra(MatchActivity.EXTRA_DATA, data.toString());
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    private void applyForceFinish(JSONObject data) {
        if (remoteFinishHandled) {
            return;
        }
        remoteFinishHandled = true;
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        cancelRoundTimer();
        cancelTransitionTimer();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void cancelRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void cancelTransitionTimer() {
        if (transitionTimer != null) {
            transitionTimer.cancel();
            transitionTimer = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(gameEventReceiver, new IntentFilter(MatchActivity.ACTION_GAME_EVENT));
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(gameEventReceiver);
        } catch (Exception ignored) {
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelRoundTimer();
        cancelTransitionTimer();
        turnIndicatorAnimator.clear();
    }
}
