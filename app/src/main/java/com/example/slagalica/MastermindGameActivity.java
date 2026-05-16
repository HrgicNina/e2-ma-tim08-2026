package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.domain.MastermindGameService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Random;

public class MastermindGameActivity extends AppCompatActivity {
    private static final String GAME_ID = "master";

    private enum Phase { ROUND, STEAL, ROUND_END, FINISHED }

    private static final int MAIN_ROWS = 6;
    private static final int TOTAL_ROWS = 8;
    private static final int STEAL_ROW_INDEX = 6;
    private static final int REVEAL_ROW_INDEX = 7;
    private static final int COLS = 4;

    private static final String[] SYMBOLS = {
            "\uD83E\uDD89",
            "\u2663",
            "\u2660",
            "\u2665",
            "\u2666",
            "\u2B50"
    };

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvHeaderLeftAvatar;
    private TextView tvHeaderLeftName;
    private TextView tvHeaderLeftScore;
    private TextView tvHeaderRightAvatar;
    private TextView tvHeaderRightName;
    private TextView tvHeaderRightScore;
    private TurnIndicatorAnimator turnIndicatorAnimator;
    private TextView tvPhaseInfo;
    private LinearLayout boardContainer;
    private LinearLayout symbolBar;
    private Button btnSubmit;

    private final TextView[][] guessCells = new TextView[TOTAL_ROWS][COLS];
    private final View[][] pegCells = new View[TOTAL_ROWS][COLS];
    private final int[][] boardGuessSymbols = new int[TOTAL_ROWS][COLS];
    private final int[][] boardPegStates = new int[TOTAL_ROWS][COLS];

    private final int[] currentGuess = {-1, -1, -1, -1};
    private int activeRow = 0;

    private MastermindGameService gameService;
    private CountDownTimer prepTimer;
    private CountDownTimer roundTimer;
    private CountDownTimer stealTimer;
    private CountDownTimer roundTransitionTimer;
    private CountDownTimer finishTimer;
    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private boolean soloMode = false;
    private int lastTimerSeconds = 30;
    private boolean remoteFinishHandled = false;
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
            if ("round_change".equals(event)) {
                try {
                    applyRoundChange(new JSONObject(raw == null ? "{}" : raw));
                } catch (Exception ignored) {
                }
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

    private int currentRound = 1;
    private int roundStarter = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int[] currentSecret;
    private Phase phase = Phase.ROUND;
    private boolean stealAttemptUsed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mastermind_game);

        gameService = new MastermindGameService();
        matchRoomId = getIntent().getStringExtra("match_room_id");
        if (matchRoomId == null) {
            matchRoomId = "";
        }
        myPlayerNumber = getIntent().getIntExtra("match_my_player_number", 1);
        soloMode = getIntent().getBooleanExtra(MatchActivity.EXTRA_MATCH_SOLO_MODE, false);
        player1Score = getIntent().getIntExtra(MatchActivity.EXTRA_MATCH_BASE_PLAYER1_SCORE, 0);
        player2Score = getIntent().getIntExtra(MatchActivity.EXTRA_MATCH_BASE_PLAYER2_SCORE, 0);
        player1DisplayName = displayNameOrFallback(
                getIntent().getStringExtra(MatchActivity.EXTRA_MATCH_PLAYER1_NAME),
                "Igrac 1");
        player2DisplayName = displayNameOrFallback(
                getIntent().getStringExtra(MatchActivity.EXTRA_MATCH_PLAYER2_NAME),
                "Igrac 2");

        tvRound = findViewById(R.id.tvMasterRound);
        tvCurrentPlayer = findViewById(R.id.tvMasterCurrentPlayer);
        tvTimer = findViewById(R.id.tvMasterTimer);
        tvScore = findViewById(R.id.tvMasterScore);
        tvHeaderLeftAvatar = findViewById(R.id.tvHeaderLeftAvatar);
        tvHeaderLeftName = findViewById(R.id.tvHeaderLeftName);
        tvHeaderLeftScore = findViewById(R.id.tvHeaderLeftScore);
        tvHeaderRightAvatar = findViewById(R.id.tvHeaderRightAvatar);
        tvHeaderRightName = findViewById(R.id.tvHeaderRightName);
        tvHeaderRightScore = findViewById(R.id.tvHeaderRightScore);
        turnIndicatorAnimator = new TurnIndicatorAnimator(tvHeaderLeftAvatar, tvHeaderRightAvatar);
        tvPhaseInfo = findViewById(R.id.tvMasterPhaseInfo);
        boardContainer = findViewById(R.id.masterBoardContainer);
        symbolBar = findViewById(R.id.masterSymbolBar);
        btnSubmit = findViewById(R.id.btnMasterSubmit);
        bindMatchHeader();

