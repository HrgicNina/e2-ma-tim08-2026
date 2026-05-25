package com.example.slagalica.domain;

import com.example.slagalica.data.AssociationsRepository;
import com.example.slagalica.model.AssociationPuzzle;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AssociationsGameService {
    public static final int ROUND_COUNT = 2;
    public static final int COLUMN_COUNT = 4;
    public static final int CLUE_COUNT = 4;
    public static final int TOTAL_CLUES = COLUMN_COUNT * CLUE_COUNT;
    public static final int ROUND_TIME_MILLIS = 120000;

    public interface PuzzlesCallback {
        void onLoaded(List<AssociationPuzzle> puzzles);
    }

    private final AssociationsRepository repository;

    public AssociationsGameService(AssociationsRepository repository) {
        this.repository = repository;
    }

    public void getGamePuzzles(PuzzlesCallback callback) {
        repository.getPuzzles(puzzles -> {
            List<AssociationPuzzle> gamePuzzles = new ArrayList<>(puzzles);
            Collections.shuffle(gamePuzzles);
            if (gamePuzzles.size() > ROUND_COUNT) {
                gamePuzzles = new ArrayList<>(gamePuzzles.subList(0, ROUND_COUNT));
            }
            callback.onLoaded(gamePuzzles);
        });
    }

    public boolean isColumnGuessCorrect(AssociationPuzzle puzzle, int column, String guess) {
        return puzzle != null
                && column >= 0
                && column < COLUMN_COUNT
                && normalize(guess).equals(normalize(puzzle.getColumnSolutions().get(column)));
    }

    public boolean isFinalGuessCorrect(AssociationPuzzle puzzle, String guess) {
        return puzzle != null && normalize(guess).equals(normalize(puzzle.getFinalSolution()));
    }

    public int scoreColumn(boolean[] openedClues, int column) {
        return 2 + countUnopenedInColumn(openedClues, column);
    }

    public int scoreFinal(boolean[] openedClues, boolean[] solvedColumns) {
        int points = 7;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            if (solvedColumns[column]) {
                continue;
            }
            if (hasOpenedClueInColumn(openedClues, column)) {
                points += scoreColumn(openedClues, column);
            } else {
                points += 6;
            }
        }
        return points;
    }

    public boolean hasAnyOpenedClue(boolean[] openedClues) {
        for (boolean opened : openedClues) {
            if (opened) {
                return true;
            }
        }
        return false;
    }

    public int countUnopenedInColumn(boolean[] openedClues, int column) {
        int unopened = 0;
        for (int row = 0; row < CLUE_COUNT; row++) {
            if (!openedClues[column * CLUE_COUNT + row]) {
                unopened++;
            }
        }
        return unopened;
    }

    public boolean hasOpenedClueInColumn(boolean[] openedClues, int column) {
        for (int row = 0; row < CLUE_COUNT; row++) {
            if (openedClues[column * CLUE_COUNT + row]) {
                return true;
            }
        }
        return false;
    }

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
