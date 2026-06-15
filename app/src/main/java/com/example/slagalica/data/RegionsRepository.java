package com.example.slagalica.data;

import com.example.slagalica.model.RegionCatalog;
import com.example.slagalica.model.RegionDefinition;
import com.example.slagalica.model.RegionLeaderboardEntry;
import com.example.slagalica.model.RegionMapData;
import com.example.slagalica.model.RegionPlayerPoint;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RegionsRepository {
    public interface LoadCallback {
        void onSuccess(RegionMapData data);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    private static final long ACTIVE_WINDOW_MS = 5L * 60L * 1000L;
    private static final String FRAME_GOLD = "gold";
    private static final String FRAME_SILVER = "silver";
    private static final String FRAME_BRONZE = "bronze";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadRegionMap(String myUid, LoadCallback callback) {
        String currentCycleId = currentMonthlyCycleId();
        String cycleLabel = currentMonthlyCycleLabel();
        db.collection("users")
                .limit(500)
                .get()
                .addOnSuccessListener(users -> loadRegionStats(myUid, currentCycleId, cycleLabel, users, callback))
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam regione."));
    }

    public void processPreviousMonthlyRegionAwards(ActionCallback callback) {
        String previousCycleId = previousMonthlyCycleId();
        if (previousCycleId.isEmpty()) {
            callback.onSuccess();
            return;
        }

        db.collection("regionLeaderboards")
                .document("meta")
                .get()
                .addOnSuccessListener(meta -> {
                    String processed = meta == null ? "" : value(meta.getString("lastAvatarFrameCycleId"));
                    if (previousCycleId.equals(processed)) {
                        callback.onSuccess();
                        return;
                    }
                    applyPreviousMonthlyRegionAwards(previousCycleId, callback);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da proverim regionalni ciklus."));
    }

    private void loadRegionStats(
            String myUid,
            String currentCycleId,
            String cycleLabel,
            QuerySnapshot users,
            LoadCallback callback
    ) {
        db.collection("regionStats")
                .get()
                .addOnSuccessListener(stats -> {
                    RegionMapData out = buildRegionData(myUid, currentCycleId, cycleLabel, users, stats);
                    callback.onSuccess(out);
                })
                .addOnFailureListener(e -> {
                    RegionMapData out = buildRegionData(myUid, currentCycleId, cycleLabel, users, null);
                    callback.onSuccess(out);
                });
    }

    private RegionMapData buildRegionData(
            String myUid,
            String currentCycleId,
            String cycleLabel,
            QuerySnapshot users,
            QuerySnapshot stats
    ) {
        RegionMapData data = new RegionMapData();
        data.cycleLabel = cycleLabel;
        Map<String, RegionLeaderboardEntry> byRegion = new HashMap<>();
        for (RegionDefinition def : RegionCatalog.all()) {
            RegionLeaderboardEntry entry = new RegionLeaderboardEntry();
            entry.region = def.name;
            entry.icon = def.icon;
            byRegion.put(def.name, entry);
            data.rankings.add(entry);
        }

        if (stats != null) {
            for (DocumentSnapshot doc : stats.getDocuments()) {
                String region = RegionCatalog.canonicalName(value(doc.getString("region")));
                RegionLeaderboardEntry entry = byRegion.get(region);
                if (entry == null) {
                    continue;
                }
                entry.firstPlaces = value(doc.getLong("firstPlaces"));
                entry.secondPlaces = value(doc.getLong("secondPlaces"));
                entry.thirdPlaces = value(doc.getLong("thirdPlaces"));
            }
        }

        long now = System.currentTimeMillis();
        if (users != null) {
            for (DocumentSnapshot user : users.getDocuments()) {
                String region = RegionCatalog.canonicalName(value(user.getString("region")));
                RegionLeaderboardEntry entry = byRegion.get(region);
                if (entry == null) {
                    continue;
                }
                if (user.getId().equals(myUid)) {
                    data.myRegion = region;
                    entry.mine = true;
                }
                entry.totalPlayers++;
                Boolean appActive = user.getBoolean("appActive");
                long lastSeen = value(user.getLong("appLastSeenAtMillis"));
                if ((appActive != null && appActive) || (lastSeen > 0L && now - lastSeen <= ACTIVE_WINDOW_MS)) {
                    entry.activePlayers++;
                }
                if (currentCycleId.equals(value(user.getString("monthlyCycleId")))) {
                    entry.monthlyStars += Math.max(0L, value(user.getLong("monthlyCycleStars")));
                }
                data.points.add(pointFromUser(user, region));
            }
        }

        Collections.sort(data.rankings, (a, b) -> {
            int byStars = Long.compare(b.monthlyStars, a.monthlyStars);
            if (byStars != 0) return byStars;
            return a.region.compareToIgnoreCase(b.region);
        });
        return data;
    }

    private RegionPlayerPoint pointFromUser(DocumentSnapshot user, String region) {
        RegionPlayerPoint point = new RegionPlayerPoint();
        point.uid = user.getId();
        point.username = value(user.getString("username"));
        point.region = region;
        Double x = user.getDouble("regionPointX");
        Double y = user.getDouble("regionPointY");
        if (x != null && y != null && x >= 0d && x <= 1d && y >= 0d && y <= 1d) {
            point.x = x.floatValue();
            point.y = y.floatValue();
        } else {
            float[] stable = RegionCatalog.stablePoint(point.uid, region);
            point.x = stable[0];
            point.y = stable[1];
        }
        return point;
    }

    private void applyPreviousMonthlyRegionAwards(String cycleId, ActionCallback callback) {
        db.collection("users")
                .limit(450)
                .get()
                .addOnSuccessListener(users -> {
                    Map<String, Long> regionStars = new HashMap<>();
                    for (RegionDefinition def : RegionCatalog.all()) {
                        regionStars.put(def.name, 0L);
                    }
                    for (DocumentSnapshot user : users.getDocuments()) {
                        if (!cycleId.equals(value(user.getString("monthlyCycleId")))) {
                            continue;
                        }
                        String region = RegionCatalog.canonicalName(value(user.getString("region")));
                        if (!regionStars.containsKey(region)) {
                            continue;
                        }
                        long stars = Math.max(0L, value(user.getLong("monthlyCycleStars")));
                        regionStars.put(region, regionStars.get(region) + stars);
                    }
                    List<String> ranked = rankedRegions(regionStars);
                    WriteBatch batch = db.batch();
                    Map<String, String> frameByRegion = new HashMap<>();
                    if (ranked.size() > 0) frameByRegion.put(ranked.get(0), FRAME_GOLD);
                    if (ranked.size() > 1) frameByRegion.put(ranked.get(1), FRAME_SILVER);
                    if (ranked.size() > 2) frameByRegion.put(ranked.get(2), FRAME_BRONZE);

                    for (DocumentSnapshot user : users.getDocuments()) {
                        String region = RegionCatalog.canonicalName(value(user.getString("region")));
                        String newFrame = frameByRegion.get(region);
                        String currentFrame = value(user.getString("avatarFrameId"));
                        if (newFrame != null) {
                            batch.update(user.getReference(),
                                    "avatarFrameId", newFrame,
                                    "regionAwardFrameCycleId", cycleId);
                        } else if (isAwardFrame(currentFrame)) {
                            batch.update(user.getReference(),
                                    "avatarFrameId", "blue",
                                    "regionAwardFrameCycleId", cycleId);
                        }
                    }
                    incrementRegionPlacements(batch, ranked);
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("lastAvatarFrameCycleId", cycleId);
                    batch.set(db.collection("regionLeaderboards").document("meta"), meta);
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError("Ne mogu da azuriram regionalne nagrade."));
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam prethodni regionalni ciklus."));
    }

    private void incrementRegionPlacements(WriteBatch batch, List<String> ranked) {
        for (int i = 0; i < ranked.size() && i < 3; i++) {
            String field = i == 0 ? "firstPlaces" : i == 1 ? "secondPlaces" : "thirdPlaces";
            Map<String, Object> payload = new HashMap<>();
            payload.put("region", ranked.get(i));
            payload.put(field, com.google.firebase.firestore.FieldValue.increment(1));
            batch.set(db.collection("regionStats").document(RegionCatalog.docId(ranked.get(i))), payload, com.google.firebase.firestore.SetOptions.merge());
        }
    }

    private List<String> rankedRegions(Map<String, Long> stars) {
        List<String> regions = new ArrayList<>(stars.keySet());
        regions.sort((a, b) -> {
            int byStars = Long.compare(stars.get(b), stars.get(a));
            if (byStars != 0) return byStars;
            return a.compareToIgnoreCase(b);
        });
        List<String> out = new ArrayList<>();
        for (String region : regions) {
            if (stars.get(region) > 0L) {
                out.add(region);
            }
            if (out.size() == 3) {
                break;
            }
        }
        return out;
    }

    private boolean isAwardFrame(String frame) {
        return FRAME_GOLD.equals(frame) || FRAME_SILVER.equals(frame) || FRAME_BRONZE.equals(frame);
    }

    private String currentMonthlyCycleId() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return "M_" + new SimpleDateFormat("yyyyMM", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
    }

    private String previousMonthlyCycleId() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.add(Calendar.MONTH, -1);
        return "M_" + new SimpleDateFormat("yyyyMM", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
    }

    private String currentMonthlyCycleLabel() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        end.add(Calendar.MILLISECOND, -1);
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return fmt.format(new Date(start.getTimeInMillis())) + " - " + fmt.format(new Date(end.getTimeInMillis()));
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private long value(Long input) {
        return input == null ? 0L : input;
    }
}