        buildBoard();
        buildSymbolBar();

        btnSubmit.setOnClickListener(v -> submitGuess());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });

        startRound();
    }

    private void buildBoard() {
        boardContainer.removeAllViews();

        for (int row = 0; row < TOTAL_ROWS; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (row == STEAL_ROW_INDEX) {
                lineParams.topMargin = dp(18);
            } else if (row == REVEAL_ROW_INDEX) {
                lineParams.topMargin = dp(4);
            }
            line.setLayoutParams(lineParams);
            line.setPadding(0, dp(4), 0, dp(4));

            LinearLayout guessPart = new LinearLayout(this);
            guessPart.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams guessParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            guessPart.setLayoutParams(guessParams);

            for (int col = 0; col < COLS; col++) {
                TextView cell = new TextView(this);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
                cellParams.setMargins(dp(2), 0, dp(2), 0);
                cell.setLayoutParams(cellParams);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                cell.setBackground(ContextCompat.getDrawable(this, R.drawable.master_guess_cell_bg));
                cell.setText(" ");

                final int r = row;
                final int c = col;
                cell.setOnClickListener(v -> {
                    if (phase == Phase.FINISHED || r != activeRow || !isMyTurn()) {
                        return;
                    }
                    currentGuess[c] = -1;
                    boardGuessSymbols[r][c] = -1;
                    guessCells[r][c].setText(" ");
                    publishState();
                });

                guessCells[row][col] = cell;
                guessPart.addView(cell);
            }

            GridLayout pegPart = new GridLayout(this);
            pegPart.setColumnCount(2);
            pegPart.setRowCount(2);
            LinearLayout.LayoutParams pegParams = new LinearLayout.LayoutParams(dp(56), dp(54));
            pegParams.setMargins(dp(6), 0, 0, 0);
            pegPart.setLayoutParams(pegParams);
            pegPart.setPadding(0, dp(4), 0, 0);

            for (int i = 0; i < COLS; i++) {
                View peg = new View(this);
                GridLayout.LayoutParams p = new GridLayout.LayoutParams();
                p.width = dp(18);
                p.height = dp(18);
                p.setMargins(dp(4), dp(2), dp(4), dp(2));
                peg.setLayoutParams(p);
                peg.setBackground(ContextCompat.getDrawable(this, R.drawable.master_peg_bg));
                peg.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9EC6EA")));
                pegCells[row][i] = peg;
                pegPart.addView(peg);
            }

            line.addView(guessPart);
            line.addView(pegPart);
            boardContainer.addView(line);
        }
    }

    private void buildSymbolBar() {
        symbolBar.removeAllViews();

        for (int i = 0; i < SYMBOLS.length; i++) {
            Button symbolBtn = new Button(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            symbolBtn.setLayoutParams(lp);
            symbolBtn.setText(SYMBOLS[i]);
            symbolBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, i == 5 ? 27 : 24);
            symbolBtn.setTextColor(symbolColor(i));
            symbolBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.master_symbol_btn_bg));

            final int symbol = i;
            symbolBtn.setOnClickListener(v -> placeSymbol(symbol));
            symbolBar.addView(symbolBtn);
        }
    }

    private void placeSymbol(int symbol) {
        if (phase == Phase.FINISHED || !isMyTurn()) {
            return;
        }

        for (int col = 0; col < COLS; col++) {
            if (currentGuess[col] == -1) {
                currentGuess[col] = symbol;
                boardGuessSymbols[activeRow][col] = symbol;
                guessCells[activeRow][col].setText(SYMBOLS[symbol]);
                guessCells[activeRow][col].setTextColor(symbolColor(symbol));
                publishState();
                return;
            }
        }
    }

    private void startRound() {
        phase = Phase.ROUND;
        remoteFinishHandled = false;
        activeRow = 0;
        if (matchRoomId.isEmpty()) {
            currentSecret = gameService.generateSecretCode();
        } else {
            int seed = (matchRoomId + "_master_" + currentRound).hashCode();
            currentSecret = gameService.generateSecretCode(new Random(seed));
        }
        stealAttemptUsed = false;

        resetBoardVisuals();

        tvRound.setText("");
        tvCurrentPlayer.setText(getString(R.string.master_current_player, roundStarter));
        tvPhaseInfo.setText(R.string.master_round_starts_in);
        updateScoreText();
        tvTimer.setText(getString(R.string.master_timer_seconds, 30));
        setInteractionEnabled(false);
        refreshTurnIndicator();
        startPrepCountdown();
    }

    private void startPrepCountdown() {
        cancelTimers();
        prepTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvPhaseInfo.setText(getString(R.string.master_round_starts_in_count, seconds));
                tvTimer.setText(getString(R.string.master_timer_seconds, 30));
                lastTimerSeconds = 30;
                if (!soloMode && currentRound > 1) {
                    sendRoundChangeEvent();
                }
            }

            @Override
            public void onFinish() {
                tvPhaseInfo.setText(R.string.master_phase_round);
                tvTimer.setText(getString(R.string.master_timer_seconds, 30));
                boolean myTurn = soloMode || roundStarter == myPlayerNumber;
                setInteractionEnabled(myTurn);
                if (myTurn) {
                    startRoundTimer(30000);
                } else {
                    cancelRoundAndStealTimers();
                }
                publishState();
                refreshTurnIndicator();
            }
        }.start();
    }

    private void resetBoardVisuals() {
        for (int r = 0; r < TOTAL_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                boardGuessSymbols[r][c] = -1;
                boardPegStates[r][c] = 0;
                guessCells[r][c].setText(" ");
                guessCells[r][c].setTextColor(Color.BLACK);
                pegCells[r][c].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9EC6EA")));
            }
        }
        clearCurrentGuess();
    }

    private void startRoundTimer(long durationMs) {
        cancelRoundAndStealTimers();
        roundTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.master_timer_seconds, sec));
                lastTimerSeconds = sec;
                publishState();
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.master_timer_seconds, 0));
                openStealPhase();
            }
        }.start();
    }

    private void openStealPhase() {
        if (phase == Phase.STEAL || phase == Phase.FINISHED) {
            return;
        }
        phase = Phase.STEAL;
        lastTimerSeconds = 10;
        tvTimer.setText(getString(R.string.master_timer_seconds, 10));
        int stealPlayer = opponent(roundStarter);
        tvCurrentPlayer.setText(getString(R.string.master_current_player, stealPlayer));
        tvPhaseInfo.setText(getString(R.string.master_phase_steal, stealPlayer));

        activeRow = STEAL_ROW_INDEX;
        clearCurrentGuess();
        boolean myStealTurn = soloMode || stealPlayer == myPlayerNumber;
        setInteractionEnabled(myStealTurn);
        publishState();
        refreshTurnIndicator();

        if (myStealTurn) {
            startStealTimer(10000);
        } else {
            cancelRoundAndStealTimers();
        }
    }

    private void startStealTimer(long durationMs) {
        cancelRoundAndStealTimers();
        stealTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.master_timer_seconds, sec));
                lastTimerSeconds = sec;
                publishState();
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.master_timer_seconds, 0));
                finishRound();
            }
        }.start();
    }

    private void submitGuess() {
        if (!isMyTurn()) {
            return;
        }

        if (!isGuessComplete()) {
            Toast.makeText(this, R.string.master_fill_all_slots, Toast.LENGTH_SHORT).show();
            return;
        }

        int[] guess = currentGuess.clone();
        MastermindGameService.GuessResult result = gameService.evaluateGuess(currentSecret, guess);
        paintFeedback(activeRow, result.exact, result.colorOnly);

        if (phase == Phase.ROUND) {
            int attemptNumber = activeRow + 1;
            if (result.solved) {
                int points = gameService.pointsForSolvedAttempt(attemptNumber);
                addPoints(soloMode ? myPlayerNumber : roundStarter, points);
                Toast.makeText(this, getString(R.string.master_solved_points, points), Toast.LENGTH_SHORT).show();
                finishRound();
                return;
            }

            if (attemptNumber >= MAIN_ROWS) {
                openStealPhase();
                return;
            }

            activeRow++;
            clearCurrentGuess();
            publishState();
            return;
        }

        if (phase == Phase.STEAL) {
            if (stealAttemptUsed) {
                Toast.makeText(this, R.string.master_steal_single_attempt, Toast.LENGTH_SHORT).show();
                return;
            }
            stealAttemptUsed = true;

            if (result.solved) {
                int stealPlayer = opponent(roundStarter);
                addPoints(soloMode ? myPlayerNumber : stealPlayer, 10);
                Toast.makeText(this, getString(R.string.master_steal_won_points, stealPlayer), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.master_wrong_guess, Toast.LENGTH_SHORT).show();
            }
            finishRound();
        }
    }

    private void paintFeedback(int row, int exact, int colorOnly) {
        int index = 0;
        for (int i = 0; i < exact; i++) {
            boardPegStates[row][index] = 1;
            pegCells[row][index++].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3D3D")));
        }
        for (int i = 0; i < colorOnly; i++) {
            boardPegStates[row][index] = 2;
            pegCells[row][index++].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFD23F")));
        }
        while (index < COLS) {
            boardPegStates[row][index] = 0;
            pegCells[row][index++].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9EC6EA")));
        }
    }

    private void finishRound() {
        cancelTimers();
        showSecretRow();
        setInteractionEnabled(false);

        if (currentRound == 1) {
            phase = Phase.ROUND_END;
            tvPhaseInfo.setText(R.string.master_showing_secret);
            publishState();
            refreshTurnIndicator();
            roundTransitionTimer = new CountDownTimer(3000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                    tvTimer.setText(getString(R.string.master_timer_seconds, seconds));
                    lastTimerSeconds = seconds;
                    publishState();
                }

                @Override
                public void onFinish() {
                    currentRound = 2;
                    roundStarter = 2;
                    sendRoundChangeEvent();
                    startRound();
                }
            }.start();
            return;
        }

        phase = Phase.FINISHED;
        btnSubmit.setEnabled(false);
        tvPhaseInfo.setText(R.string.master_game_finished);
        tvCurrentPlayer.setText(R.string.master_match_done_label);
        lastTimerSeconds = 0;
        publishState();
        refreshTurnIndicator();
        sendForceFinishEvent();

        finishTimer = new CountDownTimer(1200, 1200) {
            @Override
            public void onTick(long millisUntilFinished) { }

            @Override
            public void onFinish() {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
                resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        }.start();
    }

    private void showSecretRow() {
        for (int i = 0; i < COLS; i++) {
            int symbol = currentSecret[i];
            boardGuessSymbols[REVEAL_ROW_INDEX][i] = symbol;
            guessCells[REVEAL_ROW_INDEX][i].setText(SYMBOLS[symbol]);
            guessCells[REVEAL_ROW_INDEX][i].setTextColor(symbolColor(symbol));
        }
    }

    private String symbolsForSecret() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentSecret.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(SYMBOLS[currentSecret[i]]);
        }
        return sb.toString();
    }

    private void clearCurrentGuess() {
        for (int i = 0; i < COLS; i++) {
            currentGuess[i] = -1;
            if (activeRow >= 0 && activeRow < TOTAL_ROWS) {
                boardGuessSymbols[activeRow][i] = -1;
                guessCells[activeRow][i].setText(" ");
                guessCells[activeRow][i].setTextColor(Color.BLACK);
            }
        }
    }

    private JSONArray encodeMatrix(int[][] matrix) {
        JSONArray outer = new JSONArray();
        for (int r = 0; r < matrix.length; r++) {
            JSONArray row = new JSONArray();
            for (int c = 0; c < matrix[r].length; c++) {
                row.put(matrix[r][c]);
            }
            outer.put(row);
        }
        return outer;
    }

    private void decodeMatrix(JSONArray source, int[][] target, int fallback) {
        if (source == null) {
            return;
        }
        int rows = Math.min(source.length(), target.length);
        for (int r = 0; r < rows; r++) {
            JSONArray row = source.optJSONArray(r);
            if (row == null) {
                continue;
            }
            int cols = Math.min(row.length(), target[r].length);
            for (int c = 0; c < cols; c++) {
                target[r][c] = row.optInt(c, fallback);
            }
        }
    }

    private void renderBoardFromState() {
        for (int r = 0; r < TOTAL_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int symbol = boardGuessSymbols[r][c];
                if (symbol >= 0 && symbol < SYMBOLS.length) {
                    guessCells[r][c].setText(SYMBOLS[symbol]);
                    guessCells[r][c].setTextColor(symbolColor(symbol));
                } else {
                    guessCells[r][c].setText(" ");
                    guessCells[r][c].setTextColor(Color.BLACK);
                }
                int peg = boardPegStates[r][c];
                if (peg == 1) {
                    pegCells[r][c].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3D3D")));
                } else if (peg == 2) {
                    pegCells[r][c].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFD23F")));
                } else {
                    pegCells[r][c].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9EC6EA")));
                }
            }
        }
    }

    private boolean isGuessComplete() {
        for (int v : currentGuess) {
            if (v == -1) return false;
        }
        return true;
    }

    private void addPoints(int player, int points) {
        if (player == 1) player1Score += points;
        else player2Score += points;
        updateScoreText();
    }

    private void updateScoreText() {
        tvScore.setText(getString(R.string.master_score_format, player1Score, player2Score));
        tvHeaderLeftScore.setText(String.valueOf(player1Score));
        tvHeaderRightScore.setText(String.valueOf(player2Score));
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

    private int symbolColor(int symbol) {
        switch (symbol) {
            case 3:
            case 4:
                return Color.parseColor("#E53935");
            case 5:
                return Color.parseColor("#FFD600");
            case 0:
                return Color.parseColor("#7B1FA2");
            default:
                return Color.parseColor("#1A1A1A");
        }
    }

    private int opponent(int player) {
        return player == 1 ? 2 : 1;
    }

    private boolean isMyTurn() {
        if (soloMode && phase != Phase.FINISHED) {
            return true;
        }
        if (phase == Phase.ROUND) {
            return roundStarter == myPlayerNumber;
        }
        if (phase == Phase.STEAL) {
            return opponent(roundStarter) == myPlayerNumber;
        }
        return false;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void cancelTimers() {
        if (prepTimer != null) {
            prepTimer.cancel();
            prepTimer = null;
        }
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        if (stealTimer != null) {
            stealTimer.cancel();
            stealTimer = null;
        }
        if (roundTransitionTimer != null) {
            roundTransitionTimer.cancel();
            roundTransitionTimer = null;
        }
        if (finishTimer != null) {
            finishTimer.cancel();
            finishTimer = null;
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
        cancelTimers();
        turnIndicatorAnimator.clear();
    }

    private void setInteractionEnabled(boolean enabled) {
        btnSubmit.setEnabled(enabled);
        int count = symbolBar.getChildCount();
        for (int i = 0; i < count; i++) {
            symbolBar.getChildAt(i).setEnabled(enabled);
        }
    }

    private void publishState() {
        if (soloMode || TextUtils.isEmpty(matchRoomId)) {
            return;
        }
        boolean starterTurn = roundStarter == myPlayerNumber;
        boolean stealTurn = opponent(roundStarter) == myPlayerNumber;
        boolean shouldSend = starterTurn
                || phase == Phase.FINISHED
                || phase == Phase.ROUND_END
                || (phase == Phase.STEAL && stealTurn);
        if (!shouldSend) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("round", currentRound);
            data.put("starter", roundStarter);
            data.put("phase", phase.name());
            data.put("timer", lastTimerSeconds);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("activeRow", activeRow);
            data.put("guesses", encodeMatrix(boardGuessSymbols));
            data.put("pegs", encodeMatrix(boardPegStates));

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
        roundStarter = data.optInt("starter", roundStarter);
        String phaseName = data.optString("phase", phase.name());
        try {
            phase = Phase.valueOf(phaseName);
        } catch (Exception ignored) {
        }
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        activeRow = data.optInt("activeRow", activeRow);
        decodeMatrix(data.optJSONArray("guesses"), boardGuessSymbols, -1);
        decodeMatrix(data.optJSONArray("pegs"), boardPegStates, 0);
        renderBoardFromState();
        tvRound.setText("");
        tvTimer.setText(getString(R.string.master_timer_seconds, Math.max(0, lastTimerSeconds)));
        updateScoreText();

        if (phase == Phase.ROUND) {
            tvPhaseInfo.setText(R.string.master_phase_round);
            tvCurrentPlayer.setText(getString(R.string.master_current_player, roundStarter));
            boolean myTurn = roundStarter == myPlayerNumber;
            setInteractionEnabled(myTurn);
            if (!myTurn) {
                cancelRoundAndStealTimers();
            } else if (roundTimer == null && lastTimerSeconds > 0) {
                startRoundTimer(lastTimerSeconds * 1000L);
            }
            refreshTurnIndicator();
            return;
        }

        if (phase == Phase.STEAL) {
            int stealPlayer = opponent(roundStarter);
            tvPhaseInfo.setText(getString(R.string.master_phase_steal, stealPlayer));
            tvCurrentPlayer.setText(getString(R.string.master_current_player, stealPlayer));
            activeRow = STEAL_ROW_INDEX;
            boolean myTurn = stealPlayer == myPlayerNumber;
            setInteractionEnabled(myTurn);
            if (!myTurn) {
                cancelRoundAndStealTimers();
            } else if (stealTimer == null && lastTimerSeconds > 0) {
                startStealTimer(lastTimerSeconds * 1000L);
            }
            refreshTurnIndicator();
            return;
        }

        if (phase == Phase.ROUND_END) {
            cancelRoundAndStealTimers();
            setInteractionEnabled(false);
            showSecretRow();
            tvPhaseInfo.setText(R.string.master_showing_secret);
            refreshTurnIndicator();
            return;
        }

        if (phase == Phase.FINISHED && !remoteFinishHandled) {
            cancelRoundAndStealTimers();
            setInteractionEnabled(false);
            showSecretRow();
            remoteFinishHandled = true;
            Intent resultIntent = new Intent();
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
            setResult(RESULT_OK, resultIntent);
            btnSubmit.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    finish();
                }
            }, 500);
        }
        refreshTurnIndicator();
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
        if (phase == Phase.FINISHED) {
            return;
        }
        setInteractionEnabled(true);
        if (phase == Phase.ROUND && roundTimer == null && lastTimerSeconds > 0) {
            startRoundTimer(lastTimerSeconds * 1000L);
        } else if (phase == Phase.STEAL && stealTimer == null && lastTimerSeconds > 0) {
            startStealTimer(lastTimerSeconds * 1000L);
        }
        refreshTurnIndicator();
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

    private void sendRoundChangeEvent() {
        if (soloMode || TextUtils.isEmpty(matchRoomId)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("round", currentRound);
            data.put("starter", roundStarter);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("phase", phase.name());
            Intent i = new Intent(MatchActivity.ACTION_GAME_COMMAND);
            i.putExtra(MatchActivity.EXTRA_ROOM_ID, matchRoomId);
            i.putExtra(MatchActivity.EXTRA_GAME, GAME_ID);
            i.putExtra(MatchActivity.EXTRA_EVENT, "round_change");
            i.putExtra(MatchActivity.EXTRA_DATA, data.toString());
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    private void applyRoundChange(JSONObject data) {
        if (soloMode || remoteFinishHandled) {
            return;
        }
        int remoteRound = data.optInt("round", currentRound);
        int remoteStarter = data.optInt("starter", roundStarter);
        if (remoteRound <= currentRound) {
            return;
        }
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        currentRound = remoteRound;
        roundStarter = remoteStarter;
        try {
            phase = Phase.valueOf(data.optString("phase", Phase.ROUND.name()));
        } catch (Exception ignored) {
            phase = Phase.ROUND;
        }
        cancelTimers();
        updateScoreText();
        startRound();
    }

    private void applyForceFinish(JSONObject data) {
        if (remoteFinishHandled) {
            return;
        }
        remoteFinishHandled = true;
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        cancelTimers();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void cancelRoundAndStealTimers() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        if (stealTimer != null) {
            stealTimer.cancel();
            stealTimer = null;
        }
    }

    private void refreshTurnIndicator() {
        if (phase == Phase.ROUND) {
            turnIndicatorAnimator.setActivePlayer(roundStarter);
            return;
        }
        if (phase == Phase.STEAL) {
            turnIndicatorAnimator.setActivePlayer(opponent(roundStarter));
            return;
        }
        turnIndicatorAnimator.setActivePlayer(null);
    }
}




