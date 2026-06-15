package com.example.slagalica;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.RegionsService;
import com.example.slagalica.domain.SessionManager;
import com.example.slagalica.model.RegionLeaderboardEntry;
import com.example.slagalica.model.RegionMapData;

import java.util.List;

public class RegionsActivity extends AppCompatActivity {
    private RegionMapView regionMapView;
    private LinearLayout rowsContainer;
    private TextView tvCycle;
    private TextView tvEmpty;
    private RegionsService regionsService;
    private String myUid = "";
    private RegionMapData currentData = new RegionMapData();
    private String selectedRegion = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isGuestMode()) {
            Toast.makeText(this, "Regioni su dostupni samo registrovanim igracima.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_regions);
        regionsService = new RegionsService();
        myUid = value(new AuthService().getCurrentUserId());
        if (myUid.isEmpty()) {
            Toast.makeText(this, "Niste ulogovani.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        regionMapView = findViewById(R.id.regionMapView);
        rowsContainer = findViewById(R.id.regionRowsContainer);
        tvCycle = findViewById(R.id.tvRegionsCycle);
        tvEmpty = findViewById(R.id.tvRegionsEmpty);
        regionMapView.setRegionClickListener(this::showRegionStats);

        load();
    }

    private void load() {
        regionsService.processPreviousMonthlyRegionAwards(new RegionsService.ActionCallback() {
            @Override
            public void onSuccess() {
                loadData();
            }

            @Override
            public void onError(String message) {
                loadData();
            }
        });
    }

    private void loadData() {
        regionsService.loadRegionMap(myUid, new RegionsService.LoadCallback() {
            @Override
            public void onSuccess(RegionMapData data) {
                runOnUiThread(() -> render(data));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(RegionsActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void render(RegionMapData data) {
        currentData = data == null ? new RegionMapData() : data;
        if (selectedRegion.isEmpty()) {
            selectedRegion = currentData.myRegion;
        }
        tvCycle.setText("Mesecni ciklus: " + value(currentData.cycleLabel));
        regionMapView.setData(currentData.points, currentData.myRegion, selectedRegion);
        renderRows(currentData.rankings);
    }

    private void renderRows(List<RegionLeaderboardEntry> rows) {
        rowsContainer.removeAllViews();
        boolean empty = rows == null || rows.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        int rank = 1;
        for (RegionLeaderboardEntry entry : rows) {
            rowsContainer.addView(createRow(rank, entry));
            rank++;
        }
    }

    private View createRow(int rank, RegionLeaderboardEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(9), dp(8), dp(9));
        row.setBackgroundColor(entry.mine ? 0x33F6C65B : 0x00000000);
        row.setOnClickListener(v -> showRegionStats(entry.region));

        row.addView(cell("#" + rank, 0.14f, Gravity.START, true));
        row.addView(cell(entry.icon, 0.13f, Gravity.CENTER, true));
        row.addView(cell(entry.region, 0.45f, Gravity.START, true));
        row.addView(cell(String.valueOf(entry.monthlyStars), 0.18f, Gravity.CENTER, true));
        row.addView(cell(entry.mine ? "moj" : "", 0.10f, Gravity.CENTER, false));
        return row;
    }

    private TextView cell(String text, float weight, int gravity, boolean bold) {
        TextView view = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        view.setLayoutParams(params);
        view.setGravity(gravity);
        view.setText(text);
        view.setTextColor(getResources().getColor(R.color.app_on_surface));
        view.setTextSize(14);
        view.setSingleLine(false);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private void showRegionStats(String region) {
        selectedRegion = value(region);
        regionMapView.setData(currentData.points, currentData.myRegion, selectedRegion);
        RegionLeaderboardEntry entry = findRegion(region);
        if (entry == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(entry.icon + " " + entry.region)
                .setMessage("Prva mesta: " + entry.firstPlaces
                        + "\nDruga mesta: " + entry.secondPlaces
                        + "\nTreca mesta: " + entry.thirdPlaces
                        + "\nTrenutno aktivni igraci: " + entry.activePlayers
                        + "\nUkupno registrovani igraci: " + entry.totalPlayers
                        + "\nZvezde u ciklusu: " + entry.monthlyStars)
                .setPositiveButton("OK", null)
                .show();
    }

    private RegionLeaderboardEntry findRegion(String region) {
        for (RegionLeaderboardEntry entry : currentData.rankings) {
            if (entry.region.equals(region)) {
                return entry;
            }
        }
        return null;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
