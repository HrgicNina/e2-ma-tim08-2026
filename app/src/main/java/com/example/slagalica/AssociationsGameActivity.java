package com.example.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.Normalizer;
import java.util.Locale;

public class AssociationsGameActivity extends AppCompatActivity {

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
    private boolean[] openedClues = new boolean[16];
    private boolean[] solvedColumns = new boolean[4];
    private boolean finalSolved = false;
    private CountDownTimer roundTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_associations_game);

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

        startRound();
    }

    private void startRound() {
        cancelRoundTimer();
        currentPlayer = currentRound == 0 ? 1 : 2;
        selectedColumn = -1;
        finalSolved = false;
        openedClues = new boolean[16];
        solvedColumns = new boolean[4];

        tvRound.setText(R.string.associations_title);
        tvCurrentPlayer.setText(getString(R.string.associations_current_player, currentPlayer));
        tvPhaseInfo.setText(R.string.associations_status_idle);
        tvScore.setText(getString(R.string.associations_score_format, player1Score, player2Score));
        tvSelectedColumn.setText(R.string.associations_selected_none);
        tvFinalSolution.setText(R.string.associations_final_placeholder);
        etGuess.setText("");
        btnContinue.setEnabled(false);
        btnContinue.setText(R.string.associations_finish_round);

        for (int i = 0; i < 16; i++) {
            clueButtons[i].setText(getClueLabel(i));
            clueButtons[i].setEnabled(true);
            clueButtons[i].setBackgroundResource(R.drawable.associations_closed_bg);
        }

        for (int i = 0; i < 4; i++) {
            columnButtons[i].setText(getString(R.string.associations_column_placeholder, columnName(i)));
            columnButtons[i].setEnabled(true);
            columnButtons[i].setBackgroundResource(R.drawable.associations_solution_bg);
        }

        btnSolveColumn.setEnabled(true);
        btnSolveColumn.setBackgroundResource(R.drawable.associations_solution_bg);
        btnSolveFinal.setEnabled(true);
        btnSolveFinal.setBackgroundResource(R.drawable.associations_solution_bg);
        startTimer();
    }

    private void startTimer() {
        roundTimer = new CountDownTimer(120000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int totalSeconds = (int) (millisUntilFinished / 1000);
                int minutes = totalSeconds / 60;
                int seconds = totalSeconds % 60;
                tvTimer.setText(getString(R.string.associations_timer_format, minutes, seconds));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.associations_timer_format, 0, 0));
                finishRound();
            }
        }.start();
    }

    private void openClue(int index) {
        if (openedClues[index] || finalSolved) {
            return;
        }

        openedClues[index] = true;
        int column = index / 4;
        int row = index % 4;

        clueButtons[index].setText(roundClues[currentRound][column][row]);
        clueButtons[index].setBackgroundResource(R.drawable.associations_open_bg);
        selectedColumn = column;
        tvSelectedColumn.setText(getString(R.string.associations_selected_column, columnName(column)));
        tvPhaseInfo.setText(getString(R.string.associations_opened_hint, columnName(column)));
    }

    private void selectColumn(int column) {
        if (solvedColumns[column] || finalSolved) {
            return;
        }

        selectedColumn = column;
        tvSelectedColumn.setText(getString(R.string.associations_selected_column, columnName(column)));
        tvPhaseInfo.setText(getString(R.string.associations_column_try, columnName(column)));
    }

    private void solveSelectedColumn() {
        if (selectedColumn == -1 || finalSolved) {
            tvPhaseInfo.setText(R.string.associations_select_column_first);
            return;
        }

        int column = selectedColumn;
        String guess = normalize(etGuess.getText().toString());
        String expected = normalize(columnSolutions[currentRound][column]);

        if (!guess.isEmpty() && guess.equals(expected)) {
            solvedColumns[column] = true;
            int unopened = countUnopenedInColumn(column);
            addPoints(2 + unopened);
            columnButtons[column].setText(columnSolutions[currentRound][column]);
            columnButtons[column].setBackgroundResource(R.drawable.associations_solved_bg);
            revealColumn(column);
            tvPhaseInfo.setText(getString(R.string.associations_column_solved, columnName(column)));
            etGuess.setText("");
            selectedColumn = -1;
            tvSelectedColumn.setText(R.string.associations_selected_none);
            if (allColumnsSolved()) {
                tvPhaseInfo.setText(R.string.associations_all_columns_open);
            }
            return;
        }

        tvPhaseInfo.setText(getString(R.string.associations_column_wrong, columnName(column)));
    }

    private void solveFinal() {
        if (finalSolved) {
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
            tvFinalSolution.setText(getString(R.string.associations_final_label, finalSolutions[currentRound]));
            btnSolveFinal.setBackgroundResource(R.drawable.associations_solved_bg);
            btnSolveFinal.setEnabled(false);
            btnSolveColumn.setEnabled(false);
            tvPhaseInfo.setText(R.string.associations_final_solved);
            etGuess.setText("");
            finishRound();
            return;
        }

        tvPhaseInfo.setText(R.string.associations_final_wrong);
    }

    private void finishRound() {
        cancelRoundTimer();
        revealAll();
        btnContinue.setEnabled(true);
        btnSolveColumn.setEnabled(false);
        btnSolveFinal.setEnabled(false);
        tvCurrentPlayer.setText("");
        tvSelectedColumn.setText(R.string.associations_selected_none);
        tvPhaseInfo.setText(R.string.associations_round_finished);
        btnContinue.setText(R.string.associations_finish_round);
    }

    private void continueRound() {
        if (!btnContinue.isEnabled()) {
            return;
        }

        if (currentRound == 0) {
            currentRound = 1;
            startRound();
            return;
        }

        tvRound.setText(R.string.associations_title);
        tvCurrentPlayer.setText("");
        tvSelectedColumn.setText(R.string.associations_selected_none);
        tvPhaseInfo.setText(R.string.associations_round_finished);
        btnContinue.setEnabled(false);
    }

    private void revealColumn(int column) {
        for (int row = 0; row < 4; row++) {
            int index = column * 4 + row;
            if (!openedClues[index]) {
                openedClues[index] = true;
                clueButtons[index].setText(roundClues[currentRound][column][row]);
                clueButtons[index].setBackgroundResource(R.drawable.associations_open_bg);
            }
            clueButtons[index].setEnabled(false);
        }
    }

    private void revealAll() {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                int index = column * 4 + row;
                clueButtons[index].setText(roundClues[currentRound][column][row]);
                clueButtons[index].setBackgroundResource(R.drawable.associations_open_bg);
                clueButtons[index].setEnabled(false);
            }
            columnButtons[column].setText(columnSolutions[currentRound][column]);
            columnButtons[column].setBackgroundResource(R.drawable.associations_solved_bg);
            columnButtons[column].setEnabled(false);
        }

        tvFinalSolution.setText(getString(R.string.associations_final_label, finalSolutions[currentRound]));
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

    private boolean allColumnsSolved() {
        for (boolean solved : solvedColumns) {
            if (!solved) {
                return false;
            }
        }
        return true;
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

    private void cancelRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelRoundTimer();
    }
}
