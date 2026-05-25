package com.example.slagalica.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizQuestion {
    private final String question;
    private final List<String> answers;
    private final int correctAnswerIndex;

    public QuizQuestion(String question, List<String> answers, int correctAnswerIndex) {
        this.question = question;
        this.answers = Collections.unmodifiableList(new ArrayList<>(answers));
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public int getCorrectAnswerIndex() {
        return correctAnswerIndex;
    }
}
