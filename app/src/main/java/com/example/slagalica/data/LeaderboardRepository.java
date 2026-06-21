package com.example.slagalica.data;

import com.example.slagalica.model.LeaderboardEntry;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

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

    public interface CyclesCallback {
        void onSuccess(List<CycleWindow> cycles);
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

    private static final long TWO_MINUTES_MS = 2 * 60 * 1000L;
    private static final long LEADERBOARD_PAGE_SIZE = 200L;

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

    public void loadCycle(String cycleId, LoadCallback callback) {
        if (cycleId == null || cycleId.trim().isEmpty()) {
            callback.onError("Ciklus nije izabran.");
            return;
        }
        db.collection("leaderboardCycles")
                .document(cycleId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document == null || !document.exists()) {
                        callback.onError("Izabrani ciklus ne postoji.");
                        return;
                    }
                    CycleWindow cycle = cycleFromDocument(document);
                    document.getReference().collection("entries").get()
                            .addOnSuccessListener(snapshot ->
                                    sortAndReturn(cycle, mapCycleEntries(snapshot), callback))
                            .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam izabrani ciklus."));
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam izabrani ciklus."));
    }

    public void loadCycles(boolean monthly, CyclesCallback callback) {
        CycleWindow current = monthly ? currentMonthlyCycle() : currentWeeklyCycle();
        db.collection("leaderboardCycles")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<CycleWindow> cycles = new ArrayList<>();
                    cycles.add(current);
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            Boolean documentMonthly = document.getBoolean("monthly");
                            if (documentMonthly == null || documentMonthly != monthly
                                    || current.id.equals(document.getId())) {
                                continue;
                            }
                            CycleWindow cycle = cycleFromDocument(document);
                            if (cycle.startMs > 0L && cycle.endMs > 0L) {
                                cycles.add(cycle);
                            }
                        }
                    }
                    cycles.subList(1, cycles.size()).sort((a, b) -> Long.compare(b.endMs, a.endMs));
                    callback.onSuccess(cycles);
                })
                .addOnFailureListener(e -> callback.onError("Ne mogu da ucitam istoriju ciklusa."));
    }

    public void processCycleRolloverAndRewards(ActionCallback callback) {
        callback.onSuccess();
    }

    private void loadLeaderboard(boolean monthly, LoadCallback callback) {
        CycleWindow cycle = monthly ? currentMonthlyCycle() : currentWeeklyCycle();
        db.collection("leaderboardCycles")
                .document(cycle.id)
                .collection("entries")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LeaderboardEntry> entries = mapCycleEntries(snapshot);
                    if (!entries.isEmpty()) {
                        loadCurrentLeaguesAndReturn(cycle, entries, callback);
                        return;
                    }
                    loadLegacyLeaderboard(monthly, cycle, callback);
                })
                .addOnFailureListener(e -> loadLegacyLeaderboard(monthly, cycle, callback));
    }

    private void loadLegacyLeaderboard(boolean monthly, CycleWindow cycle, LoadCallback callback) {
        String cycleIdField = monthly ? "monthlyCycleId" : "weeklyCycleId";
        String cycleStarsField = monthly ? "monthlyCycleStars" : "weeklyCycleStars";
        String cycleMatchesField = monthly ? "monthlyCycleMatches" : "weeklyCycleMatches";

        loadAllCycleUsers(cycleIdField, cycle.id, new CycleUsersCallback() {
            @Override
            public void onSuccess(List<DocumentSnapshot> documents) {
                List<LeaderboardEntry> entries = mapEntries(documents, cycleStarsField, cycleMatchesField);
                entries.sort((a, b) -> Long.compare(b.cycleStars, a.cycleStars));
                callback.onSuccess(cycle, entries);
            }

            @Override
            public void onError() {
                callback.onError("Ne mogu da ucitam rang listu.");
            }
        });
    }

    private List<LeaderboardEntry> mapEntries(List<DocumentSnapshot> documents, String starsField, String matchesField) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        if (documents == null || documents.isEmpty()) {
            return entries;
        }
        for (DocumentSnapshot doc : documents) {
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

    private void loadAllCycleUsers(String cycleIdField, String cycleId, CycleUsersCallback callback) {
        loadCycleUsersPage(cycleIdField, cycleId, null, new ArrayList<>(), callback);
    }

    private void loadCycleUsersPage(
            String cycleIdField,
            String cycleId,
            DocumentSnapshot lastDocument,
            List<DocumentSnapshot> collected,
            CycleUsersCallback callback
    ) {
        Query query = db.collection("users")
                .whereEqualTo(cycleIdField, cycleId)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                .limit(LEADERBOARD_PAGE_SIZE);
        if (lastDocument != null) {
            query = query.startAfter(lastDocument);
        }
        query.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onSuccess(collected);
                        return;
                    }
                    collected.addAll(snapshot.getDocuments());
                    if (snapshot.size() < LEADERBOARD_PAGE_SIZE) {
                        callback.onSuccess(collected);
                        return;
                    }
                    List<DocumentSnapshot> page = snapshot.getDocuments();
                    loadCycleUsersPage(
                            cycleIdField,
                            cycleId,
                            page.get(page.size() - 1),
                            collected,
                            callback
                    );
                })
                .addOnFailureListener(e -> callback.onError());
    }

    private interface CycleUsersCallback {
        void onSuccess(List<DocumentSnapshot> documents);
        void onError();
    }

    private List<LeaderboardEntry> mapCycleEntries(QuerySnapshot snapshot) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        if (snapshot == null) {
            return entries;
        }
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            long matches = value(doc.getLong("cycleMatches"));
            if (matches <= 0) {
                continue;
            }
            LeaderboardEntry item = new LeaderboardEntry();
            item.uid = doc.getId();
            item.username = value(doc.getString("username"));
            item.league = value(doc.getLong("league"));
            item.cycleStars = value(doc.getLong("cycleStars"));
            item.cycleMatches = matches;
            entries.add(item);
        }
        return entries;
    }

    private CycleWindow cycleFromDocument(DocumentSnapshot document) {
        return new CycleWindow(
                document.getId(),
                value(document.getLong("startMs")),
                value(document.getLong("endMs"))
        );
    }

    private void loadCurrentLeaguesAndReturn(
            CycleWindow cycle,
            List<LeaderboardEntry> entries,
            LoadCallback callback
    ) {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Long> leagueByUid = new HashMap<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot user : snapshot.getDocuments()) {
                            leagueByUid.put(user.getId(), value(user.getLong("league")));
                        }
                    }
                    for (LeaderboardEntry entry : entries) {
                        Long currentLeague = leagueByUid.get(entry.uid);
                        if (currentLeague != null) {
                            entry.league = currentLeague;
                        }
                    }
                    sortAndReturn(cycle, entries, callback);
                })
                .addOnFailureListener(e -> sortAndReturn(cycle, entries, callback));
    }

    private void sortAndReturn(CycleWindow cycle, List<LeaderboardEntry> entries, LoadCallback callback) {
        entries.sort((a, b) -> {
            int stars = Long.compare(b.cycleStars, a.cycleStars);
            if (stars != 0) return stars;
            return a.username.compareToIgnoreCase(b.username);
        });
        callback.onSuccess(cycle, entries);
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

        String id = "W_" + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(start.getTime());
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

        String id = "M_" + new SimpleDateFormat("yyyyMM", Locale.getDefault()).format(start.getTime());
        return new CycleWindow(id, start.getTimeInMillis(), end.getTimeInMillis());
    }

    private String value(String input) {
        return input == null ? "" : input;
    }

    private long value(Long input) {
        return input == null ? 0L : input;
    }
}
