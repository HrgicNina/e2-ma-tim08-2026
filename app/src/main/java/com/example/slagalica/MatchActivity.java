package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MatchActivity extends AppCompatActivity {

    private static final String[] GAME_NAMES = {
            "Ko zna zna",
            "Spojnice",
            "Asocijacije",
            "Korak po korak",
            "Skocko",
            "Moj broj"
    };

    private TextView tvMatchStage;
    private TextView tvMatchInfo;
    private TextView tvMatchScore;
    private TextView[] gameSteps;
    private Button btnOpenCurrentGame;
    private Button btnNextGame;

    private int currentGameIndex = 0;
    private int player1Score = 25;
    private int player2Score = 18;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match);

        tvMatchStage = findViewById(R.id.tvMatchStage);
        tvMatchInfo = findViewById(R.id.tvMatchInfo);
        tvMatchScore = findViewById(R.id.tvMatchScore);
        btnOpenCurrentGame = findViewById(R.id.btnMatchOpenGame);
        btnNextGame = findViewById(R.id.btnMatchNextGame);
        Button btnGiveUp = findViewById(R.id.btnMatchGiveUp);

        gameSteps = new TextView[]{
                findViewById(R.id.tvMatchGame1),
                findViewById(R.id.tvMatchGame2),
                findViewById(R.id.tvMatchGame3),
                findViewById(R.id.tvMatchGame4),
                findViewById(R.id.tvMatchGame5),
                findViewById(R.id.tvMatchGame6)
        };

        btnOpenCurrentGame.setOnClickListener(v -> openCurrentGame());
        btnNextGame.setOnClickListener(v -> moveToNextGame());
        btnGiveUp.setOnClickListener(v -> finish());

        renderMatch();
    }

    private void renderMatch() {
        tvMatchStage.setText(getString(R.string.match_stage_title, currentGameIndex + 1, GAME_NAMES.length));
        tvMatchInfo.setText(getString(R.string.match_current_game, GAME_NAMES[currentGameIndex]));
        tvMatchScore.setText(getString(R.string.match_score_format, player1Score, player2Score));

        for (int i = 0; i < gameSteps.length; i++) {
            if (i < currentGameIndex) {
                gameSteps[i].setText(getString(R.string.match_step_done, i + 1, GAME_NAMES[i]));
                gameSteps[i].setBackgroundResource(R.drawable.match_step_done_bg);
            } else if (i == currentGameIndex) {
                gameSteps[i].setText(getString(R.string.match_step_current, i + 1, GAME_NAMES[i]));
                gameSteps[i].setBackgroundResource(R.drawable.match_step_current_bg);
            } else {
                gameSteps[i].setText(getString(R.string.match_step_upcoming, i + 1, GAME_NAMES[i]));
                gameSteps[i].setBackgroundResource(R.drawable.match_step_upcoming_bg);
            }
        }

        btnOpenCurrentGame.setText(getString(R.string.match_open_current_game, GAME_NAMES[currentGameIndex]));
        if (currentGameIndex == GAME_NAMES.length - 1) {
            btnNextGame.setText(R.string.match_finish_match);
        } else {
            btnNextGame.setText(R.string.match_next_game);
        }
    }

    private void openCurrentGame() {
        Class<?> target;
        switch (currentGameIndex) {
            case 0:
                target = QuizGameActivity.class;
                break;
            case 1:
                target = ConnectionsGameActivity.class;
                break;
            case 2:
                target = AssociationsGameActivity.class;
                break;
            case 3:
                target = StepByStepActivity.class;
                break;
            case 4:
                target = MastermindGameActivity.class;
                break;
            default:
                target = MyNumberGameActivity.class;
                break;
        }

        startActivity(new Intent(this, target));
    }

    private void moveToNextGame() {
        if (currentGameIndex < GAME_NAMES.length - 1) {
            player1Score += 5;
            player2Score += 3;
            currentGameIndex++;
            renderMatch();
            return;
        }

        tvMatchStage.setText(R.string.match_finished_title);
        tvMatchInfo.setText(R.string.match_finished_message);
        btnOpenCurrentGame.setEnabled(false);
        btnNextGame.setEnabled(false);
        for (TextView gameStep : gameSteps) {
            gameStep.setBackgroundResource(R.drawable.match_step_done_bg);
        }
    }
}
