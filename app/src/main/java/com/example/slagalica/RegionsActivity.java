package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.domain.AuthService;
import com.example.slagalica.domain.RegionsService;
import com.example.slagalica.domain.SessionManager;
import com.example.slagalica.model.RegionLeaderboardEntry;
import com.example.slagalica.model.RegionCatalog;
import com.example.slagalica.model.RegionMapData;

import java.util.List;

public class RegionsActivity extends AppCompatActivity {
    public static final String EXTRA_CHAT_MODE = "regions_chat_mode";

    private RegionMapView regionMapView;
    private LinearLayout rowsContainer;
    private LinearLayout regionRankingPanel;
    private TextView tvTitle;
    private TextView tvCycle;
    private TextView tvEmpty;
    private RegionsService regionsService;
    private String myUid = "";
    private RegionMapData currentData = new RegionMapData();
    private String selectedRegion = "";
    private boolean chatMode = false;

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
        chatMode = getIntent().getBooleanExtra(EXTRA_CHAT_MODE, false);
        regionsService = new RegionsService();
        myUid = value(new AuthService().getCurrentUserId());
        if (myUid.isEmpty()) {
            Toast.makeText(this, "Niste ulogovani.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        regionMapView = findViewById(R.id.regionMapView);
        rowsContainer = findViewById(R.id.regionRowsContainer);
        regionRankingPanel = findViewById(R.id.regionRankingPanel);
        tvTitle = findViewById(R.id.tvRegionsTitle);
        tvCycle = findViewById(R.id.tvRegionsCycle);
        tvEmpty = findViewById(R.id.tvRegionsEmpty);
        regionMapView.setRegionClickListener(this::handleRegionClick);

        if (chatMode) {
            tvTitle.setVisibility(View.GONE);
            tvCycle.setVisibility(View.GONE);
            regionRankingPanel.setVisibility(View.GONE);
        }

        load();
    }

    private void load() {
        if (chatMode) {
            loadData();
            return;
        }
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
        if (!chatMode) {
            tvCycle.setText("Mesecni ciklus: " + value(currentData.cycleLabel));
        }
        regionMapView.setData(currentData.points, currentData.myRegion, selectedRegion);
        if (!chatMode) {
            renderRows(currentData.rankings);
        }
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
        row.setPadding(dp(2), dp(4), dp(2), dp(4));
        row.setBackgroundColor(entry.mine ? 0x33F6C65B : 0x00000000);
        row.setOnClickListener(v -> showRegionStats(entry.region));

        row.addView(cell("#" + rank, 0.07f, Gravity.START, true));
        row.addView(regionIconCell(entry));
        TextView regionCell = cell(entry.region, 0.50f, Gravity.START, true);
        regionCell.setPadding(dp(6), 0, 0, 0);
        row.addView(regionCell);
        row.addView(cell(String.valueOf(entry.monthlyStars), 0.18f, Gravity.CENTER, true));
        row.addView(cell(entry.mine ? "moj" : "", 0.08f, Gravity.CENTER, false));
        return row;
    }

    private View regionIconCell(RegionLeaderboardEntry entry) {
        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.17f
        ));

        int avatarResource = regionAvatarResource(entry.region);
        if (avatarResource != 0) {
            ImageView avatar = new ImageView(this);
            avatar.setImageResource(avatarResource);
            avatar.setContentDescription(entry.region);
            FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
            container.addView(avatar, avatarParams);
        } else {
            TextView fallback = new TextView(this);
            fallback.setText(entry.icon);
            fallback.setTextColor(getResources().getColor(R.color.app_on_surface));
            fallback.setTextSize(14);
            fallback.setTypeface(fallback.getTypeface(), android.graphics.Typeface.BOLD);
            fallback.setGravity(Gravity.CENTER);
            container.addView(fallback, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
            ));
        }
        return container;
    }

    private int regionAvatarResource(String region) {
        if ("Vojvodina".equals(region)) {
            return R.drawable.region_avatar_vojvodina;
        }
        if ("Šumadija".equals(region)) {
            return R.drawable.region_avatar_sumadija;
        }
        if ("Južno Pomoravlje".equals(region)) {
            return R.drawable.region_avatar_juzno_pomoravlje;
        }
        if ("Kosovo i Metohija".equals(region)) {
            return R.drawable.region_avatar_kosovo_metohija;
        }
        if ("Podrinje i Posavina".equals(region)) {
            return R.drawable.region_avatar_podrinje_posavina;
        }
        if ("Rasina i Toplica".equals(region)) {
            return R.drawable.region_avatar_rasina_toplica;
        }
        if ("Raška".equals(region)) {
            return R.drawable.region_avatar_raska;
        }
        if ("Timok i Braničevo".equals(region)) {
            return R.drawable.region_avatar_timok_branicevo;
        }
        if ("Šopluk".equals(region)) {
            return R.drawable.region_avatar_sopluk;
        }
        return 0;
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
        RegionLeaderboardEntry entry = findRegion(region);
        if (entry == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setCustomTitle(regionStatsTitle(entry))
                .setMessage("Prva mesta: " + entry.firstPlaces
                        + "\nDruga mesta: " + entry.secondPlaces
                        + "\nTreca mesta: " + entry.thirdPlaces
                        + "\nTrenutno aktivni igraci: " + entry.activePlayers
                        + "\nUkupno registrovani igraci: " + entry.totalPlayers
                        + "\nZvezde u ciklusu: " + entry.monthlyStars)
                .setPositiveButton("OK", null)
                .show();
    }

    private void handleRegionClick(String region) {
        if (!chatMode) {
            showRegionStats(region);
            return;
        }

        String clickedRegion = RegionCatalog.canonicalName(value(region));
        String playerRegion = RegionCatalog.canonicalName(value(currentData.myRegion));
        if (clickedRegion.isEmpty() || !clickedRegion.equals(playerRegion)) {
            Toast.makeText(
                    this,
                    getString(R.string.chat_region_restricted, clickedRegion),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        selectedRegion = clickedRegion;
        regionMapView.setData(currentData.points, currentData.myRegion, selectedRegion);
        startActivity(new Intent(this, ChatActivity.class));
    }

    private View regionStatsTitle(RegionLeaderboardEntry entry) {
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(24), dp(20), dp(24), dp(8));

        int avatarResource = regionAvatarResource(entry.region);
        if (avatarResource != 0) {
            ImageView avatar = new ImageView(this);
            avatar.setImageResource(avatarResource);
            avatar.setContentDescription(entry.region);
            title.addView(avatar, new LinearLayout.LayoutParams(dp(32), dp(32)));
        }

        TextView regionName = new TextView(this);
        regionName.setText(entry.region);
        regionName.setTextColor(getResources().getColor(R.color.app_on_surface));
        regionName.setTextSize(20);
        regionName.setTypeface(regionName.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMarginStart(avatarResource == 0 ? 0 : dp(10));
        title.addView(regionName, nameParams);
        return title;
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
