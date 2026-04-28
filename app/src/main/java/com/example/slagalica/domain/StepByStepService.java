package com.example.slagalica.domain;

import com.example.slagalica.data.StepByStepRepository;
import com.example.slagalica.model.StepByStepPuzzle;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class StepByStepService {

    public interface PuzzleCallback {
        void onLoaded(StepByStepPuzzle puzzle);
    }

    private final StepByStepRepository repository;
    private final Random random;

    public StepByStepService(StepByStepRepository repository) {
        this.repository = repository;
        this.random = new Random();
    }

    public void getRandomPuzzle(PuzzleCallback callback) {
        repository.getPuzzles(new StepByStepRepository.PuzzlesCallback() {
            @Override
            public void onLoaded(List<StepByStepPuzzle> puzzles) {
                callback.onLoaded(puzzles.get(random.nextInt(puzzles.size())));
            }
        });
    }

    public boolean isCorrectAnswer(String input, String correctAnswer) {
        if (input == null || correctAnswer == null) {
            return false;
        }
        return normalize(input).equals(normalize(correctAnswer));
    }

    public int pointsForStep(int revealedStepCount) {
        int points = 20 - (revealedStepCount - 1) * 2;
        return Math.max(points, 8);
    }

    private String normalize(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
