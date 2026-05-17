package com.example.slagalica.data;

import com.example.slagalica.model.LeaderboardEntry;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LeaderboardRepository {

    public interface LoadCallback {
        void onSuccess(CycleWindow cycle, List<LeaderboardEntry> entries);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    public static class CycleWindow {
        public final String id;
        public final long startMs;
        public final long endMs;

        public CycleWindow(String id, long startMs, long endMs) {
            this.id = id;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        public String label() {
            SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            return fmt.format(new Date(startMs)) + " - " + fmt.format(new Date(endMs));
        }
    }

    private static final int[] WEEKLY_REWARDS = {5, 3, 2};
    private static final int[] MONTHLY_REWARDS = {10, 6, 4};
    private static final long TWO_MINUTES_MS = 2 * 60 * 1000L;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public long getRefreshIntervalMs() {
        return TWO_MINUTES_MS;
    }

    public CycleWindow currentWeeklyCycle() {
        return buildWeeklyCycle(System.currentTimeMillis());
    }

    public CycleWindow currentMonthlyCycle() {
        return buildMonthlyCycle(System.currentTimeMillis());
    }

    public void loadWeeklyLeaderboard(LoadCallback callback) {
        loadLeaderboard(false, callback);
    }

    public void loadMonthlyLeaderboard(LoadCallback callback) {
        loadLeaderboard(true, callback);
    }

    public void processCycleRolloverAndRewards(ActionCallback callback) {
        CycleWindow weeklyNow = currentWeeklyCycle();
        CycleWindow monthlyNow = currentMonthlyCycle();
        DocumentReference metaRef = db.collection("leaderboards").document("meta");

        metaRef.get()
                .addOnSuccessListener(meta -> {
                    if (!meta.exists()) {
                        Map<String, Object> init = new HashMap<>();
                        init.put("weeklyCurrentCycleId", weeklyNow.id);
                        init.put("weeklyStartMs", weeklyNow.startMs);
                        init.put("weeklyEndMs", weeklyNow.endMs);
                        init.put("monthlyCurrentCycleId", monthlyNow.id);
                        init.put("monthlyStartMs", monthlyNow.startMs);
                        init.put("monthlyEndMs", monthlyNow.endMs);
                        metaRef.set(init)
                                .addOnSuccessListener(unused -> callback.onSuccess())
                                .addOnFailureListener(e -> callback.onError("Ne mogu da inicijalizujem rang ciklus."));
                        return;
                    }

                    String oldWeeklyId = value(meta.getString("weeklyCurrentCycleId"));
                    String oldMonthlyId = value(meta.getString("monthlyCurrentCycleId"));

                    boolean weeklyChanged = !oldWeeklyId.equals(weeklyNow.id);
                    boolean monthlyChanged = !oldMonthlyId.equals(monthlyNow.id);

                    if (!weeklyChanged && !monthlyChanged) {
                        callback.onSuccess();
                        return;
                    }

                    runRolloverRewardsSequentially(oldWeeklyId, oldMonthlyId, weeklyNow, monthlyNow, callback);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da procitam stanje rang ciklusa."));
    }

    private void runRolloverRewardsSequentially(
            String oldWeeklyId,
            String oldMonthlyId,
            CycleWindow weeklyNow,
            CycleWindow monthlyNow,
            ActionCallback callback
    ) {
        distributeCycleRewards(false, oldWeeklyId, new ActionCallback() {
            @Override
            public void onSuccess() {
                distributeCycleRewards(true, oldMonthlyId, new ActionCallback() {
                    @Override
                    public void onSuccess() {
                        Map<String, Object> update = new HashMap<>();
                        update.put("weeklyCurrentCycleId", weeklyNow.id);
                        update.put("weeklyStartMs", weeklyNow.startMs);
                        update.put("weeklyEndMs", weeklyNow.endMs);
                        update.put("monthlyCurrentCycleId", monthlyNow.id);
                        update.put("monthlyStartMs", monthlyNow.startMs);
                        update.put("monthlyEndMs", monthlyNow.endMs);
                        db.collection("leaderboards")
                                .document("meta")
                                .set(update)
                                .addOnSuccessListener(unused -> callback.onSuccess())
                                .addOnFailureListener(e -> callback.onError("Ne mogu da upisem novi rang ciklus."));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void distributeCycleRewards(boolean monthly, String cycleId, ActionCallback callback) {
        if (cycleId.isEmpty()) {
            callback.onSuccess();
            return;
        }
        String cycleIdField = monthly ? "monthlyCycleId" : "weeklyCycleId";
        String cycleStarsField = monthly ? "monthlyCycleStars" : "weeklyCycleStars";
        String cycleMatchesField = monthly ? "monthlyCycleMatches" : "weeklyCycleMatches";

        db.collection("users")
                .whereEqualTo(cycleIdField, cycleId)
                .limit(200)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LeaderboardEntry> ranked = mapEntries(snapshot, cycleStarsField, cycleMatchesField);
                    ranked.sort((a, b) -> Long.compare(b.cycleStars, a.cycleStars));
                    List<WinnerReward> rewards = buildRewards(ranked, monthly, cycleId);
                    applyRewardsSequentially(rewards, 0, callback);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da rasporedim nagrade za rang listu."));
    }

    private void applyRewardsSequentially(List<WinnerReward> rewards, int index, ActionCallback callback) {
        if (index >= rewards.size()) {
            callback.onSuccess();
            return;
        }
        WinnerReward reward = rewards.get(index);
        applyRewardToUser(reward, new ActionCallback() {
            @Override
            public void onSuccess() {
                applyRewardsSequentially(rewards, index + 1, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void applyRewardToUser(WinnerReward reward, ActionCallback callback) {
        DocumentReference userRef = db.collection("users").document(reward.uid);
        DocumentReference rewardNotificationRef = db.collection("users")
                .document(reward.uid)
                .collection("notifications")
                .document();
        DocumentReference rankingNotificationRef = db.collection("users")
                .document(reward.uid)
                .collection("notifications")
                .document();

        db.runTransaction((Transaction.Function<Boolean>) transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            Long tokens = user.getLong("tokens");
            if (tokens == null) {
                tokens = 0L;
            }
            String claimedField = reward.monthly ? "monthlyRewardClaimedCycleId" : "weeklyRewardClaimedCycleId";
            String alreadyClaimedFor = value(user.getString(claimedField));
            if (alreadyClaimedFor.equals(reward.cycleId)) {
                return false;
            }

            long newTokens = tokens + reward.tokens;
            transaction.update(userRef, "tokens", newTokens, claimedField, reward.cycleId);

            Map<String, Object> rankingPayload = new HashMap<>();
            rankingPayload.put("type", "ranking");
            rankingPayload.put("title", reward.monthly ? "Mesecni plasman" : "Nedeljni plasman");
            rankingPayload.put("message", "Zauzeli ste mesto #" + reward.rank + " na "
                    + (reward.monthly ? "mesecnoj" : "nedeljnoj") + " rang listi.");
            rankingPayload.put("read", false);
            rankingPayload.put("localShown", false);
            rankingPayload.put("createdAt", Timestamp.now());
            rankingPayload.put("actionType", "open_rankings");
            rankingPayload.put("actionPayload", reward.cycleId);
            transaction.set(rankingNotificationRef, rankingPayload);

            Map<String, Object> rewardPayload = new HashMap<>();
            rewardPayload.put("type", "rewards");
            rewardPayload.put("title", reward.monthly ? "Mesecna rang nagrada" : "Nedeljna rang nagrada");
            rewardPayload.put("message", reward.message);
            rewardPayload.put("read", false);
            rewardPayload.put("localShown", false);
            rewardPayload.put("createdAt", Timestamp.now());
            rewardPayload.put("actionType", "open_ranking_rewards");
            rewardPayload.put("actionPayload", reward.cycleId);
            transaction.set(rewardNotificationRef, rewardPayload);
            return true;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Ne mogu da dodelim nagradu pobedniku rang liste."));
    }

    private List<WinnerReward> buildRewards(List<LeaderboardEntry> ranked, boolean monthly, String cycleId) {
        List<WinnerReward> out = new ArrayList<>();
        int rank = 1;
        for (LeaderboardEntry entry : ranked) {
            if (rank > 10) {
                break;
            }
            long tokens = rewardForRank(rank, monthly);
            if (tokens > 0) {
                WinnerReward reward = new WinnerReward();
                reward.uid = entry.uid;
                reward.cycleId = cycleId;
                reward.monthly = monthly;
                reward.rank = rank;
                reward.tokens = tokens;
                reward.message = "Osvojeno mesto #" + rank + " na "
                        + (monthly ? "mesecnoj" : "nedeljnoj")
                        + " rang listi. Dobijas " + tokens + " tokena.";
                out.add(reward);
            }
            rank++;
        }
        return out;
    }

    private long rewardForRank(int rank, boolean monthly) {
        if (rank == 1) {
            return monthly ? MONTHLY_REWARDS[0] : WEEKLY_REWARDS[0];
        }
        if (rank == 2) {
            return monthly ? MONTHLY_REWARDS[1] : WEEKLY_REWARDS[1];
        }
        if (rank == 3) {
            return monthly ? MONTHLY_REWARDS[2] : WEEKLY_REWARDS[2];
        }
        if (rank >= 4 && rank <= 10) {
            return monthly ? 2 : 1;
        }
        return 0;
    }

    private void loadLeaderboard(boolean monthly, LoadCallback callback) {
        CycleWindow cycle = monthly ? currentMonthlyCycle() : currentWeeklyCycle();
        String cycleIdField = monthly ? "monthlyCycleId" : "weeklyCycleId";
        String cycleStarsField = monthly ? "monthlyCycleStars" : "weeklyCycleStars";
        String cycleMatchesField = monthly ? "monthlyCycleMatches" : "weeklyCycleMatches";

        db.collection("users")
                .whereEqualTo(cycleIdField, cycle.id)
                .limit(200)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LeaderboardEntry> entries = mapEntries(snapshot, cycleStarsField, cycleMatchesField);
                    entries.sort((a, b) -> Long.compare(b.cycleStars, a.cycleStars));
                    callback.onSuccess(cycle, entries);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam rang listu."));
    }

    private List<LeaderboardEntry> mapEntries(QuerySnapshot snapshot, String starsField, String matchesField) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        if (snapshot == null || snapshot.isEmpty()) {
            return entries;
        }
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Long matches = doc.getLong(matchesField);
            if (matches == null || matches <= 0) {
                continue;
            }
            LeaderboardEntry item = new LeaderboardEntry();
            item.uid = doc.getId();
            item.username = value(doc.getString("username"));
            item.league = value(doc.getLong("league"));
            item.cycleStars = value(doc.getLong(starsField));
            item.cycleMatches = matches;
            entries.add(item);
        }
        return entries;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private CycleWindow buildWeeklyCycle(long nowMs) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(nowMs);
        start.setFirstDayOfWeek(Calendar.MONDAY);
        start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        String id = "W_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
        return new CycleWindow(id, start.getTimeInMillis(), end.getTimeInMillis());
    }

    private CycleWindow buildMonthlyCycle(long nowMs) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(nowMs);
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        end.add(Calendar.MILLISECOND, -1);

        String id = "M_" + new SimpleDateFormat("yyyyMM", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
        return new CycleWindow(id, start.getTimeInMillis(), end.getTimeInMillis());
    }

    private static class WinnerReward {
        String uid;
        String cycleId;
        boolean monthly;
        int rank;
        long tokens;
        String message;
    }
}
