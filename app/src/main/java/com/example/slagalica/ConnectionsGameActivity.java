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

import org.json.JSONArray;
import org.json.JSONObject;

public class ConnectionsGameActivity extends AppCompatActivity {
    private static final String GAME_ID = "connections";

    private final String[][] leftRounds = {
            {"Zeljko Joksimovic", "Marija Serifovic", "Bajaga", "Sasa Matic", "Zdravko Colic"},
            {"Tesla", "Andric", "Pupin", "Milankovic", "Mokranjac"}
    };

    private final String[][] rightRounds = {
            {"Lane moje", "Molitva", "Moji drugovi", "Maskara", "Ti si mi u krvi"},
            {"Na Drini cuprija", "Himna Vuku", "Milutin Milankovic", "Naizmenicna struja", "Idvorske price"}
    };

    private final int[][] mappingRounds = {
            {0, 1, 2, 3, 4},
            {3, 0, 4, 2, 1}
    };

    private final int[] starterByRound = {1, 2};

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

    private int currentRound = 0;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int selectedLeft = -1;
    private int selectedRight = -1;
    private final boolean[] matchedLeft = new boolean[5];
    private final boolean[] matchedRight = new boolean[5];
    private final int[] matchOwnerLeft = new int[5];
    private final int[] matchOwnerRight = new int[5];
    private boolean stealPhase = false;
    private boolean roundFinished = false;
    private boolean gameFinished = false;
    private int lastTimerSeconds = 30;
    private CountDownTimer roundTimer;

    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private boolean soloMode = false;
    private String player1DisplayName = "Igrac 1";
    private String player2DisplayName = "Igrac 2";
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

        for (int i = 0; i < 5; i++) {
            final int index = i;
            leftButtons[i].setOnClickListener(v -> selectLeft(index));
            rightButtons[i].setOnClickListener(v -> selectRight(index));
        }

