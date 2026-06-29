package com.example.slagalica;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.slagalica.model.LeaderboardEntry;

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
                : 0L;
        long weeklyMatches = weeklyId.equals(prefs.getString(uid + WEEKLY_ID, ""))
                ? prefs.getLong(uid + WEEKLY_MATCHES, 0L)
                : 0L;
        long monthlyStars = monthlyId.equals(prefs.getString(uid + MONTHLY_ID, ""))
                ? prefs.getLong(uid + MONTHLY_STARS, 0L)
                : 0L;
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

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long value(Long input) {
        return input == null ? 0L : input;
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

    private static String currentMonthlyCycleId() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return "M_" + new SimpleDateFormat("yyyyMM", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
    }
}
