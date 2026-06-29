package com.example.slagalica;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.slagalica.model.LeaderboardEntry;
import com.google.firebase.firestore.FirebaseFirestore;
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

final class LocalEconomyFallback {
    private static final String PREFS = "local_economy_fallback";
    private static final String STARS = "_stars";
    private static final String TOKENS = "_tokens";
    private static final String USERNAME = "_username";
    private static final String LEAGUE = "_league";
    private static final String WEEKLY_ID = "_weekly_id";
    private static final String WEEKLY_STARS = "_weekly_stars";
    private static final String WEEKLY_MATCHES = "_weekly_matches";
    private static final String MONTHLY_ID = "_monthly_id";
    private static final String MONTHLY_STARS = "_monthly_stars";
    private static final String MONTHLY_MATCHES = "_monthly_matches";

    interface SyncCallback {
        void onComplete();
    }

    private LocalEconomyFallback() {
    }

    static void save(Context context, String uid, long stars, long tokens) {
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        prefs(context).edit()
                .putLong(uid + STARS, Math.max(0L, stars))
                .putLong(uid + TOKENS, Math.max(0L, tokens))
                .apply();
    }

    static void saveMatchResult(
            Context context,
            String uid,
            String username,
            long league,
            long stars,
            long tokens,
            long starDelta
    ) {
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        String weeklyId = currentWeeklyCycleId();
        String monthlyId = currentMonthlyCycleId();
        long weeklyStars = weeklyId.equals(prefs.getString(uid + WEEKLY_ID, ""))
                ? prefs.getLong(uid + WEEKLY_STARS, 0L)
                : Math.max(0L, stars - starDelta);
        long weeklyMatches = weeklyId.equals(prefs.getString(uid + WEEKLY_ID, ""))
                ? prefs.getLong(uid + WEEKLY_MATCHES, 0L)
                : 0L;
        long monthlyStars = monthlyId.equals(prefs.getString(uid + MONTHLY_ID, ""))
                ? prefs.getLong(uid + MONTHLY_STARS, 0L)
                : Math.max(0L, stars - starDelta);
        long monthlyMatches = monthlyId.equals(prefs.getString(uid + MONTHLY_ID, ""))
                ? prefs.getLong(uid + MONTHLY_MATCHES, 0L)
                : 0L;

        prefs.edit()
                .putLong(uid + STARS, Math.max(0L, stars))
                .putLong(uid + TOKENS, Math.max(0L, tokens))
                .putString(uid + USERNAME, username == null ? "" : username)
                .putLong(uid + LEAGUE, Math.max(0L, league))
                .putString(uid + WEEKLY_ID, weeklyId)
                .putLong(uid + WEEKLY_STARS, weeklyStars + starDelta)
                .putLong(uid + WEEKLY_MATCHES, weeklyMatches + 1L)
                .putString(uid + MONTHLY_ID, monthlyId)
                .putLong(uid + MONTHLY_STARS, monthlyStars + starDelta)
                .putLong(uid + MONTHLY_MATCHES, monthlyMatches + 1L)
                .apply();
    }

    static Map<String, Long> merge(Context context, String uid, Map<String, Long> remote) {
        Map<String, Long> out = new HashMap<>();
        if (remote != null) {
            out.putAll(remote);
        }
        if (uid == null || uid.trim().isEmpty()) {
            return out;
        }
        SharedPreferences prefs = prefs(context);
        String starsKey = uid + STARS;
        String tokensKey = uid + TOKENS;
        if (!prefs.contains(starsKey) && !prefs.contains(tokensKey)) {
            return out;
        }
        long remoteStars = value(out.get("stars"));
        long remoteTokens = value(out.get("tokens"));
        long localStars = prefs.getLong(starsKey, remoteStars);
        long localTokens = prefs.getLong(tokensKey, remoteTokens);
        out.put("stars", Math.max(remoteStars, localStars));
        out.put("tokens", Math.max(remoteTokens, localTokens));
        return out;
    }

