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

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;

public class AssociationsGameActivity extends AppCompatActivity {
    private static final String GAME_ID = "associations";

    private final String[][][] roundClues = {
            {
                    {"Sneg", "Deda Mraz", "Sanke", "Novogodisnja jelka"},
                    {"Mleko", "Jogurt", "Pavlaka", "Sir"},
                    {"Motor", "Tocak", "Volan", "Kocnica"},
                    {"Vuk", "Lija", "Medved", "Zec"}
            },
            {
                    {"Tastatura", "Mis", "Monitor", "Procesor"},
                    {"Berlin", "Pariz", "Rim", "Madrid"},
                    {"Dunav", "Sava", "Morava", "Drina"},
                    {"Mocart", "Betoven", "Bach", "Vivaldi"}
            }
    };

    private final String[][] columnSolutions = {
            {"ZIMA", "MLECNI PROIZVODI", "AUTO", "SUMA"},
            {"RACUNAR", "GLAVNI GRADOVI", "REKE", "KOMPOZITORI"}
    };

    private final String[] finalSolutions = {"PRIRODA", "EVROPA"};

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvPhaseInfo;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvSelectedColumn;
    private Button[] clueButtons;
    private Button[] columnButtons;
    private Button btnSolveColumn;
    private Button btnSolveFinal;
    private Button btnContinue;
    private TextView tvFinalSolution;
    private EditText etGuess;

    private int currentRound = 0;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int selectedColumn = -1;
    private final boolean[] openedClues = new boolean[16];
    private final boolean[] solvedColumns = new boolean[4];
    private boolean finalSolved = false;
    private boolean roundFinished = false;
    private boolean gameFinished = false;
    private int lastTimerSeconds = 120;
    private CountDownTimer roundTimer;

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
        setContentView(R.layout.activity_associations_game);

        matchRoomId = getIntent().getStringExtra("match_room_id");
        if (matchRoomId == null) {
            matchRoomId = "";
        }
        myPlayerNumber = getIntent().getIntExtra("match_my_player_number", 1);
        soloMode = getIntent().getBooleanExtra(MatchActivity.EXTRA_MATCH_SOLO_MODE, false) || TextUtils.isEmpty(matchRoomId);

        tvRound = findViewById(R.id.tvAssociationsRound);
        tvCurrentPlayer = findViewById(R.id.tvAssociationsCurrentPlayer);
        tvPhaseInfo = findViewById(R.id.tvAssociationsPhaseInfo);
        tvTimer = findViewById(R.id.tvAssociationsTimer);
        tvScore = findViewById(R.id.tvAssociationsScore);
        tvSelectedColumn = findViewById(R.id.tvAssociationsSelectedColumn);
        etGuess = findViewById(R.id.etAssociationsGuess);
        btnSolveColumn = findViewById(R.id.btnAssociationsSolveColumn);
        btnSolveFinal = findViewById(R.id.btnAssociationsSolveFinal);
        btnContinue = findViewById(R.id.btnAssociationsContinue);
        tvFinalSolution = findViewById(R.id.tvAssociationsFinalSolution);

        clueButtons = new Button[]{
                findViewById(R.id.btnA1), findViewById(R.id.btnA2), findViewById(R.id.btnA3), findViewById(R.id.btnA4),
                findViewById(R.id.btnB1), findViewById(R.id.btnB2), findViewById(R.id.btnB3), findViewById(R.id.btnB4),
                findViewById(R.id.btnC1), findViewById(R.id.btnC2), findViewById(R.id.btnC3), findViewById(R.id.btnC4),
                findViewById(R.id.btnD1), findViewById(R.id.btnD2), findViewById(R.id.btnD3), findViewById(R.id.btnD4)
        };

        columnButtons = new Button[]{
                findViewById(R.id.btnSolveA),
                findViewById(R.id.btnSolveB),
                findViewById(R.id.btnSolveC),
                findViewById(R.id.btnSolveD)
        };

        for (int i = 0; i < clueButtons.length; i++) {
            final int index = i;
            clueButtons[i].setOnClickListener(v -> openClue(index));
        }

        for (int i = 0; i < columnButtons.length; i++) {
            final int index = i;
            columnButtons[i].setOnClickListener(v -> selectColumn(index));
        }

