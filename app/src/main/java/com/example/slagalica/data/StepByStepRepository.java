package com.example.slagalica.data;

import androidx.annotation.NonNull;

import com.example.slagalica.model.StepByStepPuzzle;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StepByStepRepository {

    public interface PuzzlesCallback {
        void onLoaded(List<StepByStepPuzzle> puzzles);
    }

    private final FirebaseFirestore db;

    public StepByStepRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void getPuzzles(PuzzlesCallback callback) {
        db.collection("games_step_by_step")
                .get(Source.CACHE)
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        List<StepByStepPuzzle> cached = mapPuzzles(task);
                        if (!cached.isEmpty()) {
                            callback.onLoaded(cached);
                            return;
                        }

                        db.collection("games_step_by_step")
                                .get(Source.SERVER)
                                .addOnCompleteListener(serverTask -> {
                                    List<StepByStepPuzzle> fromServer = mapPuzzles(serverTask);
                                    if (!fromServer.isEmpty()) {
                                        callback.onLoaded(fromServer);
                                        return;
                                    }
                                    seedDefaultsToFirestore();
                                    callback.onLoaded(getLocalPuzzles());
                                });
                    }
                });
    }

    private List<StepByStepPuzzle> mapPuzzles(Task<QuerySnapshot> task) {
        List<StepByStepPuzzle> puzzles = new ArrayList<>();
        if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
            return puzzles;
        }

        for (QueryDocumentSnapshot doc : task.getResult()) {
            String answer = doc.getString("answer");
            List<String> clues = (List<String>) doc.get("clues");
            if (answer == null || clues == null || clues.size() != 7) {
                continue;
            }
            puzzles.add(new StepByStepPuzzle(clues, answer));
        }
        return puzzles;
    }

    private void seedDefaultsToFirestore() {
        List<StepByStepPuzzle> defaults = getLocalPuzzles();
        WriteBatch batch = db.batch();

        for (int i = 0; i < defaults.size(); i++) {
            StepByStepPuzzle puzzle = defaults.get(i);
            String docId = "puzzle_" + (i + 1);
            batch.set(db.collection("games_step_by_step").document(docId),
                    new java.util.HashMap<String, Object>() {{
                        put("answer", puzzle.getAnswer());
                        put("clues", puzzle.getClues());
                    }});
        }
        batch.commit();
    }

    private List<StepByStepPuzzle> getLocalPuzzles() {
        List<StepByStepPuzzle> puzzles = new ArrayList<>();

        puzzles.add(new StepByStepPuzzle(Arrays.asList(
                "Rodjen u Smiljanu.",
                "Poznat po naizmenicnoj struji.",
                "Rivalitet sa Edisonom.",
                "Bavio se elektromagnetizmom.",
                "Njegovo ime nosi jedinica magnetne indukcije.",
                "Bio je srpsko-americki pronalazac.",
                "Prezime pocinje na T."
        ), "Nikola Tesla"));

        puzzles.add(new StepByStepPuzzle(Arrays.asList(
                "Glavni grad Srbije.",
                "Nalazi se na uscu Save u Dunav.",
                "Ima Kalemegdan.",
                "Najveci grad u drzavi.",
                "Poznat po Knez Mihailovoj ulici.",
                "Ima opstine Zemun i Novi Beograd.",
                "Pocinje na B."
        ), "Beograd"));

        puzzles.add(new StepByStepPuzzle(Arrays.asList(
                "Voce crvene boje.",
                "Cesto se koristi u slatkisima i kolacima.",
                "Raste na drvetu.",
                "Moze biti kisela ili slatka.",
                "Postoji i pita sa ovim vocem.",
                "Na engleskom je apple.",
                "Pocinje na J."
        ), "Jabuka"));

        return puzzles;
    }
}