    static List<LeaderboardEntry> mergeLeaderboardEntries(
            Context context,
            String uid,
            boolean monthly,
            List<LeaderboardEntry> remote
    ) {
        List<LeaderboardEntry> out = new ArrayList<>();
        if (remote != null) {
            out.addAll(remote);
        }
        if (uid == null || uid.trim().isEmpty()) {
            return out;
        }
        SharedPreferences prefs = prefs(context);
        String cycleId = monthly ? currentMonthlyCycleId() : currentWeeklyCycleId();
        String storedCycleId = prefs.getString(uid + (monthly ? MONTHLY_ID : WEEKLY_ID), "");
        long matches = prefs.getLong(uid + (monthly ? MONTHLY_MATCHES : WEEKLY_MATCHES), 0L);
        if (!cycleId.equals(storedCycleId) || matches <= 0L) {
            return out;
        }
        long localStars = prefs.getLong(uid + (monthly ? MONTHLY_STARS : WEEKLY_STARS), 0L);
        LeaderboardEntry mine = null;
        for (LeaderboardEntry entry : out) {
            if (uid.equals(entry.uid)) {
                mine = entry;
                break;
            }
        }
        if (mine == null) {
            mine = new LeaderboardEntry();
            mine.uid = uid;
            mine.username = prefs.getString(uid + USERNAME, "");
            mine.league = prefs.getLong(uid + LEAGUE, 0L);
            out.add(mine);
        }
        mine.cycleStars = Math.max(mine.cycleStars, localStars);
        mine.cycleMatches = Math.max(mine.cycleMatches, matches);
        if (mine.username == null || mine.username.trim().isEmpty()) {
            mine.username = prefs.getString(uid + USERNAME, "");
        }
        mine.league = Math.max(mine.league, prefs.getLong(uid + LEAGUE, 0L));
        Collections.sort(out, (a, b) -> {
            int byStars = Long.compare(b.cycleStars, a.cycleStars);
            if (byStars != 0) return byStars;
            String left = a.username == null ? "" : a.username;
            String right = b.username == null ? "" : b.username;
            return left.compareToIgnoreCase(right);
        });
        return out;
    }