        btnContinue.setOnClickListener(v -> handleContinue());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });

        startRound();
    }

    private boolean isController() {
        return soloMode || currentPlayer == myPlayerNumber;
    }

    private void startRound() {
        cancelRoundTimer();
        currentPlayer = starterByRound[currentRound];
        stealPhase = false;
        roundFinished = false;
        selectedLeft = -1;
        selectedRight = -1;
        lastTimerSeconds = 30;

        for (int i = 0; i < 5; i++) {
            matchedLeft[i] = false;
            matchedRight[i] = false;
            matchOwnerLeft[i] = 0;
            matchOwnerRight[i] = 0;
        }

        refreshUiFromState();
        if (isController()) {
            startTimer();
        }
        refreshTurnIndicator();
        publishState();
    }

    private void refreshUiFromState() {
        if (gameFinished) {
            tvRound.setText(R.string.connections_title);
            tvCurrentPlayer.setText("");
            tvPhaseInfo.setText("");
            tvTimer.setText(getString(R.string.connections_timer_seconds, 0));
            btnContinue.setEnabled(false);
            refreshButtons();
            return;
        }

        tvRound.setText(R.string.connections_title);
        tvCurrentPlayer.setText(roundFinished ? "" : getString(R.string.connections_current_player, currentPlayer));
        tvPhaseInfo.setText("");
        updateScoreText();
        tvTimer.setText(getString(R.string.connections_timer_seconds, Math.max(0, lastTimerSeconds)));

        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(leftRounds[currentRound][i]);
            rightButtons[i].setText(rightRounds[currentRound][i]);
        }

        btnContinue.setText(R.string.connections_finish_round);
        btnContinue.setEnabled(roundFinished && isController());
        refreshButtons();
        refreshTurnIndicator();
    }

    private void startTimer() {
        startTimerWithDuration(30000);
    }

    private void startTimerWithDuration(long durationMs) {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                lastTimerSeconds = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.connections_timer_seconds, lastTimerSeconds));
                publishState();
            }

            @Override
            public void onFinish() {
                lastTimerSeconds = 0;
                tvTimer.setText(getString(R.string.connections_timer_seconds, 0));
                if (!stealPhase && hasUnmatchedPairs()) {
                    openStealPhase();
                } else {
                    finishRound();
                }
            }
        }.start();
    }

    private void selectLeft(int index) {
        if (!isController() || roundFinished || matchedLeft[index] || gameFinished) {
            return;
        }
        selectedLeft = index;
        refreshButtons();
        attemptMatch();
        publishState();
    }

    private void selectRight(int index) {
        if (!isController() || roundFinished || matchedRight[index] || gameFinished) {
            return;
        }
        selectedRight = index;
        refreshButtons();
        attemptMatch();
        publishState();
    }

    private void attemptMatch() {
        if (selectedLeft == -1 || selectedRight == -1) {
            return;
        }

        if (mappingRounds[currentRound][selectedLeft] == selectedRight) {
            matchedLeft[selectedLeft] = true;
            matchedRight[selectedRight] = true;
            matchOwnerLeft[selectedLeft] = currentPlayer;
            matchOwnerRight[selectedRight] = currentPlayer;

            if (currentPlayer == 1) {
                player1Score += 2;
            } else {
                player2Score += 2;
            }

            updateScoreText();
            selectedLeft = -1;
            selectedRight = -1;
            refreshButtons();

            if (!hasUnmatchedPairs()) {
                finishRound();
            }
            return;
        }

        selectedLeft = -1;
        selectedRight = -1;
        refreshButtons();
    }

    private void openStealPhase() {
        stealPhase = true;
        currentPlayer = currentPlayer == 1 ? 2 : 1;
        selectedLeft = -1;
        selectedRight = -1;
        lastTimerSeconds = 30;
        refreshUiFromState();
        if (isController()) {
            startTimer();
        } else {
            cancelRoundTimer();
        }
        publishState();
    }

    private void finishRound() {
        cancelRoundTimer();
        roundFinished = true;
        selectedLeft = -1;
        selectedRight = -1;
        refreshUiFromState();
        publishState();
    }

    private void handleContinue() {
        if (!roundFinished || gameFinished || !isController()) {
            return;
        }

        if (currentRound == 0) {
            currentRound = 1;
            startRound();
            return;
        }

        finishGameWithResult();
    }

    private void finishGameWithResult() {
        if (gameFinished) {
            return;
        }
        gameFinished = true;
        cancelRoundTimer();
        refreshUiFromState();
        publishState();
        sendForceFinishEvent();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
        setResult(RESULT_OK, resultIntent);
        btnContinue.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 500);
    }

    private boolean hasUnmatchedPairs() {
        for (boolean matched : matchedLeft) {
            if (!matched) {
                return true;
            }
        }
        return false;
    }

    private void refreshButtons() {
        boolean canInteract = isController() && !roundFinished && !gameFinished;
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setEnabled(canInteract && !matchedLeft[i]);
            rightButtons[i].setEnabled(canInteract && !matchedRight[i]);

            if (matchedLeft[i]) {
                leftButtons[i].setBackgroundResource(matchOwnerLeft[i] == 1
                        ? R.drawable.connections_matched_player1_bg
                        : R.drawable.connections_matched_player2_bg);
            } else if (selectedLeft == i) {
                leftButtons[i].setBackgroundResource(R.drawable.connections_selected_bg);
            } else {
                leftButtons[i].setBackgroundResource(R.drawable.connections_item_bg);
            }

            if (matchedRight[i]) {
                rightButtons[i].setBackgroundResource(matchOwnerRight[i] == 1
                        ? R.drawable.connections_matched_player1_bg
                        : R.drawable.connections_matched_player2_bg);
            } else if (selectedRight == i) {
                rightButtons[i].setBackgroundResource(R.drawable.connections_selected_bg);
            } else {
                rightButtons[i].setBackgroundResource(R.drawable.connections_item_bg);
            }
        }
    }

    private void updateScoreText() {
        tvScore.setText(getString(R.string.connections_score_format, player1Score, player2Score));
        tvHeaderLeftScore.setText(String.valueOf(player1Score));
        tvHeaderRightScore.setText(String.valueOf(player2Score));
    }

    private void refreshTurnIndicator() {
        if (gameFinished || roundFinished) {
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
        if (soloMode || TextUtils.isEmpty(matchRoomId) || (!isController() && !roundFinished)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("round", currentRound);
            data.put("currentPlayer", currentPlayer);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("selectedLeft", selectedLeft);
            data.put("selectedRight", selectedRight);
            data.put("stealPhase", stealPhase);
            data.put("roundFinished", roundFinished);
            data.put("gameFinished", gameFinished);
            data.put("timer", lastTimerSeconds);
            data.put("matchedLeft", booleanArrayToJson(matchedLeft));
            data.put("matchedRight", booleanArrayToJson(matchedRight));
            data.put("ownerLeft", intArrayToJson(matchOwnerLeft));
            data.put("ownerRight", intArrayToJson(matchOwnerRight));

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
        if (soloMode) {
            return;
        }
        currentRound = data.optInt("round", currentRound);
        if (currentRound < 0 || currentRound >= leftRounds.length) {
            return;
        }
        currentPlayer = data.optInt("currentPlayer", currentPlayer);
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        selectedLeft = data.optInt("selectedLeft", selectedLeft);
        selectedRight = data.optInt("selectedRight", selectedRight);
        stealPhase = data.optBoolean("stealPhase", stealPhase);
        roundFinished = data.optBoolean("roundFinished", roundFinished);
        gameFinished = data.optBoolean("gameFinished", gameFinished);
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        jsonToBooleanArray(data.optJSONArray("matchedLeft"), matchedLeft);
        jsonToBooleanArray(data.optJSONArray("matchedRight"), matchedRight);
        jsonToIntArray(data.optJSONArray("ownerLeft"), matchOwnerLeft);
        jsonToIntArray(data.optJSONArray("ownerRight"), matchOwnerRight);
        cancelRoundTimer();
        refreshUiFromState();
        if (gameFinished) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
            setResult(RESULT_OK, resultIntent);
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }
        refreshTurnIndicator();
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
        if (!roundFinished && !gameFinished && roundTimer == null && lastTimerSeconds > 0) {
            startTimerWithDuration(lastTimerSeconds * 1000L);
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
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
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
        turnIndicatorAnimator.clear();
    }
}


