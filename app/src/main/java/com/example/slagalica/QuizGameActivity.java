package com.example.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class QuizGameActivity extends AppCompatActivity {

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
    private CountDownTimer questionTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_game);

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

        renderQuestion();
    }

    private void renderQuestion() {
        answered = false;
        cancelQuestionTimer();
        tvQuestionIndex.setText(getString(R.string.quiz_question_index, currentQuestion + 1));
        tvCurrentPlayer.setText(getString(R.string.quiz_current_player, POINT_OWNER[currentQuestion]));
        tvPhaseInfo.setText(questionStates[currentQuestion]);
        tvTimer.setText(getString(R.string.quiz_timer_seconds, 5));
        tvScore.setText(getString(R.string.quiz_score_format, player1Score, player2Score));
        tvQuestion.setText(questions[currentQuestion]);

        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setText(answers[currentQuestion][i]);
            answerButtons[i].setEnabled(true);
            answerButtons[i].setBackgroundResource(R.drawable.quiz_answer_default_bg);
        }

        if (currentQuestion == questions.length - 1) {
            btnNextQuestion.setText(R.string.quiz_finish_round);
        } else {
            btnNextQuestion.setText(R.string.quiz_next_question);
        }
        btnNextQuestion.setEnabled(true);

        startQuestionTimer();
    }

    private void selectAnswer(int selectedIndex) {
        if (answered) {
            return;
        }

        answered = true;
        int correctIndex = correctAnswers[currentQuestion];

        for (Button answerButton : answerButtons) {
            answerButton.setEnabled(false);
        }

        if (selectedIndex == correctIndex) {
            answerButtons[selectedIndex].setBackgroundResource(R.drawable.quiz_answer_correct_bg);
            if (POINT_OWNER[currentQuestion] == 1) {
                player1Score += 10;
            } else {
                player2Score += 10;
            }
            tvPhaseInfo.setText(R.string.quiz_status_correct);
        } else {
            answerButtons[selectedIndex].setBackgroundResource(R.drawable.quiz_answer_wrong_bg);
            answerButtons[correctIndex].setBackgroundResource(R.drawable.quiz_answer_correct_bg);
            if (POINT_OWNER[currentQuestion] == 1) {
                player1Score -= 5;
            } else {
                player2Score -= 5;
            }
            tvPhaseInfo.setText(R.string.quiz_status_wrong);
        }

        cancelQuestionTimer();
        tvScore.setText(getString(R.string.quiz_score_format, player1Score, player2Score));
    }

    private void goToNextQuestion() {
        cancelQuestionTimer();
        if (currentQuestion < questions.length - 1) {
            currentQuestion++;
            renderQuestion();
            return;
        }

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
    }

    private void startQuestionTimer() {
        questionTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(getString(R.string.quiz_timer_seconds, seconds));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.quiz_timer_seconds, 0));
                if (!answered) {
                    for (Button answerButton : answerButtons) {
                        answerButton.setEnabled(false);
                    }
                    tvPhaseInfo.setText(R.string.quiz_status_no_answer);
                    btnNextQuestion.postDelayed(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            goToNextQuestion();
                        }
                    }, 900);
                }
            }
        }.start();
    }

    private void cancelQuestionTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelQuestionTimer();
    }
}
