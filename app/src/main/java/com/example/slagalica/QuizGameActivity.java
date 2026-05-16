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

import org.json.JSONObject;

public class QuizGameActivity extends AppCompatActivity {
    private static final String GAME_ID = "quiz";

    private static final int[] POINT_OWNER = {1, 2, 2, 1, 1};

    private final String[] questions = {
            "Koja reka prolazi kroz Beograd?",
            "Koliko kontinenata postoji na Zemlji?",
            "Ko je napisao roman Na Drini cuprija?",
            "Koji je hemijski simbol za zlato?",
            "Koja planeta je najbliza Suncu?"
    };

    private final String[][] answers = {
            {"Sava", "Morava", "Drina", "Tisa"},
            {"Pet", "Sest", "Sedam", "Osam"},
            {"Mesa Selimovic", "Ivo Andric", "Branko Copic", "Jovan Ducic"},
            {"Ag", "Au", "Fe", "Zn"},
            {"Venera", "Mars", "Merkur", "Jupiter"}
    };

    private final int[] correctAnswers = {0, 2, 1, 1, 2};
    private final String[] questionStates = {
            "Oba igraca su odgovorila tacno. Brzi je Igrac 1.",
            "Oba igraca su odgovorila tacno. Brzi je Igrac 2.",
            "Ako niko ne odgovori za 5 sekundi, prelazi se dalje bez promene rezultata.",
            "Oba igraca su odgovorila tacno. Brzi je Igrac 1.",
            "Poslednje pitanje u rundi. Brzi odgovor nosi bodove."
    };

    private TextView tvQuestionIndex;
    private TextView tvCurrentPlayer;
    private TextView tvPhaseInfo;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvQuestion;
    private Button[] answerButtons;
    private Button btnNextQuestion;

    private int currentQuestion = 0;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean answered = false;
    private boolean noAnswerLock = false;
    private int selectedAnswerIndex = -1;
    private boolean gameFinished = false;
    private int lastTimerSeconds = 5;
    private CountDownTimer questionTimer;

    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private boolean soloMode = false;
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
        setContentView(R.layout.activity_quiz_game);

        matchRoomId = getIntent().getStringExtra("match_room_id");
        if (matchRoomId == null) {
            matchRoomId = "";
        }
        myPlayerNumber = getIntent().getIntExtra("match_my_player_number", 1);
        soloMode = getIntent().getBooleanExtra(MatchActivity.EXTRA_MATCH_SOLO_MODE, false) || TextUtils.isEmpty(matchRoomId);

        tvQuestionIndex = findViewById(R.id.tvQuizQuestionIndex);
        tvCurrentPlayer = findViewById(R.id.tvQuizCurrentPlayer);
        tvPhaseInfo = findViewById(R.id.tvQuizPhaseInfo);
        tvTimer = findViewById(R.id.tvQuizTimer);
        tvScore = findViewById(R.id.tvQuizScore);
        tvQuestion = findViewById(R.id.tvQuizQuestion);
        btnNextQuestion = findViewById(R.id.btnQuizNext);

        answerButtons = new Button[]{
                findViewById(R.id.btnQuizAnswer1),
                findViewById(R.id.btnQuizAnswer2),
                findViewById(R.id.btnQuizAnswer3),
                findViewById(R.id.btnQuizAnswer4)
        };

        for (int i = 0; i < answerButtons.length; i++) {
            final int index = i;
            answerButtons[i].setOnClickListener(v -> selectAnswer(index));
        }

