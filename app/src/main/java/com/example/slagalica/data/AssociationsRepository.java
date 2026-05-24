package com.example.slagalica.data;

import androidx.annotation.NonNull;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalica.model.AssociationPuzzle;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssociationsRepository {

    public interface PuzzlesCallback {
        void onLoaded(List<AssociationPuzzle> puzzles);
    }

    private final FirebaseFirestore db;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public AssociationsRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void getPuzzles(PuzzlesCallback callback) {
        boolean[] delivered = {false};
        db.collection("games_associations")
                .get(Source.CACHE)
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        List<AssociationPuzzle> cached = mapPuzzles(task);
                        if (cached.size() >= 2) {
                            delivered[0] = true;
                            callback.onLoaded(cached);
                            return;
                        }

                        db.collection("games_associations")
                                .get(Source.SERVER)
                                .addOnCompleteListener(serverTask -> {
                                    if (delivered[0]) {
                                        return;
                                    }
                                    List<AssociationPuzzle> fromServer = mapPuzzles(serverTask);
                                    if (fromServer.size() >= 2) {
                                        delivered[0] = true;
                                        callback.onLoaded(fromServer);
                                        return;
                                    }
                                    delivered[0] = true;
                                    seedDefaultsToFirestore();
                                    callback.onLoaded(getLocalPuzzles());
                                });
                        handler.postDelayed(() -> {
                            if (delivered[0]) {
                                return;
                            }
                            delivered[0] = true;
                            seedDefaultsToFirestore();
                            callback.onLoaded(getLocalPuzzles());
                        }, 1500L);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private List<AssociationPuzzle> mapPuzzles(Task<QuerySnapshot> task) {
        List<AssociationPuzzle> puzzles = new ArrayList<>();
        if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
            return puzzles;
        }

        for (QueryDocumentSnapshot doc : task.getResult()) {
            String title = doc.getString("title");
            List<List<String>> columns = mapColumns(doc.get("columns"));
            List<String> columnSolutions = (List<String>) doc.get("columnSolutions");
            String finalSolution = doc.getString("finalSolution");
            if (isValidPuzzle(columns, columnSolutions, finalSolution)) {
                puzzles.add(new AssociationPuzzle(title == null ? "" : title, columns, columnSolutions, finalSolution));
            }
        }
        return puzzles;
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> mapColumns(Object value) {
        List<List<String>> columns = new ArrayList<>();
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            for (int i = 0; i < 4; i++) {
                Object column = map.get("column" + i);
                if (!(column instanceof List)) {
                    return new ArrayList<>();
                }
                columns.add((List<String>) column);
            }
            return columns;
        }
        if (value instanceof List) {
            List<?> raw = (List<?>) value;
            for (Object item : raw) {
                if (!(item instanceof List)) {
                    return new ArrayList<>();
                }
                columns.add((List<String>) item);
            }
        }
        return columns;
    }

    private boolean isValidPuzzle(List<List<String>> columns, List<String> columnSolutions, String finalSolution) {
        if (columns == null || columnSolutions == null || finalSolution == null || columns.size() != 4 || columnSolutions.size() != 4) {
            return false;
        }
        for (List<String> column : columns) {
            if (column == null || column.size() != 4) {
                return false;
            }
        }
        return !finalSolution.trim().isEmpty();
    }

    private void seedDefaultsToFirestore() {
        List<AssociationPuzzle> defaults = getLocalPuzzles();
        WriteBatch batch = db.batch();

        for (int i = 0; i < defaults.size(); i++) {
            AssociationPuzzle puzzle = defaults.get(i);
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> columns = new HashMap<>();
            for (int column = 0; column < puzzle.getColumns().size(); column++) {
                columns.put("column" + column, puzzle.getColumns().get(column));
            }
            data.put("title", puzzle.getTitle());
            data.put("columns", columns);
            data.put("columnSolutions", puzzle.getColumnSolutions());
            data.put("finalSolution", puzzle.getFinalSolution());
            batch.set(db.collection("games_associations").document("puzzle_" + (i + 1)), data);
        }
        batch.commit();
    }

    private List<AssociationPuzzle> getLocalPuzzles() {
        List<AssociationPuzzle> puzzles = new ArrayList<>();

        puzzles.add(new AssociationPuzzle(
                "Priroda",
                Arrays.asList(
                        Arrays.asList("Sneg", "Deda Mraz", "Sanke", "Novogodisnja jelka"),
                        Arrays.asList("Mleko", "Jogurt", "Pavlaka", "Sir"),
                        Arrays.asList("Motor", "Tocak", "Volan", "Kocnica"),
                        Arrays.asList("Vuk", "Lija", "Medved", "Zec")
                ),
                Arrays.asList("Zima", "Mlecni proizvodi", "Auto", "Suma"),
                "Priroda"
        ));
        puzzles.add(new AssociationPuzzle(
                "Evropa",
                Arrays.asList(
                        Arrays.asList("Tastatura", "Mis", "Monitor", "Procesor"),
                        Arrays.asList("Berlin", "Pariz", "Rim", "Madrid"),
                        Arrays.asList("Dunav", "Sava", "Morava", "Drina"),
                        Arrays.asList("Mocart", "Betoven", "Bach", "Vivaldi")
                ),
                Arrays.asList("Racunar", "Glavni gradovi", "Reke", "Kompozitori"),
                "Evropa"
        ));
        puzzles.add(new AssociationPuzzle(
                "More",
                Arrays.asList(
                        Arrays.asList("Sidro", "Paluba", "Kapetan", "Jedro"),
                        Arrays.asList("So", "Talas", "Plaza", "Skoljka"),
                        Arrays.asList("Peraja", "Krljust", "Akvarijum", "Udica"),
                        Arrays.asList("Svetionik", "Luka", "Dok", "Mornar")
                ),
                Arrays.asList("Brod", "Obala", "Riba", "Pristaniste"),
                "More"
        ));

        return puzzles;
    }
}
