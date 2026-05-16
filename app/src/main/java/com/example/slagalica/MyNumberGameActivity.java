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

import com.example.slagalica.domain.MyNumberGameService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class MyNumberGameActivity extends AppCompatActivity implements SensorEventListener {
    private static final String GAME_ID = "number";

    private enum Phase { STOP_TARGET, STOP_NUMBERS, MAIN_ATTEMPT, STEAL_ATTEMPT, ROUND_END, FINISHED }

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvPhase;
    private TextView tvTimer;
    private TextView tvScore;
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
    private int lastTimerSeconds = 60;
    private boolean remoteFinishHandled = false;
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

    private Phase phase = Phase.STOP_TARGET;
    private int currentRound = 1;
    private int roundStarter = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    private Integer targetNumber = null;
    private List<Integer> availableNumbers = new ArrayList<>();
    private final boolean[] numberUsed = new boolean[6];
    private Double starterValue = null;
    private boolean starterEmpty = true;

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
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        tvRound = findViewById(R.id.tvNumberRound);
        tvCurrentPlayer = findViewById(R.id.tvNumberCurrentPlayer);
        tvPhase = findViewById(R.id.tvNumberPhase);
        tvTimer = findViewById(R.id.tvNumberTimer);
        tvScore = findViewById(R.id.tvNumberScore);
        tvTarget = findViewById(R.id.tvTargetNumber);
        etExpression = findViewById(R.id.etExpression);
        btnStop = findViewById(R.id.btnStop);
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
        starterValue = null;
        starterEmpty = true;

        tvRound.setText(getString(R.string.number_round_label, currentRound));
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
                if (myTurn) {
                    startTargetSpin();
                    startAutoStopTimer(MyNumberGameActivity.this::revealTarget);
                }
                publishState();
            }
        }.start();
    }

    private void handleMainButtonClick() {
        if (!soloMode && (phase == Phase.STOP_TARGET || phase == Phase.STOP_NUMBERS || phase == Phase.MAIN_ATTEMPT)) {
            if (roundStarter != myPlayerNumber) {
                return;
            }
        }
        if (!soloMode && phase == Phase.STEAL_ATTEMPT) {
            if (opponent(roundStarter) != myPlayerNumber) {
                return;
            }
        }
        if (phase == Phase.STOP_TARGET) {
            revealTarget();
        } else if (phase == Phase.STOP_NUMBERS) {
            revealNumbers();
        } else if (phase == Phase.MAIN_ATTEMPT || phase == Phase.STEAL_ATTEMPT) {
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
        boolean myMainTurn = soloMode || roundStarter == myPlayerNumber;
        btnStop.setEnabled(myMainTurn);
        btnStop.setText(R.string.number_confirm_expression);
        etExpression.setEnabled(myMainTurn);
        etExpression.setText("");
        for (TextView view : numberViews) {
            view.setEnabled(myMainTurn);
        }
        if (myMainTurn) {
            startMainRoundTimer(60000);
        } else {
            cancelRoundTimers();
        }
        publishState();
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
                captureStarterAttemptAndOpenSteal();
            }
        }.start();
    }

    private void captureStarterAttemptAndOpenSteal() {
        if (phase != Phase.MAIN_ATTEMPT) {
            return;
        }
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        if (stealTimer != null) {
            stealTimer.cancel();
            stealTimer = null;
        }
        phase = Phase.STEAL_ATTEMPT;

        MyNumberGameService.EvalResult starterEval = gameService.evaluate(etExpression.getText().toString(), availableNumbers);
        starterEmpty = starterEval.empty;
        starterValue = starterEval.valid ? starterEval.value : null;

        if (isExact(starterValue)) {
            addPoints(scoreOwner(roundStarter), 10);
            Toast.makeText(this, getString(R.string.number_exact_points, roundStarter), Toast.LENGTH_SHORT).show();
            finishRound();
            return;
        }

        int stealPlayer = opponent(roundStarter);
        tvCurrentPlayer.setText(getString(R.string.number_current_player, stealPlayer));
        tvPhase.setText(getString(R.string.number_phase_steal, stealPlayer));
        etExpression.setText("");
        boolean myStealTurn = soloMode || stealPlayer == myPlayerNumber;
        etExpression.setEnabled(myStealTurn);
        btnStop.setEnabled(myStealTurn);
        btnStop.setText(R.string.number_confirm_expression);
        resetNumberUsage();
        for (TextView view : numberViews) {
            view.setEnabled(myStealTurn);
            view.setAlpha(1f);
        }
        if (myStealTurn) {
            startStealTimer(10000);
        } else {
            cancelRoundTimers();
        }
        publishState();
    }

    private void startStealTimer(long durationMs) {
        cancelRoundTimers();
        stealTimer = new CountDownTimer(durationMs, 1000) {
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
                resolveSteal();
            }
        }.start();
    }

    private void submitExpression() {
        if (phase != Phase.MAIN_ATTEMPT && phase != Phase.STEAL_ATTEMPT) {
            return;
        }
        if (!soloMode && phase == Phase.MAIN_ATTEMPT && roundStarter != myPlayerNumber) {
            return;
        }
        if (!soloMode && phase == Phase.STEAL_ATTEMPT && opponent(roundStarter) != myPlayerNumber) {
            return;
        }

        if (phase == Phase.MAIN_ATTEMPT) {
            MyNumberGameService.EvalResult eval = gameService.evaluate(etExpression.getText().toString(), availableNumbers);
            starterEmpty = eval.empty;
            starterValue = eval.valid ? eval.value : null;

            if (isExact(starterValue)) {
                addPoints(scoreOwner(roundStarter), 10);
                Toast.makeText(this, getString(R.string.number_exact_points, roundStarter), Toast.LENGTH_SHORT).show();
                finishRound();
                return;
            }

            captureStarterAttemptAndOpenSteal();
            return;
        }

        if (phase == Phase.STEAL_ATTEMPT) {
            resolveSteal();
        }
    }

    private void resolveSteal() {
        if (phase != Phase.STEAL_ATTEMPT) {
            return;
        }
        cancelRoundTimers();

        int stealPlayer = opponent(roundStarter);
        MyNumberGameService.EvalResult stealEval = gameService.evaluate(etExpression.getText().toString(), availableNumbers);
        boolean stealEmpty = stealEval.empty;
        Double stealValue = stealEval.valid ? stealEval.value : null;

        if (isExact(stealValue)) {
            addPoints(scoreOwner(stealPlayer), 10);
            Toast.makeText(this, getString(R.string.number_exact_points, stealPlayer), Toast.LENGTH_SHORT).show();
            finishRound();
            return;
        }

        if (starterEmpty && stealEmpty) {
            Toast.makeText(this, R.string.number_both_empty, Toast.LENGTH_SHORT).show();
            finishRound();
            return;
        }

        if (starterValue == null && stealValue != null) {
            addPoints(scoreOwner(stealPlayer), 5);
            Toast.makeText(this, getString(R.string.number_closer_points, stealPlayer), Toast.LENGTH_SHORT).show();
            finishRound();
            return;
        }

        if (starterValue != null && stealValue == null) {
            addPoints(scoreOwner(roundStarter), 5);
            Toast.makeText(this, getString(R.string.number_closer_points, roundStarter), Toast.LENGTH_SHORT).show();
            finishRound();
            return;
        }

        if (starterValue == null) {
            finishRound();
            return;
        }

        int starterDistance = Math.abs((int) Math.round(starterValue - targetNumber));
        int stealDistance = Math.abs((int) Math.round(stealValue - targetNumber));

        if (starterDistance < stealDistance) {
            addPoints(scoreOwner(roundStarter), 5);
            Toast.makeText(this, getString(R.string.number_closer_points, roundStarter), Toast.LENGTH_SHORT).show();
        } else if (stealDistance < starterDistance) {
            addPoints(scoreOwner(stealPlayer), 5);
            Toast.makeText(this, getString(R.string.number_closer_points, stealPlayer), Toast.LENGTH_SHORT).show();
        } else {
            if (Math.round(starterValue) != 0) {
                addPoints(scoreOwner(roundStarter), 5);
                Toast.makeText(this, getString(R.string.number_tie_starter_points, roundStarter), Toast.LENGTH_SHORT).show();
            }
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
                    tvTimer.setText(getString(R.string.number_next_round_in, seconds));
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
        String winner;
        if (player1Score > player2Score) {
            winner = getString(R.string.number_winner_player, 1);
        } else if (player2Score > player1Score) {
            winner = getString(R.string.number_winner_player, 2);
        } else {
            winner = getString(R.string.number_draw);
        }

        Toast.makeText(this,
                getString(R.string.number_final_score_message, player1Score, player2Score, winner),
                Toast.LENGTH_LONG).show();
        lastTimerSeconds = 0;
        publishState();
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
            if (phase == Phase.MAIN_ATTEMPT || phase == Phase.STEAL_ATTEMPT) {
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
        if (phase != Phase.MAIN_ATTEMPT && phase != Phase.STEAL_ATTEMPT) {
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
        if (phase != Phase.MAIN_ATTEMPT && phase != Phase.STEAL_ATTEMPT) {
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
    }

    private int opponent(int player) {
        return player == 1 ? 2 : 1;
    }

    private int scoreOwner(int normalOwner) {
        return soloMode ? myPlayerNumber : normalOwner;
    }

    private boolean isMyTurnForInput() {
        if (soloMode && phase != Phase.FINISHED) {
            return true;
        }
        if (phase == Phase.MAIN_ATTEMPT || phase == Phase.STOP_TARGET || phase == Phase.STOP_NUMBERS) {
            return roundStarter == myPlayerNumber;
        }
        if (phase == Phase.STEAL_ATTEMPT) {
            return opponent(roundStarter) == myPlayerNumber;
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
        boolean stealTurn = opponent(roundStarter) == myPlayerNumber;
        boolean shouldSend = starterTurn
                || phase == Phase.FINISHED
                || phase == Phase.ROUND_END
                || (phase == Phase.STEAL_ATTEMPT && stealTurn);
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
        int remoteTarget = data.optInt("target", 0);
        if (remoteTarget > 0) {
            targetNumber = remoteTarget;
        }
        JSONArray nums = data.optJSONArray("numbers");
        if (nums != null && nums.length() == 6) {
            availableNumbers.clear();
            for (int i = 0; i < nums.length(); i++) {
                availableNumbers.add(nums.optInt(i));
            }
            resetNumberUsage();
            for (int i = 0; i < numberViews.length; i++) {
                numberViews[i].setText(String.valueOf(availableNumbers.get(i)));
                numberViews[i].setAlpha(1f);
            }
        }

        tvRound.setText(getString(R.string.number_round_label, currentRound));
        tvTimer.setText(getString(R.string.number_timer_seconds, Math.max(0, lastTimerSeconds)));
        updateScoreText();
        tvTarget.setText(targetNumber == null ? getString(R.string.number_unknown_target) : String.valueOf(targetNumber));

        if (phase == Phase.STOP_TARGET) {
            cancelRoundTimers();
            etExpression.setEnabled(false);
            btnStop.setEnabled(roundStarter == myPlayerNumber);
            btnStop.setText(R.string.number_stop_target);
            tvPhase.setText(R.string.number_phase_stop_target);
            for (TextView view : numberViews) {
                view.setEnabled(false);
            }
            return;
        }

        if (phase == Phase.STOP_NUMBERS) {
            cancelRoundTimers();
            etExpression.setEnabled(false);
            btnStop.setEnabled(roundStarter == myPlayerNumber);
            btnStop.setText(R.string.number_stop_numbers);
            tvPhase.setText(R.string.number_phase_stop_numbers);
            for (TextView view : numberViews) {
                view.setEnabled(false);
            }
            return;
        }

        if (phase == Phase.MAIN_ATTEMPT) {
            tvPhase.setText(R.string.number_phase_main);
            btnStop.setText(R.string.number_confirm_expression);
            boolean myTurn = roundStarter == myPlayerNumber;
            etExpression.setEnabled(myTurn);
            btnStop.setEnabled(myTurn);
            for (TextView view : numberViews) {
                view.setEnabled(myTurn);
            }
            if (myTurn && roundTimer == null && lastTimerSeconds > 0) {
                startMainRoundTimer(lastTimerSeconds * 1000L);
            } else if (!myTurn) {
                cancelRoundTimers();
            }
            return;
        }

        if (phase == Phase.STEAL_ATTEMPT) {
            int stealPlayer = opponent(roundStarter);
            tvCurrentPlayer.setText(getString(R.string.number_current_player, stealPlayer));
            tvPhase.setText(getString(R.string.number_phase_steal, stealPlayer));
            btnStop.setText(R.string.number_confirm_expression);
            boolean myTurn = stealPlayer == myPlayerNumber;
            etExpression.setEnabled(myTurn);
            btnStop.setEnabled(myTurn);
            for (TextView view : numberViews) {
                view.setEnabled(myTurn);
                if (view.getAlpha() < 1f) {
                    view.setEnabled(false);
                }
            }
            if (myTurn && stealTimer == null && lastTimerSeconds > 0) {
                startStealTimer(lastTimerSeconds * 1000L);
            } else if (!myTurn) {
                cancelRoundTimers();
            }
            return;
        }

        if (phase == Phase.ROUND_END) {
            cancelRoundTimers();
            etExpression.setEnabled(false);
            btnStop.setEnabled(false);
            tvPhase.setText(R.string.number_next_round_wait);
            for (TextView view : numberViews) {
                view.setEnabled(false);
            }
            return;
        }

        if (phase == Phase.FINISHED && !remoteFinishHandled) {
            cancelRoundTimers();
            etExpression.setEnabled(false);
            btnStop.setEnabled(false);
            remoteFinishHandled = true;
            Intent resultIntent = new Intent();
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
            setResult(RESULT_OK, resultIntent);
            btnStop.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    finish();
                }
            }, 500);
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
        if (phase == Phase.FINISHED) {
            return;
        }
        if (phase == Phase.MAIN_ATTEMPT) {
            etExpression.setEnabled(true);
            btnStop.setEnabled(true);
            for (TextView view : numberViews) {
                view.setEnabled(true);
                if (view.getAlpha() < 1f) {
                    view.setEnabled(false);
                }
            }
            if (roundTimer == null && lastTimerSeconds > 0) {
                startMainRoundTimer(lastTimerSeconds * 1000L);
            }
        } else if (phase == Phase.STEAL_ATTEMPT) {
            etExpression.setEnabled(true);
            btnStop.setEnabled(true);
            for (TextView view : numberViews) {
                view.setEnabled(true);
                if (view.getAlpha() < 1f) {
                    view.setEnabled(false);
                }
            }
            if (stealTimer == null && lastTimerSeconds > 0) {
                startStealTimer(lastTimerSeconds * 1000L);
            }
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
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