        btnNextQuestion.setOnClickListener(v -> goToNextQuestion());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });

        renderFreshQuestion();
    }

    private boolean isControllerForCurrentQuestion() {
        return soloMode || POINT_OWNER[currentQuestion] == myPlayerNumber;
    }

    private void renderFreshQuestion() {
        answered = false;
        noAnswerLock = false;
        selectedAnswerIndex = -1;
        lastTimerSeconds = 5;
        refreshUiFromState();
        if (isControllerForCurrentQuestion()) {
            startQuestionTimer();
        } else {
            cancelQuestionTimer();
        }
        publishState();
    }

    private void refreshUiFromState() {
        if (gameFinished) {
            tvQuestionIndex.setText(R.string.quiz_round_finished);
            tvCurrentPlayer.setText("");
            tvPhaseInfo.setText(R.string.quiz_round_only_finished);
            tvTimer.setText(getString(R.string.quiz_timer_seconds, 0));
            tvQuestion.setText(R.string.quiz_round_only_finished);
            for (Button answerButton : answerButtons) {
                answerButton.setEnabled(false);
                answerButton.setBackgroundResource(R.drawable.quiz_answer_default_bg);
                answerButton.setText("");
            }
            btnNextQuestion.setEnabled(false);
            return;
        }

        tvQuestionIndex.setText(getString(R.string.quiz_question_index, currentQuestion + 1));
        tvCurrentPlayer.setText(getString(R.string.quiz_current_player, POINT_OWNER[currentQuestion]));
        tvScore.setText(getString(R.string.quiz_score_format, player1Score, player2Score));
        tvQuestion.setText(questions[currentQuestion]);
        tvTimer.setText(getString(R.string.quiz_timer_seconds, Math.max(0, lastTimerSeconds)));

        if (answered) {
            tvPhaseInfo.setText(selectedAnswerIndex == correctAnswers[currentQuestion]
                    ? getString(R.string.quiz_status_correct)
                    : getString(R.string.quiz_status_wrong));
        } else if (noAnswerLock) {
            tvPhaseInfo.setText(R.string.quiz_status_no_answer);
        } else {
            tvPhaseInfo.setText(questionStates[currentQuestion]);
        }

        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setText(answers[currentQuestion][i]);
            answerButtons[i].setBackgroundResource(R.drawable.quiz_answer_default_bg);
            answerButtons[i].setEnabled(isControllerForCurrentQuestion() && !answered && !noAnswerLock);
        }

        if (answered) {
            int correctIndex = correctAnswers[currentQuestion];
            if (selectedAnswerIndex == correctIndex) {
                answerButtons[selectedAnswerIndex].setBackgroundResource(R.drawable.quiz_answer_correct_bg);
            } else if (selectedAnswerIndex >= 0) {
                answerButtons[selectedAnswerIndex].setBackgroundResource(R.drawable.quiz_answer_wrong_bg);
                answerButtons[correctIndex].setBackgroundResource(R.drawable.quiz_answer_correct_bg);
            }
        }

        btnNextQuestion.setText(currentQuestion == questions.length - 1
                ? getString(R.string.quiz_finish_round)
                : getString(R.string.quiz_next_question));
        btnNextQuestion.setEnabled(isControllerForCurrentQuestion());
    }

    private void selectAnswer(int selectedIndex) {
        if (!isControllerForCurrentQuestion() || answered || noAnswerLock || gameFinished) {
            return;
        }

        answered = true;
        selectedAnswerIndex = selectedIndex;
        int correctIndex = correctAnswers[currentQuestion];

        if (selectedIndex == correctIndex) {
            if (POINT_OWNER[currentQuestion] == 1) {
                player1Score += 10;
            } else {
                player2Score += 10;
            }
        } else {
            if (POINT_OWNER[currentQuestion] == 1) {
                player1Score -= 5;
            } else {
                player2Score -= 5;
            }
        }

        cancelQuestionTimer();
        refreshUiFromState();
        publishState();
    }

    private void goToNextQuestion() {
        if (!isControllerForCurrentQuestion() || gameFinished) {
            return;
        }
        cancelQuestionTimer();
        if (currentQuestion < questions.length - 1) {
            currentQuestion++;
            renderFreshQuestion();
            return;
        }
        finishGameWithResult();
    }

    private void finishGameWithResult() {
        if (gameFinished) {
            return;
        }
        gameFinished = true;
        cancelQuestionTimer();
        refreshUiFromState();
        publishState();
        sendForceFinishEvent();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
        setResult(RESULT_OK, resultIntent);
        btnNextQuestion.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 500);
    }

    private void startQuestionTimer() {
        startQuestionTimerWithDuration(5000);
    }

    private void startQuestionTimerWithDuration(long durationMs) {
        cancelQuestionTimer();
        questionTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                lastTimerSeconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(getString(R.string.quiz_timer_seconds, lastTimerSeconds));
                publishState();
            }

            @Override
            public void onFinish() {
                lastTimerSeconds = 0;
                tvTimer.setText(getString(R.string.quiz_timer_seconds, 0));
                if (!answered) {
                    noAnswerLock = true;
                    refreshUiFromState();
                    publishState();
                    btnNextQuestion.postDelayed(() -> {
                        if (!isFinishing() && !isDestroyed() && !gameFinished) {
                            goToNextQuestion();
                        }
                    }, 900);
                }
            }
        }.start();
    }

    private void applyRemoteState(JSONObject data) {
        if (soloMode) {
            return;
        }
        int remoteQuestion = data.optInt("q", currentQuestion);
        if (remoteQuestion < 0 || remoteQuestion >= questions.length) {
            return;
        }
        currentQuestion = remoteQuestion;
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        answered = data.optBoolean("answered", answered);
        noAnswerLock = data.optBoolean("noAnswer", noAnswerLock);
        selectedAnswerIndex = data.optInt("selected", selectedAnswerIndex);
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        gameFinished = data.optBoolean("finished", gameFinished);
        cancelQuestionTimer();
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
    }

    private void publishState() {
        if (soloMode || TextUtils.isEmpty(matchRoomId) || !isControllerForCurrentQuestion()) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("q", currentQuestion);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("answered", answered);
            data.put("noAnswer", noAnswerLock);
            data.put("selected", selectedAnswerIndex);
            data.put("timer", lastTimerSeconds);
            data.put("finished", gameFinished);

            Intent i = new Intent(MatchActivity.ACTION_GAME_COMMAND);
            i.putExtra(MatchActivity.EXTRA_ROOM_ID, matchRoomId);
            i.putExtra(MatchActivity.EXTRA_GAME, GAME_ID);
            i.putExtra(MatchActivity.EXTRA_EVENT, "state");
            i.putExtra(MatchActivity.EXTRA_DATA, data.toString());
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    private void cancelQuestionTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
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
        if (gameFinished || answered || noAnswerLock) {
            return;
        }
        refreshUiFromState();
        if (questionTimer == null && lastTimerSeconds > 0) {
            startQuestionTimerWithDuration(lastTimerSeconds * 1000L);
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
        cancelQuestionTimer();
    }
}