    static void syncRankingToRemote(Context context, String uid, SyncCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onComplete();
            return;
        }
        SharedPreferences prefs = prefs(context);
        String weeklyId = prefs.getString(uid + WEEKLY_ID, "");
        String monthlyId = prefs.getString(uid + MONTHLY_ID, "");
        long weeklyMatches = prefs.getLong(uid + WEEKLY_MATCHES, 0L);
        long monthlyMatches = prefs.getLong(uid + MONTHLY_MATCHES, 0L);
        boolean hasWeekly = currentWeeklyCycleId().equals(weeklyId) && weeklyMatches > 0L;
        boolean hasMonthly = currentMonthlyCycleId().equals(monthlyId) && monthlyMatches > 0L;
        if (!hasWeekly && !hasMonthly) {
            callback.onComplete();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String username = prefs.getString(uid + USERNAME, "");
        long league = prefs.getLong(uid + LEAGUE, 0L);
        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("stars", Math.max(0L, prefs.getLong(uid + STARS, 0L)));
        userUpdate.put("tokens", Math.max(0L, prefs.getLong(uid + TOKENS, 0L)));

        if (hasWeekly) {
            userUpdate.put("weeklyCycleId", weeklyId);
            userUpdate.put("weeklyCycleStars", Math.max(0L, prefs.getLong(uid + WEEKLY_STARS, 0L)));
            userUpdate.put("weeklyCycleMatches", Math.max(1L, weeklyMatches));
        }
        if (hasMonthly) {
            userUpdate.put("monthlyCycleId", monthlyId);
            userUpdate.put("monthlyCycleStars", Math.max(0L, prefs.getLong(uid + MONTHLY_STARS, 0L)));
            userUpdate.put("monthlyCycleMatches", Math.max(1L, monthlyMatches));
        }

        db.collection("users").document(uid)
                .set(userUpdate, SetOptions.merge())
                .addOnCompleteListener(userTask -> {
                    WriteBatch batch = db.batch();
                    if (hasWeekly) {
                        writeRemoteCycle(
                                batch,
                                db,
                                weeklyId,
                                false,
                                uid,
                                username,
                                league,
                                Math.max(0L, prefs.getLong(uid + WEEKLY_STARS, 0L)),
                                Math.max(1L, weeklyMatches)
                        );
                    }
                    if (hasMonthly) {
                        writeRemoteCycle(
                                batch,
                                db,
                                monthlyId,
                                true,
                                uid,
                                username,
                                league,
                                Math.max(0L, prefs.getLong(uid + MONTHLY_STARS, 0L)),
                                Math.max(1L, monthlyMatches)
                        );
                    }
                    batch.commit().addOnCompleteListener(task -> callback.onComplete());
                });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long value(Long input) {
        return input == null ? 0L : input;
    }

    private static void writeRemoteCycle(
            WriteBatch batch,
            FirebaseFirestore db,
            String cycleId,
            boolean monthly,
            String uid,
            String username,
            long league,
            long stars,
            long matches
    ) {
        long[] window = cycleWindow(cycleId, monthly);
        if (window == null || matches <= 0L) {
            return;
        }
        Map<String, Object> cycle = new HashMap<>();
        cycle.put("cycleId", cycleId);
        cycle.put("monthly", monthly);
        cycle.put("startMs", window[0]);
        cycle.put("endMs", window[1]);
        cycle.put("processed", false);
        cycle.put("rewardsProcessed", false);
        cycle.put("updatedAtMillis", System.currentTimeMillis());
        batch.set(db.collection("leaderboardCycles").document(cycleId), cycle, SetOptions.merge());

        Map<String, Object> entry = new HashMap<>();
        entry.put("username", username == null ? "" : username);
        entry.put("league", Math.max(0L, league));
        entry.put("cycleStars", stars);
        entry.put("cycleMatches", matches);
        entry.put("updatedAtMillis", System.currentTimeMillis());
        batch.set(
                db.collection("leaderboardCycles").document(cycleId).collection("entries").document(uid),
                entry,
                SetOptions.merge()
        );
    }

    private static String currentWeeklyCycleId() {
        Calendar start = Calendar.getInstance();
        start.setFirstDayOfWeek(Calendar.MONDAY);
        start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return "W_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
    }

    static String currentWeeklyCycleIdForRanking() {
        return currentWeeklyCycleId();
    }

    private static String currentMonthlyCycleId() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return "M_" + new SimpleDateFormat("yyyyMM", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
    }

    static String currentMonthlyCycleIdForRanking() {
        return currentMonthlyCycleId();
    }

    private static long[] cycleWindow(String cycleId, boolean monthly) {
        try {
            Calendar start = Calendar.getInstance();
            if (monthly) {
                if (!cycleId.startsWith("M_") || cycleId.length() != 8) return null;
                start.set(Calendar.YEAR, Integer.parseInt(cycleId.substring(2, 6)));
                start.set(Calendar.MONTH, Integer.parseInt(cycleId.substring(6, 8)) - 1);
                start.set(Calendar.DAY_OF_MONTH, 1);
            } else {
                if (!cycleId.startsWith("W_") || cycleId.length() != 10) return null;
                start.set(Calendar.YEAR, Integer.parseInt(cycleId.substring(2, 6)));
                start.set(Calendar.MONTH, Integer.parseInt(cycleId.substring(6, 8)) - 1);
                start.set(Calendar.DAY_OF_MONTH, Integer.parseInt(cycleId.substring(8, 10)));
            }
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            Calendar end = (Calendar) start.clone();
            if (monthly) {
                end.add(Calendar.MONTH, 1);
                end.add(Calendar.MILLISECOND, -1);
            } else {
                end.add(Calendar.DAY_OF_MONTH, 6);
                end.set(Calendar.HOUR_OF_DAY, 23);
                end.set(Calendar.MINUTE, 59);
                end.set(Calendar.SECOND, 59);
                end.set(Calendar.MILLISECOND, 999);
            }
            return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
        } catch (Exception ignored) {
            return null;
        }
    }
}
