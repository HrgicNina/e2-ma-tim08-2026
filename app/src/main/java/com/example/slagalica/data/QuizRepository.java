package com.example.slagalica.data;

import androidx.annotation.NonNull;

import com.example.slagalica.model.QuizQuestion;
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

public class QuizRepository {

    public interface QuestionsCallback {
        void onLoaded(List<QuizQuestion> questions);
    }

    private final FirebaseFirestore db;

    public QuizRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void getQuestions(QuestionsCallback callback) {
        db.collection("games_quiz")
                .get(Source.CACHE)
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        List<QuizQuestion> cached = mapQuestions(task);
                        if (cached.size() >= 5) {
                            callback.onLoaded(cached);
                            return;
                        }

                        db.collection("games_quiz")
                                .get(Source.SERVER)
                                .addOnCompleteListener(serverTask -> {
                                    List<QuizQuestion> fromServer = mapQuestions(serverTask);
                                    if (fromServer.size() >= 5) {
                                        callback.onLoaded(fromServer);
                                        return;
                                    }
                                    seedDefaultsToFirestore();
                                    callback.onLoaded(getLocalQuestions());
                                });
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private List<QuizQuestion> mapQuestions(Task<QuerySnapshot> task) {
        List<QuizQuestion> questions = new ArrayList<>();
        if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
            return questions;
        }

        for (QueryDocumentSnapshot doc : task.getResult()) {
            String question = doc.getString("question");
            List<String> answers = (List<String>) doc.get("answers");
            Long correctAnswerIndex = doc.getLong("correctAnswerIndex");
            if (question == null || answers == null || answers.size() != 4 || correctAnswerIndex == null) {
                continue;
            }
            int correctIndex = correctAnswerIndex.intValue();
            if (correctIndex < 0 || correctIndex > 3) {
                continue;
            }
            questions.add(new QuizQuestion(question, answers, correctIndex));
        }
        return questions;
    }

    private void seedDefaultsToFirestore() {
        List<QuizQuestion> defaults = getLocalQuestions();
        WriteBatch batch = db.batch();

        for (int i = 0; i < defaults.size(); i++) {
            QuizQuestion question = defaults.get(i);
            Map<String, Object> data = new HashMap<>();
            data.put("question", question.getQuestion());
            data.put("answers", question.getAnswers());
            data.put("correctAnswerIndex", question.getCorrectAnswerIndex());
            batch.set(db.collection("games_quiz").document("question_" + (i + 1)), data);
        }
        batch.commit();
    }

    private List<QuizQuestion> getLocalQuestions() {
        List<QuizQuestion> questions = new ArrayList<>();

        questions.add(new QuizQuestion(
                "Koja reka prolazi kroz Beograd?",
                Arrays.asList("Sava", "Morava", "Drina", "Tisa"),
                0
        ));
        questions.add(new QuizQuestion(
                "Koliko kontinenata postoji na Zemlji?",
                Arrays.asList("Pet", "Sest", "Sedam", "Osam"),
                2
        ));
        questions.add(new QuizQuestion(
                "Ko je napisao roman Na Drini cuprija?",
                Arrays.asList("Mesa Selimovic", "Ivo Andric", "Branko Copic", "Jovan Ducic"),
                1
        ));
        questions.add(new QuizQuestion(
                "Koji je hemijski simbol za zlato?",
                Arrays.asList("Ag", "Au", "Fe", "Zn"),
                1
        ));
        questions.add(new QuizQuestion(
                "Koja planeta je najbliza Suncu?",
                Arrays.asList("Venera", "Mars", "Merkur", "Jupiter"),
                2
        ));
        questions.add(new QuizQuestion(
                "Koji grad je glavni grad Italije?",
                Arrays.asList("Milano", "Rim", "Napulj", "Torino"),
                1
        ));
        questions.add(new QuizQuestion(
                "Koliko igraca ima jedan fudbalski tim na terenu?",
                Arrays.asList("9", "10", "11", "12"),
                2
        ));

        return questions;
    }
}
