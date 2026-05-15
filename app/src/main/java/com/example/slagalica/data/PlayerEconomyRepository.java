package com.example.slagalica.data;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class PlayerEconomyRepository {

    public interface EconomyCallback {
        void onSuccess(Map<String, Long> values);

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
            if (lastGrantAt < todayStart) {
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
        db.runTransaction((Transaction.Function<Map<String, Long>>) transaction -> {
            Long stars = transaction.get(ref).getLong("stars");
            Long tokens = transaction.get(ref).getLong("tokens");
            if (stars == null) stars = 0L;
            if (tokens == null) tokens = 0L;

            long bonusFromScore = Math.max(0, score / 40);
            long delta = winner ? (10 + bonusFromScore) : (bonusFromScore - 10);
            long newStars = Math.max(0, stars + delta);

            long oldTokenMilestones = stars / 50;
            long newTokenMilestones = newStars / 50;
            long extraTokens = Math.max(0, newTokenMilestones - oldTokenMilestones);
            long newTokens = tokens + extraTokens;

            transaction.update(ref, "stars", newStars, "tokens", newTokens);

            Map<String, Long> out = new HashMap<>();
            out.put("stars", newStars);
            out.put("tokens", newTokens);
            return out;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError("Neuspesna obrada rezultata partije."));
    }
}
