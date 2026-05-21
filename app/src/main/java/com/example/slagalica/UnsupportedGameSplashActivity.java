package com.example.slagalica;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class UnsupportedGameSplashActivity extends AppCompatActivity {

    private static final long DEFAULT_DURATION_MS = 2_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable finishRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unsupported_game_splash);

        String gameName = getIntent().getStringExtra(MatchActivity.EXTRA_GAME);
        long durationMs = getIntent().getLongExtra("duration_ms", DEFAULT_DURATION_MS);

        TextView tvTitle = findViewById(R.id.tvUnsupportedGameTitle);
        if (gameName == null || gameName.trim().isEmpty()) {
            tvTitle.setText("Sledeca igra");
        } else {
            tvTitle.setText(gameName);
        }

        finishRunnable = () -> {
            setResult(RESULT_OK);
            finish();
        };
        handler.postDelayed(finishRunnable, Math.max(500L, durationMs));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (finishRunnable != null) {
            handler.removeCallbacks(finishRunnable);
            finishRunnable = null;
        }
    }
}

