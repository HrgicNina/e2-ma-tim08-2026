package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.QuizRepository;
import com.example.slagalica.domain.EconomyService;
import com.example.slagalica.domain.PlayerStatsService;
import com.example.slagalica.domain.QuizGameService;
import com.example.slagalica.model.QuizQuestion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class QuizGameActivity extends AppCompatActivity {
    private static final String GAME_ID = "quiz";
    private static final long REVIEW_DELAY_MS = 1200L;

    private enum Phase {
        LOADING,
        ACTIVE,
        REVIEW,
        FINISHED
    }

    private TextView tvQuestionIndex;
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
    private TextView tvQuestion;
    private Button[] answerButtons;
    private Button btnNextQuestion;

    private QuizGameService quizService;
    private final List<QuizQuestion> questions = new ArrayList<>();
    private final int[] selectedAnswerIndexes = {-1, -1};
    private final long[] answeredAtMillis = {-1L, -1L};
    private Phase phase = Phase.LOADING;
    private CountDownTimer questionTimer;
    private CountDownTimer transitionTimer;

    private int currentQuestion = 0;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean gameFinished = false;
    private int lastTimerSeconds = 5;
    private int lastPlayer1Delta = 0;
    private int lastPlayer2Delta = 0;
    private int statsCorrect = 0;
    private int statsWrong = 0;
    private int statsNoAnswer = 0;
    private long questionStartedAtMillis = 0L;
    private String lastResultMessage = "";

    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private boolean soloMode = false;
    private boolean remoteFinishHandled = false;
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
            if ("answer".equals(event)) {
                try {
                    applyRemoteAnswerAttempt(new JSONObject(raw == null ? "{}" : raw));
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

        quizService = new QuizGameService(new QuizRepository());
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

        tvQuestionIndex = findViewById(R.id.tvQuizQuestionIndex);
        tvCurrentPlayer = findViewById(R.id.tvQuizCurrentPlayer);
        tvPhaseInfo = findViewById(R.id.tvQuizPhaseInfo);
        tvTimer = findViewById(R.id.tvQuizTimer);
        tvScore = findViewById(R.id.tvQuizScore);
        tvHeaderLeftAvatar = findViewById(R.id.tvHeaderLeftAvatar);
        tvHeaderLeftName = findViewById(R.id.tvHeaderLeftName);
        tvHeaderLeftScore = findViewById(R.id.tvHeaderLeftScore);
        tvHeaderRightAvatar = findViewById(R.id.tvHeaderRightAvatar);
        tvHeaderRightName = findViewById(R.id.tvHeaderRightName);
        tvHeaderRightScore = findViewById(R.id.tvHeaderRightScore);
        turnIndicatorAnimator = new TurnIndicatorAnimator(tvHeaderLeftAvatar, tvHeaderRightAvatar);
        tvQuestion = findViewById(R.id.tvQuizQuestion);
        btnNextQuestion = findViewById(R.id.btnQuizNext);
        bindMatchHeader();
        setupMyProfileTap();
        loadOpponentStatus();

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

        btnNextQuestion.setEnabled(false);
        btnNextQuestion.setText(R.string.quiz_next_question);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });

        renderLoading();
        if (isQuizController()) {
            loadQuestionsAndStart();
        }
    }

    private boolean isQuizController() {
        return soloMode || myPlayerNumber == 1;
    }

    private void loadQuestionsAndStart() {
        quizService.getRoundQuestions(loadedQuestions -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            questions.clear();
            questions.addAll(loadedQuestions);
            currentQuestion = 0;
            startQuestion();
        });
    }

    private void renderLoading() {
        phase = Phase.LOADING;
        tvQuestionIndex.setText("");
        tvCurrentPlayer.setText("");
        tvPhaseInfo.setText(isQuizController()
                ? getString(R.string.quiz_loading_questions)
                : getString(R.string.quiz_waiting_questions));
        tvTimer.setText(getString(R.string.quiz_timer_seconds, 0));
        updateScoreText();
        tvQuestion.setText(isQuizController()
                ? R.string.quiz_loading_questions
                : R.string.quiz_waiting_questions);
        for (Button answerButton : answerButtons) {
            answerButton.setText("");
            setAnswerButtonInteractive(answerButton, false);
            setAnswerButtonBackground(answerButton, R.drawable.quiz_answer_default_bg);
        }
        btnNextQuestion.setEnabled(false);
        refreshTurnIndicator();
    }

    private void startQuestion() {
        if (questions.isEmpty()) {
            renderLoading();
            return;
        }
        cancelQuestionTimer();
        cancelTransitionTimer();
        phase = Phase.ACTIVE;
        selectedAnswerIndexes[0] = -1;
        selectedAnswerIndexes[1] = -1;
        answeredAtMillis[0] = -1L;
        answeredAtMillis[1] = -1L;
        lastPlayer1Delta = 0;
        lastPlayer2Delta = 0;
        lastResultMessage = "";
        lastTimerSeconds = QuizGameService.QUESTION_TIME_MILLIS / 1000;
        questionStartedAtMillis = SystemClock.elapsedRealtime();
        refreshUiFromState();
        if (isQuizController()) {
            startQuestionTimer(QuizGameService.QUESTION_TIME_MILLIS);
        } else {
            cancelQuestionTimer();
        }
        publishState();
    }

    private void refreshUiFromState() {
        if (questions.isEmpty()) {
            renderLoading();
            return;
        }

        if (gameFinished || phase == Phase.FINISHED) {
            tvQuestionIndex.setText("");
            tvCurrentPlayer.setText("");
            tvPhaseInfo.setText(R.string.quiz_round_only_finished);
            tvTimer.setText(getString(R.string.quiz_timer_seconds, 0));
            tvQuestion.setText(R.string.quiz_round_only_finished);
            for (Button answerButton : answerButtons) {
                setAnswerButtonInteractive(answerButton, false);
                setAnswerButtonBackground(answerButton, R.drawable.quiz_answer_default_bg);
                answerButton.setText("");
            }
            btnNextQuestion.setEnabled(false);
            updateScoreText();
            refreshTurnIndicator();
            return;
        }

        QuizQuestion question = questions.get(currentQuestion);
        tvQuestionIndex.setText(getString(R.string.quiz_question_index, currentQuestion + 1));
        tvCurrentPlayer.setText(getString(R.string.quiz_current_player, myPlayerNumber));
        tvQuestion.setText(question.getQuestion());
        tvTimer.setText(getString(R.string.quiz_timer_seconds, Math.max(0, lastTimerSeconds)));
        updateScoreText();

        if (phase == Phase.ACTIVE) {
            if (hasAnswered(myPlayerNumber)) {
                tvPhaseInfo.setText(R.string.quiz_answer_saved);
            } else {
                tvPhaseInfo.setText(R.string.quiz_answer_now);
            }
        } else if (phase == Phase.REVIEW) {
            tvPhaseInfo.setText(lastResultMessage);
        } else {
            tvPhaseInfo.setText("");
        }

        List<String> answers = question.getAnswers();
        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setText(answers.get(i));
            setAnswerButtonBackground(answerButtons[i], R.drawable.quiz_answer_default_bg);
            setAnswerButtonInteractive(answerButtons[i], canAnswerLocal());
        }

        if (phase == Phase.REVIEW) {
            revealLocalAnswerStyle(question);
        } else if (phase == Phase.ACTIVE && hasAnswered(myPlayerNumber)) {
            applyPendingAnswerStyle();
        }

        btnNextQuestion.setText(currentQuestion == questions.size() - 1
                ? getString(R.string.quiz_finish_round)
                : getString(R.string.quiz_next_question));
        btnNextQuestion.setEnabled(false);
        refreshTurnIndicator();
    }

    private boolean canAnswerLocal() {
        return phase == Phase.ACTIVE
                && !gameFinished
                && !questions.isEmpty()
                && !hasAnswered(myPlayerNumber);
    }

    private boolean hasAnswered(int player) {
        return player >= 1 && player <= 2 && selectedAnswerIndexes[player - 1] >= 0;
    }

    private void selectAnswer(int selectedIndex) {
        if (!canAnswerLocal()) {
            return;
        }
        long answerTime = elapsedAnswerTime();
        recordAnswer(myPlayerNumber, selectedIndex, answerTime);
        refreshUiFromState();

        if (isQuizController()) {
            publishState();
            if (shouldResolveQuestionEarly()) {
                resolveQuestion();
            }
            return;
        }

        sendAnswerAttemptEvent(selectedIndex, answerTime);
    }

    private long elapsedAnswerTime() {
        long elapsed = SystemClock.elapsedRealtime() - questionStartedAtMillis;
        return Math.max(0L, Math.min(QuizGameService.QUESTION_TIME_MILLIS, elapsed));
    }

    private void recordAnswer(int player, int selectedIndex, long answerTime) {
        if (player < 1 || player > 2 || selectedIndex < 0 || selectedIndex >= 4 || hasAnswered(player)) {
            return;
        }
        selectedAnswerIndexes[player - 1] = selectedIndex;
        answeredAtMillis[player - 1] = Math.max(0L, Math.min(QuizGameService.QUESTION_TIME_MILLIS, answerTime));
    }

    private boolean shouldResolveQuestionEarly() {
        if (soloMode) {
            return hasAnswered(myPlayerNumber);
        }
        return hasAnswered(1) && hasAnswered(2);
    }

    private void resolveQuestion() {
        if (phase != Phase.ACTIVE || questions.isEmpty() || gameFinished) {
            return;
        }
        phase = Phase.REVIEW;
        cancelQuestionTimer();
        lastTimerSeconds = 0;

        QuizQuestion question = questions.get(currentQuestion);
        QuizGameService.AnswerAttempt player1Attempt = buildAttempt(1);
        QuizGameService.AnswerAttempt player2Attempt = buildAttempt(2);
        QuizGameService.QuestionScore score = quizService.scoreQuestion(question, player1Attempt, player2Attempt);
        captureLocalQuestionStats(question);

        lastPlayer1Delta = score.getPlayer1Delta();
        lastPlayer2Delta = score.getPlayer2Delta();
        player1Score += score.getPlayer1Delta();
        player2Score += score.getPlayer2Delta();
        lastResultMessage = buildResultMessage(score);

        refreshUiFromState();
        publishState();
        scheduleNextStep();
    }

    private QuizGameService.AnswerAttempt buildAttempt(int player) {
        if (!hasAnswered(player)) {
            return null;
        }
        return new QuizGameService.AnswerAttempt(
                selectedAnswerIndexes[player - 1],
                answeredAtMillis[player - 1]
        );
    }

    private String buildResultMessage(QuizGameService.QuestionScore score) {
        if (score.getFastestCorrectPlayer() == 0) {
            if (!hasAnswered(1) && !hasAnswered(2)) {
                return getString(R.string.quiz_status_no_answer);
            }
            return getString(R.string.quiz_no_correct_answer);
        }
        if (score.isPlayer1Correct() && score.isPlayer2Correct()) {
            return getString(R.string.quiz_fastest_player, score.getFastestCorrectPlayer());
        }
        return getString(R.string.quiz_single_correct_player, score.getFastestCorrectPlayer());
    }

    private void setAnswerButtonBackground(Button answerButton, int drawableRes) {
        answerButton.setBackgroundTintList(null);
        answerButton.setAlpha(1f);
        answerButton.setBackgroundResource(drawableRes);
    }

    private void setAnswerButtonInteractive(Button answerButton, boolean interactive) {
        answerButton.setEnabled(true);
        answerButton.setClickable(interactive);
        answerButton.setFocusable(interactive);
    }

    private void applyPendingAnswerStyle() {
        int selected = selectedAnswerIndexes[myPlayerNumber - 1];
        for (int i = 0; i < answerButtons.length; i++) {
            setAnswerButtonInteractive(answerButtons[i], false);
            setAnswerButtonBackground(answerButtons[i], i == selected
                    ? R.drawable.quiz_answer_pending_bg
                    : R.drawable.quiz_answer_disabled_bg);
        }
    }

    private void revealLocalAnswerStyle(QuizQuestion question) {
        for (Button answerButton : answerButtons) {
            setAnswerButtonInteractive(answerButton, false);
            setAnswerButtonBackground(answerButton, R.drawable.quiz_answer_default_bg);
        }
        if (!hasAnswered(myPlayerNumber)) {
            return;
        }
        int selected = selectedAnswerIndexes[myPlayerNumber - 1];
        if (selected != question.getCorrectAnswerIndex()) {
            setAnswerButtonBackground(answerButtons[selected], R.drawable.quiz_answer_wrong_bg);
            return;
        }
        int localDelta = myPlayerNumber == 1 ? lastPlayer1Delta : lastPlayer2Delta;
        setAnswerButtonBackground(answerButtons[selected], localDelta > 0
                ? R.drawable.quiz_answer_correct_bg
                : R.drawable.quiz_answer_late_correct_bg);
    }

    private void scheduleNextStep() {
        cancelTransitionTimer();
        transitionTimer = new CountDownTimer(REVIEW_DELAY_MS, REVIEW_DELAY_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                if (isFinishing() || isDestroyed() || gameFinished) {
                    return;
                }
                if (currentQuestion < questions.size() - 1) {
                    currentQuestion++;
                    startQuestion();
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
        cancelQuestionTimer();
        cancelTransitionTimer();
        refreshUiFromState();
        publishState();
        sendForceFinishEvent();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
        PlayerStatsService.putBaseGameStats(resultIntent, GAME_ID, 0, 50);
        resultIntent.putExtra(PlayerStatsService.EXTRA_STATS_QUIZ_CORRECT, statsCorrect);
        resultIntent.putExtra(PlayerStatsService.EXTRA_STATS_QUIZ_WRONG, statsWrong);
        resultIntent.putExtra(PlayerStatsService.EXTRA_STATS_QUIZ_NO_ANSWER, statsNoAnswer);
        setResult(RESULT_OK, resultIntent);
        btnNextQuestion.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 500);
    }

    private void captureLocalQuestionStats(QuizQuestion question) {
        if (!hasAnswered(myPlayerNumber)) {
            statsNoAnswer++;
            return;
        }
        int selected = selectedAnswerIndexes[myPlayerNumber - 1];
        if (selected == question.getCorrectAnswerIndex()) {
            statsCorrect++;
        } else {
            statsWrong++;
        }
    }

    private void startQuestionTimer(long durationMs) {
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
                resolveQuestion();
            }
        }.start();
    }

    private void applyRemoteState(JSONObject data) {
        if (soloMode) {
            return;
        }
        int remoteQuestion = data.optInt("q", currentQuestion);
        JSONArray questionsJson = data.optJSONArray("questions");
        if (questionsJson != null && questionsJson.length() >= QuizGameService.QUESTION_COUNT) {
            questions.clear();
            questions.addAll(parseQuestions(questionsJson));
        }
        if (questions.isEmpty() || remoteQuestion < 0 || remoteQuestion >= questions.size()) {
            return;
        }

        int previousQuestion = currentQuestion;
        Phase previousPhase = phase;
        Phase remotePhase = phaseFromString(data.optString("phase", phase.name()));
        int incomingP1Selected = data.optInt("p1Selected", -1);
        int incomingP2Selected = data.optInt("p2Selected", -1);
        long incomingP1Time = data.optLong("p1Time", -1L);
        long incomingP2Time = data.optLong("p2Time", -1L);

        if (remotePhase == Phase.ACTIVE && remoteQuestion == currentQuestion && hasAnswered(myPlayerNumber)) {
            if (myPlayerNumber == 1 && incomingP1Selected < 0) {
                incomingP1Selected = selectedAnswerIndexes[0];
                incomingP1Time = answeredAtMillis[0];
            } else if (myPlayerNumber == 2 && incomingP2Selected < 0) {
                incomingP2Selected = selectedAnswerIndexes[1];
                incomingP2Time = answeredAtMillis[1];
            }
        }

        currentQuestion = remoteQuestion;
        phase = remotePhase;
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        lastPlayer1Delta = data.optInt("p1Delta", lastPlayer1Delta);
        lastPlayer2Delta = data.optInt("p2Delta", lastPlayer2Delta);
        selectedAnswerIndexes[0] = incomingP1Selected;
        selectedAnswerIndexes[1] = incomingP2Selected;
        answeredAtMillis[0] = incomingP1Time;
        answeredAtMillis[1] = incomingP2Time;
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        lastResultMessage = data.optString("result", lastResultMessage);
        gameFinished = data.optBoolean("finished", gameFinished);

        cancelQuestionTimer();
        cancelTransitionTimer();
        if (phase == Phase.ACTIVE && (previousQuestion != currentQuestion || previousPhase != Phase.ACTIVE)) {
            long elapsed = QuizGameService.QUESTION_TIME_MILLIS - Math.max(0, lastTimerSeconds) * 1000L;
            questionStartedAtMillis = SystemClock.elapsedRealtime() - Math.max(0L, elapsed);
        }
        refreshUiFromState();
        if (gameFinished || phase == Phase.FINISHED) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
            setResult(RESULT_OK, resultIntent);
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }
    }

    private Phase phaseFromString(String value) {
        try {
            return Phase.valueOf(value);
        } catch (Exception ignored) {
            return phase;
        }
    }

    private void publishState() {
        if (soloMode || TextUtils.isEmpty(matchRoomId) || !isQuizController() || questions.isEmpty()) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("q", currentQuestion);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("phase", phase.name());
            data.put("timer", lastTimerSeconds);
            data.put("finished", gameFinished);
            data.put("p1Selected", selectedAnswerIndexes[0]);
            data.put("p2Selected", selectedAnswerIndexes[1]);
            data.put("p1Delta", lastPlayer1Delta);
            data.put("p2Delta", lastPlayer2Delta);
            data.put("p1Time", answeredAtMillis[0]);
            data.put("p2Time", answeredAtMillis[1]);
            data.put("result", lastResultMessage == null ? "" : lastResultMessage);
            data.put("questions", questionsToJson());

            Intent i = new Intent(MatchActivity.ACTION_GAME_COMMAND);
            i.putExtra(MatchActivity.EXTRA_ROOM_ID, matchRoomId);
            i.putExtra(MatchActivity.EXTRA_GAME, GAME_ID);
            i.putExtra(MatchActivity.EXTRA_EVENT, "state");
            i.putExtra(MatchActivity.EXTRA_DATA, data.toString());
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    private JSONArray questionsToJson() {
        JSONArray out = new JSONArray();
        for (QuizQuestion question : questions) {
            JSONObject item = new JSONObject();
            JSONArray answers = new JSONArray();
            try {
                item.put("question", question.getQuestion());
                for (String answer : question.getAnswers()) {
                    answers.put(answer);
                }
                item.put("answers", answers);
                item.put("correctAnswerIndex", question.getCorrectAnswerIndex());
                out.put(item);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private List<QuizQuestion> parseQuestions(JSONArray values) {
        List<QuizQuestion> parsed = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONArray answersJson = item.optJSONArray("answers");
            if (answersJson == null || answersJson.length() != 4) {
                continue;
            }
            List<String> answers = new ArrayList<>();
            for (int j = 0; j < answersJson.length(); j++) {
                answers.add(answersJson.optString(j));
            }
            String question = item.optString("question");
            int correct = item.optInt("correctAnswerIndex", -1);
            if (!TextUtils.isEmpty(question) && correct >= 0 && correct < 4) {
                parsed.add(new QuizQuestion(question, answers, correct));
            }
        }
        return parsed;
    }

    private void cancelQuestionTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }
    }

    private void cancelTransitionTimer() {
        if (transitionTimer != null) {
            transitionTimer.cancel();
            transitionTimer = null;
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
        if (gameFinished || phase == Phase.FINISHED) {
            refreshTurnIndicator();
            return;
        }
        if (phase == Phase.ACTIVE && hasAnswered(myPlayerNumber)) {
            resolveQuestion();
            return;
        }
        refreshUiFromState();
        if (phase == Phase.ACTIVE && questionTimer == null && lastTimerSeconds > 0) {
            long duration = Math.max(1000L, lastTimerSeconds * 1000L);
            questionStartedAtMillis = SystemClock.elapsedRealtime()
                    - (QuizGameService.QUESTION_TIME_MILLIS - duration);
            startQuestionTimer(duration);
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

    private void refreshTurnIndicator() {
        if (gameFinished || phase == Phase.FINISHED || phase == Phase.REVIEW || phase == Phase.LOADING) {
            turnIndicatorAnimator.setActivePlayer(null);
            return;
        }
        if (!hasAnswered(myPlayerNumber)) {
            turnIndicatorAnimator.setActivePlayer(myPlayerNumber);
            return;
        }
        int opponent = myPlayerNumber == 1 ? 2 : 1;
        turnIndicatorAnimator.setActivePlayer(hasAnswered(opponent) ? null : opponent);
    }

    private void updateScoreText() {
        tvScore.setText(getString(R.string.quiz_score_format, player1Score, player2Score));
        tvHeaderLeftScore.setText(String.valueOf(player1Score));
        tvHeaderRightScore.setText(String.valueOf(player2Score));
    }

    private void bindMatchHeader() {
        tvHeaderLeftName.setText(headerName(player1DisplayName));
        tvHeaderRightName.setText(headerName(player2DisplayName));
        AvatarFrameHelper.applyMatchAvatars(
                tvHeaderLeftAvatar,
                tvHeaderRightAvatar,
                getIntent(),
                player1DisplayName,
                player2DisplayName
        );
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

    private void sendAnswerAttemptEvent(int selectedIndex, long answerTime) {
        if (soloMode || TextUtils.isEmpty(matchRoomId)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("q", currentQuestion);
            data.put("player", myPlayerNumber);
            data.put("selected", selectedIndex);
            data.put("answeredAtMillis", answerTime);
            Intent i = new Intent(MatchActivity.ACTION_GAME_COMMAND);
            i.putExtra(MatchActivity.EXTRA_ROOM_ID, matchRoomId);
            i.putExtra(MatchActivity.EXTRA_GAME, GAME_ID);
            i.putExtra(MatchActivity.EXTRA_EVENT, "answer");
            i.putExtra(MatchActivity.EXTRA_DATA, data.toString());
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    private void applyRemoteAnswerAttempt(JSONObject data) {
        if (!isQuizController() || phase != Phase.ACTIVE) {
            return;
        }
        int q = data.optInt("q", -1);
        if (q != currentQuestion) {
            return;
        }
        int player = data.optInt("player", 0);
        int selected = data.optInt("selected", -1);
        long answerTime = data.optLong("answeredAtMillis", QuizGameService.QUESTION_TIME_MILLIS);
        if ((player != 1 && player != 2) || selected < 0 || selected >= answerButtons.length) {
            return;
        }
        recordAnswer(player, selected, answerTime);
        refreshUiFromState();
        publishState();
        if (shouldResolveQuestionEarly()) {
            resolveQuestion();
        }
    }

    private void applyForceFinish(JSONObject data) {
        if (remoteFinishHandled) {
            return;
        }
        remoteFinishHandled = true;
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        cancelQuestionTimer();
        cancelTransitionTimer();
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
        cancelTransitionTimer();
        turnIndicatorAnimator.clear();
    }
}

