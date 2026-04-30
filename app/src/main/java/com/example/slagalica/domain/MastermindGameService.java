package com.example.slagalica.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MastermindGameService {

    public static final int CODE_LENGTH = 4;
    private static final int SYMBOL_COUNT = 6;

    private final Random random = new Random();

    public int[] generateSecretCode() {
        int[] code = new int[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            code[i] = random.nextInt(SYMBOL_COUNT);
        }
        return code;
    }

    public GuessResult evaluateGuess(int[] secret, int[] guess) {
        boolean[] secretUsed = new boolean[CODE_LENGTH];
        boolean[] guessUsed = new boolean[CODE_LENGTH];

        int exact = 0;
        int colorOnly = 0;

        for (int i = 0; i < CODE_LENGTH; i++) {
            if (secret[i] == guess[i]) {
                exact++;
                secretUsed[i] = true;
                guessUsed[i] = true;
            }
        }

        for (int i = 0; i < CODE_LENGTH; i++) {
            if (guessUsed[i]) {
                continue;
            }
            for (int j = 0; j < CODE_LENGTH; j++) {
                if (secretUsed[j]) {
                    continue;
                }
                if (guess[i] == secret[j]) {
                    colorOnly++;
                    secretUsed[j] = true;
                    guessUsed[i] = true;
                    break;
                }
            }
        }

        return new GuessResult(exact, colorOnly, exact == CODE_LENGTH);
    }

    public int pointsForSolvedAttempt(int attemptNumber) {
        if (attemptNumber <= 2) {
            return 20;
        }
        if (attemptNumber <= 4) {
            return 15;
        }
        return 10;
    }

    public String symbolsToDisplay(int[] values) {
        List<String> symbols = new ArrayList<>();
        for (int value : values) {
            symbols.add(symbolName(value));
        }
        return String.join(" ", symbols);
    }

    public String symbolName(int value) {
        switch (value) {
            case 0:
                return "SMILE";
            case 1:
                return "CLUB";
            case 2:
                return "SPADE";
            case 3:
                return "HEART";
            case 4:
                return "DIAMOND";
            case 5:
                return "STAR";
            default:
                return "?";
        }
    }

    public static class GuessResult {
        public final int exact;
        public final int colorOnly;
        public final boolean solved;

        public GuessResult(int exact, int colorOnly, boolean solved) {
            this.exact = exact;
            this.colorOnly = colorOnly;
            this.solved = solved;
        }
    }
}
