package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.EconomyService;

import com.example.slagalica.domain.StepByStepService;
import com.example.slagalica.model.StepByStepPuzzle;

import org.json.JSONObject;

import java.util.List;
import java.util.Random;

public class StepByStepActivity extends AppCompatActivity {
    private static final String GAME_ID = "step";

    private enum Phase {
        PREP,
        MAIN,
        STEAL,
        ROUND_END,
        FINISHED
    }

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
    private int revealedStepCount = 0;
    private int lastGuessedStep = -1;

    private Phase phase = Phase.PREP;
    private CountDownTimer prepTimer;
    private CountDownTimer mainTimer;
    private CountDownTimer stealTimer;
    private CountDownTimer roundTransitionTimer;
    private CountDownTimer finishTimer;
    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private boolean soloMode = false;
    private int lastTimerSeconds = 70;
    private boolean remoteFinishHandled = false;
    private int lastBootstrapPublishedRound = -1;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_by_step);

        service = new StepByStepService();
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
        myTokens = getIntent().getLongExtra(MatchActivity.EXTRA_MATCH_MY_TOKENS, 0L);
        myStars = getIntent().getLongExtra(MatchActivity.EXTRA_MATCH_MY_STARS, 0L);
        myLeague = getIntent().getLongExtra(MatchActivity.EXTRA_MATCH_MY_LEAGUE, 0L);

        tvRound = findViewById(R.id.tvStepRound);
        tvCurrentPlayer = findViewById(R.id.tvStepCurrentPlayer);
        tvTimer = findViewById(R.id.tvStepTimer);
        tvScore = findViewById(R.id.tvStepScore);
        tvHeaderLeftAvatar = findViewById(R.id.tvHeaderLeftAvatar);
        tvHeaderLeftName = findViewById(R.id.tvHeaderLeftName);
        tvHeaderLeftScore = findViewById(R.id.tvHeaderLeftScore);
        tvHeaderRightAvatar = findViewById(R.id.tvHeaderRightAvatar);
        tvHeaderRightName = findViewById(R.id.tvHeaderRightName);
        tvHeaderRightScore = findViewById(R.id.tvHeaderRightScore);
        turnIndicatorAnimator = new TurnIndicatorAnimator(tvHeaderLeftAvatar, tvHeaderRightAvatar);
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
        bindMatchHeader();
        setupMyProfileTap();
        loadOpponentStatus();

        btnSubmit.setOnClickListener(v -> submitAnswer());
        etAnswer.setOnEditorActionListener((v, actionId, event) -> {
            boolean imeDone = actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEND;
            boolean enterDown = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (imeDone || enterDown) {
                submitAnswer();
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });

        startRound();
    }

    private void startRound() {
        phase = Phase.PREP;
        remoteFinishHandled = false;
        revealedStepCount = 0;
        lastGuessedStep = -1;

        tvRound.setText("");
        tvCurrentPlayer.setText(getString(R.string.step_current_player, roundStartingPlayer));
        tvPhaseInfo.setText("");
        tvSolution.setText("");
        for (TextView clueView : tvClues) {
            clueView.setText("");
        }
        tvTimer.setText(getString(R.string.step_timer_seconds, 70));
        lastTimerSeconds = 70;
        etAnswer.setText("");
        etAnswer.setEnabled(false);
        btnSubmit.setEnabled(false);
        updateScoreText();
        refreshTurnIndicator();

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
            publishState(true);
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
                if (!soloMode && currentRound > 1) {
                    sendRoundChangeEvent();
                }
            }

            @Override
            public void onFinish() {
                phase = Phase.MAIN;
                revealedStepCount = 1;
                tvPhaseInfo.setText(R.string.step_phase_main);
                tvTimer.setText(getString(R.string.step_timer_seconds, 70));
                lastTimerSeconds = 70;
                renderClues();
                boolean myTurn = soloMode || roundStartingPlayer == myPlayerNumber;
                btnSubmit.setEnabled(myTurn);
                etAnswer.setEnabled(myTurn);
                if (myTurn) {
                    startMainTimer(70000);
                } else {
                    cancelMainAndStealTimers();
                }
                publishState(false);
                refreshTurnIndicator();
            }
        }.start();
    }

    private void startMainTimer(long durationMs) {
        cancelMainAndStealTimers();
        mainTimer = new CountDownTimer(durationMs, 1000) {
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
                publishState(false);
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.step_timer_seconds, 0));
                openStealPhase();
            }
        }.start();
    }

    private void openStealPhase() {
        if (soloMode) {
            finishRound();
            return;
        }
        phase = Phase.STEAL;
        lastTimerSeconds = 10;
        tvTimer.setText(getString(R.string.step_timer_seconds, 10));
        int stealPlayer = opponent(roundStartingPlayer);
        tvCurrentPlayer.setText(getString(R.string.step_current_player, stealPlayer));
        tvPhaseInfo.setText(getString(R.string.step_phase_steal, stealPlayer));
        etAnswer.setText("");
        boolean myStealTurn = soloMode || stealPlayer == myPlayerNumber;
        etAnswer.setEnabled(myStealTurn);
        btnSubmit.setEnabled(myStealTurn);
        publishState(false);
        refreshTurnIndicator();

        if (myStealTurn) {
            startStealTimer(10000);
        } else {
            cancelMainAndStealTimers();
        }
    }

    private void startStealTimer(long durationMs) {
        cancelMainAndStealTimers();
        stealTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                tvTimer.setText(getString(R.string.step_timer_seconds, secondsLeft));
                lastTimerSeconds = secondsLeft;
                publishState(false);
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

        if (!soloMode && phase == Phase.MAIN && roundStartingPlayer != myPlayerNumber) {
            return;
        }
        if (!soloMode && phase == Phase.STEAL && opponent(roundStartingPlayer) != myPlayerNumber) {
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
                addPoints(soloMode ? myPlayerNumber : roundStartingPlayer, points);
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
            addPoints(soloMode ? myPlayerNumber : stealPlayer, 5);
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

        if (currentRound == 1 && !soloMode) {
            phase = Phase.ROUND_END;
            tvPhaseInfo.setText(R.string.step_next_round_waiting);
            publishState(false);
            refreshTurnIndicator();
            roundTransitionTimer = new CountDownTimer(5000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                    tvTimer.setText(getString(R.string.step_timer_seconds, seconds));
                    lastTimerSeconds = seconds;
                    publishState(false);
                }

                @Override
                public void onFinish() {
                    currentRound = 2;
                    roundStartingPlayer = soloMode ? myPlayerNumber : 2;
                    sendRoundChangeEvent();
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
        publishState(false);
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

    private int opponent(int player) {
        return player == 1 ? 2 : 1;
    }

    private void cancelTimers() {
        if (prepTimer != null) {
            prepTimer.cancel();
            prepTimer = null;
        }
        cancelMainAndStealTimers();
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
        turnIndicatorAnimator.clear();
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

    private void publishState() {
        publishState(false);
    }

    private void publishState(boolean force) {
        if (soloMode || TextUtils.isEmpty(matchRoomId) || currentPuzzle == null) {
            return;
        }
        boolean iAmRoundOwner = roundStartingPlayer == myPlayerNumber;
        boolean iAmStealPlayer = opponent(roundStartingPlayer) == myPlayerNumber;
        boolean shouldSend = iAmRoundOwner
                || phase == Phase.FINISHED
                || phase == Phase.ROUND_END
                || (phase == Phase.STEAL && iAmStealPlayer);
        if (!force && !shouldSend) {
            return;
        }
        if (force) {
            lastBootstrapPublishedRound = currentRound;
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
        if (soloMode) {
            return;
        }
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

        tvRound.setText("");
        tvTimer.setText(getString(R.string.step_timer_seconds, Math.max(0, lastTimerSeconds)));
        updateScoreText();

        if (phase == Phase.PREP) {
            cancelMainAndStealTimers();
            renderHiddenClues();
            etAnswer.setEnabled(false);
            btnSubmit.setEnabled(false);
            tvPhaseInfo.setText(R.string.step_loading_data);
        } else if (phase == Phase.MAIN) {
            cancelMainAndStealTimers();
            renderClues();
            tvPhaseInfo.setText(R.string.step_phase_main);
            tvCurrentPlayer.setText(getString(R.string.step_current_player, roundStartingPlayer));
            boolean myTurn = roundStartingPlayer == myPlayerNumber;
            etAnswer.setEnabled(myTurn);
            btnSubmit.setEnabled(myTurn);
            if (myTurn && mainTimer == null && lastTimerSeconds > 0) {
                startMainTimer(lastTimerSeconds * 1000L);
            }
        } else if (phase == Phase.STEAL) {
            cancelMainAndStealTimers();
            renderClues();
            int stealPlayer = opponent(roundStartingPlayer);
            tvCurrentPlayer.setText(getString(R.string.step_current_player, stealPlayer));
            tvPhaseInfo.setText(getString(R.string.step_phase_steal, stealPlayer));
            boolean myTurn = stealPlayer == myPlayerNumber;
            etAnswer.setEnabled(myTurn);
            btnSubmit.setEnabled(myTurn);
            if (myTurn && stealTimer == null && lastTimerSeconds > 0) {
                startStealTimer(lastTimerSeconds * 1000L);
            }
        } else if (phase == Phase.ROUND_END) {
            cancelMainAndStealTimers();
            revealAllClues();
            String answer = data.optString("answer", currentPuzzle.getAnswer());
            tvSolution.setText(getString(R.string.step_solution_format, answer));
            tvPhaseInfo.setText(R.string.step_next_round_waiting);
            etAnswer.setEnabled(false);
            btnSubmit.setEnabled(false);
        } else {
            cancelMainAndStealTimers();
            revealAllClues();
            String answer = data.optString("answer", currentPuzzle.getAnswer());
            tvSolution.setText(getString(R.string.step_solution_format, answer));
            etAnswer.setEnabled(false);
            btnSubmit.setEnabled(false);
            if (!remoteFinishHandled) {
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
        }
        refreshTurnIndicator();
    }

    private void enableSoloModeAfterForfeit() {
        soloMode = true;
        if (roundStartingPlayer != myPlayerNumber) {
            cancelTimers();
            currentRound = 2;
            roundStartingPlayer = myPlayerNumber;
            startRound();
            return;
        }
        roundStartingPlayer = myPlayerNumber;
        tvCurrentPlayer.setText(getString(R.string.step_current_player, roundStartingPlayer));
        if (phase == Phase.STEAL) {
            finishRound();
            return;
        }
        if (phase == Phase.MAIN) {
            etAnswer.setEnabled(true);
            btnSubmit.setEnabled(true);
            if (mainTimer == null && lastTimerSeconds > 0) {
                startMainTimer(lastTimerSeconds * 1000L);
            }
        }
        refreshTurnIndicator();
    }

    private void cancelMainAndStealTimers() {
        if (mainTimer != null) {
            mainTimer.cancel();
            mainTimer = null;
        }
        if (stealTimer != null) {
            stealTimer.cancel();
            stealTimer = null;
        }
    }

    private void refreshTurnIndicator() {
        if (soloMode && phase != Phase.FINISHED) {
            turnIndicatorAnimator.setActivePlayer(myPlayerNumber);
            return;
        }
        if (phase == Phase.MAIN) {
            turnIndicatorAnimator.setActivePlayer(roundStartingPlayer);
            return;
        }
        if (phase == Phase.STEAL) {
            turnIndicatorAnimator.setActivePlayer(opponent(roundStartingPlayer));
            return;
        }
        turnIndicatorAnimator.setActivePlayer(null);
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
            data.put("starter", roundStartingPlayer);
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

    private void applyRoundChange(JSONObject data) {
        if (soloMode || remoteFinishHandled) {
            return;
        }
        int remoteRound = data.optInt("round", currentRound);
        int remoteStarter = data.optInt("starter", roundStartingPlayer);
        if (remoteRound <= currentRound) {
            return;
        }
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        currentRound = remoteRound;
        roundStartingPlayer = remoteStarter;
        try {
            phase = Phase.valueOf(data.optString("phase", Phase.MAIN.name()));
        } catch (Exception ignored) {
            phase = Phase.MAIN;
        }
        cancelTimers();
        updateScoreText();
        startRound();
    }
}



