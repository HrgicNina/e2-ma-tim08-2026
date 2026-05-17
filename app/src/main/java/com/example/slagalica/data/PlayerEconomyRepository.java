package com.example.slagalica.data;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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
            Long tokens = transaction.get(ref).getLong("tokens");
            Long stars = transaction.get(ref).getLong("stars");
            Long league = transaction.get(ref).getLong("league");
            Long lastGrantAt = transaction.get(ref).getLong("lastDailyTokenGrantAt");
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
            if (lastGrantAt <= 0L) {
                transaction.update(ref, "lastDailyTokenGrantAt", System.currentTimeMillis());
            } else if (lastGrantAt < todayStart) {
                newTokens = tokens + 5;
                transaction.update(ref, "tokens", newTokens, "lastDailyTokenGrantAt", System.currentTimeMillis());
            }

            Map<String, Long> out = new HashMap<>();
            out.put("tokens", newTokens);
            out.put("stars", stars);
            out.put("league", league);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError("Neuspesna dnevna dodela tokena."));
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
                    if (e.getMessage() != null && e.getMessage().contains("NO_TOKENS")) {
                        msg = "Nemate dovoljno tokena za partiju.";
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
            Long stars = transaction.get(ref).getLong("stars");
            Long tokens = transaction.get(ref).getLong("tokens");
            String storedWeeklyCycleId = value(transaction.get(ref).getString("weeklyCycleId"));
            String storedMonthlyCycleId = value(transaction.get(ref).getString("monthlyCycleId"));
            Long weeklyCycleStars = transaction.get(ref).getLong("weeklyCycleStars");
            Long monthlyCycleStars = transaction.get(ref).getLong("monthlyCycleStars");
            Long weeklyCycleMatches = transaction.get(ref).getLong("weeklyCycleMatches");
            Long monthlyCycleMatches = transaction.get(ref).getLong("monthlyCycleMatches");
            if (stars == null) stars = 0L;
            if (tokens == null) tokens = 0L;
            if (weeklyCycleStars == null) weeklyCycleStars = 0L;
            if (monthlyCycleStars == null) monthlyCycleStars = 0L;
            if (weeklyCycleMatches == null) weeklyCycleMatches = 0L;
            if (monthlyCycleMatches == null) monthlyCycleMatches = 0L;

            long bonusFromScore = Math.max(0, score / 40);
            long delta = winner ? (10 + bonusFromScore) : (bonusFromScore - 10);
            long newStars = Math.max(0, stars + delta);
            long actualDelta = newStars - stars;

            long oldTokenMilestones = stars / 50;
            long newTokenMilestones = newStars / 50;
            long extraTokens = Math.max(0, newTokenMilestones - oldTokenMilestones);
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

            Map<String, Long> out = new HashMap<>();
            out.put("stars", newStars);
            out.put("tokens", newTokens);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError("Neuspesna obrada rezultata partije."));
    }

    public void applyForfeitLoserPenalty(String uid, EconomyCallback callback) {
        DocumentReference ref = db.collection("users").document(uid);
        String weeklyCycleId = currentWeeklyCycleId();
        String monthlyCycleId = currentMonthlyCycleId();
        db.runTransaction((Transaction.Function<Map<String, Long>>) transaction -> {
            Long stars = transaction.get(ref).getLong("stars");
            Long tokens = transaction.get(ref).getLong("tokens");
            String storedWeeklyCycleId = value(transaction.get(ref).getString("weeklyCycleId"));
            String storedMonthlyCycleId = value(transaction.get(ref).getString("monthlyCycleId"));
            Long weeklyCycleStars = transaction.get(ref).getLong("weeklyCycleStars");
            Long monthlyCycleStars = transaction.get(ref).getLong("monthlyCycleStars");
            Long weeklyCycleMatches = transaction.get(ref).getLong("weeklyCycleMatches");
            Long monthlyCycleMatches = transaction.get(ref).getLong("monthlyCycleMatches");
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

    private String value(String input) {
        return input == null ? "" : input;
    }
}
