package com.example.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ConnectionsGameActivity extends AppCompatActivity {

    private final String[][] leftRounds = {
            {"Zeljko Joksimovic", "Marija Serifovic", "Bajaga", "Sasa Matic", "Zdravko Colic"},
            {"Tesla", "Andric", "Pupin", "Milankovic", "Mokranjac"}
    };

    private final String[][] rightRounds = {
            {"Lane moje", "Molitva", "Moji drugovi", "Maskara", "Ti si mi u krvi"},
            {"Na Drini cuprija", "Himna Vuku", "Milutin Milankovic", "Naizmenicna struja", "Idvorske price"}
    };

    private final int[][] mappingRounds = {
            {0, 1, 2, 3, 4},
            {3, 0, 4, 2, 1}
    };

    private final int[] starterByRound = {1, 2};

    private TextView tvRound;
    private TextView tvCurrentPlayer;
    private TextView tvPhaseInfo;
    private TextView tvTimer;
    private TextView tvScore;
    private Button[] leftButtons;
    private Button[] rightButtons;
    private Button btnContinue;

    private int currentRound = 0;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int selectedLeft = -1;
    private int selectedRight = -1;
    private boolean[] matchedLeft = new boolean[5];
    private boolean[] matchedRight = new boolean[5];
    private int[] matchOwnerLeft = new int[5];
    private int[] matchOwnerRight = new int[5];
    private boolean stealPhase = false;
    private boolean roundFinished = false;
    private CountDownTimer roundTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connections_game);

        tvRound = findViewById(R.id.tvConnectionsRound);
        tvCurrentPlayer = findViewById(R.id.tvConnectionsCurrentPlayer);
        tvPhaseInfo = findViewById(R.id.tvConnectionsPhaseInfo);
        tvTimer = findViewById(R.id.tvConnectionsTimer);
        tvScore = findViewById(R.id.tvConnectionsScore);
        btnContinue = findViewById(R.id.btnConnectionsContinue);

        leftButtons = new Button[]{
                findViewById(R.id.btnLeft1),
                findViewById(R.id.btnLeft2),
                findViewById(R.id.btnLeft3),
                findViewById(R.id.btnLeft4),
                findViewById(R.id.btnLeft5)
        };

        rightButtons = new Button[]{
                findViewById(R.id.btnRight1),
                findViewById(R.id.btnRight2),
                findViewById(R.id.btnRight3),
                findViewById(R.id.btnRight4),
                findViewById(R.id.btnRight5)
        };

        for (int i = 0; i < 5; i++) {
            final int index = i;
            leftButtons[i].setOnClickListener(v -> selectLeft(index));
            rightButtons[i].setOnClickListener(v -> selectRight(index));
        }

        btnContinue.setOnClickListener(v -> handleContinue());

        startRound();
    }

    private void startRound() {
        cancelRoundTimer();
        currentPlayer = starterByRound[currentRound];
        stealPhase = false;
        roundFinished = false;
        selectedLeft = -1;
        selectedRight = -1;

        matchedLeft = new boolean[5];
        matchedRight = new boolean[5];
        matchOwnerLeft = new int[5];
        matchOwnerRight = new int[5];

        tvRound.setText(R.string.connections_title);
        tvCurrentPlayer.setText(getString(R.string.connections_current_player, currentPlayer));
        tvPhaseInfo.setText("");
        tvScore.setText(getString(R.string.connections_score_format, player1Score, player2Score));
        btnContinue.setEnabled(false);
        btnContinue.setText(R.string.connections_finish_round);

        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(leftRounds[currentRound][i]);
            rightButtons[i].setText(rightRounds[currentRound][i]);
        }

        refreshButtons();
        startTimer();
    }

    private void startTimer() {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.connections_timer_seconds, (int) (millisUntilFinished / 1000)));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.connections_timer_seconds, 0));
                if (!stealPhase && hasUnmatchedPairs()) {
                    openStealPhase();
                } else {
                    finishRound();
                }
            }
        }.start();
    }

    private void selectLeft(int index) {
        if (roundFinished || matchedLeft[index]) {
            return;
        }
        selectedLeft = index;
        refreshButtons();
        attemptMatch();
    }

    private void selectRight(int index) {
        if (roundFinished || matchedRight[index]) {
            return;
        }
        selectedRight = index;
        refreshButtons();
        attemptMatch();
    }

    private void attemptMatch() {
        if (selectedLeft == -1 || selectedRight == -1) {
            return;
        }

        if (mappingRounds[currentRound][selectedLeft] == selectedRight) {
            matchedLeft[selectedLeft] = true;
            matchedRight[selectedRight] = true;
            matchOwnerLeft[selectedLeft] = currentPlayer;
            matchOwnerRight[selectedRight] = currentPlayer;

            if (currentPlayer == 1) {
                player1Score += 2;
            } else {
                player2Score += 2;
            }

            tvScore.setText(getString(R.string.connections_score_format, player1Score, player2Score));
            tvPhaseInfo.setText(getString(R.string.connections_status_correct, currentPlayer));
            selectedLeft = -1;
            selectedRight = -1;
            refreshButtons();

            if (!hasUnmatchedPairs()) {
                finishRound();
            }
            return;
        }

        tvPhaseInfo.setText(getString(R.string.connections_status_wrong, currentPlayer));
        selectedLeft = -1;
        selectedRight = -1;
        refreshButtons();
    }

    private void openStealPhase() {
        stealPhase = true;
        currentPlayer = currentPlayer == 1 ? 2 : 1;
        selectedLeft = -1;
        selectedRight = -1;
        tvCurrentPlayer.setText(getString(R.string.connections_current_player, currentPlayer));
        tvPhaseInfo.setText("");
        btnContinue.setEnabled(false);
        refreshButtons();
        startTimer();
    }

    private void finishRound() {
        cancelRoundTimer();
        roundFinished = true;
        selectedLeft = -1;
        selectedRight = -1;
        tvRound.setText(R.string.connections_title);
        tvCurrentPlayer.setText("");
        tvPhaseInfo.setText("");
        btnContinue.setEnabled(true);
        btnContinue.setText(R.string.connections_finish_round);
        refreshButtons();
    }

    private void handleContinue() {
        if (!roundFinished) {
            return;
        }

        if (currentRound == 0) {
            currentRound = 1;
            startRound();
            return;
        }

        tvRound.setText(R.string.connections_title);
        tvCurrentPlayer.setText("");
        tvPhaseInfo.setText("");
        btnContinue.setEnabled(false);
    }

    private boolean hasUnmatchedPairs() {
        for (boolean matched : matchedLeft) {
            if (!matched) {
                return true;
            }
        }
        return false;
    }

    private void refreshButtons() {
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setEnabled(!matchedLeft[i] && !roundFinished);
            rightButtons[i].setEnabled(!matchedRight[i] && !roundFinished);

            if (matchedLeft[i]) {
                leftButtons[i].setBackgroundResource(matchOwnerLeft[i] == 1
                        ? R.drawable.connections_matched_player1_bg
                        : R.drawable.connections_matched_player2_bg);
            } else if (selectedLeft == i) {
                leftButtons[i].setBackgroundResource(R.drawable.connections_selected_bg);
            } else {
                leftButtons[i].setBackgroundResource(R.drawable.connections_item_bg);
            }

            if (matchedRight[i]) {
                rightButtons[i].setBackgroundResource(matchOwnerRight[i] == 1
                        ? R.drawable.connections_matched_player1_bg
                        : R.drawable.connections_matched_player2_bg);
            } else if (selectedRight == i) {
                rightButtons[i].setBackgroundResource(R.drawable.connections_selected_bg);
            } else {
                rightButtons[i].setBackgroundResource(R.drawable.connections_item_bg);
            }
        }
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
