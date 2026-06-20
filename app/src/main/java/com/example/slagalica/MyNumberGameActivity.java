package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.EconomyService;

import com.example.slagalica.domain.MyNumberGameService;
import com.example.slagalica.domain.PlayerStatsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class MyNumberGameActivity extends AppCompatActivity implements SensorEventListener {
    private static final String GAME_ID = "number";

    private enum Phase { STOP_TARGET, STOP_NUMBERS, MAIN_ATTEMPT, ROUND_END, FINISHED }

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvPhase;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvHeaderLeftAvatar;
    private TextView tvHeaderLeftName;
    private TextView tvHeaderLeftScore;
    private TextView tvHeaderRightAvatar;
    private TextView tvHeaderRightName;
    private TextView tvHeaderRightScore;
    private TurnIndicatorAnimator turnIndicatorAnimator;
    private TextView tvTarget;
    private TextView[] numberViews;
    private EditText etExpression;
    private Button btnStop;

    private MyNumberGameService gameService;
    private CountDownTimer prepTimer;
    private CountDownTimer autoStopTimer;
    private CountDownTimer spinTargetTimer;
    private CountDownTimer spinNumbersTimer;
    private CountDownTimer roundTimer;
    private CountDownTimer stealTimer;
    private CountDownTimer transitionTimer;
    private CountDownTimer finishTimer;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float lastX;
    private float lastY;
    private float lastZ;
    private long lastShakeTime = 0L;
    private final java.util.Random uiRandom = new java.util.Random();
    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private boolean soloMode = false;
    private boolean opponentForfeited = false;
    private int lastTimerSeconds = 60;
    private boolean remoteFinishHandled = false;
    private String player1DisplayName = "Igrac 1";
    private String player2DisplayName = "Igrac 2";
    private long myTokens = 0L;
    private long myStars = 0L;
    private long myLeague = 0L;
    private Long opponentTokens = null;
    private Long opponentStars = null;
    private Long opponentLeague = null;
    private int statsBestDistance = -1;
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
            if ("main_submit".equals(event)) {
                try {
                    applyRemoteMainSubmit(new JSONObject(raw == null ? "{}" : raw));
                } catch (Exception ignored) {
                }
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

    private Phase phase = Phase.STOP_TARGET;
    private int currentRound = 1;
    private int roundStarter = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    private Integer targetNumber = null;
    private List<Integer> availableNumbers = new ArrayList<>();
    private final boolean[] numberUsed = new boolean[6];
    private boolean player1Submitted = false;
    private boolean player2Submitted = false;
    private boolean player1AttemptEmpty = true;
    private boolean player2AttemptEmpty = true;
    private Double player1AttemptValue = null;
    private Double player2AttemptValue = null;
    private int outcomeSeq = 0;
    private int lastShownOutcomeSeq = 0;
    private String lastOutcomeMessage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_number_game);

        gameService = new MyNumberGameService();
        matchRoomId = getIntent().getStringExtra("match_room_id");
        if (matchRoomId == null) {
            matchRoomId = "";
        }
        myPlayerNumber = getIntent().getIntExtra("match_my_player_number", 1);
        soloMode = getIntent().getBooleanExtra(MatchActivity.EXTRA_MATCH_SOLO_MODE, false);
        opponentForfeited = getIntent().getBooleanExtra(
                MatchActivity.EXTRA_OPPONENT_FORFEITED,
                soloMode
        );
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
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        tvRound = findViewById(R.id.tvNumberRound);
        tvCurrentPlayer = findViewById(R.id.tvNumberCurrentPlayer);
        tvPhase = findViewById(R.id.tvNumberPhase);
        tvTimer = findViewById(R.id.tvNumberTimer);
        tvScore = findViewById(R.id.tvNumberScore);
        tvHeaderLeftAvatar = findViewById(R.id.tvHeaderLeftAvatar);
        tvHeaderLeftName = findViewById(R.id.tvHeaderLeftName);
        tvHeaderLeftScore = findViewById(R.id.tvHeaderLeftScore);
        tvHeaderRightAvatar = findViewById(R.id.tvHeaderRightAvatar);
        tvHeaderRightName = findViewById(R.id.tvHeaderRightName);
        tvHeaderRightScore = findViewById(R.id.tvHeaderRightScore);
        turnIndicatorAnimator = new TurnIndicatorAnimator(tvHeaderLeftAvatar, tvHeaderRightAvatar);
        tvTarget = findViewById(R.id.tvTargetNumber);
        etExpression = findViewById(R.id.etExpression);
        btnStop = findViewById(R.id.btnStop);
        bindMatchHeader();
        setupMyProfileTap();
        loadOpponentStatus();
        TextView btnDeleteChar = findViewById(R.id.btnDeleteChar);

        numberViews = new TextView[]{
                findViewById(R.id.tvN1),
                findViewById(R.id.tvN2),
                findViewById(R.id.tvN3),
                findViewById(R.id.tvN4),
                findViewById(R.id.tvN5),
                findViewById(R.id.tvN6)
        };

        btnStop.setOnClickListener(v -> handleMainButtonClick());
        btnDeleteChar.setOnClickListener(v -> deleteOneCharacter());

        wireOperatorButtons();
        wireNumberButtons();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });
        startRound();
    }

    private void startRound() {
        cancelTimers();
        phase = Phase.STOP_TARGET;
        remoteFinishHandled = false;
        targetNumber = null;
        availableNumbers.clear();
        player1Submitted = false;
        player2Submitted = false;
        player1AttemptEmpty = true;
        player2AttemptEmpty = true;
        player1AttemptValue = null;
        player2AttemptValue = null;
        markForfeitedOpponentAsEmpty();

        tvRound.setText("");
        tvCurrentPlayer.setText(getString(R.string.number_current_player, roundStarter));
        tvPhase.setText(R.string.number_phase_stop_target);
        tvTimer.setText(getString(R.string.number_timer_seconds, 60));
        updateScoreText();

        tvTarget.setText(getString(R.string.number_unknown_target));
        for (TextView view : numberViews) {
            view.setText(getString(R.string.number_unknown_value));
            view.setEnabled(false);
            view.setAlpha(1f);
        }
        resetNumberUsage();
        etExpression.setText("");
        etExpression.setEnabled(false);
        btnStop.setEnabled(true);
        btnStop.setText(R.string.number_stop_target);
        btnStop.setEnabled(false);
        refreshTurnIndicator();

        startPrepCountdown();
    }

    private void startPrepCountdown() {
        if (prepTimer != null) {
            prepTimer.cancel();
            prepTimer = null;
        }
        prepTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvPhase.setText(getString(R.string.number_round_starts_in, seconds));
                tvTimer.setText(getString(R.string.number_timer_seconds, 60));
                lastTimerSeconds = 60;
                if (!soloMode && currentRound > 1) {
                    sendRoundChangeEvent();
                }
            }

            @Override
            public void onFinish() {
                tvPhase.setText(R.string.number_phase_stop_target);
                boolean myTurn = soloMode || roundStarter == myPlayerNumber;
                btnStop.setEnabled(myTurn);
                startTargetSpin();
                if (myTurn) {
                    startAutoStopTimer(MyNumberGameActivity.this::revealTarget);
                }
                publishState();
                refreshTurnIndicator();
            }
        }.start();
    }

    private void handleMainButtonClick() {
        if (!soloMode && (phase == Phase.STOP_TARGET || phase == Phase.STOP_NUMBERS)) {
            if (roundStarter != myPlayerNumber) {
                return;
            }
        }
        if (phase == Phase.STOP_TARGET) {
            revealTarget();
        } else if (phase == Phase.STOP_NUMBERS) {
            revealNumbers();
        } else if (phase == Phase.MAIN_ATTEMPT) {
            submitExpression();
        }
    }

    private void revealTarget() {
        if (phase != Phase.STOP_TARGET) {
            return;
        }

        cancelAutoStopTimer();
        stopSpinTimers();
        if (matchRoomId.isEmpty()) {
            targetNumber = gameService.generateTarget();
        } else {
            int seed = (matchRoomId + "_number_target_" + currentRound).hashCode();
            targetNumber = gameService.generateTarget(new Random(seed));
        }
        tvTarget.setText(String.valueOf(targetNumber));
        phase = Phase.STOP_NUMBERS;
        tvPhase.setText(R.string.number_phase_stop_numbers);
        btnStop.setEnabled(true);
        btnStop.setText(R.string.number_stop_numbers);
        startNumbersSpin();
        startAutoStopTimer(this::revealNumbers);
        publishState();
        refreshTurnIndicator();
    }

    private void revealNumbers() {
        if (phase != Phase.STOP_NUMBERS) {
            return;
        }

        cancelAutoStopTimer();
        stopSpinTimers();
        if (matchRoomId.isEmpty()) {
            availableNumbers = gameService.generateNumbers();
        } else {
            int seed = (matchRoomId + "_number_values_" + currentRound).hashCode();
            availableNumbers = gameService.generateNumbers(new Random(seed));
        }
        resetNumberUsage();
        for (int i = 0; i < numberViews.length; i++) {
            numberViews[i].setText(String.valueOf(availableNumbers.get(i)));
            numberViews[i].setEnabled(true);
            numberViews[i].setAlpha(1f);
        }

        phase = Phase.MAIN_ATTEMPT;
        tvPhase.setText(R.string.number_phase_main);
        stopSpinTimers();
        btnStop.setEnabled(!hasSubmitted(myPlayerNumber));
        btnStop.setText(R.string.number_confirm_expression);
        etExpression.setEnabled(!hasSubmitted(myPlayerNumber));
        etExpression.setText("");
        for (TextView view : numberViews) {
            view.setEnabled(!hasSubmitted(myPlayerNumber));
        }
        if (soloMode || roundStarter == myPlayerNumber) {
            startMainRoundTimer(60000);
        } else {
            cancelRoundTimers();
        }
        publishState();
        refreshTurnIndicator();
    }

    private void startTargetSpin() {
        if (spinTargetTimer != null) {
            spinTargetTimer.cancel();
        }
        spinTargetTimer = new CountDownTimer(600000, 120) {
            @Override
            public void onTick(long millisUntilFinished) {
                int randomTarget = 100 + uiRandom.nextInt(900);
                tvTarget.setText(String.valueOf(randomTarget));
            }

            @Override
            public void onFinish() {
            }
        }.start();
    }

    private void startNumbersSpin() {
        if (spinNumbersTimer != null) {
            spinNumbersTimer.cancel();
        }
        spinNumbersTimer = new CountDownTimer(600000, 120) {
            @Override
            public void onTick(long millisUntilFinished) {
                for (int i = 0; i < numberViews.length; i++) {
                    int value;
                    if (i < 4) {
                        value = 1 + uiRandom.nextInt(9);
                    } else if (i == 4) {
                        int[] mids = {10, 15, 20};
                        value = mids[uiRandom.nextInt(mids.length)];
                    } else {
                        int[] highs = {25, 50, 75, 100};
                        value = highs[uiRandom.nextInt(highs.length)];
                    }
                    numberViews[i].setText(String.valueOf(value));
                    numberViews[i].setEnabled(false);
                    numberViews[i].setAlpha(1f);
                }
            }

            @Override
            public void onFinish() {
            }
        }.start();
    }

    private void startAutoStopTimer(Runnable onFinishAction) {
        cancelAutoStopTimer();
        autoStopTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvPhase.setText(getString(R.string.number_phase_auto_stop, seconds));
                publishState();
            }

            @Override
            public void onFinish() {
                onFinishAction.run();
            }
        }.start();
    }

    private void startMainRoundTimer(long durationMs) {
        cancelRoundTimers();
        roundTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.number_timer_seconds, sec));
                lastTimerSeconds = sec;
                publishState();
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.number_timer_seconds, 0));
                lastTimerSeconds = 0;
                finalizeMainAttemptRound();
            }
        }.start();
    }

    private void submitExpression() {
        if (phase != Phase.MAIN_ATTEMPT) {
            return;
        }
        if (hasSubmitted(myPlayerNumber)) {
            return;
        }
        MyNumberGameService.EvalResult eval = gameService.evaluate(etExpression.getText().toString(), availableNumbers);
        setAttemptForPlayer(myPlayerNumber, eval);
        lockLocalInputAfterSubmit();
        sendMainSubmitEvent(myPlayerNumber, eval);
        publishState();
        if (soloMode || roundStarter == myPlayerNumber) {
            maybeFinalizeRoundEarly();
        }
    }

    private void finalizeMainAttemptRound() {
        if (phase != Phase.MAIN_ATTEMPT) {
            return;
        }
        cancelRoundTimers();

        if (soloMode && !opponentForfeited) {
            if (isExact(attemptValueFor(myPlayerNumber))) {
                addPoints(myPlayerNumber, 10);
                announceRoundOutcome(getString(R.string.number_exact_points, myPlayerNumber));
            } else if (attemptValueFor(myPlayerNumber) == null) {
                announceRoundOutcome(getString(R.string.number_both_empty));
            }
            finishRound();
            return;
        }

        boolean p1Exact = isExact(player1AttemptValue);
        boolean p2Exact = isExact(player2AttemptValue);
        if (p1Exact && p2Exact) {
            addPoints(roundStarter, 10);
            announceRoundOutcome(getString(R.string.number_exact_points, roundStarter));
            finishRound();
            return;
        }
        if (p1Exact) {
            addPoints(1, 10);
            announceRoundOutcome(getString(R.string.number_exact_points, 1));
            finishRound();
            return;
        }
        if (p2Exact) {
            addPoints(2, 10);
            announceRoundOutcome(getString(R.string.number_exact_points, 2));
            finishRound();
            return;
        }

        if (player1AttemptEmpty && player2AttemptEmpty) {
            announceRoundOutcome(getString(R.string.number_both_empty));
            finishRound();
            return;
        }

        if (player1AttemptValue == null && player2AttemptValue != null) {
            addPoints(2, 5);
            announceRoundOutcome(getString(R.string.number_closer_points, 2));
            finishRound();
            return;
        }
        if (player1AttemptValue != null && player2AttemptValue == null) {
            addPoints(1, 5);
            announceRoundOutcome(getString(R.string.number_closer_points, 1));
            finishRound();
            return;
        }
        if (player1AttemptValue == null) {
            announceRoundOutcome(getString(R.string.number_both_empty));
            finishRound();
            return;
        }

        int p1Distance = Math.abs((int) Math.round(player1AttemptValue - targetNumber));
        int p2Distance = Math.abs((int) Math.round(player2AttemptValue - targetNumber));
        if (p1Distance < p2Distance) {
            addPoints(1, 5);
            announceRoundOutcome(getString(R.string.number_closer_points, 1));
        } else if (p2Distance < p1Distance) {
            addPoints(2, 5);
            announceRoundOutcome(getString(R.string.number_closer_points, 2));
        } else if (Math.round(player1AttemptValue) != 0) {
            addPoints(roundStarter, 5);
            announceRoundOutcome(getString(R.string.number_tie_starter_points, roundStarter));
        }

        finishRound();
    }

    private void finishRound() {
        cancelTimers();
        etExpression.setEnabled(false);
        btnStop.setEnabled(false);
        for (TextView view : numberViews) {
            view.setEnabled(false);
        }

        if (currentRound == 1) {
            phase = Phase.ROUND_END;
            tvPhase.setText(R.string.number_next_round_wait);
            publishState();
            transitionTimer = new CountDownTimer(3000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                    tvTimer.setText(getString(R.string.number_timer_seconds, seconds));
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
        tvPhase.setText(R.string.number_game_finished);
        tvCurrentPlayer.setText(R.string.number_match_done);
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
                addStatsToResult(resultIntent);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        }.start();
    }

    private void wireOperatorButtons() {
        TextView btnL = findViewById(R.id.btnOpL);
        TextView btnR = findViewById(R.id.btnOpR);
        TextView btnPlus = findViewById(R.id.btnOpPlus);
        TextView btnMinus = findViewById(R.id.btnOpMinus);
        TextView btnMul = findViewById(R.id.btnOpMul);
        TextView btnDiv = findViewById(R.id.btnOpDiv);
        TextView btnClear = findViewById(R.id.btnClearExpr);

        btnL.setOnClickListener(v -> appendToExpression("("));
        btnR.setOnClickListener(v -> appendToExpression(")"));
        btnPlus.setOnClickListener(v -> appendToExpression("+"));
        btnMinus.setOnClickListener(v -> appendToExpression("-"));
        btnMul.setOnClickListener(v -> appendToExpression("*"));
        btnDiv.setOnClickListener(v -> appendToExpression("/"));
        btnClear.setOnClickListener(v -> {
            if (!isMyTurnForInput()) {
                return;
            }
            etExpression.setText("");
            if (phase == Phase.MAIN_ATTEMPT) {
                resetNumberUsage();
                for (TextView view : numberViews) {
                    if (!availableNumbers.isEmpty()) {
                        view.setEnabled(true);
                        view.setAlpha(1f);
                    }
                }
            }
        });
    }

    private void deleteOneCharacter() {
        if (phase != Phase.MAIN_ATTEMPT) {
            return;
        }
        if (!isMyTurnForInput()) {
            return;
        }
        String current = etExpression.getText().toString();
        if (current.isEmpty()) {
            return;
        }
        String trimmed = current.trim();
        if (trimmed.isEmpty()) {
            etExpression.setText("");
            refreshNumberUsageFromExpression();
            return;
        }

        int lastSpace = trimmed.lastIndexOf(' ');
        String updated = lastSpace >= 0 ? trimmed.substring(0, lastSpace).trim() : "";
        etExpression.setText(updated);
        etExpression.setSelection(updated.length());
        refreshNumberUsageFromExpression();
    }

    private void wireNumberButtons() {
        for (int i = 0; i < numberViews.length; i++) {
            final int index = i;
            TextView view = numberViews[i];
            view.setOnClickListener(v -> {
                if (!v.isEnabled()) {
                    return;
                }
                if (!isMyTurnForInput()) {
                    return;
                }
                if (index < numberUsed.length && numberUsed[index]) {
                    return;
                }
                appendToExpression(((TextView) v).getText().toString());
                if (index < numberUsed.length) {
                    numberUsed[index] = true;
                    v.setEnabled(false);
                    v.setAlpha(0.45f);
                }
            });
        }
    }

    private void appendToExpression(String token) {
        if (phase != Phase.MAIN_ATTEMPT) {
            return;
        }
        if (!isMyTurnForInput()) {
            return;
        }

        String current = etExpression.getText().toString();
        if (!current.isEmpty() && needsSpace(current, token)) {
            current += " ";
        }
        current += token;
        etExpression.setText(current);
        etExpression.setSelection(current.length());
    }

    private boolean needsSpace(String current, String token) {
        char last = current.charAt(current.length() - 1);
        if (Character.isWhitespace(last)) {
            return false;
        }
        return Character.isDigit(last) || Character.isDigit(token.charAt(0)) || "+-*/()".contains(token);
    }

    private boolean isExact(Double value) {
        if (value == null || targetNumber == null) {
            return false;
        }
        return Math.abs(value - targetNumber) < 1e-9;
    }

    private void setAttemptForPlayer(int player, MyNumberGameService.EvalResult eval) {
        boolean empty = eval != null && eval.empty;
        Double value = (eval != null && eval.valid) ? eval.value : null;
        if (player == myPlayerNumber && value != null && targetNumber != null) {
            int distance = Math.abs((int) Math.round(value - targetNumber));
            if (statsBestDistance < 0 || distance < statsBestDistance) {
                statsBestDistance = distance;
            }
        }
        if (player == 1) {
            player1Submitted = true;
            player1AttemptEmpty = empty;
            player1AttemptValue = value;
        } else {
            player2Submitted = true;
            player2AttemptEmpty = empty;
            player2AttemptValue = value;
        }
    }

    private boolean hasSubmitted(int player) {
        return player == 1 ? player1Submitted : player2Submitted;
    }

    private Double attemptValueFor(int player) {
        return player == 1 ? player1AttemptValue : player2AttemptValue;
    }

    private void lockLocalInputAfterSubmit() {
        etExpression.setEnabled(false);
        btnStop.setEnabled(false);
        for (TextView view : numberViews) {
            view.setEnabled(false);
        }
    }

    private void announceRoundOutcome(String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        lastOutcomeMessage = message;
        outcomeSeq++;
        lastShownOutcomeSeq = outcomeSeq;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void maybeFinalizeRoundEarly() {
        if (phase != Phase.MAIN_ATTEMPT) {
            return;
        }
        if (soloMode) {
            if (hasSubmitted(myPlayerNumber)) {
                finalizeMainAttemptRound();
            }
            return;
        }
        if (player1Submitted && player2Submitted) {
            finalizeMainAttemptRound();
        }
    }

    private void addPoints(int player, int points) {
        if (player == 1) {
            player1Score += points;
        } else {
            player2Score += points;
        }
        updateScoreText();
    }

    private void updateScoreText() {
        tvScore.setText(getString(R.string.number_score_format, player1Score, player2Score));
        tvHeaderLeftScore.setText(String.valueOf(player1Score));
        tvHeaderRightScore.setText(String.valueOf(player2Score));
    }

    private void refreshTurnIndicator() {
        if (soloMode && phase != Phase.FINISHED) {
            turnIndicatorAnimator.setActivePlayer(myPlayerNumber);
            return;
        }
        if (phase == Phase.STOP_TARGET || phase == Phase.STOP_NUMBERS) {
            turnIndicatorAnimator.setActivePlayer(roundStarter);
            return;
        }
        turnIndicatorAnimator.setActivePlayer(null);
    }

    private int opponent(int player) {
        return player == 1 ? 2 : 1;
    }

    private boolean isMyTurnForInput() {
        if (soloMode && phase != Phase.FINISHED) {
            if (phase == Phase.MAIN_ATTEMPT) {
                return !hasSubmitted(myPlayerNumber);
            }
            return true;
        }
        if (phase == Phase.STOP_TARGET || phase == Phase.STOP_NUMBERS) {
            return roundStarter == myPlayerNumber;
        }
        if (phase == Phase.MAIN_ATTEMPT) {
            return !hasSubmitted(myPlayerNumber);
        }
        return false;
    }

    private void cancelAutoStopTimer() {
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }
    }

    private void cancelRoundTimers() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        if (stealTimer != null) {
            stealTimer.cancel();
            stealTimer = null;
        }
    }

    private void cancelTimers() {
        if (prepTimer != null) {
            prepTimer.cancel();
            prepTimer = null;
        }
        stopSpinTimers();
        cancelAutoStopTimer();
        cancelRoundTimers();
        if (transitionTimer != null) {
            transitionTimer.cancel();
            transitionTimer = null;
        }
        if (finishTimer != null) {
            finishTimer.cancel();
            finishTimer = null;
        }
    }

    private void stopSpinTimers() {
        if (spinTargetTimer != null) {
            spinTargetTimer.cancel();
            spinTargetTimer = null;
        }
        if (spinNumbersTimer != null) {
            spinNumbersTimer.cancel();
            spinNumbersTimer = null;
        }
    }

    private void stopTargetSpinIfRunning() {
        if (spinTargetTimer != null) {
            spinTargetTimer.cancel();
            spinTargetTimer = null;
        }
    }

    private void resetNumberUsage() {
        for (int i = 0; i < numberUsed.length; i++) {
            numberUsed[i] = false;
        }
    }

    private void refreshNumberUsageFromExpression() {
        if (availableNumbers.isEmpty()) {
            return;
        }

        resetNumberUsage();
        for (TextView view : numberViews) {
            view.setEnabled(true);
            view.setAlpha(1f);
        }

        String expr = etExpression.getText().toString();
        if (expr.isEmpty()) {
            return;
        }

        Matcher matcher = Pattern.compile("\\d+").matcher(expr);
        while (matcher.find()) {
            int usedValue;
            try {
                usedValue = Integer.parseInt(matcher.group());
            } catch (NumberFormatException ex) {
                continue;
            }

            for (int i = 0; i < availableNumbers.size() && i < numberUsed.length; i++) {
                if (!numberUsed[i] && availableNumbers.get(i) == usedValue) {
                    numberUsed[i] = true;
                    numberViews[i].setEnabled(false);
                    numberViews[i].setAlpha(0.45f);
                    break;
                }
            }
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
    protected void onResume() {
        super.onResume();
        if (accelerometer != null && sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimers();
        turnIndicatorAnimator.clear();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float deltaX = Math.abs(lastX - x);
        float deltaY = Math.abs(lastY - y);
        float deltaZ = Math.abs(lastZ - z);
        lastX = x;
        lastY = y;
        lastZ = z;

        float shake = deltaX + deltaY + deltaZ;
        long now = System.currentTimeMillis();
        if (shake > 20f && now - lastShakeTime > 1200) {
            if (!soloMode && roundStarter != myPlayerNumber) {
                return;
            }
            lastShakeTime = now;
            if (phase == Phase.STOP_TARGET) {
                revealTarget();
                Toast.makeText(this, R.string.number_shake_stopped, Toast.LENGTH_SHORT).show();
            } else if (phase == Phase.STOP_NUMBERS) {
                revealNumbers();
                Toast.makeText(this, R.string.number_shake_stopped, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void publishState() {
        if (soloMode || TextUtils.isEmpty(matchRoomId)) {
            return;
        }
        boolean starterTurn = roundStarter == myPlayerNumber;
        boolean shouldSend = starterTurn || phase == Phase.FINISHED || phase == Phase.ROUND_END;
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
            data.put("target", targetNumber == null ? 0 : targetNumber);
            JSONArray numbers = new JSONArray();
            for (int n : availableNumbers) {
                numbers.put(n);
            }
            data.put("numbers", numbers);
            data.put("p1Submitted", player1Submitted);
            data.put("p2Submitted", player2Submitted);
            data.put("p1Empty", player1AttemptEmpty);
            data.put("p2Empty", player2AttemptEmpty);
            data.put("p1Value", player1AttemptValue == null ? JSONObject.NULL : player1AttemptValue);
            data.put("p2Value", player2AttemptValue == null ? JSONObject.NULL : player2AttemptValue);
            data.put("outcomeSeq", outcomeSeq);
            data.put("outcomeMessage", lastOutcomeMessage == null ? "" : lastOutcomeMessage);

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
        int previousRound = currentRound;
        currentRound = data.optInt("round", currentRound);
        if (currentRound != previousRound) {
            player1Submitted = false;
            player2Submitted = false;
            player1AttemptEmpty = true;
            player2AttemptEmpty = true;
            player1AttemptValue = null;
            player2AttemptValue = null;
            etExpression.setText("");
            resetNumberUsage();
        }
        roundStarter = data.optInt("starter", roundStarter);
        String phaseName = data.optString("phase", phase.name());
        try {
            phase = Phase.valueOf(phaseName);
        } catch (Exception ignored) {
        }
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        player1Submitted = data.optBoolean("p1Submitted", player1Submitted);
        player2Submitted = data.optBoolean("p2Submitted", player2Submitted);
        player1AttemptEmpty = data.optBoolean("p1Empty", player1AttemptEmpty);
        player2AttemptEmpty = data.optBoolean("p2Empty", player2AttemptEmpty);
        player1AttemptValue = readNullableDouble(data, "p1Value", player1AttemptValue);
        player2AttemptValue = readNullableDouble(data, "p2Value", player2AttemptValue);
        int remoteOutcomeSeq = data.optInt("outcomeSeq", outcomeSeq);
        String remoteOutcomeMessage = data.optString("outcomeMessage", "");
        if (remoteOutcomeSeq > lastShownOutcomeSeq && !TextUtils.isEmpty(remoteOutcomeMessage)) {
            lastShownOutcomeSeq = remoteOutcomeSeq;
            outcomeSeq = Math.max(outcomeSeq, remoteOutcomeSeq);
            lastOutcomeMessage = remoteOutcomeMessage;
            Toast.makeText(this, remoteOutcomeMessage, Toast.LENGTH_SHORT).show();
        }
        int remoteTarget = data.optInt("target", 0);
        if (remoteTarget > 0) {
            targetNumber = remoteTarget;
        }
        JSONArray nums = data.optJSONArray("numbers");
        if (nums != null && nums.length() == 6) {
            boolean shouldResetUsageVisuals =
                    phase == Phase.STOP_TARGET
                            || phase == Phase.STOP_NUMBERS
                            || availableNumbers.isEmpty();
            availableNumbers.clear();
            for (int i = 0; i < nums.length(); i++) {
                availableNumbers.add(nums.optInt(i));
            }
            if (shouldResetUsageVisuals) {
                resetNumberUsage();
            }
            for (int i = 0; i < numberViews.length; i++) {
                numberViews[i].setText(String.valueOf(availableNumbers.get(i)));
                if (shouldResetUsageVisuals) {
                    numberViews[i].setAlpha(1f);
                }
            }
        }

        tvRound.setText("");
        tvTimer.setText(getString(R.string.number_timer_seconds, Math.max(0, lastTimerSeconds)));
        updateScoreText();
        tvTarget.setText(targetNumber == null ? getString(R.string.number_unknown_target) : String.valueOf(targetNumber));

        if (phase == Phase.STOP_TARGET) {
            cancelRoundTimers();
            startTargetSpin();
            etExpression.setEnabled(false);
            btnStop.setEnabled(roundStarter == myPlayerNumber);
            btnStop.setText(R.string.number_stop_target);
            tvPhase.setText(R.string.number_phase_stop_target);
            for (TextView view : numberViews) {
                view.setEnabled(false);
            }
            refreshTurnIndicator();
            return;
        }

        if (phase == Phase.STOP_NUMBERS) {
            cancelRoundTimers();
            stopTargetSpinIfRunning();
            startNumbersSpin();
            etExpression.setEnabled(false);
            btnStop.setEnabled(roundStarter == myPlayerNumber);
            btnStop.setText(R.string.number_stop_numbers);
            tvPhase.setText(R.string.number_phase_stop_numbers);
            for (TextView view : numberViews) {
                view.setEnabled(false);
            }
            refreshTurnIndicator();
            return;
        }

        if (phase == Phase.MAIN_ATTEMPT) {
            stopSpinTimers();
            tvPhase.setText(R.string.number_phase_main);
            btnStop.setText(R.string.number_confirm_expression);
            boolean myTurn = !hasSubmitted(myPlayerNumber);
            etExpression.setEnabled(myTurn);
            btnStop.setEnabled(myTurn);
            for (TextView view : numberViews) {
                view.setEnabled(myTurn);
            }
            if (myTurn) {
                refreshNumberUsageFromExpression();
            } else {
                for (TextView view : numberViews) {
                    view.setEnabled(false);
                }
            }
            if ((soloMode || roundStarter == myPlayerNumber) && roundTimer == null && lastTimerSeconds > 0) {
                startMainRoundTimer(lastTimerSeconds * 1000L);
            } else if (!soloMode && roundStarter != myPlayerNumber) {
                cancelRoundTimers();
            }
            refreshTurnIndicator();
            return;
        }

        if (phase == Phase.ROUND_END) {
            cancelRoundTimers();
            stopSpinTimers();
            etExpression.setEnabled(false);
            btnStop.setEnabled(false);
            tvPhase.setText(R.string.number_next_round_wait);
            for (TextView view : numberViews) {
                view.setEnabled(false);
            }
            refreshTurnIndicator();
            return;
        }

        if (phase == Phase.FINISHED && !remoteFinishHandled) {
            cancelRoundTimers();
            stopSpinTimers();
            etExpression.setEnabled(false);
            btnStop.setEnabled(false);
            remoteFinishHandled = true;
                Intent resultIntent = new Intent();
                resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
                resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
                addStatsToResult(resultIntent);
                setResult(RESULT_OK, resultIntent);
            btnStop.postDelayed(() -> {
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
        opponentForfeited = true;
        soloMode = true;
        markForfeitedOpponentAsEmpty();
        tvCurrentPlayer.setText(getString(R.string.number_current_player, roundStarter));
        if (phase == Phase.FINISHED) {
            refreshTurnIndicator();
            return;
        }
        if (phase == Phase.ROUND_END && currentRound == 1) {
            cancelTimers();
            currentRound = 2;
            roundStarter = 2;
            startRound();
            return;
        }
        if (phase == Phase.STOP_TARGET) {
            btnStop.setEnabled(true);
            if (autoStopTimer == null) {
                startAutoStopTimer(this::revealTarget);
            }
            refreshTurnIndicator();
            return;
        }
        if (phase == Phase.STOP_NUMBERS) {
            btnStop.setEnabled(true);
            if (autoStopTimer == null) {
                startAutoStopTimer(this::revealNumbers);
            }
            refreshTurnIndicator();
            return;
        }
        if (phase == Phase.MAIN_ATTEMPT) {
            boolean canInput = !hasSubmitted(myPlayerNumber);
            etExpression.setEnabled(canInput);
            btnStop.setEnabled(canInput);
            for (TextView view : numberViews) {
                view.setEnabled(canInput);
            }
            if (roundTimer == null && lastTimerSeconds > 0) {
                startMainRoundTimer(lastTimerSeconds * 1000L);
            }
        }
        refreshTurnIndicator();
    }

    private void markForfeitedOpponentAsEmpty() {
        if (!opponentForfeited) {
            return;
        }
        int absentPlayer = opponent(myPlayerNumber);
        if (absentPlayer == 1) {
            player1Submitted = true;
            player1AttemptEmpty = true;
            player1AttemptValue = null;
        } else {
            player2Submitted = true;
            player2AttemptEmpty = true;
            player2AttemptValue = null;
        }
    }

    private void sendMainSubmitEvent(int player, MyNumberGameService.EvalResult eval) {
        if (soloMode || TextUtils.isEmpty(matchRoomId)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("round", currentRound);
            data.put("player", player);
            data.put("empty", eval != null && eval.empty);
            if (eval != null && eval.valid) {
                data.put("value", eval.value);
            } else {
                data.put("value", JSONObject.NULL);
            }
            Intent i = new Intent(MatchActivity.ACTION_GAME_COMMAND);
            i.putExtra(MatchActivity.EXTRA_ROOM_ID, matchRoomId);
            i.putExtra(MatchActivity.EXTRA_GAME, GAME_ID);
            i.putExtra(MatchActivity.EXTRA_EVENT, "main_submit");
            i.putExtra(MatchActivity.EXTRA_DATA, data.toString());
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    private void applyRemoteMainSubmit(JSONObject data) {
        if (soloMode || phase != Phase.MAIN_ATTEMPT) {
            return;
        }
        int remoteRound = data.optInt("round", -1);
        if (remoteRound != currentRound) {
            return;
        }
        int player = data.optInt("player", 0);
        if (player != 1 && player != 2) {
            return;
        }
        boolean empty = data.optBoolean("empty", false);
        Double value = readNullableDouble(data, "value", null);
        if (player == 1) {
            player1Submitted = true;
            player1AttemptEmpty = empty;
            player1AttemptValue = value;
        } else {
            player2Submitted = true;
            player2AttemptEmpty = empty;
            player2AttemptValue = value;
        }
        if (player == myPlayerNumber) {
            lockLocalInputAfterSubmit();
        }
        if (roundStarter == myPlayerNumber) {
            publishState();
            maybeFinalizeRoundEarly();
        }
    }

    private Double readNullableDouble(JSONObject data, String key, Double fallback) {
        if (!data.has(key) || data.isNull(key)) {
            return fallback;
        }
        try {
            return data.getDouble(key);
        } catch (Exception ignored) {
            return fallback;
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

    private void sendRoundChangeEvent() {
        if (soloMode || TextUtils.isEmpty(matchRoomId)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("round", currentRound);
            data.put("starter", roundStarter);
            data.put("phase", phase.name());
            data.put("p1", player1Score);
            data.put("p2", player2Score);
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
            phase = Phase.valueOf(data.optString("phase", Phase.STOP_TARGET.name()));
        } catch (Exception ignored) {
            phase = Phase.STOP_TARGET;
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
        addStatsToResult(resultIntent);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void addStatsToResult(Intent resultIntent) {
        PlayerStatsService.putBaseGameStats(resultIntent, GAME_ID, 0, 20);
        resultIntent.putExtra(PlayerStatsService.EXTRA_STATS_NUMBER_DISTANCE, statsBestDistance);
    }

    private void bindMatchHeader() {
        tvHeaderLeftName.setText(headerName(player1DisplayName));
        tvHeaderRightName.setText(headerName(player2DisplayName));
        tvHeaderLeftAvatar.setText(initialForName(player1DisplayName, "1"));
        tvHeaderRightAvatar.setText(initialForName(player2DisplayName, "2"));
        AvatarFrameHelper.applyMatchFrames(tvHeaderLeftAvatar, tvHeaderRightAvatar, getIntent());
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
}





