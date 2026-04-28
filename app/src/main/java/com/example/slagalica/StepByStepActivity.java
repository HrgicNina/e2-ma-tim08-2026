package com.example.slagalica;

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

import java.util.List;

public class StepByStepActivity extends AppCompatActivity {

    private enum Phase {
        MAIN,
        STEAL,
        FINISHED
    }

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvClues;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_by_step);

        service = new StepByStepService(new StepByStepRepository());

        tvRound = findViewById(R.id.tvStepRound);
        tvCurrentPlayer = findViewById(R.id.tvStepCurrentPlayer);
        tvTimer = findViewById(R.id.tvStepTimer);
        tvScore = findViewById(R.id.tvStepScore);
        tvClues = findViewById(R.id.tvStepClues);
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
        tvClues.setText(R.string.step_loading_data);
        tvTimer.setText(getString(R.string.step_timer_seconds, 70));
        etAnswer.setText("");
        btnSubmit.setEnabled(false);
        updateScoreText();

        service.getRandomPuzzle(puzzle -> {
            currentPuzzle = puzzle;
            renderHiddenClues();
            startPrepCountdown();
        });
    }

    private void startPrepCountdown() {
        cancelTimers();
        btnSubmit.setEnabled(false);
        prepTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvPhaseInfo.setText(getString(R.string.step_round_starts_in, secondsLeft));
                tvTimer.setText(getString(R.string.step_timer_seconds, 70));
            }

            @Override
            public void onFinish() {
                tvPhaseInfo.setText(R.string.step_phase_main);
                tvTimer.setText(getString(R.string.step_timer_seconds, 70));
                renderClues();
                btnSubmit.setEnabled(true);
                startMainTimer();
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

                int elapsedSeconds = 70 - secondsLeft;
                int shouldReveal = Math.min(7, 1 + (elapsedSeconds / 10));
                if (shouldReveal > revealedStepCount) {
                    revealedStepCount = shouldReveal;
                    lastGuessedStep = -1;
                    renderClues();
                }
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

        stealTimer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.step_timer_seconds, secondsLeft));
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

        Toast.makeText(this,
                getString(R.string.step_round_finished_answer, currentPuzzle.getAnswer()),
                Toast.LENGTH_LONG).show();

        if (currentRound == 1) {
            currentRound = 2;
            roundStartingPlayer = 2;
            startRound();
            return;
        }

        phase = Phase.FINISHED;
        btnSubmit.setEnabled(false);
        tvPhaseInfo.setText(R.string.step_match_finished);
        tvCurrentPlayer.setText(R.string.step_match_done_label);
        tvTimer.setText(getString(R.string.step_timer_seconds, 0));

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
    }

    private void renderClues() {
        StringBuilder builder = new StringBuilder();
        List<String> clues = currentPuzzle.getClues();
        for (int i = 0; i < clues.size(); i++) {
            if (i < revealedStepCount) {
                builder.append(i + 1).append(". ").append(clues.get(i));
            } else {
                builder.append(i + 1).append(". ???");
            }
            if (i < clues.size() - 1) {
                builder.append("\n");
            }
        }
        tvClues.setText(builder.toString());
    }

    private void renderHiddenClues() {
        if (currentPuzzle == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        List<String> clues = currentPuzzle.getClues();
        for (int i = 0; i < clues.size(); i++) {
            builder.append(i + 1).append(". ???");
            if (i < clues.size() - 1) {
                builder.append("\n");
            }
        }
        tvClues.setText(builder.toString());
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimers();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && !isFinishing()) {
            finish();
        }
    }
}
