package com.example.slagalica.data;

import com.example.slagalica.model.PlayerStats;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class PlayerStatsRepository {

    public interface StatsCallback {
        void onSuccess(PlayerStats stats);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadStats(String uid, StatsCallback callback) {
        db.collection("users")
                .document(uid)
                .collection("stats")
                .document("summary")
                .get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(fromSnapshot(snapshot)))
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam statistiku."));
    }

    public void recordGameStats(String uid, String gameId, int points, int maxPoints, Map<String, Long> details, ActionCallback callback) {
        if (uid == null || uid.trim().isEmpty() || gameId == null || gameId.trim().isEmpty()) {
            callback.onError("Nedostaje korisnik ili igra.");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        String prefix = normalizedGameId(gameId);
        updates.put(prefix + "PointsTotal", FieldValue.increment(Math.max(0, points)));
        updates.put(prefix + "MaxPointsTotal", FieldValue.increment(Math.max(0, maxPoints)));
        updates.put(prefix + "Plays", FieldValue.increment(1));
        if (details != null) {
            for (Map.Entry<String, Long> entry : details.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                updates.put(entry.getKey(), FieldValue.increment(entry.getValue()));
            }
        }
        merge(uid, updates, callback);
    }

    public void recordMatchResult(String uid, boolean win, boolean draw, ActionCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError("Nedostaje korisnik.");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("matchesTotal", FieldValue.increment(1));
        if (draw) {
            updates.put("draws", FieldValue.increment(1));
        } else if (win) {
            updates.put("wins", FieldValue.increment(1));
        } else {
            updates.put("losses", FieldValue.increment(1));
        }
        merge(uid, updates, callback);
    }

    private void merge(String uid, Map<String, Object> updates, ActionCallback callback) {
        db.collection("users")
                .document(uid)
                .collection("stats")
                .document("summary")
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Statistika nije sacuvana."));
    }

    private PlayerStats fromSnapshot(DocumentSnapshot snapshot) {
        PlayerStats stats = new PlayerStats();
        if (snapshot == null || !snapshot.exists()) {
            return stats;
        }
        stats.matchesTotal = value(snapshot, "matchesTotal");
        stats.wins = value(snapshot, "wins");
        stats.losses = value(snapshot, "losses");
        stats.draws = value(snapshot, "draws");

        fillGame(stats, snapshot, "quiz");
        fillGame(stats, snapshot, "connections");
        fillGame(stats, snapshot, "associations");
        fillGame(stats, snapshot, "master");
        fillGame(stats, snapshot, "step");
        fillGame(stats, snapshot, "number");

        stats.quizCorrect = value(snapshot, "quizCorrect");
        stats.quizWrong = value(snapshot, "quizWrong");
        stats.quizNoAnswer = value(snapshot, "quizNoAnswer");
        stats.connectionsMatched = value(snapshot, "connectionsMatched");
        stats.connectionsMissed = value(snapshot, "connectionsMissed");
        stats.numberExact = value(snapshot, "numberExact");
        stats.numberDistance1To4 = value(snapshot, "numberDistance1To4");
        stats.numberDistance5To9 = value(snapshot, "numberDistance5To9");
        stats.numberDistance10To19 = value(snapshot, "numberDistance10To19");
        stats.numberDistance20To49 = value(snapshot, "numberDistance20To49");
        stats.numberDistance50To99 = value(snapshot, "numberDistance50To99");
        stats.numberDistance100Plus = value(snapshot, "numberDistance100Plus");

        for (int i = 0; i < stats.associationsSolvedCounts.length; i++) {
            stats.associationsSolvedCounts[i] = value(snapshot, "associationsSolved" + i);
        }
        for (int i = 1; i < stats.stepSolvedAt.length; i++) {
            stats.stepSolvedAt[i] = value(snapshot, "stepSolvedAt" + i);
        }
        stats.stepSolvedAt[0] = value(snapshot, "stepUnsolved");
        for (int i = 1; i < stats.mastermindAttempts.length; i++) {
            stats.mastermindAttempts[i] = value(snapshot, "masterAttempt" + i);
        }
        stats.mastermindAttempts[0] = value(snapshot, "masterUnsolved");
        return stats;
    }

    private void fillGame(PlayerStats stats, DocumentSnapshot snapshot, String gameId) {
        String prefix = normalizedGameId(gameId);
        stats.pointsTotal.put(gameId, value(snapshot, prefix + "PointsTotal"));
        stats.maxPointsTotal.put(gameId, value(snapshot, prefix + "MaxPointsTotal"));
        stats.plays.put(gameId, value(snapshot, prefix + "Plays"));
    }

    private String normalizedGameId(String gameId) {
        if ("mastermind".equals(gameId)) {
            return "master";
        }
        return gameId;
    }

    private long value(DocumentSnapshot snapshot, String key) {
        Long value = snapshot.getLong(key);
        return value == null ? 0L : value;
    }
}
