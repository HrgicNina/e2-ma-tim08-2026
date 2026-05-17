package com.example.slagalica;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.LeaderboardService;
import com.example.slagalica.domain.SessionManager;
import com.example.slagalica.model.LeaderboardEntry;

import java.util.List;

public class RankingsActivity extends AppCompatActivity {

    private final LeaderboardService leaderboardService = new LeaderboardService();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvCycleRange;
    private TextView tvLastRefresh;
    private TextView tvEmpty;
    private LinearLayout llRows;
    private Button btnWeekly;
    private Button btnMonthly;
    private boolean monthlyMode = false;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadCurrentMode();
            handler.postDelayed(this, leaderboardService.getRefreshIntervalMs());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isGuestMode()) {
            Toast.makeText(this, "Rang lista je dostupna samo registrovanim igracima.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_rankings);
        tvCycleRange = findViewById(R.id.tvRankingCycleRange);
        tvLastRefresh = findViewById(R.id.tvRankingLastRefresh);
        tvLastRefresh.setVisibility(TextView.GONE);
        tvEmpty = findViewById(R.id.tvRankingEmpty);
        llRows = findViewById(R.id.llRankingRows);
        btnWeekly = findViewById(R.id.btnWeeklyRanking);
        btnMonthly = findViewById(R.id.btnMonthlyRanking);

        btnWeekly.setOnClickListener(v -> {
            monthlyMode = false;
            loadCurrentMode();
        });
        btnMonthly.setOnClickListener(v -> {
            monthlyMode = true;
            loadCurrentMode();
        });

        bootstrapRolloverAndLoad();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.postDelayed(refreshRunnable, leaderboardService.getRefreshIntervalMs());
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(refreshRunnable);
        super.onStop();
    }

    private void loadCurrentMode() {
        refreshToggleButtons();
        if (monthlyMode) {
            leaderboardService.loadMonthlyLeaderboard(new LeaderboardService.LoadCallback() {
                @Override
                public void onSuccess(LeaderboardService.CycleWindow cycle, List<LeaderboardEntry> entries) {
                    render(cycle, entries);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(RankingsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            leaderboardService.loadWeeklyLeaderboard(new LeaderboardService.LoadCallback() {
                @Override
                public void onSuccess(LeaderboardService.CycleWindow cycle, List<LeaderboardEntry> entries) {
                    render(cycle, entries);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(RankingsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void bootstrapRolloverAndLoad() {
        leaderboardService.processCycleRolloverAndRewards(new LeaderboardService.ActionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> loadCurrentMode());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> loadCurrentMode());
            }
        });
    }

    private void refreshToggleButtons() {
        btnWeekly.setEnabled(monthlyMode);
        btnMonthly.setEnabled(!monthlyMode);
        btnWeekly.setAlpha(monthlyMode ? 1f : 0.58f);
        btnMonthly.setAlpha(monthlyMode ? 0.58f : 1f);
    }

    private void render(LeaderboardService.CycleWindow cycle, List<LeaderboardEntry> entries) {
        tvCycleRange.setText(getString(R.string.rankings_cycle_range, cycle.label));

        llRows.removeAllViews();
        tvEmpty.setVisibility(entries == null || entries.isEmpty() ? TextView.VISIBLE : TextView.GONE);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        int rank = 1;
        for (LeaderboardEntry item : entries) {
            llRows.addView(createRow(rank, item));
            rank++;
        }
    }

    private LinearLayout createRow(int rank, LeaderboardEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(8), dp(6), dp(8));

        TextView tvRank = buildCell(rankLabel(rank), 0.16f, Gravity.START);
        TextView tvUser = buildCell(entry.username == null || entry.username.trim().isEmpty() ? "-" : entry.username, 0.44f, Gravity.START);
        TextView tvLeague = buildCell(leagueLabel(entry.league), 0.18f, Gravity.START);
        TextView tvStars = buildCell(String.valueOf(entry.cycleStars), 0.22f, Gravity.CENTER_HORIZONTAL);

        row.addView(tvRank);
        row.addView(tvUser);
        row.addView(tvLeague);
        row.addView(tvStars);
        return row;
    }

    private String rankLabel(int rank) {
        if (rank == 1) return "\uD83E\uDD47";
        if (rank == 2) return "\uD83E\uDD48";
        if (rank == 3) return "\uD83E\uDD49";
        return String.valueOf(rank);
    }

    private TextView buildCell(String text, float weight, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(19f);
        tv.setTextColor(getColor(R.color.app_on_surface));
        tv.setGravity(gravity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(params);
        return tv;
    }

    private String leagueLabel(long league) {
        if (league >= 5) return "🏆 L5";
        if (league == 4) return "💎 L4";
        if (league == 3) return "🥇 L3";
        if (league == 2) return "🥈 L2";
        if (league == 1) return "🥉 L1";
        return "🔹 L0";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
