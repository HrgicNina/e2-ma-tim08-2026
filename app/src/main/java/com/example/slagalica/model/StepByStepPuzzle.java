package com.example.slagalica.model;

import java.util.List;

public class StepByStepPuzzle {
    private final List<String> clues;
    private final String answer;

    public StepByStepPuzzle(List<String> clues, String answer) {
        this.clues = clues;
        this.answer = answer;
    }

    public List<String> getClues() {
        return clues;
    }

    public String getAnswer() {
        return answer;
    }
}