        btnSolveColumn.setOnClickListener(v -> solveSelectedColumn());
        btnSolveFinal.setOnClickListener(v -> solveFinal());
        btnContinue.setOnClickListener(v -> continueRound());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveGameDialog();
            }
        });

        startRound();
    }

    private boolean isController() {
        return soloMode || currentPlayer == myPlayerNumber;
    }

    private void startRound() {
        cancelRoundTimer();
        currentPlayer = currentRound == 0 ? 1 : 2;
        selectedColumn = -1;
        finalSolved = false;
        roundFinished = false;
        gameFinished = false;
        lastTimerSeconds = 120;

        for (int i = 0; i < openedClues.length; i++) {
            openedClues[i] = false;
        }
        for (int i = 0; i < solvedColumns.length; i++) {
            solvedColumns[i] = false;
        }

        refreshUiFromState();
        if (isController()) {
            startTimer();
        }
        publishState();
    }

    private void refreshUiFromState() {
        if (gameFinished) {
            tvRound.setText(R.string.associations_title);
            tvCurrentPlayer.setText("");
            tvPhaseInfo.setText(R.string.associations_round_finished);
            tvTimer.setText(getString(R.string.associations_timer_format, 0, 0));
            tvSelectedColumn.setText(R.string.associations_selected_none);
            btnContinue.setEnabled(false);
            return;
        }

        tvRound.setText(R.string.associations_title);
        tvCurrentPlayer.setText(roundFinished ? "" : getString(R.string.associations_current_player, currentPlayer));
        tvScore.setText(getString(R.string.associations_score_format, player1Score, player2Score));
        tvSelectedColumn.setText(selectedColumn == -1
                ? getString(R.string.associations_selected_none)
                : getString(R.string.associations_selected_column, columnName(selectedColumn)));
        tvFinalSolution.setText(finalSolved
                ? getString(R.string.associations_final_label, finalSolutions[currentRound])
                : getString(R.string.associations_final_placeholder));

        int minutes = Math.max(0, lastTimerSeconds) / 60;
        int seconds = Math.max(0, lastTimerSeconds) % 60;
        tvTimer.setText(getString(R.string.associations_timer_format, minutes, seconds));

        boolean canInteract = isController() && !roundFinished && !gameFinished;
        for (int i = 0; i < 16; i++) {
            int column = i / 4;
            int row = i % 4;
            if (openedClues[i]) {
                clueButtons[i].setText(roundClues[currentRound][column][row]);
                clueButtons[i].setBackgroundResource(R.drawable.associations_open_bg);
            } else {
                clueButtons[i].setText(getClueLabel(i));
                clueButtons[i].setBackgroundResource(R.drawable.associations_closed_bg);
            }
            clueButtons[i].setEnabled(canInteract && !openedClues[i] && !finalSolved);
        }

        for (int i = 0; i < 4; i++) {
            if (solvedColumns[i]) {
                columnButtons[i].setText(columnSolutions[currentRound][i]);
                columnButtons[i].setBackgroundResource(R.drawable.associations_solved_bg);
            } else {
                columnButtons[i].setText(getString(R.string.associations_column_placeholder, columnName(i)));
                columnButtons[i].setBackgroundResource(R.drawable.associations_solution_bg);
            }
            columnButtons[i].setEnabled(canInteract && !solvedColumns[i] && !finalSolved);
        }

        btnSolveColumn.setEnabled(canInteract && !finalSolved);
        btnSolveFinal.setEnabled(canInteract && !finalSolved);
        etGuess.setEnabled(canInteract && !roundFinished);
        btnContinue.setEnabled(roundFinished && isController());

        if (roundFinished) {
            tvPhaseInfo.setText(R.string.associations_round_finished);
        } else if (finalSolved) {
            tvPhaseInfo.setText(R.string.associations_final_solved);
        } else {
            tvPhaseInfo.setText(R.string.associations_status_idle);
        }
    }

    private void startTimer() {
        startTimerWithDuration(120000);
    }

    private void startTimerWithDuration(long durationMs) {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                lastTimerSeconds = (int) (millisUntilFinished / 1000);
                int minutes = lastTimerSeconds / 60;
                int seconds = lastTimerSeconds % 60;
                tvTimer.setText(getString(R.string.associations_timer_format, minutes, seconds));
                publishState();
            }

            @Override
            public void onFinish() {
                lastTimerSeconds = 0;
                tvTimer.setText(getString(R.string.associations_timer_format, 0, 0));
                finishRound();
            }
        }.start();
    }

    private void openClue(int index) {
        if (!isController() || openedClues[index] || finalSolved || roundFinished || gameFinished) {
            return;
        }
        openedClues[index] = true;
        selectedColumn = index / 4;
        refreshUiFromState();
        publishState();
    }

    private void selectColumn(int column) {
        if (!isController() || solvedColumns[column] || finalSolved || roundFinished || gameFinished) {
            return;
        }
        selectedColumn = column;
        refreshUiFromState();
        publishState();
    }

    private void solveSelectedColumn() {
        if (!isController() || selectedColumn == -1 || finalSolved || roundFinished || gameFinished) {
            return;
        }

        int column = selectedColumn;
        String guess = normalize(etGuess.getText().toString());
        String expected = normalize(columnSolutions[currentRound][column]);

        if (!guess.isEmpty() && guess.equals(expected)) {
            solvedColumns[column] = true;
            int unopened = countUnopenedInColumn(column);
            addPoints(2 + unopened);
            revealColumn(column);
            selectedColumn = -1;
            etGuess.setText("");
            refreshUiFromState();
            publishState();
            return;
        }

        tvPhaseInfo.setText(getString(R.string.associations_column_wrong, columnName(column)));
    }

    private void solveFinal() {
        if (!isController() || finalSolved || roundFinished || gameFinished) {
            return;
        }

        String guess = normalize(etGuess.getText().toString());
        String expected = normalize(finalSolutions[currentRound]);

        if (!guess.isEmpty() && guess.equals(expected)) {
            finalSolved = true;
            int points = 7;
            for (int i = 0; i < 4; i++) {
                if (solvedColumns[i]) {
                    continue;
                }
                int unopened = countUnopenedInColumn(i);
                if (hasOpenedClueInColumn(i)) {
                    points += 2 + unopened;
                } else {
                    points += 6;
                }
            }
            addPoints(points);
            revealAll();
            etGuess.setText("");
            finishRound();
            return;
        }

        tvPhaseInfo.setText(R.string.associations_final_wrong);
    }

    private void finishRound() {
        cancelRoundTimer();
        revealAll();
        roundFinished = true;
        refreshUiFromState();
        publishState();
    }

    private void continueRound() {
        if (!isController() || !roundFinished || gameFinished) {
            return;
        }

        if (currentRound == 0) {
            currentRound = 1;
            startRound();
            return;
        }

        finishGameWithResult();
    }

    private void finishGameWithResult() {
        if (gameFinished) {
            return;
        }
        gameFinished = true;
        cancelRoundTimer();
        refreshUiFromState();
        publishState();
        sendForceFinishEvent();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER1_SCORE, player1Score);
        resultIntent.putExtra(MatchActivity.EXTRA_GAME_PLAYER2_SCORE, player2Score);
        setResult(RESULT_OK, resultIntent);
        btnContinue.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, 500);
    }

    private void revealColumn(int column) {
        for (int row = 0; row < 4; row++) {
            int index = column * 4 + row;
            openedClues[index] = true;
        }
    }

    private void revealAll() {
        for (int i = 0; i < openedClues.length; i++) {
            openedClues[i] = true;
        }
        for (int i = 0; i < solvedColumns.length; i++) {
            solvedColumns[i] = true;
        }
        finalSolved = true;
    }

    private int countUnopenedInColumn(int column) {
        int unopened = 0;
        for (int row = 0; row < 4; row++) {
            if (!openedClues[column * 4 + row]) {
                unopened++;
            }
        }
        return unopened;
    }

    private boolean hasOpenedClueInColumn(int column) {
        for (int row = 0; row < 4; row++) {
            if (openedClues[column * 4 + row]) {
                return true;
            }
        }
        return false;
    }

    private void addPoints(int points) {
        if (currentPlayer == 1) {
            player1Score += points;
        } else {
            player2Score += points;
        }
        tvScore.setText(getString(R.string.associations_score_format, player1Score, player2Score));
    }

    private String getClueLabel(int index) {
        int column = index / 4;
        int row = index % 4;
        return columnName(column) + (row + 1);
    }

    private String columnName(int column) {
        return String.valueOf((char) ('A' + column));
    }

    private String normalize(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    private void publishState() {
        if (soloMode || TextUtils.isEmpty(matchRoomId) || (!isController() && !roundFinished)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("round", currentRound);
            data.put("currentPlayer", currentPlayer);
            data.put("p1", player1Score);
            data.put("p2", player2Score);
            data.put("selectedColumn", selectedColumn);
            data.put("finalSolved", finalSolved);
            data.put("roundFinished", roundFinished);
            data.put("gameFinished", gameFinished);
            data.put("timer", lastTimerSeconds);
            data.put("opened", booleanArrayToJson(openedClues));
            data.put("solved", booleanArrayToJson(solvedColumns));

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
        if (currentRound < 0 || currentRound >= roundClues.length) {
            return;
        }
        currentPlayer = data.optInt("currentPlayer", currentPlayer);
        player1Score = data.optInt("p1", player1Score);
        player2Score = data.optInt("p2", player2Score);
        selectedColumn = data.optInt("selectedColumn", selectedColumn);
        finalSolved = data.optBoolean("finalSolved", finalSolved);
        roundFinished = data.optBoolean("roundFinished", roundFinished);
        gameFinished = data.optBoolean("gameFinished", gameFinished);
        lastTimerSeconds = data.optInt("timer", lastTimerSeconds);
        jsonToBooleanArray(data.optJSONArray("opened"), openedClues);
        jsonToBooleanArray(data.optJSONArray("solved"), solvedColumns);
        cancelRoundTimer();
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
        if (!roundFinished && !gameFinished && roundTimer == null && lastTimerSeconds > 0) {
            startTimerWithDuration(lastTimerSeconds * 1000L);
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

    private void cancelRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
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
    }
}
