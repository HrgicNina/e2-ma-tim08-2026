package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.data.AssociationsRepository;
import com.example.slagalica.domain.AssociationsGameService;
import com.example.slagalica.domain.EconomyService;
import com.example.slagalica.domain.PlayerStatsService;
import com.example.slagalica.model.AssociationPuzzle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AssociationsGameActivity extends AppCompatActivity {
    private static final String GAME_ID = "associations";
    private static final long ROUND_TRANSITION_MS = 1200L;

    private enum Phase {
        LOADING,
        ACTIVE,
        ROUND_FINISHED,
        FINISHED
    }

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
    private TextView tvSelectedColumn;
    private Button[] clueButtons;
    private EditText[] columnInputs;
    private Button btnSolveColumn;
    private ImageButton btnSolveFinal;
    private Button btnContinue;
    private TextView tvFinalSolution;
    private EditText etGuess;
    private EditText etFinalGuess;

    private AssociationsGameService associationsService;
    private final List<AssociationPuzzle> puzzles = new ArrayList<>();
    private final boolean[] openedClues = new boolean[AssociationsGameService.TOTAL_CLUES];
    private final boolean[] solvedColumns = new boolean[AssociationsGameService.COLUMN_COUNT];

    private Phase phase = Phase.LOADING;
    private int currentRound = 0;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int roundAwardedPoints = 0;
    private int selectedColumn = -1;
    private boolean canGuess = false;
    private boolean finalSolved = false;
    private boolean gameFinished = false;
    private boolean remoteFinishHandled = false;
    private int lastTimerSeconds = 120;
    private int statsSolvedCount = 0;
    private CountDownTimer roundTimer;
    private CountDownTimer transitionTimer;

    private String matchRoomId = "";
    private int myPlayerNumber = 1;
    private boolean soloMode = false;
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
        setContentView(R.layout.activity_associations_game);

        associationsService = new AssociationsGameService(new AssociationsRepository());
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

        tvRound = findViewById(R.id.tvAssociationsRound);
        tvCurrentPlayer = findViewById(R.id.tvAssociationsCurrentPlayer);
        tvPhaseInfo = findViewById(R.id.tvAssociationsPhaseInfo);
        tvTimer = findViewById(R.id.tvAssociationsTimer);
        tvScore = findViewById(R.id.tvAssociationsScore);
        tvHeaderLeftAvatar = findViewById(R.id.tvHeaderLeftAvatar);
        tvHeaderLeftName = findViewById(R.id.tvHeaderLeftName);
        tvHeaderLeftScore = findViewById(R.id.tvHeaderLeftScore);
        tvHeaderRightAvatar = findViewById(R.id.tvHeaderRightAvatar);
        tvHeaderRightName = findViewById(R.id.tvHeaderRightName);
        tvHeaderRightScore = findViewById(R.id.tvHeaderRightScore);
        turnIndicatorAnimator = new TurnIndicatorAnimator(tvHeaderLeftAvatar, tvHeaderRightAvatar);
        tvSelectedColumn = findViewById(R.id.tvAssociationsSelectedColumn);
        etGuess = findViewById(R.id.etAssociationsGuess);
        etFinalGuess = findViewById(R.id.etAssociationsFinalGuess);
        btnSolveColumn = findViewById(R.id.btnAssociationsSolveColumn);
        btnSolveFinal = findViewById(R.id.btnAssociationsSolveFinal);
        btnContinue = findViewById(R.id.btnAssociationsContinue);
        tvFinalSolution = findViewById(R.id.tvAssociationsFinalSolution);
        bindMatchHeader();
        setupMyProfileTap();
        loadOpponentStatus();

        clueButtons = new Button[]{
                findViewById(R.id.btnA1), findViewById(R.id.btnA2), findViewById(R.id.btnA3), findViewById(R.id.btnA4),
                findViewById(R.id.btnB1), findViewById(R.id.btnB2), findViewById(R.id.btnB3), findViewById(R.id.btnB4),
                findViewById(R.id.btnC1), findViewById(R.id.btnC2), findViewById(R.id.btnC3), findViewById(R.id.btnC4),
                findViewById(R.id.btnD1), findViewById(R.id.btnD2), findViewById(R.id.btnD3), findViewById(R.id.btnD4)
        };

        columnInputs = new EditText[]{
                findViewById(R.id.btnSolveA),
                findViewById(R.id.btnSolveB),
                findViewById(R.id.btnSolveC),
                findViewById(R.id.btnSolveD)
        };

        for (int i = 0; i < clueButtons.length; i++) {
            final int index = i;
            clueButtons[i].setOnClickListener(v -> openClue(index));
        }

        for (int i = 0; i < columnInputs.length; i++) {
            final int index = i;
            columnInputs[i].setHint(getString(R.string.associations_column_placeholder, columnName(index)));
            columnInputs[i].setOnClickListener(v -> selectColumn(index));
            columnInputs[i].setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    selectColumn(index);
                }
            });
            columnInputs[i].setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    solveColumn(index);
                    return true;
                }
                return false;
            });
        }

        btnSolveColumn.setOnClickListener(v -> solveSelectedColumn());
        btnSolveFinal.setOnClickListener(v -> confirmCurrentGuess());
        etFinalGuess.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                solveFinal();
                return true;
            }
            return false;
        });
        btnContinue.setOnClickListener(v -> passTurn());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });

        renderLoading();
        if (isPuzzleLoader()) {
            loadPuzzlesAndStart();
        }
    }

    private boolean isPuzzleLoader() {
        return soloMode || myPlayerNumber == 1;
    }

    private boolean isController() {
        return soloMode || currentPlayer == myPlayerNumber;
    }

    private boolean isRoundActive() {
        return phase == Phase.ACTIVE && !gameFinished && !finalSolved;
    }

    private void loadPuzzlesAndStart() {
        associationsService.getGamePuzzles(loadedPuzzles -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            puzzles.clear();
            puzzles.addAll(loadedPuzzles);
            currentRound = 0;
            startRound();
        });
    }

    private void renderLoading() {
        phase = Phase.LOADING;
        tvRound.setText(R.string.associations_title);
        tvCurrentPlayer.setText("");
        tvPhaseInfo.setText(isPuzzleLoader()
                ? R.string.associations_loading_puzzles
                : R.string.associations_waiting_puzzles);
        tvTimer.setText(getString(R.string.associations_timer_seconds, 0));
        tvSelectedColumn.setText(R.string.associations_selected_none);
        tvFinalSolution.setText(R.string.associations_final_placeholder);
        updateScoreText();
        for (Button button : clueButtons) {
            button.setText("");
            setGameButtonBackground(button, R.drawable.associations_closed_bg);
            setGameButtonInteractive(button, false);
        }
        for (EditText input : columnInputs) {
            input.setText("");
            input.setEnabled(false);
            input.setAlpha(0.55f);
        }
        etFinalGuess.setText("");
        etFinalGuess.setEnabled(false);
        etFinalGuess.setAlpha(0.55f);
        updateControls(false);
        refreshTurnIndicator();
    }

    private void startRound() {
        if (puzzles.size() < AssociationsGameService.ROUND_COUNT) {
            renderLoading();
            return;
        }
        cancelRoundTimer();
        cancelTransitionTimer();
        phase = Phase.ACTIVE;
        currentPlayer = currentRound == 0 ? 1 : 2;
        roundAwardedPoints = 0;
        selectedColumn = -1;
        canGuess = false;
        finalSolved = false;
        gameFinished = false;
        lastTimerSeconds = AssociationsGameService.ROUND_TIME_MILLIS / 1000;

        for (int i = 0; i < openedClues.length; i++) {
            openedClues[i] = false;
        }
        for (int i = 0; i < solvedColumns.length; i++) {
            solvedColumns[i] = false;
        }

        clearEditableGuesses();
        refreshUiFromState();
        if (isController()) {
            startTimer(AssociationsGameService.ROUND_TIME_MILLIS);
        }
        publishState();
    }

    private void refreshUiFromState() {
        if (puzzles.isEmpty()) {
            renderLoading();
            return;
        }

        if (gameFinished || phase == Phase.FINISHED) {
            tvRound.setText(R.string.associations_title);
            tvCurrentPlayer.setText("");
            tvPhaseInfo.setText(R.string.associations_round_finished);
            tvTimer.setText(getString(R.string.associations_timer_seconds, 0));
            tvSelectedColumn.setText(R.string.associations_selected_none);
            tvFinalSolution.setText(getString(R.string.associations_final_label, puzzles.get(currentRound).getFinalSolution()));
            updateRoundTexts();
            refreshButtons();
            updateControls(false);
            updateScoreText();
            refreshTurnIndicator();
            return;
        }

        tvRound.setText(getString(R.string.associations_round_title, currentRound + 1));
        tvCurrentPlayer.setText(phase == Phase.ROUND_FINISHED ? "" : getString(R.string.associations_current_player, currentPlayer));
        updateScoreText();
        tvSelectedColumn.setText(selectedColumn == -1
                ? getString(R.string.associations_selected_none)
                : getString(R.string.associations_selected_column, columnName(selectedColumn)));
        tvFinalSolution.setText(finalSolved || phase == Phase.ROUND_FINISHED
                ? getString(R.string.associations_final_label, puzzles.get(currentRound).getFinalSolution())
                : getString(R.string.associations_final_placeholder));
        tvTimer.setText(getString(R.string.associations_timer_seconds, Math.max(0, lastTimerSeconds)));

        updateRoundTexts();
        refreshButtons();
        updateControls(isController() && isRoundActive());
        refreshTurnIndicator();

        if (phase == Phase.ROUND_FINISHED) {
            tvPhaseInfo.setText(R.string.associations_round_finished);
        } else if (!isController()) {
            tvPhaseInfo.setText(R.string.associations_spectator_turn);
        } else if (canGuess) {
            tvPhaseInfo.setText(R.string.associations_guess_phase);
        } else {
            tvPhaseInfo.setText(R.string.associations_status_idle);
        }
    }

    private void updateRoundTexts() {
        AssociationPuzzle puzzle = puzzles.get(currentRound);
        for (int i = 0; i < clueButtons.length; i++) {
            int column = i / AssociationsGameService.CLUE_COUNT;
            int row = i % AssociationsGameService.CLUE_COUNT;
            if (openedClues[i] || phase == Phase.ROUND_FINISHED || phase == Phase.FINISHED || finalSolved) {
                clueButtons[i].setText(puzzle.getColumns().get(column).get(row));
            } else {
                clueButtons[i].setText(getClueLabel(i));
            }
        }
        for (int i = 0; i < columnInputs.length; i++) {
            columnInputs[i].setHint(getString(R.string.associations_column_placeholder, columnName(i)));
            if (solvedColumns[i] || phase == Phase.ROUND_FINISHED || phase == Phase.FINISHED || finalSolved) {
                columnInputs[i].setText(puzzle.getColumnSolutions().get(i));
            }
        }
        if (finalSolved || phase == Phase.ROUND_FINISHED || phase == Phase.FINISHED) {
            etFinalGuess.setText(puzzle.getFinalSolution());
        }
    }

    private void startTimer(long durationMs) {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                lastTimerSeconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(getString(R.string.associations_timer_seconds, Math.max(0, lastTimerSeconds)));
                publishState();
            }

            @Override
            public void onFinish() {
                lastTimerSeconds = 0;
                tvTimer.setText(getString(R.string.associations_timer_seconds, 0));
                if (isController() && canGuess && !hasAnyTypedGuess()) {
                    showOwlToast("Protivnik na potezu");
                }
                finishRound();
            }
        }.start();
    }

    private void openClue(int index) {
        if (!canOpenClue(index)) {
            return;
        }
        openedClues[index] = true;
        selectedColumn = index / AssociationsGameService.CLUE_COUNT;
        canGuess = true;
        clearEditableGuesses();
        hideKeyboard();
        refreshUiFromState();
        publishState();
    }

    private boolean canOpenClue(int index) {
        int column = index / AssociationsGameService.CLUE_COUNT;
        return isController()
                && isRoundActive()
                && !canGuess
                && !openedClues[index]
                && !solvedColumns[column];
    }

    private void selectColumn(int column) {
        if (!isController() || !isRoundActive() || !canGuess || solvedColumns[column]) {
            return;
        }
        selectedColumn = column;
        refreshUiFromState();
        publishState();
    }

    private void solveSelectedColumn() {
        solveColumn(selectedColumn);
    }

    private void confirmCurrentGuess() {
        if (!isController() || !isRoundActive() || !canGuess) {
            return;
        }
        if (!TextUtils.isEmpty(etFinalGuess.getText().toString().trim())) {
            solveFinal();
            return;
        }
        if (selectedColumn != -1 && !TextUtils.isEmpty(columnInputs[selectedColumn].getText().toString().trim())) {
            solveColumn(selectedColumn);
            return;
        }
        passTurn();
    }

    private void solveColumn(int column) {
        if (!isController() || !isRoundActive() || !canGuess || column < 0 || column >= columnInputs.length || solvedColumns[column]) {
            return;
        }

        selectedColumn = column;
        String guess = columnInputs[column].getText().toString();
        if (associationsService.isColumnGuessCorrect(puzzles.get(currentRound), column, guess)) {
            solvedColumns[column] = true;
            if (currentPlayer == myPlayerNumber) {
                statsSolvedCount++;
            }
            addPoints(associationsService.scoreColumn(openedClues, column));
            revealColumn(column);
            selectedColumn = -1;
            columnInputs[column].setText("");
            hideKeyboard();
            showOwlToast("Tacno resenje");
            refreshUiFromState();
            publishState();
            return;
        }

        columnInputs[column].setText("");
        hideKeyboard();
        showOwlToast("Pogresno resenje");
        switchTurn();
    }

    private void solveFinal() {
        if (!isController() || !isRoundActive() || !canGuess) {
            return;
        }
        if (!associationsService.hasAnyOpenedClue(openedClues)) {
            return;
        }

        String guess = etFinalGuess.getText().toString();
        if (associationsService.isFinalGuessCorrect(puzzles.get(currentRound), guess)) {
            addPoints(associationsService.scoreFinal(openedClues, solvedColumns));
            if (currentPlayer == myPlayerNumber) {
                statsSolvedCount++;
            }
            finalSolved = true;
            revealAll();
            clearEditableGuesses();
            hideKeyboard();
            showOwlToast("Tacno resenje");
            finishRound();
            return;
        }

        etFinalGuess.setText("");
        hideKeyboard();
        showOwlToast("Pogresno resenje");
        switchTurn();
    }

    private void passTurn() {
        if (!isController() || !isRoundActive() || !canGuess) {
            return;
        }
        clearEditableGuesses();
        showOwlToast("Protivnik na potezu");
        switchTurn();
    }

    private void switchTurn() {
        cancelRoundTimer();
        clearEditableGuesses();
        hideKeyboard();
        currentPlayer = currentPlayer == 1 ? 2 : 1;
        selectedColumn = -1;
        canGuess = !hasClosedPlayableClue();
        refreshUiFromState();
        publishState();
        if (isController() && lastTimerSeconds > 0) {
            startTimer(Math.max(1000L, lastTimerSeconds * 1000L));
        }
    }

    private boolean hasClosedPlayableClue() {
        for (int i = 0; i < openedClues.length; i++) {
            int column = i / AssociationsGameService.CLUE_COUNT;
            if (!openedClues[i] && !solvedColumns[column]) {
                return true;
            }
        }
        return false;
    }

    private void finishRound() {
        cancelRoundTimer();
        cancelTransitionTimer();
        revealAll();
        phase = Phase.ROUND_FINISHED;
        selectedColumn = -1;
        canGuess = false;
        lastTimerSeconds = 0;
        refreshUiFromState();
        publishState();
        scheduleNextStep();
    }

    private void scheduleNextStep() {
        cancelTransitionTimer();
        transitionTimer = new CountDownTimer(ROUND_TRANSITION_MS, ROUND_TRANSITION_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                if (isFinishing() || isDestroyed() || gameFinished) {
                    return;
                }
                if (currentRound < AssociationsGameService.ROUND_COUNT - 1) {
                    currentRound++;
                    startRound();
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
        cancelRoundTimer();
        cancelTransitionTimer();
        refreshUiFromState();
        publishState();
        sendForceFinishEvent();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
        PlayerStatsService.putBaseGameStats(resultIntent, GAME_ID, 0, 60);
        resultIntent.putExtra(PlayerStatsService.EXTRA_STATS_ASSOCIATIONS_SOLVED_COUNT, statsSolvedCount);
        setResult(RESULT_OK, resultIntent);
        btnContinue.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 500);
    }

    private void revealColumn(int column) {
        for (int row = 0; row < AssociationsGameService.CLUE_COUNT; row++) {
            openedClues[column * AssociationsGameService.CLUE_COUNT + row] = true;
        }
    }

    private void revealAll() {
        for (int i = 0; i < openedClues.length; i++) {
            openedClues[i] = true;
        }
        for (int i = 0; i < solvedColumns.length; i++) {
            solvedColumns[i] = true;
        }
    }

    private void addPoints(int points) {
        int awarded = Math.max(0, Math.min(points, 30 - roundAwardedPoints));
        roundAwardedPoints += awarded;
        if (currentPlayer == 1) {
            player1Score += awarded;
        } else {
            player2Score += awarded;
        }
        updateScoreText();
    }

    private void refreshButtons() {
        boolean spectator = phase == Phase.ACTIVE && !isController();
        float alpha = spectator ? 0.55f : 1f;
        int darkText = getResources().getColor(R.color.app_on_surface);
        for (int i = 0; i < clueButtons.length; i++) {
            int column = i / AssociationsGameService.CLUE_COUNT;
            clueButtons[i].setAlpha(alpha);
            setGameButtonInteractive(clueButtons[i], canOpenClue(i));
            if (openedClues[i] || finalSolved || phase == Phase.ROUND_FINISHED || phase == Phase.FINISHED) {
                setGameButtonBackground(clueButtons[i], R.drawable.associations_open_bg);
                clueButtons[i].setTextColor(darkText);
            } else {
                setGameButtonBackground(clueButtons[i], solvedColumns[column]
                        ? R.drawable.associations_solved_bg
                        : R.drawable.associations_closed_bg);
                clueButtons[i].setTextColor(Color.WHITE);
            }
        }
        for (int i = 0; i < columnInputs.length; i++) {
            boolean interactive = isController() && isRoundActive() && canGuess && !solvedColumns[i];
            columnInputs[i].setAlpha(alpha);
            columnInputs[i].setEnabled(true);
            columnInputs[i].setClickable(interactive);
            columnInputs[i].setFocusable(interactive);
            columnInputs[i].setFocusableInTouchMode(interactive);
            if (solvedColumns[i] || finalSolved || phase == Phase.ROUND_FINISHED || phase == Phase.FINISHED) {
                setInputBackground(columnInputs[i], R.drawable.associations_solved_bg);
            } else if (selectedColumn == i) {
                setInputBackground(columnInputs[i], R.drawable.associations_selected_bg);
            } else {
                setInputBackground(columnInputs[i], R.drawable.associations_solution_bg);
            }
        }
    }

    private void updateControls(boolean active) {
        boolean canUseGuess = active && canGuess;
        etGuess.setEnabled(canUseGuess);
        etGuess.setAlpha(active ? 1f : 0.55f);
        boolean finalInteractive = canUseGuess && associationsService.hasAnyOpenedClue(openedClues);
        etFinalGuess.setEnabled(true);
        etFinalGuess.setClickable(finalInteractive);
        etFinalGuess.setFocusable(finalInteractive);
        etFinalGuess.setFocusableInTouchMode(finalInteractive);
        etFinalGuess.setAlpha(active ? 1f : 0.55f);
        btnSolveColumn.setEnabled(canUseGuess && selectedColumn != -1 && !solvedColumns[selectedColumn]);
        btnSolveFinal.setEnabled(canUseGuess && associationsService.hasAnyOpenedClue(openedClues));
        btnContinue.setEnabled(canUseGuess);
        btnContinue.setText(phase == Phase.ACTIVE ? R.string.associations_pass_turn : R.string.associations_finish_round);
        btnSolveColumn.setAlpha(active ? 1f : 0.55f);
        btnSolveFinal.setAlpha(active ? 1f : 0.55f);
        btnContinue.setAlpha(active ? 1f : 0.55f);
    }

    private void setGameButtonBackground(Button button, int drawableRes) {
        button.setBackgroundTintList(null);
        button.setBackgroundResource(drawableRes);
    }

    private void setInputBackground(EditText input, int drawableRes) {
        input.setBackgroundTintList(null);
        input.setBackgroundResource(drawableRes);
    }

    private void setGameButtonInteractive(Button button, boolean interactive) {
        button.setEnabled(true);
        button.setClickable(interactive);
        button.setFocusable(interactive);
    }

    private String getClueLabel(int index) {
        int column = index / AssociationsGameService.CLUE_COUNT;
        int row = index % AssociationsGameService.CLUE_COUNT;
        return columnName(column) + (row + 1);
    }

    private String columnName(int column) {
        return String.valueOf((char) ('A' + column));
    }

    private void updateScoreText() {
        tvScore.setText(getString(R.string.associations_score_format, player1Score, player2Score));
        tvHeaderLeftScore.setText(String.valueOf(player1Score));
        tvHeaderRightScore.setText(String.valueOf(player2Score));
    }

    private void refreshTurnIndicator() {
        if (gameFinished || phase == Phase.FINISHED || phase == Phase.ROUND_FINISHED || phase == Phase.LOADING) {
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

    private void publishState() {
        if (soloMode || TextUtils.isEmpty(matchRoomId) || puzzles.isEmpty()) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("sender", myPlayerNumber);
            data.put("round", currentRound);
            data.put("phase", phase.name());
            data.put("currentPlayer", currentPlayer);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("roundAwarded", roundAwardedPoints);
            data.put("selectedColumn", selectedColumn);
            data.put("canGuess", canGuess);
            data.put("finalSolved", finalSolved);
            data.put("gameFinished", gameFinished);
            data.put("timer", lastTimerSeconds);
            data.put("opened", booleanArrayToJson(openedClues));
            data.put("solved", booleanArrayToJson(solvedColumns));
            data.put("puzzles", puzzlesToJson());

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
        if (soloMode || data.optInt("sender", 0) == myPlayerNumber) {
            return;
        }
        JSONArray puzzlesJson = data.optJSONArray("puzzles");
        if (puzzlesJson != null && puzzlesJson.length() >= AssociationsGameService.ROUND_COUNT) {
            puzzles.clear();
            puzzles.addAll(parsePuzzles(puzzlesJson));
        }
        if (puzzles.isEmpty()) {
            return;
        }

        currentRound = data.optInt("round", currentRound);
        if (currentRound < 0 || currentRound >= puzzles.size()) {
            return;
        }
        phase = phaseFromString(data.optString("phase", phase.name()));
        currentPlayer = data.optInt("currentPlayer", currentPlayer);
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        roundAwardedPoints = data.optInt("roundAwarded", roundAwardedPoints);
        selectedColumn = data.optInt("selectedColumn", selectedColumn);
        canGuess = data.optBoolean("canGuess", canGuess);
        finalSolved = data.optBoolean("finalSolved", finalSolved);
        gameFinished = data.optBoolean("gameFinished", gameFinished);
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        jsonToBooleanArray(data.optJSONArray("opened"), openedClues);
        jsonToBooleanArray(data.optJSONArray("solved"), solvedColumns);
        cancelRoundTimer();
        cancelTransitionTimer();
        refreshUiFromState();
        if (gameFinished || phase == Phase.FINISHED) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
            resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
            setResult(RESULT_OK, resultIntent);
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
            return;
        }
        if (isController() && phase == Phase.ACTIVE && lastTimerSeconds > 0) {
            startTimer(Math.max(1000L, lastTimerSeconds * 1000L));
        }
    }

    private Phase phaseFromString(String value) {
        try {
            return Phase.valueOf(value);
        } catch (Exception ignored) {
            return phase;
        }
    }

    private JSONArray puzzlesToJson() {
        JSONArray out = new JSONArray();
        for (AssociationPuzzle puzzle : puzzles) {
            JSONObject item = new JSONObject();
            JSONArray columns = new JSONArray();
            JSONArray solutions = new JSONArray();
            try {
                item.put("title", puzzle.getTitle());
                for (List<String> column : puzzle.getColumns()) {
                    JSONArray columnJson = new JSONArray();
                    for (String clue : column) {
                        columnJson.put(clue);
                    }
                    columns.put(columnJson);
                }
                for (String solution : puzzle.getColumnSolutions()) {
                    solutions.put(solution);
                }
                item.put("columns", columns);
                item.put("columnSolutions", solutions);
                item.put("finalSolution", puzzle.getFinalSolution());
                out.put(item);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private List<AssociationPuzzle> parsePuzzles(JSONArray values) {
        List<AssociationPuzzle> parsed = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONArray columnsJson = item.optJSONArray("columns");
            JSONArray solutionsJson = item.optJSONArray("columnSolutions");
            String finalSolution = item.optString("finalSolution", "");
            if (columnsJson == null || solutionsJson == null
                    || columnsJson.length() != AssociationsGameService.COLUMN_COUNT
                    || solutionsJson.length() != AssociationsGameService.COLUMN_COUNT
                    || finalSolution.trim().isEmpty()) {
                continue;
            }
            List<List<String>> columns = new ArrayList<>();
            for (int column = 0; column < AssociationsGameService.COLUMN_COUNT; column++) {
                JSONArray columnJson = columnsJson.optJSONArray(column);
                if (columnJson == null || columnJson.length() != AssociationsGameService.CLUE_COUNT) {
                    columns.clear();
                    break;
                }
                List<String> clues = new ArrayList<>();
                for (int row = 0; row < AssociationsGameService.CLUE_COUNT; row++) {
                    clues.add(columnJson.optString(row));
                }
                columns.add(clues);
            }
            if (columns.size() != AssociationsGameService.COLUMN_COUNT) {
                continue;
            }
            List<String> solutions = new ArrayList<>();
            for (int column = 0; column < AssociationsGameService.COLUMN_COUNT; column++) {
                solutions.add(solutionsJson.optString(column));
            }
            parsed.add(new AssociationPuzzle(item.optString("title", ""), columns, solutions, finalSolution));
        }
        return parsed;
    }

    private JSONArray booleanArrayToJson(boolean[] values) {
        JSONArray out = new JSONArray();
        for (boolean value : values) {
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

    private void clearEditableGuesses() {
        etGuess.setText("");
        etFinalGuess.setText("");
        if (columnInputs == null) {
            return;
        }
        for (int i = 0; i < columnInputs.length; i++) {
            if (!solvedColumns[i]) {
                columnInputs[i].setText("");
            }
        }
    }

    private boolean hasAnyTypedGuess() {
        if (!TextUtils.isEmpty(etFinalGuess.getText().toString().trim())) {
            return true;
        }
        if (columnInputs == null) {
            return false;
        }
        for (EditText input : columnInputs) {
            if (!TextUtils.isEmpty(input.getText().toString().trim())) {
                return true;
            }
        }
        return false;
    }

    private void showOwlToast(String message) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(8), dp(14), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(2), getResources().getColor(R.color.app_secondary_blue));
        bg.setCornerRadius(dp(12));
        box.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.owl_icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.setMargins(0, 0, dp(8), 0);
        box.addView(icon, iconParams);

        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(getResources().getColor(R.color.app_on_surface));
        text.setTextSize(15f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(text);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(72));
        toast.setView(box);
        toast.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View view = getCurrentFocus();
            if (imm != null && view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
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
        if (phase == Phase.ACTIVE && roundTimer == null && lastTimerSeconds > 0) {
            startTimer(Math.max(1000L, lastTimerSeconds * 1000L));
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
        if (remoteFinishHandled) {
            return;
        }
        remoteFinishHandled = true;
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        cancelRoundTimer();
        cancelTransitionTimer();
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

    private void cancelTransitionTimer() {
        if (transitionTimer != null) {
            transitionTimer.cancel();
            transitionTimer = null;
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
        cancelTransitionTimer();
        turnIndicatorAnimator.clear();
    }
}
