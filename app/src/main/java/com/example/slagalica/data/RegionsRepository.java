package com.example.slagalica.data;

import com.example.slagalica.model.RegionCatalog;
import com.example.slagalica.model.RegionDefinition;
import com.example.slagalica.model.RegionLeaderboardEntry;
import com.example.slagalica.model.RegionMapData;
import com.example.slagalica.model.RegionPlayerPoint;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
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

    private interface UsersCallback {
        void onSuccess(List<DocumentSnapshot> users);
        void onError();
    }

    private static final long ACTIVE_WINDOW_MS = 5L * 60L * 1000L;
    private static final long USERS_PAGE_SIZE = 250L;
    private static final int FRAME_BATCH_SIZE = 400;
    private static final String FRAME_GOLD = "gold";
    private static final String FRAME_SILVER = "silver";
    private static final String FRAME_BRONZE = "bronze";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static final class FrameUpdate {
        final DocumentReference userRef;
        final String frame;

        FrameUpdate(DocumentReference userRef, String frame) {
            this.userRef = userRef;
            this.frame = frame;
        }
    }

    public void loadRegionMap(String myUid, LoadCallback callback) {
        String currentCycleId = currentMonthlyCycleId();
        String cycleLabel = currentMonthlyCycleLabel();
        loadAllUsers(new UsersCallback() {
            @Override
            public void onSuccess(List<DocumentSnapshot> users) {
                loadRegionStats(myUid, currentCycleId, cycleLabel, users, callback);
            }

            @Override
            public void onError() {
                callback.onError("Ne mogu da ucitam regione.");
            }
        });
    }

    private void loadAllUsers(UsersCallback callback) {
        List<DocumentSnapshot> users = new ArrayList<>();
        Query firstPage = db.collection("users")
                .orderBy(FieldPath.documentId())
                .limit(USERS_PAGE_SIZE);
        loadUsersPage(firstPage, users, callback);
    }

    private void loadUsersPage(
            Query query,
            List<DocumentSnapshot> users,
            UsersCallback callback
    ) {
        query.get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> page = snapshot.getDocuments();
                    users.addAll(page);
                    if (page.size() < USERS_PAGE_SIZE) {
                        callback.onSuccess(users);
                        return;
                    }

                    DocumentSnapshot lastUser = page.get(page.size() - 1);
                    Query nextPage = db.collection("users")
                            .orderBy(FieldPath.documentId())
                            .startAfter(lastUser)
                            .limit(USERS_PAGE_SIZE);
                    loadUsersPage(nextPage, users, callback);
                })
                .addOnFailureListener(e -> callback.onError());
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
                    boolean rankingStored = meta != null
                            && Boolean.TRUE.equals(meta.getBoolean("lastAvatarFrameRankingStored"));
                    String framesApplied = meta == null
                            ? ""
                            : value(meta.getString("avatarFramesAppliedCycleId"));
                    if (previousCycleId.equals(processed) && !rankingStored) {
                        callback.onSuccess();
                        return;
                    }
                    if (previousCycleId.equals(processed) && previousCycleId.equals(framesApplied)) {
                        callback.onSuccess();
                        return;
                    }
                    if (previousCycleId.equals(processed)) {
                        List<String> ranked = stringList(meta.get("lastAvatarFrameRankedRegions"));
                        loadUsersAndApplyFrames(previousCycleId, ranked, callback);
                        return;
                    }
                    loadAllUsers(new UsersCallback() {
                        @Override
                        public void onSuccess(List<DocumentSnapshot> users) {
                            finalizeRankingAndApplyFrames(previousCycleId, users, callback);
                        }

                        @Override
                        public void onError() {
                            callback.onError("Ne mogu da ucitam prethodni regionalni ciklus.");
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da proverim regionalni ciklus."));
    }

    private void loadRegionStats(
            String myUid,
            String currentCycleId,
            String cycleLabel,
            List<DocumentSnapshot> users,
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
            List<DocumentSnapshot> users,
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
            for (DocumentSnapshot user : users) {
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
                long presenceAge = now - lastSeen;
                boolean recentlySeen = lastSeen > 0L
                        && presenceAge >= 0L
                        && presenceAge <= ACTIVE_WINDOW_MS;
                if (Boolean.TRUE.equals(appActive) && recentlySeen) {
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
        if (x != null && y != null
                && x >= 0d && x <= 1d && y >= 0d && y <= 1d
                && RegionCatalog.containsPoint(region, x.floatValue(), y.floatValue())) {
            point.x = x.floatValue();
            point.y = y.floatValue();
        } else {
            float[] stable = RegionCatalog.stablePoint(point.uid, region);
            point.x = stable[0];
            point.y = stable[1];
        }
        return point;
    }

    private void loadUsersAndApplyFrames(
            String cycleId,
            List<String> ranked,
            ActionCallback callback
    ) {
        loadAllUsers(new UsersCallback() {
            @Override
            public void onSuccess(List<DocumentSnapshot> users) {
                applyFramesInBatches(cycleId, ranked, users, callback);
            }

            @Override
            public void onError() {
                callback.onError("Ne mogu da ucitam korisnike za regionalne okvire.");
            }
        });
    }

    private void finalizeRankingAndApplyFrames(
            String cycleId,
            List<DocumentSnapshot> users,
            ActionCallback callback
    ) {
        Map<String, Long> regionStars = new HashMap<>();
        for (RegionDefinition def : RegionCatalog.all()) {
            regionStars.put(def.name, 0L);
        }
        for (DocumentSnapshot user : users) {
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
        List<String> candidateRanking = rankedRegions(regionStars);
        finalizeRanking(cycleId, candidateRanking, new RankingCallback() {
            @Override
            public void onSuccess(List<String> canonicalRanking) {
                applyFramesInBatches(cycleId, canonicalRanking, users, callback);
            }

            @Override
            public void onError() {
                callback.onError("Ne mogu da sacuvam regionalni plasman.");
            }
        });
    }

    private interface RankingCallback {
        void onSuccess(List<String> ranked);
        void onError();
    }

    private void finalizeRanking(
            String cycleId,
            List<String> candidateRanking,
            RankingCallback callback
    ) {
        DocumentReference metaRef = db.collection("regionLeaderboards").document("meta");
        db.runTransaction(transaction -> {
            DocumentSnapshot meta = transaction.get(metaRef);
            String processedCycle = value(meta.getString("lastAvatarFrameCycleId"));
            boolean rankingStored = Boolean.TRUE.equals(meta.getBoolean("lastAvatarFrameRankingStored"));
            if (cycleId.equals(processedCycle) && rankingStored) {
                return stringList(meta.get("lastAvatarFrameRankedRegions"));
            }

            List<String> canonicalRanking = new ArrayList<>(candidateRanking);
            if (!cycleId.equals(processedCycle)) {
                for (int i = 0; i < canonicalRanking.size() && i < 3; i++) {
                    String field = i == 0 ? "firstPlaces" : i == 1 ? "secondPlaces" : "thirdPlaces";
                    String region = canonicalRanking.get(i);
                    Map<String, Object> placement = new HashMap<>();
                    placement.put("region", region);
                    placement.put(field, FieldValue.increment(1));
                    transaction.set(
                            db.collection("regionStats").document(RegionCatalog.docId(region)),
                            placement,
                            SetOptions.merge()
                    );
                }
            }

            Map<String, Object> metaUpdate = new HashMap<>();
            metaUpdate.put("lastAvatarFrameCycleId", cycleId);
            metaUpdate.put("lastAvatarFrameRankedRegions", canonicalRanking);
            metaUpdate.put("lastAvatarFrameRankingStored", true);
            transaction.set(metaRef, metaUpdate, SetOptions.merge());
            return canonicalRanking;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError());
    }

    private void applyFramesInBatches(
            String cycleId,
            List<String> ranked,
            List<DocumentSnapshot> users,
            ActionCallback callback
    ) {
        Map<String, String> frameByRegion = new HashMap<>();
        if (ranked.size() > 0) frameByRegion.put(ranked.get(0), FRAME_GOLD);
        if (ranked.size() > 1) frameByRegion.put(ranked.get(1), FRAME_SILVER);
        if (ranked.size() > 2) frameByRegion.put(ranked.get(2), FRAME_BRONZE);

        List<FrameUpdate> updates = new ArrayList<>();
        for (DocumentSnapshot user : users) {
            String region = RegionCatalog.canonicalName(value(user.getString("region")));
            String newFrame = frameByRegion.get(region);
            String currentFrame = value(user.getString("avatarFrameId"));
            String appliedCycle = value(user.getString("regionAwardFrameCycleId"));
            if (newFrame != null) {
                if (!newFrame.equals(currentFrame) || !cycleId.equals(appliedCycle)) {
                    updates.add(new FrameUpdate(user.getReference(), newFrame));
                }
            } else if (isAwardFrame(currentFrame)) {
                updates.add(new FrameUpdate(user.getReference(), "blue"));
            }
        }
        commitFrameBatch(cycleId, updates, 0, callback);
    }

    private void commitFrameBatch(
            String cycleId,
            List<FrameUpdate> updates,
            int start,
            ActionCallback callback
    ) {
        if (start >= updates.size()) {
            markFramesApplied(cycleId, callback);
            return;
        }
        int end = Math.min(start + FRAME_BATCH_SIZE, updates.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            FrameUpdate update = updates.get(i);
            batch.update(
                    update.userRef,
                    "avatarFrameId", update.frame,
                    "regionAwardFrameCycleId", cycleId
            );
        }
        batch.commit()
                .addOnSuccessListener(unused -> commitFrameBatch(cycleId, updates, end, callback))
                .addOnFailureListener(e -> callback.onError("Ne mogu da azuriram regionalne okvire."));
    }

    private void markFramesApplied(String cycleId, ActionCallback callback) {
        Map<String, Object> update = new HashMap<>();
        update.put("avatarFramesAppliedCycleId", cycleId);
        db.collection("regionLeaderboards")
                .document("meta")
                .set(update, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Okviri su azurirani, ali ciklus nije potvrđen."));
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

    private List<String> stringList(Object input) {
        List<String> out = new ArrayList<>();
        if (!(input instanceof Iterable<?>)) {
            return out;
        }
        for (Object item : (Iterable<?>) input) {
            String value = item == null ? "" : item.toString().trim();
            if (!value.isEmpty()) {
                out.add(RegionCatalog.canonicalName(value));
            }
        }
        return out;
    }
}
