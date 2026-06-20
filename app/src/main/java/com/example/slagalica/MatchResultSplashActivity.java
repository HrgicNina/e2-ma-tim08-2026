package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MatchResultSplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_result_splash);

        String player1Name = safeName(getIntent().getStringExtra(MatchActivity.EXTRA_RESULT_PLAYER1_NAME), "Igrac 1");
        String player2Name = safeName(getIntent().getStringExtra(MatchActivity.EXTRA_RESULT_PLAYER2_NAME), "Igrac 2");
        int player1Score = getIntent().getIntExtra(MatchActivity.EXTRA_RESULT_PLAYER1_SCORE, 0);
        int player2Score = getIntent().getIntExtra(MatchActivity.EXTRA_RESULT_PLAYER2_SCORE, 0);
        boolean isCurrentPlayer1 = getIntent().getBooleanExtra(MatchActivity.EXTRA_RESULT_IS_CURRENT_PLAYER1, true);

        TextView tvLeftAvatar = findViewById(R.id.tvResultAvatarLeft);
        TextView tvRightAvatar = findViewById(R.id.tvResultAvatarRight);
        TextView tvLeftName = findViewById(R.id.tvResultNameLeft);
        TextView tvRightName = findViewById(R.id.tvResultNameRight);
        TextView tvLeftScore = findViewById(R.id.tvResultScoreLeft);
        TextView tvRightScore = findViewById(R.id.tvResultScoreRight);
        TextView tvWinner = findViewById(R.id.tvResultWinner);
        TextView tvEconomy = findViewById(R.id.tvResultEconomy);
        Button btnGoHome = findViewById(R.id.btnResultGoHome);

        tvLeftAvatar.setText(initials(player1Name));
        tvRightAvatar.setText(initials(player2Name));
        AvatarFrameHelper.applyMatchFrames(tvLeftAvatar, tvRightAvatar, getIntent());
        tvLeftName.setText(player1Name);
        tvRightName.setText(player2Name);
        tvLeftScore.setText(String.valueOf(player1Score));
        tvRightScore.setText(String.valueOf(player2Score));

        if (player1Score > player2Score) {
            tvWinner.setText(player1Name + " je pobednik");
        } else if (player2Score > player1Score) {
            tvWinner.setText(player2Name + " je pobednik");
        } else {
            tvWinner.setText("Nereseno");
        }

        long starDelta = getIntent().getLongExtra(MatchActivity.EXTRA_RESULT_STAR_DELTA, 0L);
        String note = getIntent().getStringExtra(MatchActivity.EXTRA_RESULT_ECONOMY_NOTE);
        if (note != null && !note.trim().isEmpty()) {
            tvEconomy.setText(note);
        } else {
            int myScore = isCurrentPlayer1 ? player1Score : player2Score;
            int opponentScore = isCurrentPlayer1 ? player2Score : player1Score;

            if (myScore < opponentScore && myScore >= 40) {
                long loserBonus = myScore / 40L;
                tvEconomy.setText("Izgubili ste: 10 zvezda\nDobili ste: " + loserBonus + " zvezda");
            } else if (starDelta > 0) {
                tvEconomy.setText("Dobili ste: " + starDelta + " zvezda");
            } else if (starDelta < 0) {
                tvEconomy.setText("Izgubili ste: " + Math.abs(starDelta) + " zvezda");
            } else {
                tvEconomy.setText("Bez promene zvezda");
            }
        }

        btnGoHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private String safeName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        String first = parts[0].substring(0, 1).toUpperCase();
        String second = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + second;
    }

}
