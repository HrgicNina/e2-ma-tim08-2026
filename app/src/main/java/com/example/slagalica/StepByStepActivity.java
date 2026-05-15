package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.StepByStepRepository;
import com.example.slagalica.domain.StepByStepService;
import com.example.slagalica.model.StepByStepPuzzle;

import org.json.JSONObject;

import java.util.List;
import java.util.Random;

public class StepByStepActivity extends AppCompatActivity {
    private static final String GAME_ID = "step";

    private enum Phase {
        MAIN,
        STEAL,
        FINISHED
    }

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView[] tvClues;
    private TextView tvSolution;
    private TextView tvPhaseInfo;
    private EditText etAnswer;
    private Button btnSubmit;

    private StepByStepService service;
    private StepByStepPuzzle currentPuzzle;

    private int currentRound = 1;
    private int roundStartingPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int revealedStepCount = 1;
    private int lastGuessedStep = -1;

    private Phase phase = Phase.MAIN;
    private CountDownTimer prepTimer;
    private CountDownTimer mainTimer;
    private CountDownTimer stealTimer;
    private CountDownTimer roundTransitionTimer;
    private CountDownTimer finishTimer;
    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private int lastTimerSeconds = 70;
    private final BroadcastReceiver gameEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!MatchActivity.ACTION_GAME_EVENT.equals(intent.getAction())) {
                return;
            }
            String room = intent.getStringExtra(MatchActivity.EXTRA_ROOM_ID);
            String game = intent.getStringExtra(MatchActivity.EXTRA_GAME);
            String event = intent.getStringExtra(MatchActivity.EXTRA_EVENT);
            if (!GAME_ID.equals(game) || !matchRoomId.equals(room) || !"state".equals(event)) {
                return;
            }
            String raw = intent.getStringExtra(MatchActivity.EXTRA_DATA);
            try {
                applyRemoteState(new JSONObject(raw == null ? "{}" : raw));
            } catch (Exception ignored) {
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_by_step);

        service = new StepByStepService(new StepByStepRepository());
        matchRoomId = getIntent().getStringExtra("match_room_id");
        if (matchRoomId == null) {
            matchRoomId = "";
        }
        myPlayerNumber = getIntent().getIntExtra("match_my_player_number", 1);

        tvRound = findViewById(R.id.tvStepRound);
        tvCurrentPlayer = findViewById(R.id.tvStepCurrentPlayer);
        tvTimer = findViewById(R.id.tvStepTimer);
        tvScore = findViewById(R.id.tvStepScore);
        tvClues = new TextView[]{
                findViewById(R.id.tvStepClue1),
                findViewById(R.id.tvStepClue2),
                findViewById(R.id.tvStepClue3),
                findViewById(R.id.tvStepClue4),
                findViewById(R.id.tvStepClue5),
                findViewById(R.id.tvStepClue6),
                findViewById(R.id.tvStepClue7)
        };
        tvSolution = findViewById(R.id.tvStepSolution);
        tvPhaseInfo = findViewById(R.id.tvStepPhaseInfo);
        etAnswer = findViewById(R.id.etStepAnswer);
        btnSubmit = findViewById(R.id.btnStepSubmit);

        btnSubmit.setOnClickListener(v -> submitAnswer());

        startRound();
    }

    private void startRound() {
        phase = Phase.MAIN;
        revealedStepCount = 1;
        lastGuessedStep = -1;

        tvRound.setText(getString(R.string.step_round_label, currentRound));
        tvCurrentPlayer.setText(getString(R.string.step_current_player, roundStartingPlayer));
        tvPhaseInfo.setText(R.string.step_loading_data);
        tvSolution.setText("");
        for (TextView clueView : tvClues) {
            clueView.setText(R.string.step_loading_data);
        }
        tvTimer.setText(getString(R.string.step_timer_seconds, 70));
        etAnswer.setText("");
        etAnswer.setEnabled(false);
        btnSubmit.setEnabled(false);
        updateScoreText();

        final int roundForSeed = currentRound;
        service.getPuzzles(puzzles -> {
            if (puzzles == null || puzzles.isEmpty()) {
                Toast.makeText(StepByStepActivity.this, R.string.step_loading_data, Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(matchRoomId)) {
                currentPuzzle = puzzles.get(new Random().nextInt(puzzles.size()));
            } else {
                int seed = (matchRoomId + "_step_" + roundForSeed).hashCode();
                int index = Math.floorMod(seed, puzzles.size());
                currentPuzzle = puzzles.get(index);
            }
            renderHiddenClues();
            startPrepCountdown();
        });
    }

    private void startPrepCountdown() {
        cancelTimers();
        btnSubmit.setEnabled(false);
        etAnswer.setEnabled(false);
        prepTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvPhaseInfo.setText(getString(R.string.step_round_starts_in, secondsLeft));
                tvTimer.setText(getString(R.string.step_timer_seconds, 70));
                lastTimerSeconds = 70;
            }

            @Override
            public void onFinish() {
                tvPhaseInfo.setText(R.string.step_phase_main);
                tvTimer.setText(getString(R.string.step_timer_seconds, 70));
                renderClues();
                boolean myTurn = roundStartingPlayer == myPlayerNumber;
                btnSubmit.setEnabled(myTurn);
                etAnswer.setEnabled(myTurn);
                startMainTimer();
                publishState();
            }
        }.start();
    }

    private void startMainTimer() {
        cancelTimers();
        mainTimer = new CountDownTimer(70000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.step_timer_seconds, secondsLeft));
                lastTimerSeconds = secondsLeft;

                int elapsedSeconds = 70 - secondsLeft;
                int shouldReveal = Math.min(7, 1 + (elapsedSeconds / 10));
                if (shouldReveal > revealedStepCount) {
                    revealedStepCount = shouldReveal;
                    lastGuessedStep = -1;
                    renderClues();
                }
                publishState();
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.step_timer_seconds, 0));
                openStealPhase();
            }
        }.start();
    }

    private void openStealPhase() {
        phase = Phase.STEAL;
        int stealPlayer = opponent(roundStartingPlayer);
        tvCurrentPlayer.setText(getString(R.string.step_current_player, stealPlayer));
        tvPhaseInfo.setText(getString(R.string.step_phase_steal, stealPlayer));
        etAnswer.setText("");
        boolean myStealTurn = stealPlayer == myPlayerNumber;
        etAnswer.setEnabled(myStealTurn);
        btnSubmit.setEnabled(myStealTurn);
        publishState();

        stealTimer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.step_timer_seconds, secondsLeft));
                lastTimerSeconds = secondsLeft;
                publishState();
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.step_timer_seconds, 0));
                Toast.makeText(StepByStepActivity.this, R.string.step_steal_failed, Toast.LENGTH_SHORT).show();
                finishRound();
            }
        }.start();
    }

    private void submitAnswer() {
        if (currentPuzzle == null) {
            Toast.makeText(this, R.string.step_loading_data, Toast.LENGTH_SHORT).show();
            return;
        }

        if (phase == Phase.MAIN && roundStartingPlayer != myPlayerNumber) {
            return;
        }
        if (phase == Phase.STEAL && opponent(roundStartingPlayer) != myPlayerNumber) {
            return;
        }

        String answer = etAnswer.getText().toString();
        if (TextUtils.isEmpty(answer)) {
            Toast.makeText(this, R.string.step_enter_answer, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean correct = service.isCorrectAnswer(answer, currentPuzzle.getAnswer());

        if (phase == Phase.MAIN) {
            if (lastGuessedStep == revealedStepCount) {
                Toast.makeText(this, R.string.step_one_guess_per_step, Toast.LENGTH_SHORT).show();
                return;
            }
            lastGuessedStep = revealedStepCount;

            int points = service.pointsForStep(revealedStepCount);
            if (correct) {
                addPoints(roundStartingPlayer, points);
            Toast.makeText(this, getString(R.string.step_main_correct_points, points), Toast.LENGTH_SHORT).show();
            finishRound();
            return;
            }
            Toast.makeText(this, R.string.step_wrong_answer, Toast.LENGTH_SHORT).show();
            etAnswer.setText("");
            return;
        }

        if (phase == Phase.STEAL && correct) {
            int stealPlayer = opponent(roundStartingPlayer);
            addPoints(stealPlayer, 5);
            Toast.makeText(this, getString(R.string.step_steal_correct_points, stealPlayer), Toast.LENGTH_SHORT).show();
            finishRound();
            return;
        }
        Toast.makeText(this, R.string.step_wrong_answer, Toast.LENGTH_SHORT).show();
        etAnswer.setText("");
    }

    private void finishRound() {
        cancelTimers();
        revealAllClues();
        tvSolution.setText(getString(R.string.step_solution_format, currentPuzzle.getAnswer()));
        btnSubmit.setEnabled(false);
        etAnswer.setEnabled(false);
        publishState();

        if (currentRound == 1) {
            tvPhaseInfo.setText(R.string.step_next_round_waiting);
            roundTransitionTimer = new CountDownTimer(5000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                    tvTimer.setText(getString(R.string.step_timer_seconds, seconds));
                }

                @Override
                public void onFinish() {
                    currentRound = 2;
                    roundStartingPlayer = 2;
                    etAnswer.setEnabled(true);
                    startRound();
                }
            }.start();
            return;
        }

        phase = Phase.FINISHED;
        btnSubmit.setEnabled(false);
        etAnswer.setEnabled(false);
        tvPhaseInfo.setText(R.string.step_match_finished);
        tvCurrentPlayer.setText(R.string.step_match_done_label);
        tvTimer.setText(getString(R.string.step_timer_seconds, 0));
        lastTimerSeconds = 0;
        publishState();

        String winnerMessage;
        if (player1Score > player2Score) {
            winnerMessage = getString(R.string.step_winner_player, 1);
        } else if (player2Score > player1Score) {
            winnerMessage = getString(R.string.step_winner_player, 2);
        } else {
            winnerMessage = getString(R.string.step_draw);
        }

        Toast.makeText(this,
                getString(R.string.step_final_score_message, player1Score, player2Score, winnerMessage),
                Toast.LENGTH_LONG).show();
        finishTimer = new CountDownTimer(1200, 1200) {
            @Override
            public void onTick(long millisUntilFinished) { }

            @Override
            public void onFinish() {
                setResult(RESULT_OK);
                finish();
            }
        }.start();
    }

    private void renderClues() {
        List<String> clues = currentPuzzle.getClues();
        for (int i = 0; i < clues.size(); i++) {
            if (i < revealedStepCount) {
                tvClues[i].setText((i + 1) + ". " + clues.get(i));
            } else {
                tvClues[i].setText("");
            }
        }
    }

    private void renderHiddenClues() {
        if (currentPuzzle == null) {
            return;
        }
        List<String> clues = currentPuzzle.getClues();
        for (int i = 0; i < clues.size(); i++) {
            tvClues[i].setText("");
        }
    }

    private void revealAllClues() {
        if (currentPuzzle == null) {
            return;
        }
        List<String> clues = currentPuzzle.getClues();
        for (int i = 0; i < clues.size(); i++) {
            tvClues[i].setText((i + 1) + ". " + clues.get(i));
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
        tvScore.setText(getString(R.string.step_score_format, player1Score, player2Score));
    }

    private int opponent(int player) {
        return player == 1 ? 2 : 1;
    }

    private void cancelTimers() {
        if (prepTimer != null) {
            prepTimer.cancel();
            prepTimer = null;
        }
        if (mainTimer != null) {
            mainTimer.cancel();
            mainTimer = null;
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
        if (!isChangingConfigurations() && !isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimers();
    }

    private void publishState() {
        if (TextUtils.isEmpty(matchRoomId) || currentPuzzle == null) {
            return;
        }
        boolean myTurnMain = roundStartingPlayer == myPlayerNumber;
        boolean myTurnSteal = opponent(roundStartingPlayer) == myPlayerNumber;
        boolean shouldSend = (phase == Phase.MAIN && myTurnMain) || (phase == Phase.STEAL && myTurnSteal);
        if (!shouldSend) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("round", currentRound);
            data.put("starter", roundStartingPlayer);
            data.put("phase", phase.name());
            data.put("revealed", revealedStepCount);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("timer", lastTimerSeconds);
            data.put("answer", currentPuzzle.getAnswer());

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
        if (currentPuzzle == null) {
            return;
        }
        currentRound = data.optInt("round", currentRound);
        roundStartingPlayer = data.optInt("starter", roundStartingPlayer);
        revealedStepCount = data.optInt("revealed", revealedStepCount);
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        String phaseName = data.optString("phase", phase.name());
        try {
            phase = Phase.valueOf(phaseName);
        } catch (Exception ignored) {
        }

        tvRound.setText(getString(R.string.step_round_label, currentRound));
        tvTimer.setText(getString(R.string.step_timer_seconds, Math.max(0, lastTimerSeconds)));
        updateScoreText();

        if (phase == Phase.MAIN) {
            cancelTimers();
            renderClues();
            tvPhaseInfo.setText(R.string.step_phase_main);
            tvCurrentPlayer.setText(getString(R.string.step_current_player, roundStartingPlayer));
            boolean myTurn = roundStartingPlayer == myPlayerNumber;
            etAnswer.setEnabled(myTurn);
            btnSubmit.setEnabled(myTurn);
        } else if (phase == Phase.STEAL) {
            cancelTimers();
            renderClues();
            int stealPlayer = opponent(roundStartingPlayer);
            tvCurrentPlayer.setText(getString(R.string.step_current_player, stealPlayer));
            tvPhaseInfo.setText(getString(R.string.step_phase_steal, stealPlayer));
            boolean myTurn = stealPlayer == myPlayerNumber;
            etAnswer.setEnabled(myTurn);
            btnSubmit.setEnabled(myTurn);
        } else {
            cancelTimers();
            revealAllClues();
            String answer = data.optString("answer", currentPuzzle.getAnswer());
            tvSolution.setText(getString(R.string.step_solution_format, answer));
            etAnswer.setEnabled(false);
            btnSubmit.setEnabled(false);
        }
    }
}
