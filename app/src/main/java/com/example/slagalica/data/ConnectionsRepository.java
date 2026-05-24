package com.example.slagalica.data;

import androidx.annotation.NonNull;

import com.example.slagalica.model.ConnectionRound;
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

public class ConnectionsRepository {

    public interface RoundsCallback {
        void onLoaded(List<ConnectionRound> rounds);
    }

    private final FirebaseFirestore db;

    public ConnectionsRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void getRounds(RoundsCallback callback) {
        db.collection("games_connections")
                .get(Source.CACHE)
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        List<ConnectionRound> cached = mapRounds(task);
                        if (cached.size() >= 2) {
                            callback.onLoaded(cached);
                            return;
                        }

                        db.collection("games_connections")
                                .get(Source.SERVER)
                                .addOnCompleteListener(serverTask -> {
                                    List<ConnectionRound> fromServer = mapRounds(serverTask);
                                    if (fromServer.size() >= 2) {
                                        callback.onLoaded(fromServer);
                                        return;
                                    }
                                    seedDefaultsToFirestore();
                                    callback.onLoaded(getLocalRounds());
                                });
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private List<ConnectionRound> mapRounds(Task<QuerySnapshot> task) {
        List<ConnectionRound> rounds = new ArrayList<>();
        if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
            return rounds;
        }

        for (QueryDocumentSnapshot doc : task.getResult()) {
            String title = doc.getString("title");
            List<String> left = (List<String>) doc.get("leftItems");
            List<String> right = (List<String>) doc.get("rightItems");
            List<Long> mappingValues = (List<Long>) doc.get("mapping");
            if (left == null || right == null || mappingValues == null || left.size() != 5 || right.size() != 5 || mappingValues.size() != 5) {
                continue;
            }
            List<Integer> mapping = new ArrayList<>();
            boolean valid = true;
            for (Long value : mappingValues) {
                if (value == null || value < 0 || value > 4) {
                    valid = false;
                    break;
                }
                mapping.add(value.intValue());
            }
            if (valid) {
                rounds.add(new ConnectionRound(title == null ? "" : title, left, right, mapping));
            }
        }
        return rounds;
    }

    private void seedDefaultsToFirestore() {
        List<ConnectionRound> defaults = getLocalRounds();
        WriteBatch batch = db.batch();

        for (int i = 0; i < defaults.size(); i++) {
            ConnectionRound round = defaults.get(i);
            Map<String, Object> data = new HashMap<>();
            data.put("title", round.getTitle());
            data.put("leftItems", round.getLeftItems());
            data.put("rightItems", round.getRightItems());
            data.put("mapping", round.getMapping());
            batch.set(db.collection("games_connections").document("round_" + (i + 1)), data);
        }
        batch.commit();
    }

    private List<ConnectionRound> getLocalRounds() {
        List<ConnectionRound> rounds = new ArrayList<>();

        rounds.add(new ConnectionRound(
                "Izvodjaci i pesme",
                Arrays.asList("Zeljko Joksimovic", "Marija Serifovic", "Bajaga", "Sasa Matic", "Zdravko Colic"),
                Arrays.asList("Lane moje", "Molitva", "Moji drugovi", "Maskara", "Ti si mi u krvi"),
                Arrays.asList(0, 1, 2, 3, 4)
        ));
        rounds.add(new ConnectionRound(
                "Licnosti i dela",
                Arrays.asList("Tesla", "Andric", "Pupin", "Milankovic", "Mokranjac"),
                Arrays.asList("Na Drini cuprija", "Himna Vuku", "Milutin Milankovic", "Naizmenicna struja", "Idvorske price"),
                Arrays.asList(3, 0, 4, 2, 1)
        ));
        rounds.add(new ConnectionRound(
                "Gradovi i drzave",
                Arrays.asList("Rim", "Madrid", "Berlin", "Pariz", "Atina"),
                Arrays.asList("Francuska", "Grcka", "Italija", "Nemacka", "Spanija"),
                Arrays.asList(2, 4, 3, 0, 1)
        ));

        return rounds;
    }
}
