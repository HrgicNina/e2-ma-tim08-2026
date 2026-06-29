package com.example.slagalica.data;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PlayerEconomyRepository {

    public interface EconomyCallback {
        void onSuccess(Map<String, Long> values);

        void onError(String message);
    }

    public interface EconomyObserver {
        void onChanged(Map<String, Long> values);
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void getEconomy(String uid, EconomyCallback callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    Long tokens = snapshot.getLong("tokens");
                    Long stars = snapshot.getLong("stars");
                    Long league = snapshot.getLong("league");
                    Map<String, Long> out = new HashMap<>();
                    out.put("tokens", tokens == null ? 0L : tokens);
                    out.put("stars", stars == null ? 0L : stars);
                    out.put("league", league == null ? 0L : league);
                    callback.onSuccess(out);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam tokene i zvezde."));
    }

    public void getEconomyByUsername(String username, EconomyCallback callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onError("Nedostaje korisnicko ime.");
            return;
        }
        String usernameLower = username.trim().toLowerCase(Locale.ROOT);
        db.collection("users")
                .whereEqualTo("usernameLower", usernameLower)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onError("Korisnik nije pronadjen.");
                        return;
                    }
                    Long tokens = snapshot.getDocuments().get(0).getLong("tokens");
                    Long stars = snapshot.getDocuments().get(0).getLong("stars");
                    Long league = snapshot.getDocuments().get(0).getLong("league");
                    Map<String, Long> out = new HashMap<>();
                    out.put("tokens", tokens == null ? 0L : tokens);
                    out.put("stars", stars == null ? 0L : stars);
                    out.put("league", league == null ? 0L : league);
                    callback.onSuccess(out);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam status korisnika."));
    }

    public ListenerRegistration observeEconomy(String uid, EconomyObserver observer) {
        return db.collection("users")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        observer.onError("Ne mogu da osvezim tokene i zvezde.");
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    Long tokens = snapshot.getLong("tokens");
                    Long stars = snapshot.getLong("stars");
                    Long league = snapshot.getLong("league");
                    Map<String, Long> out = new HashMap<>();
                    out.put("tokens", tokens == null ? 0L : tokens);
                    out.put("stars", stars == null ? 0L : stars);
                    out.put("league", league == null ? 0L : league);
                    observer.onChanged(out);
                });
    }

    public void grantDailyTokensIfNeeded(String uid, EconomyCallback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction((Transaction.Function<Map<String, Long>>) transaction -> {
            DocumentSnapshot user = transaction.get(ref);
            Long tokens = user.getLong("tokens");
            Long stars = user.getLong("stars");
            Long league = user.getLong("league");
            Long lastGrantAt = user.getLong("lastDailyTokenGrantAt");
            if (tokens == null) tokens = 0L;
            if (stars == null) stars = 0L;
            if (league == null) league = 0L;
            if (lastGrantAt == null) lastGrantAt = 0L;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            long newTokens = tokens;
            long now = System.currentTimeMillis();
            if (!user.exists()) {
                newTokens = 5L;
                Map<String, Object> defaults = new HashMap<>();
                defaults.put("tokens", newTokens);
                defaults.put("stars", 0L);
                defaults.put("league", 0L);
                defaults.put("starTokenMilestonesAwarded", 0L);
                defaults.put("lastDailyTokenGrantAt", now);
                defaults.put("weeklyCycleId", currentWeeklyCycleId());
                defaults.put("monthlyCycleId", currentMonthlyCycleId());
                defaults.put("weeklyCycleStars", 0L);
                defaults.put("monthlyCycleStars", 0L);
                defaults.put("weeklyCycleMatches", 0L);
                defaults.put("monthlyCycleMatches", 0L);
                transaction.set(ref, defaults, SetOptions.merge());
            } else if (lastGrantAt <= 0L) {
                Map<String, Object> defaults = new HashMap<>();
                defaults.put("tokens", newTokens);
                defaults.put("stars", stars);
                defaults.put("league", league);
                defaults.put("starTokenMilestonesAwarded", user.getLong("starTokenMilestonesAwarded") == null ? 0L : user.getLong("starTokenMilestonesAwarded"));
                defaults.put("lastDailyTokenGrantAt", now);
                transaction.set(ref, defaults, SetOptions.merge());
            } else if (lastGrantAt < todayStart) {
                newTokens = tokens + 5;
                transaction.update(ref, "tokens", newTokens, "lastDailyTokenGrantAt", now);
            }

            Map<String, Long> out = new HashMap<>();
            out.put("tokens", newTokens);
            out.put("stars", stars);
            out.put("league", league);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> {
                    String details = errorDetails(e);
                    callback.onError(details.isEmpty()
                            ? "Neuspesna dnevna dodela tokena."
                            : "Neuspesna dnevna dodela tokena. " + details);
                });
    }

    public void reserveTokenForRankedMatch(String uid, EconomyCallback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction((Transaction.Function<Map<String, Long>>) transaction -> {
            Long tokens = transaction.get(ref).getLong("tokens");
            Long stars = transaction.get(ref).getLong("stars");
            if (tokens == null) tokens = 0L;
            if (stars == null) stars = 0L;
            if (tokens <= 0) {
                throw new IllegalStateException("NO_TOKENS");
            }
            transaction.update(ref, "tokens", tokens - 1);
            Map<String, Long> out = new HashMap<>();
            out.put("tokens", tokens - 1);
            out.put("stars", stars);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> {
                    String msg = "Neuspesno rezervisanje tokena.";
                    String details = errorDetails(e);
                    if (details.contains("NO_TOKENS")) {
                        msg = "Nemate dovoljno tokena za partiju.";
                    } else if (!details.isEmpty()) {
                        msg = msg + " " + details;
                    }
                    callback.onError(msg);
                });
    }

    public void refundReservedToken(String uid, EconomyCallback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction((Transaction.Function<Map<String, Long>>) transaction -> {
            Long tokens = transaction.get(ref).getLong("tokens");
            Long stars = transaction.get(ref).getLong("stars");
            if (tokens == null) tokens = 0L;
            if (stars == null) stars = 0L;
            transaction.update(ref, "tokens", tokens + 1);
            Map<String, Long> out = new HashMap<>();
            out.put("tokens", tokens + 1);
            out.put("stars", stars);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError("Neuspesan povracaj tokena."));
    }

    public void applyRankedMatchResult(String uid, boolean winner, int score, EconomyCallback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        String weeklyCycleId = currentWeeklyCycleId();
        String monthlyCycleId = currentMonthlyCycleId();
        db.runTransaction((Transaction.Function<Map<String, Long>>) transaction -> {
            DocumentSnapshot user = transaction.get(ref);
            Long stars = user.getLong("stars");
            Long tokens = user.getLong("tokens");
            Long awardedMilestones = user.getLong("starTokenMilestonesAwarded");
            String storedWeeklyCycleId = value(user.getString("weeklyCycleId"));
            String storedMonthlyCycleId = value(user.getString("monthlyCycleId"));
            Long weeklyCycleStars = user.getLong("weeklyCycleStars");
            Long monthlyCycleStars = user.getLong("monthlyCycleStars");
            Long weeklyCycleMatches = user.getLong("weeklyCycleMatches");
            Long monthlyCycleMatches = user.getLong("monthlyCycleMatches");
            if (stars == null) stars = 0L;
            if (tokens == null) tokens = 0L;
            if (weeklyCycleStars == null) weeklyCycleStars = 0L;
            if (monthlyCycleStars == null) monthlyCycleStars = 0L;
            if (weeklyCycleMatches == null) weeklyCycleMatches = 0L;
            if (monthlyCycleMatches == null) monthlyCycleMatches = 0L;
            if (awardedMilestones == null) awardedMilestones = stars / 50;

            long bonusFromScore = Math.max(0, score / 40);
            long delta = winner ? (10 + bonusFromScore) : (bonusFromScore - 10);
            long newStars = Math.max(0, stars + delta);
            long actualDelta = newStars - stars;

            long newTokenMilestones = newStars / 50;
            long extraTokens = Math.max(0, newTokenMilestones - awardedMilestones);
            long newAwardedMilestones = Math.max(awardedMilestones, newTokenMilestones);
            long newTokens = tokens + extraTokens;

            if (!weeklyCycleId.equals(storedWeeklyCycleId)) {
                weeklyCycleStars = 0L;
                weeklyCycleMatches = 0L;
            }
            if (!monthlyCycleId.equals(storedMonthlyCycleId)) {
                monthlyCycleStars = 0L;
                monthlyCycleMatches = 0L;
            }
            long newWeeklyCycleStars = weeklyCycleStars + actualDelta;
            long newMonthlyCycleStars = monthlyCycleStars + actualDelta;
            long newWeeklyCycleMatches = weeklyCycleMatches + 1;
            long newMonthlyCycleMatches = monthlyCycleMatches + 1;
            String username = value(user.getString("username"));
            long league = value(user.getLong("league"));

            transaction.update(
                    ref,
                    "stars", newStars,
                    "tokens", newTokens,
                    "starTokenMilestonesAwarded", newAwardedMilestones,
                    "weeklyCycleId", weeklyCycleId,
                    "monthlyCycleId", monthlyCycleId,
                    "weeklyCycleStars", newWeeklyCycleStars,
                    "monthlyCycleStars", newMonthlyCycleStars,
                    "weeklyCycleMatches", newWeeklyCycleMatches,
                    "monthlyCycleMatches", newMonthlyCycleMatches
            );
            incrementCycle(transaction, uid, username, league, weeklyCycleId, actualDelta, false);
            incrementCycle(transaction, uid, username, league, monthlyCycleId, actualDelta, true);
            Map<String, Long> out = new HashMap<>();
            out.put("stars", newStars);
            out.put("tokens", newTokens);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> {
                    String details = errorDetails(e);
                    callback.onError(details.isEmpty()
                            ? "Neuspesna obrada rezultata partije."
                            : "Neuspesna obrada rezultata partije. " + details);
                });
    }

    public void applyRankedDrawResult(String uid, EconomyCallback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        String weeklyCycleId = currentWeeklyCycleId();
        String monthlyCycleId = currentMonthlyCycleId();
        db.runTransaction((Transaction.Function<Map<String, Long>>) transaction -> {
            DocumentSnapshot user = transaction.get(ref);
            Long stars = user.getLong("stars");
            Long tokens = user.getLong("tokens");
            String storedWeeklyCycleId = value(user.getString("weeklyCycleId"));
            String storedMonthlyCycleId = value(user.getString("monthlyCycleId"));
            Long weeklyCycleStars = user.getLong("weeklyCycleStars");
            Long monthlyCycleStars = user.getLong("monthlyCycleStars");
            Long weeklyCycleMatches = user.getLong("weeklyCycleMatches");
            Long monthlyCycleMatches = user.getLong("monthlyCycleMatches");
            if (stars == null) stars = 0L;
            if (tokens == null) tokens = 0L;
            if (weeklyCycleStars == null) weeklyCycleStars = 0L;
            if (monthlyCycleStars == null) monthlyCycleStars = 0L;
            if (weeklyCycleMatches == null) weeklyCycleMatches = 0L;
            if (monthlyCycleMatches == null) monthlyCycleMatches = 0L;

            long newStars = stars;
            long actualDelta = 0L;
            long newTokens = tokens;

            if (!weeklyCycleId.equals(storedWeeklyCycleId)) {
                weeklyCycleStars = 0L;
                weeklyCycleMatches = 0L;
            }
            if (!monthlyCycleId.equals(storedMonthlyCycleId)) {
                monthlyCycleStars = 0L;
                monthlyCycleMatches = 0L;
            }
            long newWeeklyCycleStars = weeklyCycleStars + actualDelta;
            long newMonthlyCycleStars = monthlyCycleStars + actualDelta;
            long newWeeklyCycleMatches = weeklyCycleMatches + 1;
            long newMonthlyCycleMatches = monthlyCycleMatches + 1;
            String username = value(user.getString("username"));
            long league = value(user.getLong("league"));

            transaction.update(
                    ref,
                    "stars", newStars,
                    "tokens", newTokens,
                    "weeklyCycleId", weeklyCycleId,
                    "monthlyCycleId", monthlyCycleId,
                    "weeklyCycleStars", newWeeklyCycleStars,
                    "monthlyCycleStars", newMonthlyCycleStars,
                    "weeklyCycleMatches", newWeeklyCycleMatches,
                    "monthlyCycleMatches", newMonthlyCycleMatches
            );
            incrementCycle(transaction, uid, username, league, weeklyCycleId, actualDelta, false);
            incrementCycle(transaction, uid, username, league, monthlyCycleId, actualDelta, true);
            Map<String, Long> out = new HashMap<>();
            out.put("stars", newStars);
            out.put("tokens", newTokens);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError("Neuspesna obrada neresenog rezultata."));
    }

    public void applyForfeitLoserPenalty(String uid, EconomyCallback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        String weeklyCycleId = currentWeeklyCycleId();
        String monthlyCycleId = currentMonthlyCycleId();
        db.runTransaction((Transaction.Function<Map<String, Long>>) transaction -> {
            DocumentSnapshot user = transaction.get(ref);
            Long stars = user.getLong("stars");
            Long tokens = user.getLong("tokens");
            String storedWeeklyCycleId = value(user.getString("weeklyCycleId"));
            String storedMonthlyCycleId = value(user.getString("monthlyCycleId"));
            Long weeklyCycleStars = user.getLong("weeklyCycleStars");
            Long monthlyCycleStars = user.getLong("monthlyCycleStars");
            Long weeklyCycleMatches = user.getLong("weeklyCycleMatches");
            Long monthlyCycleMatches = user.getLong("monthlyCycleMatches");
            if (stars == null) stars = 0L;
            if (tokens == null) tokens = 0L;
            if (weeklyCycleStars == null) weeklyCycleStars = 0L;
            if (monthlyCycleStars == null) monthlyCycleStars = 0L;
            if (weeklyCycleMatches == null) weeklyCycleMatches = 0L;
            if (monthlyCycleMatches == null) monthlyCycleMatches = 0L;

            long newStars = Math.max(0, stars - 10);
            long actualDelta = newStars - stars;

            if (!weeklyCycleId.equals(storedWeeklyCycleId)) {
                weeklyCycleStars = 0L;
                weeklyCycleMatches = 0L;
            }
            if (!monthlyCycleId.equals(storedMonthlyCycleId)) {
                monthlyCycleStars = 0L;
                monthlyCycleMatches = 0L;
            }
            long newWeeklyCycleStars = weeklyCycleStars + actualDelta;
            long newMonthlyCycleStars = monthlyCycleStars + actualDelta;
            long newWeeklyCycleMatches = weeklyCycleMatches + 1;
            long newMonthlyCycleMatches = monthlyCycleMatches + 1;
            String username = value(user.getString("username"));
            long league = value(user.getLong("league"));

            transaction.update(
                    ref,
                    "stars", newStars,
                    "weeklyCycleId", weeklyCycleId,
                    "monthlyCycleId", monthlyCycleId,
                    "weeklyCycleStars", newWeeklyCycleStars,
                    "monthlyCycleStars", newMonthlyCycleStars,
                    "weeklyCycleMatches", newWeeklyCycleMatches,
                    "monthlyCycleMatches", newMonthlyCycleMatches
            );
            incrementCycle(transaction, uid, username, league, weeklyCycleId, actualDelta, false);
            incrementCycle(transaction, uid, username, league, monthlyCycleId, actualDelta, true);
            Map<String, Long> out = new HashMap<>();
            out.put("stars", newStars);
            out.put("tokens", tokens);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError("Neuspesna obrada forfeit kazne."));
    }

    private String currentWeeklyCycleId() {
        Calendar start = Calendar.getInstance();
        start.setFirstDayOfWeek(Calendar.MONDAY);
        start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return "W_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(start.getTimeInMillis()));
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

    private void incrementCycle(
            Transaction transaction,
            String uid,
            String username,
            long league,
            String cycleId,
            long starDelta,
            boolean monthly
    ) {
        if (cycleId.isEmpty()) return;
        writeCycleMetadata(transaction, cycleId, monthly);
        Map<String, Object> entry = new HashMap<>();
        entry.put("username", username);
        entry.put("league", league);
        entry.put("cycleStars", FieldValue.increment(starDelta));
        entry.put("cycleMatches", FieldValue.increment(1));
        entry.put("updatedAtMillis", System.currentTimeMillis());
        transaction.set(
                db.collection("leaderboardCycles").document(cycleId)
                        .collection("entries").document(uid),
                entry,
                SetOptions.merge()
        );
    }

    private void archiveCycle(
            Transaction transaction,
            String uid,
            String username,
            long league,
            String cycleId,
            long cycleStars,
            long cycleMatches,
            boolean monthly
    ) {
        if (cycleId.isEmpty() || cycleMatches <= 0) return;
        writeCycleMetadata(transaction, cycleId, monthly);
        Map<String, Object> entry = new HashMap<>();
        entry.put("username", username);
        entry.put("league", league);
        entry.put("cycleStars", cycleStars);
        entry.put("cycleMatches", cycleMatches);
        entry.put("updatedAtMillis", System.currentTimeMillis());
        transaction.set(
                db.collection("leaderboardCycles").document(cycleId)
                        .collection("entries").document(uid),
                entry,
                SetOptions.merge()
        );
    }

    private void writeCycleMetadata(Transaction transaction, String cycleId, boolean monthly) {
        long[] window = cycleWindow(cycleId, monthly);
        if (window == null) return;
        Map<String, Object> cycle = new HashMap<>();
        cycle.put("cycleId", cycleId);
        cycle.put("monthly", monthly);
        cycle.put("startMs", window[0]);
        cycle.put("endMs", window[1]);
        cycle.put("processed", false);
        cycle.put("rewardsProcessed", false);
        cycle.put("updatedAtMillis", System.currentTimeMillis());
        transaction.set(
                db.collection("leaderboardCycles").document(cycleId),
                cycle,
                SetOptions.merge()
        );
    }

    private long[] cycleWindow(String cycleId, boolean monthly) {
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

    private String value(String input) {
        return input == null ? "" : input;
    }

    private long value(Long input) {
        return input == null ? 0L : input;
    }

    private String errorDetails(Throwable throwable) {
        StringBuilder out = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                if (out.length() > 0) out.append(" ");
                out.append(current.getMessage());
            }
            current = current.getCause();
        }
        return out.toString();
    }
}
